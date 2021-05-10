package com.karakun;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class StandardFileChannelTest extends AbstractFileChannelTest {
    @Override
    FileChannel getFileChannel(Path tmpFile, OpenOption... openOptions) throws IOException {
        return FileChannel.open(tmpFile, openOptions);
    }
}
