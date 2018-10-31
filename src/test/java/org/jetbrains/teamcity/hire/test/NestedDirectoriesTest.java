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

    private Map<String, List<String>> dirName2content;
    private int counter;

    @Test
    @DisplayName("Try to remove empty / not empty directory")
    public void testRemoveDirectory() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 3000);
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
    @DisplayName("Create nested files and directories, compare written names with read, remove all")
    public void testNestedDirectoriesCreating() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000_000);
        counter = 0;
        dirName2content = new HashMap<>();
        try (RootDirectory root = fileSystemsManager.load(fileSystemPath)) {
            // (((6 * 3 + 6) * 3 + 6) * 3 + 6) * 3 + 6 == 726 records
            createNestedFilesAndDirs(root, 3, 4);
            compare(root);
            clear(root);
            Assertions.assertEquals(root.getFilesCount(), 0);
        }
    }

    private void createNestedFilesAndDirs(Directory parent, int filesAndDirs, int depth) throws IOException {
        List<String> names = new ArrayList<>(filesAndDirs * 2);
        dirName2content.put(parent.getName(), names);
        if (depth == -1) {
            return;
        }
        // sequential creating: one file, one dir with subdirs
        for (int i = 0; i < filesAndDirs; i++) {
            String fileName = "File depth " + depth + " pos " + i + " num " + counter++;
            parent.createFile(fileName, 0);
            names.add(fileName);
            String directoryName = "/Directory depth " + depth + " pos " + i + " num " + counter++;
            Directory directory = parent.createDirectory(directoryName);
            names.add(directoryName);
            createNestedFilesAndDirs(directory, filesAndDirs, depth - 1);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void compare(Directory directory) throws IOException {
        List<String> readNames = directory.getFileNames();
        Assertions.assertEquals(readNames, dirName2content.get(directory.getName()));
        for (String name : readNames) {
            if (name.charAt(0) == '/') {
                compare(directory.getDirectory(name));
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void clear(Directory directory) throws IOException {
        for (String name : directory.getFileNames()) {
            if (name.charAt(0) == '/') {
                Directory nestedDirectory = directory.getDirectory(name);
                clear(nestedDirectory);
            }
            directory.removeFile(name);
        }

    }

}
