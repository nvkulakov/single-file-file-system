package org.jetbrains.teamcity.hire.test.filesystem.exceptions;

/**
 * Directory reached its maximum capacity.
 */
public class TooManyFilesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TooManyFilesException(String directoryName, int maxFiles) {
        super(String.format("The directory '%s' reached its maximum capacity %d", directoryName, maxFiles));
    }

}
