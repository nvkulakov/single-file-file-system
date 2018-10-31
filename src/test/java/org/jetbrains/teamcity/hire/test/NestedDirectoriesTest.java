package org.jetbrains.teamcity.hire.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.teamcity.hire.test.filesystem.api.Directory;
import org.jetbrains.teamcity.hire.test.filesystem.api.RootDirectory;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEmptyDirectoryException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class NestedDirectoriesTest extends RootTest {

    private Map<String, List<String>> directoryName2content;

    @Test
    @DisplayName("Try to remove empty / not empty directory")
    public void testRemoveDirectory() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000_000);
        directoryName2content = new HashMap<>();
        try (RootDirectory root = fileSystemsManager.load(fileSystemPath)) {
            Directory nestedDir = root.createDirectory("/nested");
            nestedDir.createDirectory("/subdir");
            Assertions.assertThrows(NotEmptyDirectoryException.class, () -> root.removeFile("/nested"));
            nestedDir.removeFile("/subdir");
            Assertions.assertDoesNotThrow(() -> root.removeFile("/nested"));
            Assertions.assertEquals(root.getFilesCount(), 0);

            nestedDir = root.createDirectory("/nested");
            nestedDir.createFile("subfile", 0);
            Assertions.assertThrows(NotEmptyDirectoryException.class, () -> root.removeFile("/nested"));
            nestedDir.removeFile("subfile");
            Assertions.assertDoesNotThrow(() -> root.removeFile("/nested"));
            Assertions.assertEquals(root.getFilesCount(), 0);
        }
    }

    @Test
    @DisplayName("Create nested files and directories, compare written names with read")
    public void testNestedDirectoriesCreating() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000_000);
        directoryName2content = new HashMap<>();
        try (RootDirectory root = fileSystemsManager.load(fileSystemPath)) {
            createNestedFilesAndDirs(root, "root", 3, 4);
            compare(root, "root");
        }
    }

    private void createNestedFilesAndDirs(Directory parent, String parentName, int filesAndDirs, int depth) throws IOException {
        List<String> names = new ArrayList<>(filesAndDirs * 2);
        directoryName2content.put(parentName, names);
        if (depth == 0) {
            return;
        }
        // sequential creating: one file, one dir with subdirs
        for (int i = 0; i < filesAndDirs; i++) {
            String fileName = "File depth " + depth + " num " + i;
            parent.createFile(fileName, 0);
            names.add(fileName);
            String directoryName = "/Directory depth " + depth + " num " + i;
            Directory directory = parent.createDirectory(directoryName);
            names.add(directoryName);
            createNestedFilesAndDirs(directory, directoryName, filesAndDirs, depth - 1);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void compare(Directory directory, String directoryName) throws IOException {
        List<String> readNames = directory.getFileNames();
        Assertions.assertEquals(readNames, directoryName2content.get(directoryName));
        for (String name : readNames) {
            if (name.charAt(0) == '/') {
                compare(directory.getDirectory(name), name);
            }
        }
    }

}
