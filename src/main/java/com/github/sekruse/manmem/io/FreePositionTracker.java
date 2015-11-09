package com.github.sekruse.manmem.io;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

/**
 * This class manages available positions in a file to write to.
 */
public class FreePositionTracker {

    /**
     * This set stores free positions in a file (in terms of integers 0, 1, 2, ...). It has to contain always at
     * least one value, which is the smallest available position in a file (this position might not yet exist because
     * the file is to small).
     */
    private final IntSortedSet freePositions = new IntRBTreeSet();

    /**
     * Creates a new instance. The first available position is {@code 0}.
     */
    public FreePositionTracker() {
        addFreePosition(0);
    }

    /**
     * Adds a free position.
     *
     * @param position the free position.
     */
    public void addFreePosition(int position) {
        this.freePositions.add(position);
    }

    /**
     * Retrieves any free position (preferably a small one). After retrieval, this position will not be makred free
     * anymore. However, it will be ensured that there is at least one more free position.
     */
    public int retrieveFreePosition() {
        if (this.freePositions.isEmpty()) {
            throw new IllegalStateException("No free file positions available.");
        }

        // Get the smallest free value.
        int retrievalValue = this.freePositions.firstInt();
        this.freePositions.rem(retrievalValue);

        // If we just retrieved the last value, it has to be the largest retrieved value. We therefore need to add
        // the successor position as free.
        if (this.freePositions.isEmpty()) {
            addFreePosition(retrievalValue + 1);
        }

        return retrievalValue;
    }

}
