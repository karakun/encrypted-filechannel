package com.karakun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static java.nio.file.StandardOpenOption.*;
import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractFileChannelTest {

    private Path tempDir;

    @BeforeEach
    public void setup() throws IOException {
        tempDir = Files.createTempDirectory(AbstractFileChannelTest.class.getName());
    }

    abstract FileChannel getFileChannel(Path tmpFile, OpenOption... openOptions) throws IOException;


    @Test
    public void readWithNegativePos() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(" ".getBytes().length);
            assertThrows(IllegalArgumentException.class, () -> {
                fileChannel.read(byteBuffer, -1);
            });
        }
    }

    @Test
    public void positionSet() throws IOException {
        final Random rand = new Random();
        Path tmpFile = tempDir.resolve("writeForceWrite.tlog");
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            final String text = "Hallo Welt! Hier testen wir die Position.";
            fileChannel.write(ByteBuffer.wrap(text.getBytes()));
            assertEquals(text.length(), fileChannel.position());
            for (int i = 0; i < 10; i++) {
                final int newPosition = rand.nextInt(text.length());
                fileChannel.position(newPosition);
                assertEquals(newPosition, fileChannel.position());
            }
        }
    }

    @Test
    public void writeForceWrite() throws IOException {
        Path tmpFile = tempDir.resolve("writeForceWrite.tlog");
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap("k".getBytes()));
            fileChannel.force(false);
            fileChannel.write(ByteBuffer.wrap("kk".getBytes()));
            assertEquals(3, fileChannel.size());
        }
    }

    @Test
    public void appendedWrites() throws IOException {
        final Path tmpFile = Files.createTempFile("appendedWrites", ".tlog");
        for (int append = 0; append < 100; append++) {
            try (FileChannel fileChannel = getFileChannel(tmpFile, WRITE, APPEND)) {
                fileChannel.write(ByteBuffer.wrap("k".getBytes()));
            }
        }
        FileChannel fileChannel = getFileChannel(tmpFile, READ);
        assertEquals(100, fileChannel.size());
    }

    @Test
    public void positionalWrites() throws IOException {
        final Path tmpFile = Files.createTempFile("positionalWrites", ".tlog");
        for (int append = 0; append < 100; append++) {
            try (FileChannel fileChannel = getFileChannel(tmpFile, WRITE)) {
                fileChannel.write(ByteBuffer.wrap("k".getBytes()), append);
            }
        }
        FileChannel fileChannel = getFileChannel(tmpFile, READ);
        assertEquals(100, fileChannel.size());
    }

    @Test
    public void atomicWrites() throws IOException {
        final Path tmpFile = Files.createTempFile("atomicWrites", ".tlog");
        final int numberOfFC = 100;
        final int numberOfWritesPerFC = 10;
        List<Long> aList = LongStream.rangeClosed(1, numberOfFC).boxed()
                .collect(Collectors.toList());
        aList.parallelStream().forEach(it -> {
            try (FileChannel fileChannel = getFileChannel(tmpFile, WRITE, APPEND)) {
                for (int i = 0; i < numberOfWritesPerFC; i++) {
                    fileChannel.write(ByteBuffer.wrap("k".getBytes()));
                }
            } catch (IOException e) {
                throw new RuntimeException("failed to get fileChannel", e);
            }
        });
        FileChannel fileChannel = getFileChannel(tmpFile, READ);
        assertEquals(numberOfFC * numberOfWritesPerFC, fileChannel.size());
    }

    @Test
    public void multiThreadedAccess() throws IOException {
        final Path tmpFile = Files.createTempFile("multiThreadedAccess", ".tlog");
        final int numberOfIterations = 1_000;
        final FileChannel[] fileChannels = {getFileChannel(tmpFile, READ, WRITE), getFileChannel(tmpFile, READ, WRITE)};
        final byte[] bytes = new byte[numberOfIterations];
        Arrays.fill(bytes, (byte) 0);
        fileChannels[0].write(ByteBuffer.wrap(bytes));
        assertEquals(numberOfIterations, fileChannels[0].size());
        List<Long> aList = LongStream.rangeClosed(0, numberOfIterations - 1).boxed()
                .collect(Collectors.toList());
        final Random random = new Random();
        aList.parallelStream().forEach(it -> {
            try {
                final byte[] byteVal = new byte[1];
                random.nextBytes(byteVal);
                bytes[it.intValue()] = byteVal[0];
                fileChannels[random.nextInt(1)].write(ByteBuffer.wrap(byteVal), it);

                // TODO discuss if this is a Lucene use case
                final ByteBuffer readBuffer = ByteBuffer.allocate(1);
                fileChannels[random.nextInt(1)].read(readBuffer, it);
                assertEquals(byteVal[0], readBuffer.array()[0]);
            } catch (IOException e) {
                throw new RuntimeException("failed to write fileChannel", e);
            }

        });
        FileChannel fileChannel = getFileChannel(tmpFile, READ);
        assertEquals(numberOfIterations, fileChannel.size());
        final ByteBuffer readBuffer = ByteBuffer.allocate(numberOfIterations);
        fileChannel.read(readBuffer);
        assertArrayEquals(bytes, readBuffer.array());
    }

    @Test
    public void simpleWriteThenReadToBufferLimit() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
        }
        try (final FileChannel fileChannel = getFileChannel(tmpFile, READ)) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            byteBuffer.limit(5);
            final int read = fileChannel.read(byteBuffer, 0);
            assertEquals(5, read);
            assertEquals("Hallo", new String(byteBuffer.array(), 0, read));
        }
    }

    @Test
    public void readNonExistingFile() throws IOException {
        Path tmpFile = tempDir.resolve("notExisting.tlog");
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            Files.delete(tmpFile);
            final ByteBuffer byteBuffer = ByteBuffer.allocate("Hallo Welt".getBytes().length);
            assertEquals(-1, fileChannel.read(byteBuffer), "bytes read");
        }
    }

    @Test
    public void simpleWriteThenRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            assertEquals(writeString.getBytes().length, fileChannel.size(), "size");
        }
        try (final FileChannel fileChannel = getFileChannel(tmpFile, READ)) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            fileChannel.read(byteBuffer, 0);
            assertEquals(writeString, new String(byteBuffer.array()));
            assertEquals(writeString.getBytes().length, fileChannel.size(), "size");
        }
    }

    @Test
    public void simpleWriteToOutputStreamThenRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW);
             final OutputStream outputStream = Channels.newOutputStream(fileChannel)) {
            outputStream.write(writeString.getBytes());
            assertEquals(writeString.getBytes().length, fileChannel.size(), "size");
        }
        try (final FileChannel fileChannel = getFileChannel(tmpFile, READ)) {
            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            fileChannel.read(byteBuffer, 0);
            assertEquals(writeString, new String(byteBuffer.array()));
            assertEquals(writeString.getBytes().length, fileChannel.size(), "size");
        }
    }

    @Test
    public void simpleWriteAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW);
        try {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            fileChannel.read(byteBuffer, 0);
            assertEquals(writeString, new String(byteBuffer.array()));
        } finally {
            fileChannel.close();
        }
    }

    @Test
    public void simpleWriteAndReadAtEOF() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt!";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            final int read = fileChannel.read(byteBuffer, writeString.getBytes().length);
            assertEquals(-1, read);
        }
    }

    @Test
    public void twoWritesAndOneRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String hallo = "Hallo";
        final String welt = " Welt!";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            int write = fileChannel.write(ByteBuffer.wrap(hallo.getBytes()));
            assertEquals(hallo.getBytes().length, write);
            assertEquals(hallo.getBytes().length, fileChannel.size(), "size");
            write = fileChannel.write(ByteBuffer.wrap(welt.getBytes()), hallo.getBytes().length);
            assertEquals(welt.getBytes().length, write);
            assertEquals((hallo + welt).getBytes().length, fileChannel.size(), "size");

            final int length = (hallo + welt).getBytes().length;
            final ByteBuffer byteBuffer = ByteBuffer.allocate(length);
            final int read = fileChannel.read(byteBuffer, 0);
            assertEquals(length, read);
            assertEquals(hallo + welt, new String(byteBuffer.array()));
        }
    }

    @Test
    public void distributedWriteAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        final String writeString = "Hallo Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            fileChannel.force(true);

            try (final FileChannel fileChannel1 = getFileChannel(tmpFile, READ)) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
                fileChannel1.read(byteBuffer);
                assertEquals(writeString, new String(byteBuffer.array()));
            }
        }
    }

    @Test
    public void writeMoveAndRead() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndRead.tlog");
        Path destFile = tempDir.resolve("writeAndReadMove.tlog");
        final String writeString = "Hallo Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));
            fileChannel.force(true);
            assertEquals(writeString.getBytes().length, fileChannel.size(), "size");
            Files.move(tmpFile, destFile, StandardCopyOption.ATOMIC_MOVE);

            try (final FileChannel fileChannel1 = getFileChannel(destFile, READ)) {
                final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
                fileChannel1.read(byteBuffer);
                assertEquals(writeString.getBytes().length, fileChannel1.size(), "size");
                assertEquals(writeString, new String(byteBuffer.array()));
            }
        }
    }

    @Test
    public void writeAndReadFromPosition() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndReadFromPos.tlog");
        final String writeString = "Hallo Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.";
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            fileChannel.write(ByteBuffer.wrap(writeString.getBytes()));

            final ByteBuffer byteBuffer = ByteBuffer.allocate(writeString.getBytes().length);
            final int read = fileChannel.read(byteBuffer, 6);
            assertEquals("Welt, hier ein etwas grösserer Text um zu encrypten! Mindestens grösser als der overhead.", new String(byteBuffer.array(), 0, read));
        }
    }

    @Test
    public void writeAtPosition_ex() throws IOException {
        Path tmpFile = tempDir.resolve("writeAndReadFromPos.tlog");
        try (final FileChannel fileChannel = getFileChannel(tmpFile, WRITE, READ, CREATE_NEW)) {
            final byte[] byteArray = new byte[55];
            Arrays.fill(byteArray, (byte) ' ');
            fileChannel.write(ByteBuffer.wrap(byteArray), 0);

            final byte[] byteArray2 = new byte[8192];
            Arrays.fill(byteArray2, (byte) ' ');
            fileChannel.write(ByteBuffer.wrap(byteArray2), 55);
            assertEquals(8247, fileChannel.size());

            fileChannel.write(ByteBuffer.wrap("Hallo Welt".getBytes()), 5000);

            final int length = "Hallo Welt".getBytes().length;
            final ByteBuffer byteBuffer = ByteBuffer.allocate(length);
            final int read = fileChannel.read(byteBuffer, 5006);
            assertEquals(length, read);
            assertEquals("Welt      ", new String(byteBuffer.array(), 0, read));
        }
    }

}