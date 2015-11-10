package com.github.sekruse.manmem.memory;

import java.nio.ByteBuffer;

/**
 * Common interface for any kind of access to {@link Memory}. The access is valid until the {@link #close()} method
 * has been called. An open access might exclude other accesses.
 */
public class MemoryAccess implements AutoCloseable {

    /**
     * The payload memory that is being accessed.
     */
    protected final ByteBuffer payload;

    /**
     * The {@link Memory} that is accessed by this object.
     */
    protected final Memory memory;

    /**
     * Creates a new access object for some memory.
     * @param memory the {@link Memory} that should be accessed
     */
    public MemoryAccess(Memory memory) {
        this.memory = memory;

        // Remove the MainMemorySegment from its queue.
        MainMemorySegment mainMemorySegment = this.memory.ensureMainMemorySegment();
        mainMemorySegment.unlink();
        this.payload = mainMemorySegment.asByteBuffer();
    }

    /**
     * @return the payload of the memory as {@link ByteBuffer} that is initially in read state
     */
    public ByteBuffer getPayload() {
        return this.payload;
    }

    @Override
    public void close() {
        // Let the concrete access implementation do any closing work.
        doClose();

        // Give the Memory back into control.
        this.memory.enqueue();
    }

    /**
     * This method is called from {@link #close()} and can be overwritten to perform closing tasks..
     */
    protected void doClose() {
    }
}
