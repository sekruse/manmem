package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.GlobalMemoryManager;
import com.github.sekruse.manmem.memory.Memory;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link GlobalMemoryManager}.
 */
public class GlobalMemoryManagerTest {

    @Test
    public void testMemoryLifecycle() {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and release it.
        Memory memory = memoryManager.requestDefaultMemory();
        memory.release();
    }

    @Test
    public void testMultipleMemoryLifecycles() {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and release it.
        Memory memory = memoryManager.requestDefaultMemory();
        memory.release();
        memory = memoryManager.requestDefaultMemory();
        memory.release();
        memory = memoryManager.requestDefaultMemory();
        memory.release();
        memory = memoryManager.requestDefaultMemory();
        memory.release();
    }

    @Test
    public void testExceedingCapacity() {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and release it.
        memoryManager.requestDefaultMemory();
        memoryManager.requestDefaultMemory();
        boolean thirdRequestFailed = false;
        try {
            memoryManager.requestDefaultMemory();
        } catch (CapacityExceededException e) {
            thirdRequestFailed = true;
        }

        Assert.assertTrue(thirdRequestFailed);
    }

}
