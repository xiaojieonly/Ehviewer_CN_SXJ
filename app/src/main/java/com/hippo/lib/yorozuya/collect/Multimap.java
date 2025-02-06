package com.hippo.lib.yorozuya.collect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Combines a Map with List values to provide simple way to store multiple values for a key.
 */
// Top level class to get rid of 3rd generic collection parameter for more convenient usage.
public class Multimap<K, V> extends AbstractMultimap<K, V, List<V>> {
    protected Multimap(Map<K, List<V>> map, boolean threadSafeCollections) {
        super(map, threadSafeCollections);
    }

    public static <K, V> Multimap<K, V> create() {
        return new Multimap<>(new HashMap<K, List<V>>(), false);
    }

    public static <K, V> Multimap<K, V> createWithThreadSafeLists() {
        return new Multimap<>(new HashMap<K, List<V>>(), true);
    }

    protected List<V> createNewCollection() {
        return threadSafeCollections ? new CopyOnWriteArrayList<V>() : new ArrayList<V>();
    }
}
