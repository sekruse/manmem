package com.github.sekruse.manmem.manager;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test suite for {@link MemoryManagers}.
 */
public class MemoryManagersTest {

    @Test
    public void testRequiredSegments() {
        Assert.assertEquals(0, MemoryManagers.requiredSegments(0, 32));
        Assert.assertEquals(1, MemoryManagers.requiredSegments(1, 32));
        Assert.assertEquals(1, MemoryManagers.requiredSegments(31, 32));
        Assert.assertEquals(1, MemoryManagers.requiredSegments(32, 32));
        Assert.assertEquals(2, MemoryManagers.requiredSegments(64, 32));
        Assert.assertEquals((Integer.MAX_VALUE + 1L) / 16, MemoryManagers.requiredSegments(2L * Integer.MAX_VALUE, 32));
    }

    @Test
    public void testProvidedMemroy() {
        Assert.assertEquals(0, MemoryManagers.providedMemory(0, 32));
        Assert.assertEquals(32, MemoryManagers.providedMemory(1, 32));
        Assert.assertEquals(31 * 32, MemoryManagers.providedMemory(31, 32));
        Assert.assertEquals(Integer.MAX_VALUE * 32L, MemoryManagers.providedMemory(Integer.MAX_VALUE, 32));
    }

    @Test
    public void testFitMemoryToSegments() {
        Assert.assertEquals(0L, MemoryManagers.fitMemoryToSegments(0L, 32));
        Assert.assertEquals(Integer.MAX_VALUE + 1L, MemoryManagers.fitMemoryToSegments(Integer.MAX_VALUE + 1L, 32));
        Assert.assertEquals(Integer.MAX_VALUE + 33L, MemoryManagers.fitMemoryToSegments(Integer.MAX_VALUE + 2L, 32));
    }
}
