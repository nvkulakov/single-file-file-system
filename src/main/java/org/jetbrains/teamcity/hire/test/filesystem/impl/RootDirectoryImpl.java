package org.jetbrains.teamcity.hire.test.filesystem.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;
import org.jetbrains.teamcity.hire.test.filesystem.api.RootDirectory;

class RootDirectoryImpl extends DirectoryImpl implements RootDirectory {

    private static final String ROOT_DIRECTORY_NAME = "/root";

    private final RandomAccessFile file;

    private RootDirectoryImpl(RandomAccessFile file, DataBlock dataBlock) {
        super(ROOT_DIRECTORY_NAME, dataBlock);
        this.file = file;
    }

    static RootDirectory load(RandomAccessFile file, long firstPosition) throws IOException {
        Objects.requireNonNull(file, "File system file must be not null");
        if (firstPosition < 0 || firstPosition >= file.length()) {
            throw new IllegalArgumentException(
                    String.format("firstPosition value '%s' is out of bounds for file %s", firstPosition, file));
        }
        return new RootDirectoryImpl(file, new DataBlock(file, firstPosition, file.length(), firstPosition));
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

}
