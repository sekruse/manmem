package com.github.sekruse.manmem.manager;

/**
 * This exception is the base exception for any exception that is related to the handling of managed memory. As
 * memory management utilisation can be pervasive and should generally work, this exception is uncaught.
 */
public class ManagedMemoryException extends RuntimeException {

    public ManagedMemoryException() {
    }

    public ManagedMemoryException(String message) {
        super(message);
    }

    public ManagedMemoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public ManagedMemoryException(Throwable cause) {
        super(cause);
    }

    public ManagedMemoryException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
