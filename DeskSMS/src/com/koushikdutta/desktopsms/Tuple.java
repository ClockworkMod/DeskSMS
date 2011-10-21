package com.koushikdutta.desktopsms;

public class Tuple<S,T> {
    public S First;
    public T Second;
    
    public Tuple() {
    }
    
    public Tuple(S first, T second) {
        First = first;
        Second = second;
    }
}
