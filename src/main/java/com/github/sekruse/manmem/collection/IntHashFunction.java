package com.github.sekruse.manmem.collection;

/**
 * Hash function for {@code int} values.
 */
public interface IntHashFunction {

    /**
     * Hashes the given value
     *
     * @param value the value to hash
     * @return the hash
     */
    int hash(int value);

    /**
     * A factory that creates various {@link IntHashFunction}s using salts.
     */
    interface Factory {

        /**
         * Creates a new {@link IntHashFunction}.
         *
         * @param salt salt for the {@link IntHashFunction}
         * @return the {@link IntHashFunction}
         */
        IntHashFunction create(int salt);

    }

}
