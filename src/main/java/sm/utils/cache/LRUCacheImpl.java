/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sm.utils.cache;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Simple thread-safe implementation of a LRU cache that supports reference
 * counting and TTL(time-to-live)
 *
 * @author smdeveloper
 * @param <K>
 * @param <V>
 */
public class LRUCacheImpl<K, V extends Closeable> implements LRUCache<K, V> {
    
    private static final long serialVersionUID = -6992448646407690164L;

    private static final Logger logger = Logger.getLogger(LRUCacheImpl.class);

    //Default time to live in millis
    private static final long DEFAULT_TTL = 10 * 1000; //millis

    //The internal map that stores all cache entries
    private final Map<K, V> internalMap;

    //The internal map that stores TTL for all entries
    private final Map<K, TTLValue> ttlMap;

    //All locks for thread safety
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    //This set contains all keys that needs to be evicted from cache
    private final Set<K> evictionSet = new HashSet<>();

    //For running background threads in order to carry out cache admin tasks such as eviction
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public LRUCacheImpl() {
        internalMap = new HashMap<>();
        ttlMap = new HashMap<>();
    }

    /**
     * Put a entry in the cache with a specified time to live.
     *
     * @param key
     * @param value
     * @param timeToLiveInMillis
     */
    @Override
    public void put(final K key, final V value, final long timeToLiveInMillis) {
        Collection<V> entriesToBeEvicted = null;
        try {
            writeLock.lock(); // Acquire the write lock first.
            //Trigger eviction thread before adding new elements
            //entriesToBeEvicted = markAndSweep();

            //If the value is already marked for eviction then remove it from the 
            //eviction set.
            if (entriesToBeEvicted != null && entriesToBeEvicted.contains(value)) {
                entriesToBeEvicted.remove(value);
            }

            internalMap.put(key, value);
            TTLValue ttl = new TTLValue(System.currentTimeMillis(), timeToLiveInMillis);
            ttl.refCount.incrementAndGet();
            ttlMap.put(key, ttl);

        } finally {
            writeLock.unlock();
        }

        if (entriesToBeEvicted != null && entriesToBeEvicted.size() > 0) {
            //if (logger.isDebugEnabled()) {
                //int size = entriesToBeEvicted.size();
                //logger.info("Mark&Sweep found " + size + (size > 1 ? " resources" : " resource") + " to close.");
            //}
            // close resource asynchronously
            executorService.execute(new ValueCloser<>(entriesToBeEvicted));
        }

    }

    /**
     * Put an entry in cache.
     *
     * @param key
     * @param value
     */
    @Override
    public void put(final K key, final V value) {
        this.put(key, value, DEFAULT_TTL);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Internal classes
    //
    ////////////////////////////////////////////////////////////////////////////
    private static class TTLValue {

        AtomicLong lastAccessedTimestamp; // last accessed time
        AtomicLong refCount = new AtomicLong(0);
        long ttl;

        public TTLValue(long ts, long ttl) {
            this.lastAccessedTimestamp = new AtomicLong(ts);
            this.ttl = ttl;
        }
    }

    private static class ValueCloser<V extends Closeable> implements Runnable {

        Collection<V> valuesToClose;

        public ValueCloser(Collection<V> valuesToClose) {
            this.valuesToClose = valuesToClose;
        }

        @Override
        public void run() {
            int size = valuesToClose.size();
            valuesToClose.stream().forEach((v) -> {
                try {
                    if (v != null) {
                        v.close();
                    }
                } catch (IOException e) {
                    // close quietly
                }
            });
        }
    }
}
