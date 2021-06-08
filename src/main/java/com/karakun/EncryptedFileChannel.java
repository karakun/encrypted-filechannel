package com.karakun;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.Registry;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.proto.AesGcmKey;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

/**
 * An encrypted FileChannel based on google tink library
 */
public class EncryptedFileChannel extends FileChannel {

    // All instances of this class use a 12 byte IV and a 16 byte tag.
    private static final int IV_SIZE_IN_BYTES = 12;
    private static final int TAG_SIZE_IN_BYTES = 16;
    private static final int ENCRYPTION_OVERHEAD = IV_SIZE_IN_BYTES + TAG_SIZE_IN_BYTES;
    private final FileChannel base;

    /**
     * The current position within the file, from a user perspective.
     */
    private long pos = 0;

    private final Path path;
    private final OpenOption[] openOptions;
    private final Aead cryptoPrimitive;
    private final Object positionLock = new Object();
    private final Object writeLock;
    private final OnClose onClose;

    private EncryptedFileChannel(final Path path, final byte[] encryptionKey, final OnClose onClose, final OpenOption... openOptions) throws IOException {
        if (encryptionKey == null) {
            throw new IllegalStateException(String.format("Encryption key must not be null for file '%s'.", path));
        }
        this.openOptions = openOptions;
        this.path = path;
        final Set<OpenOption> openOptionsModified = Arrays.stream(openOptions).filter(it -> APPEND != it).collect(Collectors.toSet()); //APPEND needs to be manually implemented
        openOptionsModified.add(READ); //we always need READ (also for write only)
        this.base = FileChannel.open(path, openOptionsModified);
        this.writeLock = path.toAbsolutePath().toString().intern();
        this.onClose = onClose;
        try {
            cryptoPrimitive = constructCryptoPrimitive(encryptionKey);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    public static EncryptedFileChannel open(final Path path, final byte[] encryptionKey, final OpenOption... openOptions) throws IOException {
        return new EncryptedFileChannel(path, encryptionKey, null, openOptions);
    }

    public static EncryptedFileChannel open(Path path, byte[] encryptionKey, OnClose onClose, OpenOption... openOptions) throws IOException {
        return new EncryptedFileChannel(path, encryptionKey, onClose, openOptions);
    }


    @Override
    protected void implCloseChannel() throws IOException {
        if (onClose != null) {
            onClose.execute();
        }
        if (base != null) {
            base.close();
        }
    }

    @Override
    public FileChannel position(long newPosition) {
        synchronized (positionLock) {
            this.pos = newPosition;
            return this;
        }
    }

    @Override
    public long position() {
        return pos;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        synchronized (positionLock) {
            int len = read(dst, pos);
            if (len > 0) {
                pos += len;
            }
            return len;
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        if (position < 0) {
            throw new IllegalArgumentException("negative position");
        }
        final int read;
        final ByteBuffer tmp;
        synchronized (positionLock) {
            synchronized (writeLock) {
                final int size = (int) base.size();
                if (size == 0) {
                    return -1;
                }
                tmp = ByteBuffer.allocate(size);
                read = base.read(tmp, 0);
            }
        }
        if (read > 0) {
            tmp.rewind();
            try {
                final byte[] decrypt = cryptoPrimitive.decrypt(tmp.array(), new byte[0]);
                final int destRemaining = dst.remaining();
                final int remaining = decrypt.length - (int) position;
                if (remaining < 1) {
                    //position past end of file
                    return -1;
                }
                final int decryptRead = Math.min(remaining, destRemaining);
                dst.put(decrypt, (int) position, decryptRead);
                return decryptRead;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to decrypt " + path, e);
            }
        }
        return read;

    }


    @Override
    public int write(ByteBuffer src) throws IOException {
        synchronized (positionLock) {
            synchronized (writeLock) {
                if (Arrays.stream(openOptions).anyMatch(it -> it == APPEND)) {
                    pos = base.size() == 0 ? base.size() : size();
                }
                int len = write(src, pos);
                if (len > 0) {
                    pos += len;
                }
                return len;
            }
        }
    }

    @Override
    public long write(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        if (!Arrays.asList(openOptions).contains(WRITE)) {
            throw new NonWritableChannelException();
        }
        int bytesToWrite = src.remaining();
        synchronized (positionLock) {
            synchronized (writeLock) {
                final long newSize = Math.max(size(), position + bytesToWrite);
                final ByteBuffer tmp = ByteBuffer.allocate((int) newSize);
                read(tmp, 0);
                tmp.position((int) position);
                tmp.put(src.array(), 0, bytesToWrite);
                tmp.rewind();
                internalWrite(tmp);
                src.position(src.position() + bytesToWrite);
            }
        }
        return bytesToWrite;
    }

    private void internalWrite(ByteBuffer data) throws IOException {
        try {
            final byte[] encrypt = cryptoPrimitive.encrypt(data.array(), new byte[0]);
            base.write(ByteBuffer.wrap(encrypt), 0);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt " + path, e);
        }
    }


    @Override
    public MappedByteBuffer map(MapMode mapMode, long position, long size) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long size() throws IOException {
        final long size = base.size();
        return size == 0 ? 0 : size - ENCRYPTION_OVERHEAD;
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void force(boolean metaData) throws IOException {
        base.force(metaData);
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return path.toString();
    }

    static Aead constructCryptoPrimitive(byte[] keyBytes) throws GeneralSecurityException {
        AeadConfig.register();
        final AesGcmKey key = AesGcmKey.newBuilder().setVersion(0).setKeyValue(ByteString.copyFrom(keyBytes)).build();
        return Registry.getPrimitive(AeadConfig.AES_GCM_TYPE_URL, key, Aead.class);
    }


}
