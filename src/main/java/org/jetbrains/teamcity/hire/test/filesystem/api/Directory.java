package org.jetbrains.teamcity.hire.test.filesystem.api;

import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.IllegalFileNameException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEmptyDirectoryException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.NotEnoughFreeSpaceException;
import org.jetbrains.teamcity.hire.test.filesystem.exceptions.TooManyFilesException;

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

    /**
     * Creates a directory with the provided {@code name}.
     *
     * @param name the name of the directory. Cannot be longer than 41 symbols by default
     *             (one symbol is reserved to leading slash that marks directory).
     *             Should contain only letters, digits, underscore and space, cannot start or end with space.
     *
     * @return created directory.
     *
     * @throws IOException                 if some I/O error occurs.
     * @throws IllegalFileNameException    if the provided directory name is illegal.
     * @throws NotEnoughFreeSpaceException if there are no enough free space in the file system file.
     * @throws TooManyFilesException       if the directory reached its maximum capacity (2048 files by default).
     */
    Directory createDirectory(String name)
            throws IOException, IllegalFileNameException, NotEnoughFreeSpaceException, TooManyFilesException;

    /**
     * Returns the number of files and directories in the directory.
     *
     * @return the number of files and directories in the directory.
     *
     * @throws IOException if some I/O error occurs.
     */
    int getFilesCount() throws IOException;

    /**
     * Is the directory contains any file or directory.
     *
     * @return {@code true} of the directory does not contain a file or a directory. {@code false} otherwise.
     *
     * @throws IOException if some I/O error occurs.
     */
    boolean isEmpty() throws IOException;

    /**
     * Provides files and directories names list in the directory. Directory names are started with slash.
     *
     * @return files and directories names list in the directory. Directory names are started with slash.
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

    /**
     * Returns a directory with the provided {@code name} or {@code null} if such directory does not exist.
     * Directory name should start with slash!
     *
     * @param name a name of directory starting with slash.
     *
     * @return directory or {@code null} if such directory not found.
     *
     * @throws IOException if some I/O error occurs.
     */
    @Nullable
    Directory getDirectory(String name) throws IOException;

    /**
     * Removes a file or a directory by its {@code name} if it is presented in the directory.
     * Directory name should start with slash, directory should be empty.
     *
     * @param name the name of file or directory.
     *
     * @throws IOException                if some I/O error occurs.
     * @throws NotEmptyDirectoryException if try to remove not empty directory.
     */
    void removeFile(String name) throws IOException, NotEmptyDirectoryException;

}
