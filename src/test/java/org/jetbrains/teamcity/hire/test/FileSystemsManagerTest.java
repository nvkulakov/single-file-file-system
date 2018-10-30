package org.jetbrains.teamcity.hire.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.teamcity.hire.test.filesystem.FileSystemsManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FileSystemsManagerTest extends RootTest {

    @Test
    @DisplayName("Try to create a file system file when a directory is already exists by the path")
    public void testCreateFileWithinDir() throws IOException {
        Path pathToDir = Paths.get(PROJECT_FOLDER, "randomDir 1");
        Files.deleteIfExists(pathToDir);
        Files.createDirectories(pathToDir.getParent());
        Files.createDirectory(pathToDir);
        Assertions.assertThrows(IllegalArgumentException.class, () -> FileSystemsManager.createAndFormat(pathToDir, 1000));
        Files.delete(pathToDir);
    }

    @Test
    @DisplayName("Try to create and format a file system file, check that it is formatted")
    public void testFileSystemFileFormat() throws IOException {
        FileSystemsManager.createAndFormat(fileSystemPath, 1000);
        Assertions.assertTrue(FileSystemsManager.isFormatted(fileSystemPath));
    }

}
