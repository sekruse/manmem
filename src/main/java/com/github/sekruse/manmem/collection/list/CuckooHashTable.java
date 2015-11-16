package com.github.sekruse.manmem.collection.list;

import com.github.sekruse.manmem.collection.IntHashFunction;
import com.github.sekruse.manmem.collection.JenkinsHashFunction;
import com.github.sekruse.manmem.manager.CapacityExceededException;
import com.github.sekruse.manmem.manager.MemoryManager;

import java.util.Random;

/**
 * This class represents a fixed-size, open-addressing hash table that uses the Cuckoo strategy with two subtables
 * and reciprocal eviction of entries. This implementation is not thread-safe.
 */
public class CuckooHashTable extends AbstractIntTable {

    /**
     * Null value replacement.
     */
    private final int nullKey;

    /**
     * Used to create salts for the {@link #hashFactory}.
     */
    private final Random random;

    /**
     * Creates new {@link IntHashFunction}s.
     */
    private final IntHashFunction.Factory hashFactory;

    /**
     * The subtables of this hash table. Has a size of 2.
     */
    private final Subtable[] subtables;

    /**
     * Requires the required capacity for a new instance to host a number of entries with a certain load factor.
     *
     * @param numEntries the expected number of entries
     * @param loadFactor the desired load factor
     * @return the capacity for a new instance
     */
    public static int calculateRequiredCapacity(int numEntries, double loadFactor) {
        return (int) Math.ceil(numEntries / loadFactor);
    }

    /**
     * Determine the amount of managed memory needed for a new instance..
     *
     * @param numEntries the number expected entries
     * @param loadFactor the target load factor of the table
     * @return the number of bytes required for the new instance
     */
    public static final long calculateRequiredMemory(int numEntries, double loadFactor) {
        int requiredCapacity = calculateRequiredCapacity(numEntries, loadFactor);
        return requiredCapacity * 2 * Integer.BYTES; // 2 ints per entry
    }

    /**
     * Creates a new instance.
     *
     * @param capacity      number of maximum entries in the hash table
     * @param memoryManager {@link MemoryManager} that manages the memory that back this array
     * @param nullKey       value that is not allowed to be a key
     */
    public CuckooHashTable(long capacity, MemoryManager memoryManager, int nullKey) {
        this(capacity, memoryManager, nullKey, new JenkinsHashFunction.Factory(), new Random());
    }

    /**
     * Creates a new instance with custom hash functions and randomization.
     *
     * @param capacity      number of maximum entries in the hash table
     * @param memoryManager {@link MemoryManager} that manages the memory that back this array
     * @param nullKey       value that is not allowed to be a key
     * @param hashFactory   creates {@link IntHashFunction}s to operate this object
     * @param random        to get salts for the {@link IntHashFunction}s
     */
    public CuckooHashTable(long capacity, MemoryManager memoryManager, int nullKey,
                           IntHashFunction.Factory hashFactory, Random random) {
        super(capacity * 2, memoryManager);

        // Save some important constant values.
        this.nullKey = nullKey;
        this.random = random;
        this.hashFactory = hashFactory;

        // Create hash functions.
        this.subtables = new Subtable[2];
        this.subtables[0] = new Subtable(this.hashFactory.create(this.random.nextInt()), 0L, capacity);
        this.subtables[1] = new Subtable(this.hashFactory.create(this.random.nextInt()), capacity, capacity);

        // Initialize the data.
        clear();
    }

    /**
     * Removes all entries.
     */
    public void clear() {
        clear(this.nullKey);
    }

    /**
     * Add a new entry into the hash table.
     *
     * @param key   the key
     * @param value the value
     */
    public void put(int key, int value) {
        if (key == this.nullKey) {
            throw new IllegalArgumentException("Null key given.");
        }

        // Check the first subtable.
        long subtableEntryPosition = this.subtables[0].calculateEntryPosition(key);
        int subtableKey = get(subtableEntryPosition);

        // Distinguish three cases: 1) No key yet 2) existing and given keys are equal 3) different keys
        // 1+2) No key, yet. Or keys are equal.
        if (subtableKey == this.nullKey || subtableKey == key) {
            if (subtableKey == this.nullKey) set(subtableEntryPosition, key);
            set(subtableEntryPosition + 1, value);
            return;
        }

        // 3) Different keys: Check second subtable.
        // We act as if we would have evicted this entry.
        int subtableIndex = 1;
        int evictedKey = key;
        int evictedValue = value;

        int recursionGuardian = 0;
        while (evictedKey != this.nullKey) {
            subtableEntryPosition = this.subtables[subtableIndex].calculateEntryPosition(evictedKey);
            subtableKey = get(subtableEntryPosition);

            // Distinguish again the three cases.
            // 1+2)
            if (subtableKey == this.nullKey || subtableKey == evictedKey) {
                if (subtableKey == this.nullKey) set(subtableEntryPosition, evictedKey);
                set(subtableEntryPosition + 1, evictedValue);
                evictedKey = this.nullKey;
            } else {
                set(subtableEntryPosition, evictedKey);
                evictedKey = subtableKey;
                int newEvictedValue = get(subtableEntryPosition + 1);
                set(subtableEntryPosition + 1, evictedValue);
                evictedValue = newEvictedValue;
            }

            subtableIndex = 1 - subtableIndex;

            // TODO: Rehash if necessary.
            if (++recursionGuardian >= this.sizeInInts) {
                throw new RuntimeException("Too many recursions.");
            }
        }
    }

    @Override
    public void lockForRead() throws CapacityExceededException {
        super.lockForRead();
    }

    /**
     * Looks up a value in the hash table by its key.
     *
     * @param key          the key
     * @param defaultValue a default value
     * @return the value associated with the given {@code key}; {@code defaultValue} if the {@code key}
     * is not in the map
     */
    public int get(int key, int defaultValue) {
        if (key == this.nullKey) {
            throw new IllegalArgumentException("Null key given.");
        }

        for (int subtableIndex = 0; subtableIndex < 2; subtableIndex++) {
            long subtableEntryPosition = this.subtables[subtableIndex].calculateEntryPosition(key);
            int subtableKey = get(subtableEntryPosition);
            if (subtableKey == key) {
                return get(subtableEntryPosition + 1);
            }
        }

        return defaultValue;
    }

    /**
     * One of the two subtables used by this hash table.
     */
    private static class Subtable {

        /**
         * Position of the subtable in the embedding {@link AbstractIntTable}.
         */
        private final long position;

        /**
         * Size of the subtable in the embedding {@link AbstractIntTable}.
         */
        private final long size;

        /**
         * {@link IntHashFunction} used for this subtable.
         */
        private final IntHashFunction hashFunction;

        /**
         * Creates a new instance.
         *
         * @param hashFunction see {@link #hashFunction}
         * @param position     see {@link #position}
         * @param size         see {@link #size}
         */
        public Subtable(IntHashFunction hashFunction, long position, long size) {
            this.hashFunction = hashFunction;
            this.position = position;
            this.size = size;
        }

        /**
         * Find the position of the given {@code key} in the backing {@link AbstractIntTable}.
         *
         * @param key the key of the entry
         * @return the absolute position of the entry within the backing {@link AbstractIntTable}
         */
        public long calculateEntryPosition(int key) {
            // We need to round by two.
            return this.position + (((0x7FFFFFFF & this.hashFunction.hash(key)) % this.size) & 0xFFFFFFFE);
        }
    }

}
