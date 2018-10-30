package org.jetbrains.teamcity.hire.test.exceptions;

public class IllegalFileNameException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IllegalFileNameException(String message) {
        super(message);
    }

}
