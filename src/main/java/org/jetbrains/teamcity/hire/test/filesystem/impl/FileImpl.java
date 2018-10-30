package org.jetbrains.teamcity.hire.test.filesystem.impl;

import java.io.IOException;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;
import org.jetbrains.teamcity.hire.test.exceptions.NotEnoughFreeSpaceException;
import org.jetbrains.teamcity.hire.test.filesystem.api.File;

/**
 * File abstraction providing read/write data.
 */
@NotThreadSafe
class FileImpl implements File {

    private final DataBlock dataBlock;
    private final String name;

    FileImpl(DataBlock dataBlock, String name) {
        this.dataBlock = Objects.requireNonNull(dataBlock, "dataBlock must be not null");
        this.name = Objects.requireNonNull(name, "name must be not null");
    }

    /**
     * The name of the file.
     *
     * @return the name of the file.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * The size of the file data space in bytes.
     *
     * @return size of the file data space in bytes
     *
     * @throws IOException if some I/O error occurs.
     */
    @Override
    public long getFileSize() throws IOException {
        return dataBlock.getDataChainCapacity();
    }

    /**
     * Reads {@code destination.length} bytes from the beginning of the file into the provided byte array.
     * <p>
     * The byte array length should not be greater than the file size!
     *
     * @param destination the buffer into which the data is read.
     *
     * @throws IOException if some I/O error occurs.
     */
    @Override
    public void read(byte[] destination) throws IOException {
        read(0, destination);
    }

    /**
     * Reads {@code destination.length} bytes from the file starting with byte number {@code offset} into the provided byte array.
     * <p>
     * {@code offset + destination.length} should not be greater than file size!
     *
     * @param offset      an offset in the file starting from which the data is read.
     * @param destination the buffer into which the data is read.
     *
     * @throws IOException if some I/O error occurs.
     */
    @Override
    public void read(int offset, byte[] destination) throws IOException {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        Objects.requireNonNull(destination, "destination must be not null");
        dataBlock.read(offset, destination);
    }

    /**
     * Writes {@code data.length} bytes from the provided byte array into the file from the beginning.
     * If the file size lesser than is required to write the data, it will be extended to at least {@code data.length} bytes.
     *
     * @param data the data.
     *
     * @throws IOException                 if some I/O error occurs.
     * @throws NotEnoughFreeSpaceException if the file cannot be extended due to enough free space absence.
     */
    @Override
    public void write(byte[] data) throws IOException, NotEnoughFreeSpaceException {
        write(0, data);
    }

    /**
     * Writes {@code data.length} bytes from the provided byte array into the file starting at offset {@code offset}.
     * If the file size lesser than is required to write the data, it will be extended to at least {@code offset + data.length} bytes.
     *
     * @param data the data.
     *
     * @throws IOException                 if some I/O error occurs.
     * @throws NotEnoughFreeSpaceException if the file cannot be extended due to enough free space absence.
     */
    @Override
    public void write(int offset, byte[] data) throws IOException, NotEnoughFreeSpaceException {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        Objects.requireNonNull(data, "data must be not null");
        dataBlock.write(offset, data);
    }

}
