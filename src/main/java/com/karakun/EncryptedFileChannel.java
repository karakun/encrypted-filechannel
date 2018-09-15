package com.karakun;

import com.google.crypto.tink.Config;
import com.google.crypto.tink.Registry;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.proto.AesGcmHkdfStreamingKey;
import com.google.crypto.tink.proto.AesGcmHkdfStreamingParams;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.security.GeneralSecurityException;

/**
 * An encrypted FileChannel based on google tink library
 */
public class EncryptedFileChannel extends FileChannel {


    private final FileChannel base;

    /**
     * The current position within the file, from a user perspective.
     */
    private long pos = 0;

    /**
     * The current file size, from a user perspective.
     */
    private long size = 0;

    private final String name;

    private byte[] encryptionKey;
    private boolean readOnly;
    private SeekableByteChannel readableByteChannel;
    private WritableByteChannel writableByteChannel;

    public EncryptedFileChannel(final String name, final byte[] encryptionKey, final FileChannel base, final boolean readOnly) {
        if (encryptionKey == null) {
            throw new IllegalStateException(String.format("Encryption key must not be null for file '%s'.", name));
        }
        this.name = name;
        this.base = base;
        this.encryptionKey = encryptionKey.clone();
        this.readOnly = readOnly;
    }

    private void initRead() throws IOException {
        if (readableByteChannel != null) {
            return;
        }
        if (base.size() > 0) {
            try {
                StreamingAead streamingAead = constructCryptoPrimitive(encryptionKey);
                readableByteChannel = streamingAead.newSeekableDecryptingChannel(base, new byte[]{});
                size = readableByteChannel.size();
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
        }
    }

    private void initWrite() throws IOException {
        if (readOnly) {
            throw new IllegalStateException("This encrypted FileChannel is readonly");
        }
        if (writableByteChannel != null) {
            return;
        }
        initRead();
        try {
            StreamingAead streamingAead = constructCryptoPrimitive(encryptionKey);
            writableByteChannel = streamingAead.newEncryptingChannel(base, new byte[]{});
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected void implCloseChannel() throws IOException {
        if (readableByteChannel != null) {
            readableByteChannel.close();
        }
    }

    @Override
    public FileChannel position(long newPosition) {
        this.pos = newPosition;
        return this;
    }

    @Override
    public long position() {
        return pos;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int len = read(dst, pos);
        if (len > 0) {
            pos += len;
        }
        return len;
    }

    @Override
    public long read(ByteBuffer[] byteBuffers, int i, int i1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int read(ByteBuffer dst, long position) throws IOException {
        initRead();
        readableByteChannel.position(position);
        return readableByteChannel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        int len = write(src, pos);
        if (len > 0) {
            pos += len;
        }
        return len;
    }

    @Override
    public long write(ByteBuffer[] byteBuffers, int i, int i1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized int write(ByteBuffer src, long position) throws IOException {
        initWrite();
        int bytesToWrite = src.remaining();
        final long newSize = Math.max(size, position + bytesToWrite);
        final ByteBuffer tmp = ByteBuffer.allocate((int) newSize);
        if (readableByteChannel != null) {
            read(tmp, 0);
        }
        tmp.put(src.array(), 0, bytesToWrite);
        tmp.flip();
        int written = 0;
        do {
            written += writableByteChannel.write(src);
        } while (written < bytesToWrite);
        size = newSize;
        pos = pos + bytesToWrite;
        return bytesToWrite;
    }


    @Override
    public MappedByteBuffer map(MapMode mapMode, long l, long l1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock lock(long l, long l1, boolean b) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long size() throws IOException {
        initRead();
        return size;
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        initRead();
        if (this.size < size) {
            return this;
        }
        final ByteBuffer tmp = ByteBuffer.allocate((int) size);
        if (readableByteChannel != null) {
            read(tmp, 0);
            tmp.flip();
            this.size = size;
            write(tmp, 0);
            pos = Math.min(pos, this.size);
        }
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        base.force(metaData);
    }

    @Override
    public long transferTo(long l, long l1, WritableByteChannel writableByteChannel) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferFrom(ReadableByteChannel readableByteChannel, long l, long l1) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared)
            throws IOException {
        return base.tryLock(position, size, shared);
    }

    @Override
    public String toString() {
        return name;
    }

    static StreamingAead constructCryptoPrimitive(byte[] keyBytes) throws GeneralSecurityException {
        Config.register(StreamingAeadConfig.LATEST);
        final AesGcmHkdfStreamingParams params = AesGcmHkdfStreamingParams.newBuilder()
                .setDerivedKeySize(32)
                .setCiphertextSegmentSize(128)
                .setHkdfHashType(HashType.SHA256)
                .build();
        AesGcmHkdfStreamingKey streamingKey = AesGcmHkdfStreamingKey.newBuilder()
                .setKeyValue(ByteString.copyFrom(keyBytes))
                .setParams(params)
                .build();
        return (StreamingAead) Registry.getPrimitive(StreamingAeadConfig.AES_GCM_HKDF_STREAMINGAEAD_TYPE_URL, streamingKey);
    }


}
