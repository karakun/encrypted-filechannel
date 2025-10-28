# EncryptedFileChannel

A drop-in replacement for Java’s `FileChannel` that transparently encrypts and decrypts file data
using [Google Tink](https://github.com/tink-crypto).

Originally developed for a custom Apache Lucene store plugin, it allows encrypted index and transaction log files
without changing Lucene’s logic or APIs.

## Features

- Extends `java.nio.channels.FileChannel` - but not all methods are implemented
- Transparent AES-256-GCM-HKDF streaming encryption (via Google Tink)
- Supports random access, append, and positional writes
- Thread-safe and atomic writes
- Tested with JUnit for correctness and concurrency safety

## Usage

```java
private static final byte[] ENCRYPTION_KEY = Base64.getUrlDecoder().decode("cxGrfBkPPMpbUGKUU1iaBW8RCDeID8-uR40jslBQaMY=");

private FileChannel getFileChannel(Path tmpFile, OpenOption... openOptions) throws IOException {
    return EncryptedFileChannel.open(tmpFile, ENCRYPTION_KEY, openOptions);
}

private void readAndWrite() throws IOException {
    Path tmpFile = Files.createTempDirectory(Scratch.class.getName()).resolve("writeAndRead.tlog");
    final String writeString = "Hello world!";
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
```

## Security Notes

- Do not store encryption keys as `String`. Use `byte[]` or a `KeysetHandle`.
- Strings are immutable and may remain in the JVM’s string pool, while byte[] can be cleared from memory.
- Manage keysets securely (for example, using Tinkey, Vault, or a KMS).

## Why Tink

Google Tink enforces safe defaults, authenticated encryption, and key rotation.
It helps prevent common cryptographic mistakes such as nonce reuse, missing authentication tags, or unsafe cipher modes.
In short: **don’t roll your own crypto!**.

## Build and Test

Requires Java 21 and Maven 3.9+.

```bash
mvn clean package
```

Continuous integration runs automatically on GitHub Actions with Java 21 (Temurin).
![Build](https://github.com/karakun/encrypted-filechannel/actions/workflows/maven.yml/badge.svg)