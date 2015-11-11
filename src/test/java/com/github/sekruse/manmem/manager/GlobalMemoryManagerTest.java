package com.github.sekruse.manmem.manager;

import com.github.sekruse.manmem.memory.VirtualMemorySegment;
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
        VirtualMemorySegment virtualMemorySegment = memoryManager.requestDefaultMemory();
        virtualMemorySegment.release();
    }

    @Test
    public void testMultipleMemoryLifecycles() {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and release it.
        VirtualMemorySegment virtualMemorySegment = memoryManager.requestDefaultMemory();
        virtualMemorySegment.release();
        virtualMemorySegment = memoryManager.requestDefaultMemory();
        virtualMemorySegment.release();
        virtualMemorySegment = memoryManager.requestDefaultMemory();
        virtualMemorySegment.release();
        virtualMemorySegment = memoryManager.requestDefaultMemory();
        virtualMemorySegment.release();
    }

    @Test
    public void testExceedingCapacity() {
        // Create a new memory manager.
        GlobalMemoryManager memoryManager = new GlobalMemoryManager(1024, 512);

        // Request some memory and lock it.
        final VirtualMemorySegment virtualMemorySegment1 = memoryManager.requestDefaultMemory();
        final ReadAccess readAccess1 = virtualMemorySegment1.getReadAccess();

        final VirtualMemorySegment virtualMemorySegment2 = memoryManager.requestDefaultMemory();
        final ReadAccess readAccess2 = virtualMemorySegment2.getReadAccess();

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
        final VirtualMemorySegment virtualMemorySegment1 = memoryManager.requestDefaultMemory();
        final ReadAccess readAccess1 = virtualMemorySegment1.getReadAccess();

        final VirtualMemorySegment virtualMemorySegment2 = memoryManager.requestDefaultMemory();
        final WriteAccess writeAccess2 = virtualMemorySegment2.getWriteAccess();

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
        final VirtualMemorySegment virtualMemorySegment1 = memoryManager.requestDefaultMemory();
        try (WriteAccess writeAccess = virtualMemorySegment1.getWriteAccess()) {
            final ByteBuffer buffer = writeAccess.getPayload();
            buffer.clear();
            buffer.put((byte) 1).flip();
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }

        // Do it again.
        final VirtualMemorySegment virtualMemorySegment2 = memoryManager.requestDefaultMemory();
        try (WriteAccess writeAccess = virtualMemorySegment2.getWriteAccess()) {
            final ByteBuffer buffer = writeAccess.getPayload();
            buffer.clear();
            buffer.put((byte) 2).flip();
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }

        // Allocate a third memory segment.
        final VirtualMemorySegment virtualMemorySegment3 = memoryManager.requestDefaultMemory();

        // The capacity is exceeded, check if the other two memories are nevertheless valid.
        try (ReadAccess readAccess = virtualMemorySegment1.getReadAccess()) {
            final ByteBuffer buffer = readAccess.getPayload();
            Assert.assertEquals(1, buffer.limit());
            Assert.assertEquals((byte) 1, buffer.get());
        } catch (Exception e) {
            throw new ManagedMemoryException(e);
        }
        try (ReadAccess readAccess = virtualMemorySegment2.getReadAccess()) {
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
