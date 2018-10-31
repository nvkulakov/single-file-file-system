package org.jetbrains.teamcity.hire.test.filesystem.exceptions;

/**
 * The specified file or directory name is not supported.
 */
public class IllegalFileNameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalFileNameException(String message) {
        super(message);
    }

}
