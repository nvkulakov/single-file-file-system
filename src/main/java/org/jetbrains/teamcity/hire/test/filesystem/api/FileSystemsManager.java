package org.jetbrains.teamcity.hire.test.filesystem.api;

import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages file system in a file creation, format and loading {@link RootDirectory} from properly formatted file.
 */
@NotThreadSafe
public interface FileSystemsManager {

    /**
     * Creates and formats a file at the {@code path} with the file system in a file format.
     * After file formatting, {@link RootDirectory} can be obtained with {@link #load(Path)};
     *
     * @param path     the path to the formatting file.
     * @param fileSize file system full size. Cannot be lesser than 1000 by default.
     *
     * @throws IOException if some I/O error occurs.
     */
    void createAndFormat(Path path, long fileSize) throws IOException;

    /**
     * Returns {@code true} if a file by the {@code path} is formatted according with the file system in a file format.
     *
     * @param path the path to the formatted file.
     *
     * @return {@code true} if the file by the path exists and is formatted according with the file system format.
     *
     * @throws IOException if some I/O error occurs.
     */
    boolean isFormatted(Path path) throws IOException;

    /**
     * Loads the previously formatted file, returns {@link RootDirectory} to operate with files.
     * It is not possible to load the same file twice as well as perform any another I/O operations with it
     * before the obtained {@link RootDirectory} will be closed.
     *
     * @param path the path to the formatted file.
     *
     * @return {@link RootDirectory} to operate with files. Take a note that {@link RootDirectory} should be closed after using!
     *
     * @throws IOException if the formatted file is already loaded or some another I/O error occurs.
     */
    RootDirectory load(Path path) throws IOException;

}
