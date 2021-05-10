package com.karakun;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Base64;

public class EncryptedFileChannelTest extends AbstractFileChannelTest{

    private final byte[] encryptionKey = Base64.getUrlDecoder().decode("cxGrfBkPPMpbUGKUU1iaBW8RCDeID8-uR40jslBQaMY=");

    @Override
    FileChannel getFileChannel(Path tmpFile, OpenOption... openOptions) throws IOException {
        return EncryptedFileChannel.open(tmpFile, encryptionKey, openOptions);
    }
}