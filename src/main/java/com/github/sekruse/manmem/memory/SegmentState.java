package com.github.sekruse.manmem.memory;

/**
 * The states in the enum can be assumed by a main memory segment.
 */
public enum SegmentState {

    /**
     * A free memory segment is not in use by any client.
     */
    FREE,

    /**
     * A dirty memory segment is in use by a client and contains that data that is not represented in the secondary
     * storage.
     */
    DIRTY,

    /**
     * A backed memory segment is in use by a client but its contained data is also represented in the secondary
     * storage.
     */
    BACKED

}
