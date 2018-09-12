package org.h2.store.fs;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;

import static java.nio.file.StandardOpenOption.*;

public class EncryptedH2FileChannelTest {

    private Path tempDir;
    private byte[] encryptionKey = Base64.getUrlDecoder().decode("cxGrfBkPPMpbUGKUU1iaBW8RCDeID8-uR40jslBQaMY=");

    @Before
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory(EncryptedH2FileChannelTest.class.getName());
    }

    @Test
    public void simpleWriteAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt";
        try (final FileChannel base = FileChannel.open(tmpFile, WRITE, READ, CREATE_NEW);
             final FileChannel encryptedFileChannel = new EncryptedFileChannel("name", encryptionKey, base, false)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.length());
            encryptedFileChannel.read(byteBuffer, 0);
            Assert.assertEquals(writeString, new String(byteBuffer.array()));
        }
    }

    @Test
    public void distributedWriteAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt";
        try (final FileChannel base = FileChannel.open(tmpFile, WRITE, CREATE_NEW);
             final FileChannel encryptedFileChannel = new EncryptedFileChannel("name", encryptionKey, base, false)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            encryptedFileChannel.force(true);

            try (final FileChannel base2 = FileChannel.open(tmpFile, READ);
                 final FileChannel encryptedFileChannel2 = new EncryptedFileChannel("name", encryptionKey, base2, true)) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.length());
                encryptedFileChannel2.read(byteBuffer);
                Assert.assertEquals(writeString, new String(byteBuffer.array()));
            }
        }
    }

    @Test
    public void writeMoveAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        Path destFile = tempDir.resolve("writeAndReadMove.tlog");
        final String writeString = "Hallo Welt";
        try (final FileChannel base = FileChannel.open(tmpFile, WRITE, CREATE_NEW);
             final FileChannel encryptedFileChannel =  new EncryptedFileChannel("name", encryptionKey, base, false)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            encryptedFileChannel.force(true);
            Files.move(tmpFile, destFile, StandardCopyOption.ATOMIC_MOVE);

            try (final FileChannel base2 = FileChannel.open(destFile, READ);
                 final FileChannel encryptedFileChannel2 = new EncryptedFileChannel("name", encryptionKey, base2, true)) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.length());
                encryptedFileChannel2.read(byteBuffer);
                Assert.assertEquals(writeString, new String(byteBuffer.array()));
            }
        }
    }

    @Test
    public void writeAndReadFromPos() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndReadFromPos.tlog");
        final String writeString = "Hallo Welt";
        try (final FileChannel base = FileChannel.open(tmpFile, WRITE, READ, CREATE_NEW);
             final FileChannel encryptedFileChannel = new EncryptedFileChannel("name", encryptionKey, base, false)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.length());
            final int read = encryptedFileChannel.read(byteBuffer, 6);
            Assert.assertEquals("Welt", new String(byteBuffer.array(), 0, read));
        }
    }

    @Test
    public void writeAtPos_ex() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndReadFromPos.tlog");
        try (final FileChannel base = FileChannel.open(tmpFile, WRITE, READ, CREATE_NEW);
             final FileChannel encryptedFileChannel = new EncryptedFileChannel("name", encryptionKey, base, false)) {
            final byte[] byteArray = new byte[55];
            Arrays.fill(byteArray, (byte) ' ');
            encryptedFileChannel.write(ByteBuffer.wrap(byteArray), 0);

            final byte[] byteArray2 = new byte[8192];
            Arrays.fill(byteArray2, (byte) ' ');
            encryptedFileChannel.write(ByteBuffer.wrap(byteArray2), 55);
            Assert.assertEquals(8247, encryptedFileChannel.size());

            encryptedFileChannel.write(ByteBuffer.wrap("Hallo Welt".getBytes()), 5000);

            final ByteBuffer byteBuffer = ByteBuffer.allocate("Hallo Welt".length());
            final int read = encryptedFileChannel.read(byteBuffer, 5006);
            Assert.assertEquals("Welt      ", new String(byteBuffer.array(), 0, read));

        }
    }

}