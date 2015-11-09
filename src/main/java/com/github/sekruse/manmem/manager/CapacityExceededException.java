package com.github.sekruse.manmem.manager;

/**
 * This exception signals that a request for memory cannot be served by a {@link MemoryManager} without exceeding the
 * granted memory capacity.
 */
public class CapacityExceededException extends RuntimeException {

    /**
     * @see RuntimeException#RuntimeException()
     */
    public CapacityExceededException() {
    }

    /**
     * @see RuntimeException#RuntimeException(String)
     */
    public CapacityExceededException(String message) {
        super(message);
    }

    /**
     * @see RuntimeException#RuntimeException(String, Throwable)
     */
    public CapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @see RuntimeException#RuntimeException(Throwable)
     */
    public CapacityExceededException(Throwable cause) {
        super(cause);
    }

    /**
     * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
     */
    public CapacityExceededException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
