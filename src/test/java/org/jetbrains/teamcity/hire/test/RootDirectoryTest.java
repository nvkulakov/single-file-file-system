package org.jetbrains.teamcity.hire.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.jetbrains.teamcity.hire.test.exceptions.IllegalFileNameException;
import org.jetbrains.teamcity.hire.test.exceptions.NotEnoughFreeSpaceException;
import org.jetbrains.teamcity.hire.test.filesystem.api.File;
import org.jetbrains.teamcity.hire.test.filesystem.api.RootDirectory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class RootDirectoryTest extends RootTest {

    @Test
    @DisplayName("Create a file with size of all the free data space size")
    public void testMaxFileSizeCreating() throws IOException {
        int fileSystemFileSize = 1000;
        fileSystemsManager.createAndFormat(fileSystemPath, fileSystemFileSize);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            int rootStartPosition = 26;
            int serviceBytes = 25;
            int rootBlockLength = 800 + serviceBytes;
            int maxFileSize = fileSystemFileSize - (rootStartPosition + rootBlockLength + serviceBytes);
            Assertions.assertDoesNotThrow(() -> directory.createFile("First", maxFileSize));
        }
    }

    @Test
    @DisplayName("Create too big file")
    public void testNotEnoughFreeSpace() throws IOException {
        int fileSystemFileSize = 1000;
        fileSystemsManager.createAndFormat(fileSystemPath, fileSystemFileSize);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            int rootStartPosition = 26;
            int serviceBytes = 25;
            int rootBlockLength = 800 + serviceBytes;
            int maxFileSize = fileSystemFileSize - (rootStartPosition + rootBlockLength + serviceBytes);
            Assertions.assertThrows(NotEnoughFreeSpaceException.class, () -> directory.createFile("First", maxFileSize + 1));
        }
    }

    @Test
    @DisplayName("Create a few files in the root directory, compare read file names with the written ones")
    public void testGetFileNames() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            String name0 = "AAAbbbCCCddd 1";
            String name1 = "eeeFFFgggHHH_2";
            String name2 = "JjjKkkLllMmm _ 131";
            directory.createFile(name0, 0);
            directory.createFile(name1, 0);
            directory.createFile(name2, 0);
            List<String> fileNames = directory.getFileNames();
            Assertions.assertEquals(fileNames.size(), 3);
            Assertions.assertEquals(fileNames.get(0), name0);
            Assertions.assertEquals(fileNames.get(1), name1);
            Assertions.assertEquals(fileNames.get(2), name2);
        }
    }

    @Test
    @DisplayName("Create several files in the root directory, remove some of them")
    public void testRemoveFiles() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            String name0 = "AAAbbbCCCddd 1";
            String name1 = "eeeFFFgggHHH_2";
            String name2 = "JjjKkkLllMmm _ 131";
            directory.createFile(name0, 0);
            directory.createFile(name1, 0);
            directory.createFile(name2, 0);
            directory.removeFile(name0);
            directory.removeFile(name2);
            List<String> fileNames = directory.getFileNames();
            Assertions.assertEquals(fileNames.size(), 1);
            Assertions.assertEquals(fileNames.get(0), name1);
        }
    }

    @Test
    @DisplayName("Create second file with name of first")
    public void testFileNamesCorrectness() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            String validName = "Correct file_name 1";
            Assertions.assertDoesNotThrow(() -> directory.createFile(validName, 0));
            String invalidName1 = "Very very long file name, it cannot be so big";
            Assertions.assertThrows(IllegalFileNameException.class, () -> directory.createFile(invalidName1, 0));
            String invalidName2 = "Name with illegal symbol &";
            Assertions.assertThrows(IllegalFileNameException.class, () -> directory.createFile(invalidName2, 0));
            String invalidName3 = "Name with illegal symbol .";
            Assertions.assertThrows(IllegalFileNameException.class, () -> directory.createFile(invalidName3, 0));
            String invalidName4 = "Name ends with space ";
            Assertions.assertThrows(IllegalFileNameException.class, () -> directory.createFile(invalidName4, 0));
            String invalidName5 = " name starts with space";
            Assertions.assertThrows(IllegalFileNameException.class, () -> directory.createFile(invalidName5, 0));
        }
    }

    @Test
    @DisplayName("Create second file with name of first")
    public void testFileNameDuplication() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            String name = "AAA_bbb CCC 111_ddd 97";
            directory.createFile(name, 0);
            Assertions.assertThrows(IllegalFileNameException.class, () -> directory.createFile(name, 0));
        }
    }

    @Test
    @DisplayName("Create a lot of files in the root directory, compare gotten names with written")
    public void testRootDirectoryGrowing() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 100_000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            int filesCount = 1000;
            List<String> names = new ArrayList<>(filesCount);
            for (int i = 0; i < filesCount; i++) {
                String name = "File number " + i;
                names.add(name);
                directory.createFile(name, 1);
            }
            Assertions.assertEquals(names, directory.getFileNames());
        }
    }

    @Test
    @DisplayName("Create/remove files with equal size, check their content")
    public void testWriteReadRemoveSimpleDataFile() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 30_000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            int dataSize = 200;
            int maxFiles = 100;
            Random random = new Random(0);
            int counter = 1;
            List<NameAndData> nameAndDataList = new LinkedList<>();
            while (nameAndDataList.size() < maxFiles) {
                // Add two files, then remove one of them etc.
                if (counter % 3 != 0) {
                    String name = "File num " + counter;
                    byte[] data = new byte[dataSize];
                    Arrays.fill(data, (byte) counter);
                    File file = directory.createFile(name, dataSize);
                    file.write(data);
                    nameAndDataList.add(new NameAndData(name, data));
                } else {
                    int indexToRemove = random.nextInt(nameAndDataList.size());
                    directory.removeFile(nameAndDataList.get(indexToRemove).name);
                    nameAndDataList.remove(indexToRemove);
                }
                counter++;
            }
            List<String> fileNames = directory.getFileNames();
            Assertions.assertEquals(fileNames.size(), maxFiles);
            for (NameAndData nameAndData : nameAndDataList) {
                File file = directory.getFile(nameAndData.name);
                Assertions.assertNotNull(file);
                byte[] readData = new byte[dataSize];
                file.read(readData);
                Assertions.assertEquals(file.getName(), nameAndData.name);
                Assertions.assertArrayEquals(readData, nameAndData.data);
            }
        }
    }

    @Test
    @DisplayName("Create/remove files with different size and random content, check their content")
    public void testWriteReadRemoveVariousData() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1_000_000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            int maxFiles = 100;
            int maxBlockSize = 1000;
            List<NameAndData> nameAndDataList = new LinkedList<>();
            int counter = 1;
            Random random = new Random(0);
            while (nameAndDataList.size() < maxFiles) {
                // Add two files, then remove one of them etc.
                if (counter % 3 != 0) {
                    String name = "File num " + counter;
                    int dataSize = random.nextInt(maxBlockSize);
                    byte[] data = new byte[dataSize];
                    random.nextBytes(data);
                    File file = directory.createFile(name, dataSize);
                    file.write(data);
                    nameAndDataList.add(new NameAndData(name, data));
                } else {
                    int indexToRemove = random.nextInt(nameAndDataList.size());
                    directory.removeFile(nameAndDataList.get(indexToRemove).name);
                    nameAndDataList.remove(indexToRemove);
                }
                counter++;
            }
            List<String> fileNames = directory.getFileNames();
            Assertions.assertEquals(maxFiles, fileNames.size());
            for (NameAndData nameAndData : nameAndDataList) {
                File file = directory.getFile(nameAndData.name);
                Assertions.assertNotNull(file);
                byte[] readData = new byte[nameAndData.data.length];
                file.read(readData);
                Assertions.assertEquals(file.getName(), nameAndData.name);
                Assertions.assertArrayEquals(readData, nameAndData.data);
            }
        }
    }

    private static class NameAndData {
        final String name;
        final byte[] data;

        NameAndData(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

}
