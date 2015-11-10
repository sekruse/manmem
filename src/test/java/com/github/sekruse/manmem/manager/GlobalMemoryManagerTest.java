package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.memory.Memory;
import com.github.sekruse.manmem.memory.ReadAccess;
import com.github.sekruse.manmem.memory.WriteAccess;
import jdk.nashorn.internal.ir.annotations.Ignore;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

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

        // Request some memory and lock it.
        final Memory memory1 = memoryManager.requestDefaultMemory();
        final ReadAccess readAccess1 = memory1.getReadAccess();

        final Memory memory2 = memoryManager.requestDefaultMemory();
        final ReadAccess readAccess2 = memory2.getReadAccess();

        boolean thirdRequestFailed = false;
        try {
            memoryManager.requestDefaultMemory();
        } catch (CapacityExceededException e) {
            thirdRequestFailed = true;
        }

        Assert.assertTrue(thirdRequestFailed);
    }

    @Test
    public void testReleaseLocks() throws Exception {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and lock it.
        final Memory memory1 = memoryManager.requestDefaultMemory();
        final ReadAccess readAccess1 = memory1.getReadAccess();

        final Memory memory2 = memoryManager.requestDefaultMemory();
        final WriteAccess writeAccess2 = memory2.getWriteAccess();

        // Release the locks.
        readAccess1.close();
        writeAccess2.close();

        // Now allocate some memory.
        memoryManager.requestDefaultMemory();
    }

    @Test
    public void testOverallocation() {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and write it.
        final Memory memory1 = memoryManager.requestDefaultMemory();
        try (WriteAccess writeAccess = memory1.getWriteAccess()) {
            final ByteBuffer buffer = writeAccess.getPayload();
            buffer.clear();
            buffer.put((byte) 1).flip();
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }

        // Do it again.
        final Memory memory2 = memoryManager.requestDefaultMemory();
        try (WriteAccess writeAccess = memory2.getWriteAccess()) {
            final ByteBuffer buffer = writeAccess.getPayload();
            buffer.clear();
            buffer.put((byte) 2).flip();
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }

        // Allocate a third memory segment.
        final Memory memory3 = memoryManager.requestDefaultMemory();

        // The capacity is exceeded, check if the other two memories are nevertheless valid.
        try (ReadAccess readAccess = memory1.getReadAccess()) {
            final ByteBuffer buffer = readAccess.getPayload();
            Assert.assertEquals(1, buffer.limit());
            Assert.assertEquals((byte) 1, buffer.get());
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }
        try (ReadAccess readAccess = memory2.getReadAccess()) {
            final ByteBuffer buffer = readAccess.getPayload();
            Assert.assertEquals(1, buffer.limit());
            Assert.assertEquals((byte) 2, buffer.get());
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }
    }

    @Ignore
    @Test
    public void testSharedReadAccess() {
        // TODO
    }

    @Ignore
    @Test
    public void testSimulatneousReadAndWriteAccess() {
        // TODO
    }

    @Ignore
    @Test
    public void testSimulatneousWriteAccesses() {
        // TODO
    }

}
