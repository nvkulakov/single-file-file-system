package org.jetbrains.teamcity.hire.test.exceptions;

public class NotEnoughFreeSpaceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotEnoughFreeSpaceException() {
        super("Not enough free space in the file!");
    }

}
