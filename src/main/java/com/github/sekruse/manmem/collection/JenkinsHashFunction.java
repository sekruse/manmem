package com.github.sekruse.manmem.collection;

/**
 * Reversible hash function by Robert Jenkins. Salts are just added to the input value and are not part of the actual
 * hash algorithm.
 */
public class JenkinsHashFunction implements IntHashFunction {

    /**
     * The salt to be used in this {@link IntHashFunction}.
     */
    private final int salt;

    /**
     * Creates a new instance without a salt.
     */
    public JenkinsHashFunction() {
        this(0);
    }

    /**
     * Creates a new instance.
     *
     * @param salt the salt to use; {@link 0} yields the original reversible hash function
     */
    public JenkinsHashFunction(int salt) {
        this.salt = salt;
    }


    @Override
    public int hash(int value) {
        value ^= this.salt;
        value = (value + 0x7ed55d16) + (value << 12);
        value = (value ^ 0xc761c23c) ^ (value >> 19);
        value = (value + 0x165667b1) + (value << 5);
        value = (value + 0xd3a2646c) ^ (value << 9);
        value = (value + 0xfd7046c5) + (value << 3);
        value = (value ^ 0xb55a4f09) ^ (value >> 16);
        value ^= this.salt;
        return value;
    }

    /**
     * Factory for {@link JenkinsHashFunction}s.
     */
    public static class Factory implements IntHashFunction.Factory {

        @Override
        public IntHashFunction create(int salt) {
            return new JenkinsHashFunction(salt);
        }
    }
}
