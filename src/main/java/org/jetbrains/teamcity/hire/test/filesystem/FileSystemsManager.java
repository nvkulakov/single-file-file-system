package org.jetbrains.teamcity.hire.test.filesystem;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Manages file system in a file creation, format and loading {@link RootDirectory} from properly formatted file.
 */
@NotThreadSafe
public class FileSystemsManager {

    private static final long MIN_DATA_SIZE = Integer.getInteger("minFileDataSize", 200);
    private static final long MIN_FILE_SIZE = RootDirectory.DEFAULT_SIZE + MIN_DATA_SIZE;
    private static final byte[] FILE_SYSTEM_ID = "SingleFileFileSystem_v0.01".getBytes();

    /**
     * Creates and formats a file at the {@code path} with the file system in a file format.
     * After file formatting, {@link RootDirectory} can be obtained with {@link #load(Path)};
     *
     * @param path     the path to the formatting file.
     * @param fileSize file system full size. Cannot be lesser than {@link #MIN_FILE_SIZE} (1000 by default).
     *
     * @throws IOException if some I/O error occurs.
     */
    public static void createAndFormat(Path path, long fileSize) throws IOException {
        Objects.requireNonNull(path, "path must be not null");
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("path should not be a directory");
        }
        if (fileSize < MIN_FILE_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "File system file cannot be less than %s bytes, but the fileSize is: %s", MIN_FILE_SIZE, fileSize));
        }
        Files.createDirectories(path.getParent());
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(fileSize);
            file.write(FILE_SYSTEM_ID);
            new FreeBlock(file, FILE_SYSTEM_ID.length, file.length(), FILE_SYSTEM_ID.length)
                    .initialize(file.length() - FILE_SYSTEM_ID.length);
            RootDirectory.create(file, FILE_SYSTEM_ID.length);
        }
    }

    /**
     * Returns {@code true} if a file by the {@code path} is formatted according with the file system in a file format.
     *
     * @param path the path to the formatted file.
     *
     * @return {@code true} if the file by the path exists and is formatted according with the file system format.
     *
     * @throws IOException if some I/O error occurs.
     */
    public static boolean isFormatted(Path path) throws IOException {
        Objects.requireNonNull(path, "path must be not null");
        if (!Files.exists(path) || Files.isDirectory(path) || Files.size(path) < MIN_FILE_SIZE) {
            return false;
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            byte[] readIdBytes = new byte[FILE_SYSTEM_ID.length];
            file.read(readIdBytes);
            if (!Arrays.equals(FILE_SYSTEM_ID, readIdBytes)) {
                System.out.println(String.format("By path '%s' file system id is '%s', but expected is: '%s'",
                        path, new String(readIdBytes, StandardCharsets.ISO_8859_1),
                        new String(FILE_SYSTEM_ID, StandardCharsets.ISO_8859_1)));
                return false;
            }
        }
        return true;
    }

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
    public static RootDirectory load(Path path) throws IOException {
        Objects.requireNonNull(path, "path must be not null");
        if (!isFormatted(path)) {
            throw new IllegalArgumentException("Cannot load not existing or not formatted file: " + path);
        }
        RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
        file.getChannel().lock(); // lock is released with file close
        return new RootDirectory(file, FILE_SYSTEM_ID.length);
    }

}
