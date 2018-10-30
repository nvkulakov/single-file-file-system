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
import org.jetbrains.teamcity.hire.test.exceptions.IllegalFileNameException;
import org.jetbrains.teamcity.hire.test.exceptions.NotEmptyDirectoryException;
import org.jetbrains.teamcity.hire.test.exceptions.NotEnoughFreeSpaceException;
import org.jetbrains.teamcity.hire.test.exceptions.TooManyFilesException;
import org.jetbrains.teamcity.hire.test.filesystem.api.Directory;
import org.jetbrains.teamcity.hire.test.filesystem.api.File;

import static org.jetbrains.teamcity.hire.test.filesystem.impl.Block.MAX_BYTE_ARRAY_SIZE;
import static org.jetbrains.teamcity.hire.test.filesystem.impl.Block.POSITION_BYTES;

class DirectoryImpl implements Directory {

    private static final int FILE_NAME_SIZE = Integer.getInteger("fileNameSize", 42);
    private static final int FILE_RECORD_SIZE = POSITION_BYTES + FILE_NAME_SIZE;
    private static final int INITIAL_FILES_CAPACITY = Integer.getInteger("initialRootDirectoryCapacity", 16);
    static final int DEFAULT_SIZE = INITIAL_FILES_CAPACITY * FILE_RECORD_SIZE;
    private static final int MAX_FILES_IN_DIR = Math.min(
            Integer.getInteger("maxFilesInRootDirectory", 2048),
            MAX_BYTE_ARRAY_SIZE / FILE_RECORD_SIZE); // allow to load all records in one byte array

    private static final CharsetEncoder US_ASCII_ENCODER = StandardCharsets.US_ASCII.newEncoder();

    private final DataBlock contentBlock;

    DirectoryImpl(DataBlock contentBlock) {
        this.contentBlock = Objects.requireNonNull(contentBlock, "contentBlock must be not null");
    }

    @Override
    public File createFile(String name, int size)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException {
        Objects.requireNonNull(name, "File name must be not null");
        if (size < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        checkNameCorrectness(name, FILE_NAME_SIZE);
        int filesCount = getFilesCount();
        if (filesCount >= MAX_FILES_IN_DIR) {
            throw new TooManyFilesException(MAX_FILES_IN_DIR);
        }
        DataBlock fileDataBlock = contentBlock.findFirstFreeBlock().allocate(Math.max(size, Block.MIN_DATA_CAPACITY));
        addFileRecord(name, fileDataBlock, filesCount);
        return new FileImpl(fileDataBlock, name);
    }

    @Override
    public Directory createDirectory(String name)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException {
        Objects.requireNonNull(name, "Directory name must be not null");
        checkNameCorrectness(name, FILE_NAME_SIZE - 1);
        int filesCount = getFilesCount();
        if (filesCount >= MAX_FILES_IN_DIR) {
            throw new TooManyFilesException(MAX_FILES_IN_DIR);
        }
        DataBlock directoryContentBlock = contentBlock.findFirstFreeBlock().allocate(Math.max(DEFAULT_SIZE, Block.MIN_DATA_CAPACITY));
        addFileRecord('/' + name, directoryContentBlock, filesCount);
        return new DirectoryImpl(directoryContentBlock);
    }

    @Override
    public int getFilesCount() throws IOException {
        int filesCount = 0;
        for (FileRecord fileRecord : loadFileRecords()) {
            if (!fileRecord.isEmpty()) {
                filesCount++;
            }
        }
        return filesCount;
    }

    @Override
    public boolean isEmpty() throws IOException {
        return getFilesCount() == 0;
    }

    @Override
    public List<String> getFileNames() throws IOException {
        List<String> fileNames = new ArrayList<>();
        for (FileRecord record : loadFileRecords()) {
            if (!record.isEmpty()) {
                fileNames.add(record.getName());
            }
        }
        return fileNames;
    }

    @Nullable
    @Override
    public File getFile(String name) throws IOException {
        Objects.requireNonNull(name, "File name must be not null");
        if (isDirectoryName(name)) {
            return null;
        }
        for (FileRecord record : loadFileRecords()) {
            if (!record.isEmpty() && record.getName().equals(name)) {
                return record.toFile();
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Directory getDirectory(String name) throws IOException {
        Objects.requireNonNull(name, "Directory name must be not null");
        if (!isDirectoryName(name)) {
            return null;
        }
        for (FileRecord record : loadFileRecords()) {
            if (!record.isEmpty() && record.getName().equals(name)) {
                return record.toDirectory();
            }
        }
        return null;
    }

    @Override
    public void removeFile(String name) throws IOException, NotEmptyDirectoryException {
        Objects.requireNonNull(name, "name must be not null");
        for (FileRecord record : loadFileRecords()) {
            if (!record.isEmpty() && record.getName().equals(name)) {
                if (isDirectoryName(name) && !record.toDirectory().isEmpty()) {
                    throw new NotEmptyDirectoryException();
                }
                record.getDataBlock().removeChain();
                // zero position bytes mean empty record
                contentBlock.write(record.getIndex() * FILE_RECORD_SIZE, new byte[POSITION_BYTES]);
                return;
            }
        }
    }

    private boolean isDirectoryName(String name) {
        return !name.isEmpty() && name.charAt(0) == '/';
    }

    private void checkNameCorrectness(String name, int maxNameLength) throws IOException, IllegalFileNameException {
        if (name.isEmpty()) {
            throw new IllegalFileNameException("File name cannot be empty!");
        }
        if (!US_ASCII_ENCODER.canEncode(name)) {
            throw new IllegalFileNameException(String.format("The name '%s' contains symbols not in US_ASCII charset!", name));
        }
        char[] chars = name.toCharArray();
        if (chars.length > maxNameLength) {
            throw new IllegalFileNameException(
                    String.format("File name length cannot be > %d, but the name length is %d", maxNameLength, chars.length));
        }
        for (char c : chars) {
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == ' ')) {
                throw new IllegalFileNameException(
                        String.format("File name should contain only letters, digits, underscore and space, but contains: '%c'", c));
            }
        }
        if (chars[0] == ' ' || chars[chars.length - 1] == ' ') {
            throw new IllegalFileNameException("File name cannot begin or end with space!");
        }
        if (fileNameExists(name)) {
            throw new IllegalFileNameException("A file with such name is already presented!");
        }
    }

    private void addFileRecord(String name, Block dataBlock, int filesCount) throws IOException, NotEnoughFreeSpaceException {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        if (nameBytes.length > FILE_NAME_SIZE) {
            // bytes array length can be greater than string length
            throw new IllegalArgumentException(
                    String.format("Name string cannot be more than %s bytes but it is! String: %s, bytes: %s",
                            FILE_NAME_SIZE, name, Arrays.toString(nameBytes)));
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

    private boolean fileNameExists(String name) throws IOException {
        for (FileRecord record : loadFileRecords()) {
            if (!record.isEmpty() && record.getName().equals(name)) {
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
            return new DirectoryImpl(getDataBlock());
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
