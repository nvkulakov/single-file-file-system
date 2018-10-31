package org.jetbrains.teamcity.hire.test.filesystem.api;

import java.io.IOException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEnoughFreeSpaceException;

public interface File {

    /**
     * The name of the file.
     *
     * @return the name of the file.
     */
    String getName();

    /**
     * The size of the file data space in bytes.
     *
     * @return size of the file data space in bytes
     *
     * @throws IOException if some I/O error occurs.
     */
    long getFileSize() throws IOException;

    /**
     * Reads {@code destination.length} bytes from the beginning of the file into the provided byte array.
     * <p>
     * The byte array length should not be greater than the file size!
     *
     * @param destination the buffer into which the data is read.
     *
     * @throws IOException if some I/O error occurs.
     */
    void read(byte[] destination) throws IOException;

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
    void read(int offset, byte[] destination) throws IOException;

    /**
     * Writes {@code data.length} bytes from the provided byte array into the file from the beginning.
     * If the file size lesser than is required to write the data, it will be extended to at least {@code data.length} bytes.
     *
     * @param data the data.
     *
     * @throws IOException                 if some I/O error occurs.
     * @throws NotEnoughFreeSpaceException if the file cannot be extended due to enough free space absence.
     */
    void write(byte[] data) throws IOException, NotEnoughFreeSpaceException;

    /**
     * Writes {@code data.length} bytes from the provided byte array into the file starting at offset {@code offset}.
     * If the file size lesser than is required to write the data, it will be extended to at least {@code offset + data.length} bytes.
     *
     * @param data the data.
     *
     * @throws IOException                 if some I/O error occurs.
     * @throws NotEnoughFreeSpaceException if the file cannot be extended due to enough free space absence.
     */
    void write(int offset, byte[] data) throws IOException, NotEnoughFreeSpaceException;

}
