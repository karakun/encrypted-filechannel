package com.karakun;


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

public class EncryptedFileChannelTest {

    private Path tempDir;
    private byte[] encryptionKey = Base64.getUrlDecoder().decode("cxGrfBkPPMpbUGKUU1iaBW8RCDeID8-uR40jslBQaMY=");

    @Before
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory(EncryptedFileChannelTest.class.getName());
    }

    @Test
    public void simpleWriteThenRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
        }
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, READ)) {
                     final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            encryptedFileChannel.read(byteBuffer, 0);
            Assert.assertEquals(writeString, new String(byteBuffer.array()));
        }
    }

    @Test
    public void simpleWriteAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            encryptedFileChannel.read(byteBuffer, 0);
            Assert.assertEquals(writeString, new String(byteBuffer.array()));
        }
    }

    @Test
    public void simpleWriteAndReadAtEOF() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            final int read = encryptedFileChannel.read(byteBuffer, writeString.getBytes().length);
            Assert.assertEquals(-1, read);
        }
    }

    @Test
    public void twoWritesAndOneRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String hallo = "Hallo";
        final String welt = " Welt!";
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            int write = encryptedFileChannel.write(ByteBuffer.wrap(hallo.getBytes()));
            Assert.assertEquals(hallo.getBytes().length, write);
            write = encryptedFileChannel.write(ByteBuffer.wrap(welt.getBytes()), hallo.getBytes().length);
            Assert.assertEquals(welt.getBytes().length, write);


            final int length = (hallo + welt).getBytes().length;
            final ByteBuffer byteBuffer = ByteBuffer.allocate(length);
            final int read = encryptedFileChannel.read(byteBuffer, 0);
            Assert.assertEquals(length, read);
            Assert.assertEquals(hallo + welt, new String(byteBuffer.array()));

        }
    }

    @Test
    public void distributedWriteAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.";
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            encryptedFileChannel.force(true);

            try (final FileChannel encryptedFileChannel2 = EncryptedFileChannel.open(tmpFile, encryptionKey, READ)) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
                encryptedFileChannel2.read(byteBuffer);
                Assert.assertEquals(writeString, new String(byteBuffer.array()));
            }
        }
    }

    @Test
    public void writeMoveAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        Path destFile = tempDir.resolve("writeAndReadMove.tlog");
        final String writeString = "Hallo Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.";
        try (final FileChannel encryptedFileChannel =  EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            encryptedFileChannel.force(true);
            Files.move(tmpFile, destFile, StandardCopyOption.ATOMIC_MOVE);

            try (final FileChannel encryptedFileChannel2 = EncryptedFileChannel.open(destFile, encryptionKey, READ)) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
                encryptedFileChannel2.read(byteBuffer);
                Assert.assertEquals(writeString, new String(byteBuffer.array()));
            }
        }
    }

    @Test
    public void writeAndReadFromPos() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndReadFromPos.tlog");
        final String writeString = "Hallo Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.";
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            encryptedFileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            final int read = encryptedFileChannel.read(byteBuffer, 6);
            Assert.assertEquals("Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.", new String(byteBuffer.array(), 0, read));
        }
    }

    @Test
    public void writeAtPos_ex() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndReadFromPos.tlog");
        try (final FileChannel encryptedFileChannel = EncryptedFileChannel.open(tmpFile, encryptionKey, WRITE, READ, CREATE_NEW)) {
            final byte[] byteArray = new byte[55];
            Arrays.fill(byteArray, (byte) ' ');
            encryptedFileChannel.write(ByteBuffer.wrap(byteArray), 0);

            final byte[] byteArray2 = new byte[8192];
            Arrays.fill(byteArray2, (byte) ' ');
            encryptedFileChannel.write(ByteBuffer.wrap(byteArray2), 55);
            Assert.assertEquals(8247, encryptedFileChannel.size());

            encryptedFileChannel.write(ByteBuffer.wrap("Hallo Welt".getBytes()), 5000);

            final ByteBuffer byteBuffer = ByteBuffer.allocate("Hallo Welt".getBytes().length);
            final int read = encryptedFileChannel.read(byteBuffer, 5006);
            Assert.assertEquals("Welt      ", new String(byteBuffer.array(), 0, read));

        }
    }

}