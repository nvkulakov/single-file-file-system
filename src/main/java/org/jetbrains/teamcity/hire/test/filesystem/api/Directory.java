package org.jetbrains.teamcity.hire.test.filesystem.api;

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.teamcity.hire.test.exceptions.IllegalFileNameException;
import org.jetbrains.teamcity.hire.test.exceptions.NotEmptyDirectoryException;
import org.jetbrains.teamcity.hire.test.exceptions.NotEnoughFreeSpaceException;
import org.jetbrains.teamcity.hire.test.exceptions.TooManyFilesException;

public interface Directory {

    /**
     * Creates a file with the provided {@code name} at least {@code size} bytes size.
     *
     * @param name the name of the file. Cannot be longer than 42 symbols by default.
     *             Should contain only letters, digits, underscore and space, cannot start or end with space.
     * @param size non-negative value - required file size.
     *             Really created file can be a little bit greater (to reduce fragmentation).
     *
     * @return created file.
     *
     * @throws IOException                 if some I/O error occurs.
     * @throws IllegalFileNameException    if the provided file name is illegal.
     * @throws NotEnoughFreeSpaceException if there are no enough free space in the file system file.
     * @throws TooManyFilesException       if the directory reached its maximum capacity (2048 files by default).
     */
    File createFile(String name, int size)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException;

    Directory createDirectory(String name)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException;

    int getFilesCount() throws IOException;

    boolean isEmpty() throws IOException;

    /**
     * Provides file names list in the directory.
     *
     * @return file names list in the directory.
     *
     * @throws IOException if some I/O error occurs.
     */
    List<String> getFileNames() throws IOException;

    /**
     * Returns a file with the provided {@code name} or {@code null} if such file does not exist.
     *
     * @param name a name of file.
     *
     * @return file or {@code null} if such file not found.
     *
     * @throws IOException if some I/O error occurs.
     */
    @Nullable
    File getFile(String name) throws IOException;

    @Nullable
    Directory getDirectory(String name) throws IOException;

    /**
     * Removes a file by its {@code name} if it is presented in the directory.
     *
     * @param name the name of the file.
     *
     * @throws IOException if some I/O error occurs.
     */
    void removeFile(String name) throws IOException, NotEmptyDirectoryException;

}
