package com.todoapp;

/** A read result plus where it came from, so the UI can show cache hits vs database reads. */
public record ReadResult<T>(T value, Source source, long elapsedMillis) {

    public enum Source { REDIS_CACHE, DATABASE }

    public boolean fromCache() {
        return source == Source.REDIS_CACHE;
    }
}
