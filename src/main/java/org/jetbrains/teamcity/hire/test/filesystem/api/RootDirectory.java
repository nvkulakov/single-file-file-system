package org.jetbrains.teamcity.hire.test.filesystem.api;

import java.io.Closeable;

/**
 * The root directory of the file system.
 * Provides files/directories creating, getting, removing etc.
 * Root directory should be closed after using, see {@link Closeable#close()}.
 */
public interface RootDirectory extends Directory, Closeable {

}
