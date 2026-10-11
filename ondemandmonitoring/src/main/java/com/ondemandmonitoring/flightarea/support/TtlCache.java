package com.ondemandmonitoring.flightarea.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny bounded in-memory cache with per-entry expiry. The project has no shared cache
 * infrastructure (weather uses the same approach), so this keeps external calls for the
 * same location from repeating while the customer drags the map.
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int maxEntries;
    private final Clock clock;

    public TtlCache(boolean enabled, int maxEntries, Clock clock) {
        this.enabled = enabled;
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    public Optional<V> get(K key) {
        if (!enabled) return Optional.empty();
        Entry<V> entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void put(K key, V value, Duration ttl) {
        if (!enabled) return;
        if (entries.size() >= maxEntries) entries.clear();
        entries.put(key, new Entry<>(value, clock.instant().plus(ttl)));
    }

    public int size() {
        return entries.size();
    }
}
