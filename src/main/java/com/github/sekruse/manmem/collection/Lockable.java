package com.github.sekruse.manmem.collection;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.memory.VirtualMemorySegment;

/**
 * This interface should be implemented by {@link VirtualMemorySegment} backed data structures that want to allow
 * to lock the {@link VirtualMemorySegment}s. This is usually done by acquiring and holding some kind of
 * {@link com.github.sekruse.manmem.memory.MemoryAccess} to them.
 */
public interface Lockable {

    /**
     * Lock this data structure for the purpose of reading. If it is already locked for reading or writing, nothing
     * happens.
     *
     * @throws CapacityExceededException if the locking requires more than available memory
     */
    void lockForRead() throws CapacityExceededException;

    /**
     * Lock this data structure for the purpose of writing. If it is already locked for reading, nothing happens.
     *
     * @throws CapacityExceededException if the locking requires more than available memory
     * @throws IllegalStateException if the data structure is locked for reading
     */
    void lockForWrite() throws CapacityExceededException;


    /**
     * Release the lock for reading or writing.
     */
    void unlock();

}
