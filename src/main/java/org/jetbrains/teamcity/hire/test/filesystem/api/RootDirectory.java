package org.jetbrains.teamcity.hire.test.filesystem.api;

import java.io.Closeable;

/**
 * The root directory of the file system.
 * Provides files/directories creating, getting, removing etc.
 */
public interface RootDirectory extends Directory, Closeable {

}
