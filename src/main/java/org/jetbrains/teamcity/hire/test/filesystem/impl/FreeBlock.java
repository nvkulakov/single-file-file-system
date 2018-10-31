package org.jetbrains.teamcity.hire.test.filesystem.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEnoughFreeSpaceException;

class FreeBlock extends Block {

    FreeBlock(RandomAccessFile file, long fileBegin, long fileSize, long startPosition) {
        super(file, fileBegin, fileSize, startPosition);
    }

    FreeBlock(Block base) {
        super(base);
    }

    FreeBlock(Block base, long newPosition) {
        super(base, newPosition);
    }

    /**
     * White {@code type} and {@code length} system fields. Free space is not initialized!
     *
     * @param length full length of the block.
     */
    FreeBlock initialize(long length) throws IOException, NotEnoughFreeSpaceException {
        if (length < MIN_BLOCK_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Min block length is %s, but the length value is %s", MIN_BLOCK_LENGTH, length));
        }
        if (startPosition + length > fileSize) {
            throw new NotEnoughFreeSpaceException();
        }
        setFree();
        setLength(length);
        return this;
    }

    /**
     * Allocates a chain of data blocks with at least {@code dataCapacity} free space, beginning from this free block.
     */
    DataBlock allocate(long dataCapacity) throws IOException, NotEnoughFreeSpaceException {
        if (getDataCapacity() >= dataCapacity) {
            return cutDataBlock(dataCapacity);
        }
        // This freeBlock is not big enough to store all the data, a chain of blocks is required
        DataBlock firstInChain = transformToData();
        DataBlock current = firstInChain;
        long remainingDataCapacity = dataCapacity - firstInChain.getDataCapacity();
        while (remainingDataCapacity > 0) {
            FreeBlock nextFree = current.findNextFreeBlock();
            DataBlock next = nextFree.getDataCapacity() >= remainingDataCapacity
                    ? nextFree.cutDataBlock(remainingDataCapacity)
                    : nextFree.transformToData();
            current.setNextDataBlock(next);
            current = next;
            remainingDataCapacity -= current.getDataCapacity();
        }
        return firstInChain;
    }

    /**
     * Split the free block to the data block and a free smaller one, if this smaller is big enough to be a valid block.
     */
    private DataBlock cutDataBlock(long dataSize) throws IOException, NotEnoughFreeSpaceException {
        if (getDataCapacity() < dataSize) {
            throw new IllegalArgumentException(String.format("Cannot cut %s data bytes, the block is too small!", dataSize));
        }
        if (getDataCapacity() - dataSize < MIN_BLOCK_LENGTH) {
            return transformToData();
        }
        long dataBlockLength = Math.max(SERVICE_DATA_BYTES + dataSize, MIN_BLOCK_LENGTH);
        DataBlock dataBlock = new DataBlock(this).initialize(dataBlockLength);
        new FreeBlock(this, startPosition + dataBlockLength).initialize(getLength() - dataBlockLength);
        return dataBlock;
    }

    private DataBlock transformToData() throws IOException {
        return new DataBlock(this).initialize(getLength());
    }

}
