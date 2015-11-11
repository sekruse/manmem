package com.github.sekruse.manmem.memory;

/**
 * This object encapsulates a read access.
 */
public class ReadAccess extends MemoryAccess {

    /**
     * Creates a new access object for some memory.
     *
     * @param virtualMemorySegment the payload of the memory
     */
    public ReadAccess(VirtualMemorySegment virtualMemorySegment) {
        super(virtualMemorySegment);
    }

    @Override
    public void close() {
        super.close();
        this.virtualMemorySegment.notifyReadAccessDone();
    }
}
