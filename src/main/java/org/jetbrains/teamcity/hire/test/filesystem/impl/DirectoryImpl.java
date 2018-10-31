package org.jetbrains.teamcity.hire.test.filesystem.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.teamcity.hire.test.filesystem.api.Directory;
import org.jetbrains.teamcity.hire.test.filesystem.api.File;
import org.jetbrains.teamcity.hire.test.filesystem.api.RootDirectory;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.IllegalFileNameException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEmptyDirectoryException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEnoughFreeSpaceException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.TooManyFilesException;

import static org.jetbrains.teamcity.hire.test.filesystem.impl.Block.MAX_BYTE_ARRAY_SIZE;
import static org.jetbrains.teamcity.hire.test.filesystem.impl.Block.POSITION_BYTES;

class DirectoryImpl implements Directory {

    private static final int FILE_NAME_SIZE = Integer.getInteger("fileNameSize", 42);
    private static final int FILE_RECORD_SIZE = POSITION_BYTES + FILE_NAME_SIZE;
    private static final int INITIAL_FILES_CAPACITY = Integer.getInteger("initialRootDirectoryCapacity", 16);
    static final int DEFAULT_SIZE = INITIAL_FILES_CAPACITY * FILE_RECORD_SIZE;
    private static final int MAX_FILES_IN_DIR = Math.min(
            Integer.getInteger("maxFilesInDirectory", 2048),
            MAX_BYTE_ARRAY_SIZE / FILE_RECORD_SIZE); // allow to load all records in one byte array

    private static final CharsetEncoder US_ASCII_ENCODER = StandardCharsets.US_ASCII.newEncoder();

    private final String name;
    private final DataBlock contentBlock;

    /**
     * @param name         the directory name, should start with leading slash
     * @param contentBlock the directory content block
     */
    DirectoryImpl(String name, DataBlock contentBlock) {
        this.name = Objects.requireNonNull(name, "name must be not null");
        if (!isDirectoryName(name)) {
            throw new IllegalArgumentException("Unexpected directory name: " + name);
        }
        this.contentBlock = Objects.requireNonNull(contentBlock, "contentBlock must be not null");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public File createFile(String fileName, int size)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException {
        Objects.requireNonNull(fileName, "fileName must be not null");
        if (size < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        synchronized (RootDirectory.class) {
            checkFileNameCorrectness(fileName, FILE_NAME_SIZE);
            int filesCount = getFilesCount();
            if (filesCount >= MAX_FILES_IN_DIR) {
                throw new TooManyFilesException(name, MAX_FILES_IN_DIR);
            }
            DataBlock fileDataBlock = contentBlock.findFirstFreeBlock()
                    .allocate(Math.max(size, Block.MIN_DATA_CAPACITY));
            addFileRecord(fileName, fileDataBlock, filesCount);
            return new FileImpl(fileDataBlock, fileName);
        }
    }

    @Override
    public Directory createDirectory(String directoryName)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException {
        Objects.requireNonNull(directoryName, "directoryName must be not null");
        if (!isDirectoryName(directoryName)) {
            throw new IllegalFileNameException("Directory name should start with slash!");
        }
        synchronized (RootDirectory.class) {
            checkFileNameCorrectness(directoryName.substring(1), FILE_NAME_SIZE - 1);
            if (fileNameExists(directoryName)) {
                throw new IllegalFileNameException("A directory with such name is already presented!");
            }
            int filesCount = getFilesCount();
            if (filesCount >= MAX_FILES_IN_DIR) {
                throw new TooManyFilesException(name, MAX_FILES_IN_DIR);
            }
            DataBlock directoryContentBlock = contentBlock.findFirstFreeBlock()
                    .allocate(Math.max(DEFAULT_SIZE, Block.MIN_DATA_CAPACITY));
            addFileRecord(directoryName, directoryContentBlock, filesCount);
            return new DirectoryImpl(directoryName, directoryContentBlock);
        }
    }

    @Override
    public int getFilesCount() throws IOException {
        synchronized (RootDirectory.class) {
            int filesCount = 0;
            for (FileRecord fileRecord : loadFileRecords()) {
                if (!fileRecord.isEmpty()) {
                    filesCount++;
                }
            }
            return filesCount;
        }
    }

    @Override
    public boolean isEmpty() throws IOException {
        synchronized (RootDirectory.class) {
            return getFilesCount() == 0;
        }
    }

    @Override
    public List<String> getFileNames() throws IOException {
        synchronized (RootDirectory.class) {
            List<String> fileNames = new ArrayList<>();
            for (FileRecord record : loadFileRecords()) {
                if (!record.isEmpty()) {
                    fileNames.add(record.getName());
                }
            }
            return fileNames;
        }
    }

    @Nullable
    @Override
    public File getFile(String fileName) throws IOException {
        Objects.requireNonNull(fileName, "fileName must be not null");
        if (isDirectoryName(fileName)) {
            return null;
        }
        synchronized (RootDirectory.class) {
            for (FileRecord record : loadFileRecords()) {
                if (!record.isEmpty() && record.getName().equals(fileName)) {
                    return record.toFile();
                }
            }
            return null;
        }
    }

    @Nullable
    @Override
    public Directory getDirectory(String directoryName) throws IOException {
        Objects.requireNonNull(directoryName, "directoryName must be not null");
        if (!isDirectoryName(directoryName)) {
            return null;
        }
        synchronized (RootDirectory.class) {
            for (FileRecord record : loadFileRecords()) {
                if (!record.isEmpty() && record.getName().equals(directoryName)) {
                    return record.toDirectory();
                }
            }
            return null;
        }
    }

    @Override
    public void removeFile(String fileName) throws IOException, NotEmptyDirectoryException {
        Objects.requireNonNull(fileName, "fileName must be not null");
        synchronized (RootDirectory.class) {
            for (FileRecord record : loadFileRecords()) {
                if (!record.isEmpty() && record.getName().equals(fileName)) {
                    if (isDirectoryName(fileName) && !record.toDirectory().isEmpty()) {
                        throw new NotEmptyDirectoryException();
                    }
                    record.getDataBlock().removeChain();
                    // zero position bytes mean empty record
                    contentBlock.write(record.getIndex() * FILE_RECORD_SIZE, new byte[POSITION_BYTES]);
                    return;
                }
            }
        }
    }

    private boolean isDirectoryName(String name) {
        return !name.isEmpty() && name.charAt(0) == '/';
    }

    private void checkFileNameCorrectness(String name, int maxNameLength) throws IOException, IllegalFileNameException {
        if (name.isEmpty()) {
            throw new IllegalFileNameException("Name cannot be empty!");
        }
        if (!US_ASCII_ENCODER.canEncode(name)) {
            throw new IllegalFileNameException(String.format("The name '%s' contains symbols not in US_ASCII charset!", name));
        }
        char[] chars = name.toCharArray();
        if (chars.length > maxNameLength) {
            throw new IllegalFileNameException(
                    String.format("Name length cannot be > %d, but the name length is %d", maxNameLength, chars.length));
        }
        for (char c : chars) {
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == ' ')) {
                throw new IllegalFileNameException(
                        String.format("Name should contain only letters, digits, underscore and space, but contains: '%c'", c));
            }
        }
        if (chars[0] == ' ' || chars[chars.length - 1] == ' ') {
            throw new IllegalFileNameException("Name cannot begin or end with space!");
        }
        if (fileNameExists(name)) {
            throw new IllegalFileNameException("A file with such name is already presented!");
        }
    }

    private void addFileRecord(String fileName, Block dataBlock, int filesCount) throws IOException, NotEnoughFreeSpaceException {
        byte[] nameBytes = fileName.getBytes(StandardCharsets.US_ASCII);
        if (nameBytes.length > FILE_NAME_SIZE) {
            // bytes array length can be greater than string length
            throw new IllegalArgumentException(
                    String.format("Name string cannot be more than %s bytes but it is! String: %s, bytes: %s",
                            FILE_NAME_SIZE, fileName, Arrays.toString(nameBytes)));
        }
        int recordsCapacity = getFileRecordsCapacity();
        if (recordsCapacity == filesCount) {
            this.contentBlock.enlarge(2 * recordsCapacity * FILE_RECORD_SIZE);
        }
        int recordIndex = findFirstEmptyRecordIndex();
        this.contentBlock.write(recordIndex * FILE_RECORD_SIZE, dataBlock.getStartPosition());
        this.contentBlock.write(recordIndex * FILE_RECORD_SIZE + POSITION_BYTES, Arrays.copyOf(nameBytes, FILE_NAME_SIZE));
    }

    private int getFileRecordsCapacity() throws IOException {
        // expected to be <= MAX_FILES_IN_DIR => can cast to int
        return (int) (contentBlock.getDataChainCapacity() / FILE_RECORD_SIZE);
    }

    private int findFirstEmptyRecordIndex() throws IOException {
        for (FileRecord record : loadFileRecords()) {
            if (record.isEmpty()) {
                return record.getIndex();
            }
        }
        throw new IllegalStateException("No empty records found in root directory.");
    }

    private boolean fileNameExists(String fileName) throws IOException {
        for (FileRecord record : loadFileRecords()) {
            if (!record.isEmpty() && record.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private FileRecords loadFileRecords() throws IOException {
        // loading all file records at once extremely increases performance
        int recordsCapacity = getFileRecordsCapacity();
        byte[] allRecordsBytes = new byte[recordsCapacity * FILE_RECORD_SIZE];
        contentBlock.read(0, allRecordsBytes);
        return new FileRecords(allRecordsBytes);
    }

    private class FileRecords implements Iterable<FileRecord> {
        private final byte[] allRecordsBytes;
        private final int recordsCapacity;

        FileRecords(byte[] allRecordsBytes) {
            this.allRecordsBytes = Objects.requireNonNull(allRecordsBytes, "allRecordsBytes must be not null");
            this.recordsCapacity = allRecordsBytes.length / FILE_RECORD_SIZE;
        }

        private FileRecord get(int index) {
            byte[] bytes = Arrays.copyOfRange(allRecordsBytes, index * FILE_RECORD_SIZE, (index + 1) * FILE_RECORD_SIZE);
            return new FileRecord(index, bytes);
        }

        @Override
        public Iterator<FileRecord> iterator() {
            return new Iterator<FileRecord>() {
                private int position = 0;

                @Override
                public boolean hasNext() {
                    return position < recordsCapacity;
                }

                @Override
                public FileRecord next() {
                    return get(position++);
                }
            };
        }
    }

    private class FileRecord {
        private final int index;
        private final byte[] dataBlockPositionBytes;
        private final byte[] nameBytes;
        private final boolean empty;

        FileRecord(int index, byte[] recordBytes) {
            this.index = index;
            Objects.requireNonNull(recordBytes, "recordBytes must be not null");
            if (recordBytes.length != FILE_RECORD_SIZE) {
                throw new IllegalArgumentException(String.format(
                        "File recordBytes expected to be %d bytes, but is %d", FILE_RECORD_SIZE, recordBytes.length));
            }
            this.dataBlockPositionBytes = Arrays.copyOfRange(recordBytes, 0, POSITION_BYTES);
            this.nameBytes = Arrays.copyOfRange(recordBytes, POSITION_BYTES, FILE_RECORD_SIZE);
            this.empty = isAllZeros(dataBlockPositionBytes);
        }

        int getIndex() {
            return index;
        }

        boolean isEmpty() {
            return empty;
        }

        String getName() {
            if (isEmpty()) {
                throw new IllegalStateException("Empty file record do not have a name");
            }
            return new String(nameBytes, StandardCharsets.US_ASCII).trim();
        }

        DataBlock getDataBlock() {
            if (isEmpty()) {
                throw new IllegalStateException("Empty file record do not have data block");
            }
            long position = ByteBuffer.wrap(dataBlockPositionBytes).getLong();
            return new DataBlock(contentBlock, position);
        }

        File toFile() {
            return new FileImpl(getDataBlock(), getName());
        }

        Directory toDirectory() {
            return new DirectoryImpl(getName(), getDataBlock());
        }

        // The record is empty if and only if all the position bytes are zeros
        private boolean isAllZeros(byte[] bytes) {
            for (byte positionByte : bytes) {
                if (positionByte != 0) {
                    return false;
                }
            }
            return true;
        }
    }

}
