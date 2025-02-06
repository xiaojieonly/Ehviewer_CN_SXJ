package com.hippo.lib.yorozuya.collect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Combines a Map with Set values to provide simple way to store multiple values for a key.
 * Like {@link Multimap}, but element values are stored in Sets.
 */
// Top level class to get rid of 3rd generic collection parameter for more convenient usage.
public class MultimapSet<K, V> extends AbstractMultimap<K, V, Set<V>> {

    protected MultimapSet(Map<K, Set<V>> map, boolean threadSafeSets) {
        super(map, threadSafeSets);
    }

    public static <K, V> MultimapSet<K, V> create() {
        return new MultimapSet<>(new HashMap<K, Set<V>>(), false);
    }

    public static <K, V> MultimapSet<K, V> createWithThreadSafeLists() {
        return new MultimapSet<>(new HashMap<K, Set<V>>(), true);
    }

    protected Set<V> createNewCollection() {
        return threadSafeCollections ? new CopyOnWriteArraySet<V>() : new HashSet<V>();
    }
}
