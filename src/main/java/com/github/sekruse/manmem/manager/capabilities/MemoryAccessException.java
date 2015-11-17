package com.github.sekruse.manmem.manager.capabilities;

import com.github.sekruse.manmem.manager.ManagedMemoryException;

/**
 * This exception declares a problem of a method that is related to the access of memory.
 */
public class MemoryAccessException extends ManagedMemoryException {

    public MemoryAccessException() {
        super();
    }

    public MemoryAccessException(String message) {
        super(message);
    }

    public MemoryAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public MemoryAccessException(Throwable cause) {
        super(cause);
    }

    public MemoryAccessException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
