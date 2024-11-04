/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */

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
