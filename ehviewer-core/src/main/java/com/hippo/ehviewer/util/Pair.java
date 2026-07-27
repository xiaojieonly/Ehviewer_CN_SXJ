package com.hippo.ehviewer.util;

public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> of(A first, B second) {
        return new Pair<>(first, second);
    }

    public static <A, B> Pair<A, B> create(A first, B second) {
        return new Pair<>(first, second);
    }
}
