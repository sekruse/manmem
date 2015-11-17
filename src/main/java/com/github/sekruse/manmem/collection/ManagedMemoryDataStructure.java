package com.github.sekruse.manmem.collection;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.capabilities.MemoryAccessException;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;

/**
 * This interface should be implemented by {@link VirtualMemorySegment} backed data structures that want to allow
 * to lock the {@link VirtualMemorySegment}s. This is usually done by acquiring and holding some kind of
 * {@link com.github.sekruse.manmem.memory.MemoryAccess} to them.
 */
public interface ManagedMemoryDataStructure {

    /**
     * Lock this data structure for the purpose of reading. If it is already locked for reading or writing, nothing
     * happens.
     *
     * @throws CapacityExceededException if the locking requires more than available memory
     * @throws MemoryAccessException     if the data structure cannot be locked for writing
     */
    void lockForRead() throws CapacityExceededException, MemoryAccessException;

    /**
     * Lock this data structure for the purpose of writing. If it is already locked for reading, nothing happens.
     *
     * @throws CapacityExceededException if the locking requires more than available memory
     * @throws MemoryAccessException     if the data structure cannot be locked for writing
     */
    void lockForWrite() throws CapacityExceededException, MemoryAccessException;


    /**
     * Release the lock for reading or writing.
     */
    void unlock();

    /**
     * @return the amount of virtual memory used by this object
     */
    long getUsedCapacity();

    /**
     * Releases the managed memory held by this data structure.
     *
     * @throws MemoryAccessException if the memory cannot be released
     */
    void dispose() throws MemoryAccessException;
}
