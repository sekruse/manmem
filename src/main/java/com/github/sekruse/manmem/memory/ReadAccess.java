package com.github.sekruse.manmem.memory;

import java.nio.ByteBuffer;

/**
 * This object encapsulates a read access.
 */
public class ReadAccess extends MemoryAccess {

    /**
     * Creates a new access object for some memory.
     *
     * @param memory the payload of the memory
     */
    public ReadAccess(Memory memory) {
        super(memory);
    }

}
