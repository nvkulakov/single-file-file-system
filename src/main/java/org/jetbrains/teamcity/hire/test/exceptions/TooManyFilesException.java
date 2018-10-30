package org.jetbrains.teamcity.hire.test.exceptions;

public class TooManyFilesException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TooManyFilesException(int maxFiles) {
        super("The root directory reached its maximum capacity " + maxFiles);
    }

}
