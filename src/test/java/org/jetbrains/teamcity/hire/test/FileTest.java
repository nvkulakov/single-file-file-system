package org.jetbrains.teamcity.hire.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.jetbrains.teamcity.hire.test.filesystem.api.File;
import org.jetbrains.teamcity.hire.test.filesystem.api.RootDirectory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FileTest extends RootTest {

    @Test
    @DisplayName("Create a file, write simple content, read it")
    public void testWriteReadOneFile() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 1000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            String name = "First file";
            File file = directory.createFile(name, 10);
            byte[] writtenData = {22, -12, 1, 99, -128, 127, 63, 74, -14, -7};
            file.write(writtenData);
            byte[] readData = new byte[writtenData.length];
            file.read(readData);
            Assertions.assertArrayEquals(writtenData, readData);
        }
    }

    @Test
    @DisplayName("Create two files, write into them alternately random bytes, check content")
    public void testSequentialWriteIntoTwoFiles() throws IOException {
        fileSystemsManager.createAndFormat(fileSystemPath, 200_000);
        try (RootDirectory directory = fileSystemsManager.load(fileSystemPath)) {
            byte writeCycles = 100;
            int maxBlockSize = 1000;
            String name1 = "First file";
            String name2 = "Second file";
            File file1 = directory.createFile(name1, 0);
            File file2 = directory.createFile(name2, 0);
            List<byte[]> writtenBytes1 = new ArrayList<>(writeCycles);
            List<byte[]> writtenBytes2 = new ArrayList<>(writeCycles);
            int writtenBytesCount1 = 0;
            int writtenBytesCount2 = 0;
            Random random = new Random(0);
            for (byte cycle = 0; cycle < writeCycles; cycle++) {
                int length1 = random.nextInt(maxBlockSize);
                byte[] randomBytes1 = new byte[length1];
                random.nextBytes(randomBytes1);
                file1.write(writtenBytesCount1, randomBytes1);
                writtenBytesCount1 += length1;
                writtenBytes1.add(randomBytes1);

                int length2 = random.nextInt(maxBlockSize);
                byte[] randomBytes2 = new byte[length2];
                random.nextBytes(randomBytes2);
                file2.write(writtenBytesCount2, randomBytes2);
                writtenBytesCount2 += length2;
                writtenBytes2.add(randomBytes2);
            }
            byte[] readData1 = new byte[writtenBytesCount1];
            file1.read(readData1);
            byte[] readData2 = new byte[writtenBytesCount2];
            file2.read(readData2);

            int offset1 = 0;
            for (byte[] written : writtenBytes1) {
                byte[] actual = Arrays.copyOfRange(readData1, offset1, offset1 + written.length);
                Assertions.assertArrayEquals(written, actual);
                offset1 += written.length;
            }
            int offset2 = 0;
            for (byte[] written : writtenBytes2) {
                byte[] actual = Arrays.copyOfRange(readData2, offset2, offset2 + written.length);
                Assertions.assertArrayEquals(written, actual);
                offset2 += written.length;
            }
        }
    }

}
