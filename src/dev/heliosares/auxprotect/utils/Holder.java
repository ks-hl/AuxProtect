package dev.heliosares.auxprotect.utils;

import javax.annotation.Nullable;
import javax.annotation.OverridingMethodsMustInvokeSuper;

/**
 * Holds a value of the specified type. Similar to {@link java.util.concurrent.CompletableFuture} but simpler.
 * Not intended to be used asynchronously, intended mainly for passing variables out of a lambda expression.
 */
public class Holder<T> {
    private T value;
    private boolean set;

    /**
     * Completes this Holder
     */
    @OverridingMethodsMustInvokeSuper
    public void set(@Nullable T t) {
        set = true;
        value = t;
    }

    /**
     * Get the value of this Holder
     *
     * @throws IllegalStateException If the value has not been set
     */
    @Nullable
    @OverridingMethodsMustInvokeSuper
    public T get() throws IllegalStateException {
        if (!set) throw new IllegalStateException("Value not yet set");
        return value;
    }

    /**
     * Provides the value, cast to a Number if possible. This is a helper method to work around unboxing issues.
     *
     * @param def The default value to use if the value is null.
     * @throws IllegalStateException If the value is not a number or the value is not yet set.
     */
    public Number getNumberOrElse(Number def) {
        if (get() == null) return def;
        if (!(get() instanceof Number number)) throw new IllegalStateException("Not a number");
        return number;
    }

    /**
     * This is different from a null check because passing 'null' to {@link Holder#set(T)} will still cause this to return true
     *
     * @return Whether this Holder has had {@link Holder#set(T)} called at least once, regardless of the value
     */
    @OverridingMethodsMustInvokeSuper
    public boolean isSet() {
        return set;
    }
}
