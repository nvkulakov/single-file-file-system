package org.jetbrains.teamcity.hire.test.exceptions;

public class NotEmptyDirectoryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotEmptyDirectoryException() {
        super("Cannot remove not empty directory");
    }

}
