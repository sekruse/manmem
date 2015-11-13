package com.github.sekruse.manmem.collection.list;

import com.github.sekruse.manmem.manager.MemoryManager;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;

/**
 * This class represents an array that is backed by {@link VirtualMemorySegment}s.
 */
public class IntArray extends AbstractIntTable {

    /**
     * Creates a new instance. Note that it is not {@code 0}-initialized unlike {@code int[]}.
     *
     * @param size          size of the array
     * @param memoryManager {@link MemoryManager} that manages the memory that back this array
     */
    public IntArray(long size, MemoryManager memoryManager) {
        super(size, memoryManager);
    }

    /**
     * Sets the value at a given position. Note that the value at unwritten positions is undefined.
     *
     * @param pos   the position
     * @param value the new value
     */
    public void set(long pos, int value) {
        super.set(pos, value);
    }

    /**
     * Gets the value at a given position. Note that the value at unwritten positions is undefined.
     *
     * @param pos the position
     */
    public int get(long pos) {
        return super.get(pos);
    }

    /**
     * Sets the values at all positions to the specified {@code value}.
     *
     * @param value the value to set
     */
    public void setAll(int value) {
        super.clear(value);
    }

}
