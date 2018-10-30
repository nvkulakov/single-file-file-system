package org.jetbrains.teamcity.hire.test.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.teamcity.hire.test.exceptions.NotEnoughFreeSpaceException;

class Block {

    // Structure: TYPE_BYTES, LENGTH_BYTES, POSITION_BYTES, something, LENGTH_BYTES.

    static final int TYPE_BYTES = 1; // is it free or data
    static final int LENGTH_BYTES = 8; // full length of the block: long
    static final int POSITION_BYTES = 8; // position of the next data block in chain: long
    static final int SERVICE_DATA_BYTES = TYPE_BYTES + LENGTH_BYTES + POSITION_BYTES + LENGTH_BYTES;

    static final int LENGTH_FIRST_OFFSET = TYPE_BYTES;
    static final int POSITION_OFFSET = LENGTH_FIRST_OFFSET + LENGTH_BYTES;
    static final int DATA_OFFSET = POSITION_OFFSET + POSITION_BYTES;

    // To avoid too little block creation that can lead to fragmentation
    static final int MIN_DATA_CAPACITY = Integer.getInteger("minDataCapacity", 20);
    static final int MIN_BLOCK_LENGTH = SERVICE_DATA_BYTES + MIN_DATA_CAPACITY;

    // To avoid loading too big data into memory
    static final int MAX_BYTE_ARRAY_SIZE = Integer.getInteger("maxByteArraySize", 1_000_000);

    private static final byte FREE_BLOCK = 0;
    private static final byte DATA_BLOCK = 1;

    private static final int UNKNOWN_TYPE = -1;
    private static final long UNKNOWN_LENGTH = -1L;

    final RandomAccessFile file;
    final long firstBlockPosition;
    final long fileSize;
    final long startPosition; // start position of the block in file

    private int type = UNKNOWN_TYPE;
    private long length = UNKNOWN_LENGTH;

    // Transfer file length to avoid IO operations in constructor
    Block(RandomAccessFile file, long firstBlockPosition, long fileSize, long startPosition) {
        this.file = Objects.requireNonNull(file, "file must be not null");
        if (firstBlockPosition < 0) {
            throw new IllegalArgumentException("firstBlockPosition should not be negative");
        }
        this.firstBlockPosition = firstBlockPosition;
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize should be positive");
        }
        this.fileSize = fileSize;
        if (startPosition < firstBlockPosition || startPosition >= fileSize) {
            throw new IllegalArgumentException(String.format(
                    "Illegal startPosition value %s, must be between %s and %s", startPosition, firstBlockPosition, fileSize));
        }
        this.startPosition = startPosition;
    }

    Block(Block base) {
        this(base.file, base.firstBlockPosition, base.fileSize, base.startPosition);
    }

    Block(Block base, long newPosition) {
        this(base.file, base.firstBlockPosition, base.fileSize, newPosition);
    }

    boolean isFree() throws IOException {
        if (type == UNKNOWN_TYPE) {
            file.seek(startPosition);
            type = file.readByte();
        }
        return type == FREE_BLOCK;
    }

    void setFree() throws IOException {
        file.seek(startPosition);
        file.writeByte(FREE_BLOCK);
        type = FREE_BLOCK;
    }

    void setData() throws IOException {
        file.seek(startPosition);
        file.writeByte(DATA_BLOCK);
        type = DATA_BLOCK;
    }

    long getLength() throws IOException {
        if (length == UNKNOWN_LENGTH) {
            file.seek(startPosition + LENGTH_FIRST_OFFSET);
            length = file.readLong();
        }
        return length;
    }

    void setLength(long length) throws IOException {
        file.seek(startPosition + LENGTH_FIRST_OFFSET);
        file.writeLong(length);
        file.seek(startPosition + length - LENGTH_BYTES);
        // length duplicating in the end allows to find the beginning of the previous block, see getPrevious()
        file.writeLong(length);
        this.length = length;
    }

    void fillDataSpaceAsEmpty() throws IOException {
        fillWithZeros(startPosition + DATA_OFFSET, getDataCapacity());
    }

    void fillWithZeros(long position, long bytes) throws IOException {
        long bytesLeft = bytes;
        int bytesToWriteAtOnce = (int) Math.min(bytesLeft, MAX_BYTE_ARRAY_SIZE);
        byte[] emptyArray = new byte[bytesToWriteAtOnce];
        file.seek(position);
        while (bytesLeft > 0) {
            file.write(emptyArray, 0, bytesToWriteAtOnce);
            bytesLeft -= bytesToWriteAtOnce;
            bytesToWriteAtOnce = (int) Math.min(bytesLeft, MAX_BYTE_ARRAY_SIZE);
        }
    }

    long getStartPosition() {
        return startPosition;
    }

    long getDataCapacity() throws IOException {
        return getLength() - SERVICE_DATA_BYTES;
    }

    Optional<Block> getNext() throws IOException {
        long nextBlockStartPosition = startPosition + getLength();
        if (nextBlockStartPosition < fileSize) {
            return Optional.of(new Block(this, nextBlockStartPosition));
        }
        return Optional.empty();
    }

    Optional<Block> getPrevious() throws IOException {
        if (startPosition == firstBlockPosition) {
            return Optional.empty();
        }
        file.seek(startPosition - LENGTH_BYTES);
        long previousBlockLength = file.readLong();
        return Optional.of(new Block(this, startPosition - previousBlockLength));
    }

    FreeBlock findFirstFreeBlock() throws IOException, NotEnoughFreeSpaceException {
        Block first = new Block(this, firstBlockPosition);
        if (first.isFree()) {
            return new FreeBlock(first);
        }
        return first.findNextFreeBlock();
    }

    // Rather slow when there are a lot of blocks. Can be optimized with first free block position caching.
    FreeBlock findNextFreeBlock() throws IOException, NotEnoughFreeSpaceException {
        Block block = this;
        do {
            block = block.getNext().orElseThrow(NotEnoughFreeSpaceException::new);
        } while (!block.isFree());
        return new FreeBlock(block);
    }

}
