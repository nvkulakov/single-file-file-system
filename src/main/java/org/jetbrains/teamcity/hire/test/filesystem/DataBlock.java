package org.jetbrains.teamcity.hire.test.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.teamcity.hire.test.exceptions.NotEnoughFreeSpaceException;

class DataBlock extends Block {

    private static final long LAST_BLOCK_IN_DATA_CHAIN = -2L;
    private static final long UNKNOWN_POSITION = -1L;

    private long nextDataBlockPosition = UNKNOWN_POSITION;

    DataBlock(RandomAccessFile file, long fileBegin, long fileSize, long startPosition) {
        super(file, fileBegin, fileSize, startPosition);
    }

    DataBlock(Block base) {
        super(base);
    }

    DataBlock(Block base, long newPosition) {
        super(base, newPosition);
    }

    /**
     * White all the required system fields, initialize data space with zeros.
     *
     * @param length full length of the block.
     */
    DataBlock initialize(long length) throws IOException, NotEnoughFreeSpaceException {
        if (length < MIN_BLOCK_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Min block length is %s, but the length value is %s", MIN_BLOCK_LENGTH, length));
        }
        if (startPosition + length > fileSize) {
            throw new NotEnoughFreeSpaceException();
        }
        setData();
        setLength(length);
        setLastBlockInDataChain();
        fillDataSpaceAsEmpty();
        return this;
    }

    /**
     * Reads {@code destination.length} bytes starting from {@code offset} offset in the data blocks chain into {@code destination}.
     */
    void read(long offset, byte[] destination) throws IOException {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        Objects.requireNonNull(destination, "destination must be not null");
        if (destination.length == 0) {
            return;
        }
        if (offset + destination.length > getDataChainCapacity()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot read %d bytes starting from %d: the chain capacity is %s bytes!",
                    destination.length, offset, getDataChainCapacity()));
        }
        long offsetInBlock = offset;
        DataBlock dataBlock = this;
        while (offsetInBlock >= dataBlock.getDataCapacity()) {
            offsetInBlock -= dataBlock.getDataCapacity();
            dataBlock = dataBlock.getNextDataBlock()
                    .orElseThrow(() -> new IllegalStateException("There is no enough space in chain!"));
        }
        int bytesRead = 0;
        while (destination.length - bytesRead > 0) {
            int readBytesInThisBlock = (int) Math.min(destination.length - bytesRead, dataBlock.getDataCapacity() - offsetInBlock);
            file.seek(dataBlock.getStartPosition() + DATA_OFFSET + offsetInBlock);
            file.read(destination, bytesRead, readBytesInThisBlock);
            bytesRead += readBytesInThisBlock;
            if (bytesRead == destination.length) {
                break;
            }
            offsetInBlock = 0; // can be > 0 only in the first block in chain
            dataBlock = dataBlock.getNextDataBlock()
                    .orElseThrow(() -> new IllegalStateException("There is not enough space in chain!"));
        }
    }

    /**
     * Writes {@code source.length} bytes starting from {@code offset} offset in the data blocks chain from {@code source}.
     * If the data chain blocks capacity is not enough, enlarge it to at leas {@code offset + source.length}.
     */
    void write(long offset, byte[] source) throws IOException, NotEnoughFreeSpaceException {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        Objects.requireNonNull(source, "source must be not null");
        if (source.length == 0) {
            return;
        }
        if (offset + source.length > getDataChainCapacity()) {
            enlarge(offset + source.length);
        }
        long offsetInBlock = offset;
        DataBlock dataBlock = this;
        while (offsetInBlock >= dataBlock.getDataCapacity()) {
            offsetInBlock -= dataBlock.getDataCapacity();
            dataBlock = dataBlock.getNextDataBlock()
                    .orElseThrow(() -> new IllegalStateException("There is not enough space in chain!"));
        }
        int bytesWritten = 0;
        while (source.length - bytesWritten > 0) {
            int writeBytesInThisBlock = (int) Math.min(source.length - bytesWritten, dataBlock.getDataCapacity() - offsetInBlock);
            file.seek(dataBlock.getStartPosition() + DATA_OFFSET + offsetInBlock);
            file.write(source, bytesWritten, writeBytesInThisBlock);
            bytesWritten += writeBytesInThisBlock;
            if (bytesWritten == source.length) {
                break;
            }
            offsetInBlock = 0; // can be > 0 only in the first block in chain
            dataBlock = dataBlock.getNextDataBlock()
                    .orElseThrow(() -> new IllegalStateException("There is not enough space in chain!"));
        }
    }

    void write(int offset, long number) throws IOException, NotEnoughFreeSpaceException {
        write(offset, ByteBuffer.allocate(8).putLong(number).array());
    }


    /**
     * Removes all the data blocks chain starting from this.
     */
    void removeChain() throws IOException {
        if (startPosition == firstBlockPosition) {
            throw new IllegalStateException("Cannot remove first (root directory) block!");
        }
        DataBlock current = this;
        while (current != null) {
            Optional<DataBlock> nextDataBlock = current.getNextDataBlock();
            Block previous = current.getPrevious()
                    .orElseThrow(() -> new IllegalStateException("Must not happen due to check above"));
            DataBlock next = current.getNextDataBlock().orElse(null);
            if (previous.isFree()) {
                if (next != null && next.isFree()) {
                    new FreeBlock(previous).initialize(previous.getLength() + current.getLength() + next.getLength());
                } else {
                    new FreeBlock(previous).initialize(previous.getLength() + current.getLength());
                }
            } else {
                if (next != null && next.isFree()) {
                    new FreeBlock(current).initialize(current.getLength() + next.getLength());
                } else {
                    new FreeBlock(current).initialize(current.getLength());
                }
            }
            current = nextDataBlock.orElse(null);
        }
    }

    /**
     * Adds {@code newDataSize} bytes to the data blocks chain.
     */
    void enlarge(long newDataSize) throws IOException, NotEnoughFreeSpaceException {
        long dataChainCapacity = getDataChainCapacity();
        if (newDataSize <= dataChainCapacity) {
            return;
        }
        long bytesToAdd = newDataSize - dataChainCapacity;
        DataBlock lastInChain = getLastDataBlock();
        Optional<Block> next = lastInChain.getNext();
        if (next.isPresent() && next.get().isFree()) {
            // Merge with the next free - reduce fragmentation
            Block nextFree = next.get();
            if (nextFree.getLength() >= bytesToAdd) {
                if (nextFree.getLength() - bytesToAdd < MIN_BLOCK_LENGTH) {
                    // add the whole block
                    lastInChain.extendIntoNext(nextFree.getLength());
                } else {
                    lastInChain.extendIntoNext(bytesToAdd);
                    new FreeBlock(this, nextFree.getStartPosition() + bytesToAdd)
                            .initialize(nextFree.getLength() - bytesToAdd);
                }
                return;
            }
            lastInChain.extendIntoNext(nextFree.getLength());
            bytesToAdd -= nextFree.getLength();
        }
        DataBlock nextData = findFirstFreeBlock().allocate(bytesToAdd);
        lastInChain.setNextDataBlock(nextData);
    }

    private long getNextDataBlockPosition() throws IOException {
        if (nextDataBlockPosition == UNKNOWN_POSITION) {
            file.seek(startPosition + POSITION_OFFSET);
            nextDataBlockPosition = file.readLong();
        }
        return nextDataBlockPosition;
    }

    private void setLastBlockInDataChain() throws IOException {
        file.seek(startPosition + POSITION_OFFSET);
        file.writeLong(LAST_BLOCK_IN_DATA_CHAIN);
        nextDataBlockPosition = LAST_BLOCK_IN_DATA_CHAIN;
    }

    private Optional<DataBlock> getNextDataBlock() throws IOException {
        long nextBlockPosition = getNextDataBlockPosition();
        if (nextBlockPosition == LAST_BLOCK_IN_DATA_CHAIN) {
            return Optional.empty();
        }
        return Optional.of(new DataBlock(this, nextBlockPosition));
    }

    void setNextDataBlock(DataBlock next) throws IOException {
        file.seek(startPosition + POSITION_OFFSET);
        file.writeLong(next.getStartPosition());
        nextDataBlockPosition = next.getStartPosition();
    }

    long getDataChainCapacity() throws IOException {
        long dataChainCapacity = getDataCapacity();
        Optional<DataBlock> nextDataBlock = getNextDataBlock();
        while (nextDataBlock.isPresent()) {
            dataChainCapacity += nextDataBlock.get().getDataCapacity();
            nextDataBlock = nextDataBlock.get().getNextDataBlock();
        }
        return dataChainCapacity;
    }

    private DataBlock getLastDataBlock() throws IOException {
        DataBlock block = this;
        Optional<DataBlock> next = getNextDataBlock();
        while (next.isPresent()) {
            block = next.get();
            next = block.getNextDataBlock();
        }
        return block;
    }

    private void extendIntoNext(long additionalSize) throws IOException {
        if (getNextDataBlock().isPresent()) {
            throw new IllegalStateException("Only last block in the chain can be extended");
        }
        long oldLength = getLength();
        long newLength = oldLength + additionalSize;
        setLength(newLength);
        // It is important to fill the extended space with zeros but not touch the existing data
        fillWithZeros(startPosition + oldLength - LENGTH_BYTES, additionalSize);
    }

}
