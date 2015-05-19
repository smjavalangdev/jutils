/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sm.utils.cache;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
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
 * Used Java 8 - functional syntax, not compatible with jdk < 8
 * 
 * @author smdeveloper
 * @param <K>
 * @param <V>
 */
public class LRUCacheImpl<K, V extends Closeable> implements LRUCache<K, V> {

    private static final long serialVersionUID = 6992448646407690164L;

    private static final Logger logger = Logger.getLogger((LRUCacheImpl.class).getClass().getName());

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
            entriesToBeEvicted = findEntriesToEvict();

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

    /**
     * Get the matching value for the key. Since the entry has been fetched
     * update time to live.
     *
     * @param key
     * @return
     */
    @Override
    public V get(final K key) {
        try {
            readLock.lock();
            TTLValue ttlVal = ttlMap.get(key);
            if (ttlVal != null) {
                ttlVal.lastAccessedTimestamp.set(System.currentTimeMillis());
                ttlVal.refCount.incrementAndGet();
            }
            return internalMap.get(key);
        } finally {
            readLock.unlock();
        }

    }

    /**
     * Returns all the values held in cache.
     *
     * @return
     */
    @Override
    public Collection<V> getValues() {
        try {
            readLock.lock();
            Collection<V> vals = new ArrayList();
            vals.stream().forEach((v) -> {
                vals.add(v);
            });
            return vals;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * returns the size of the cache.
     *
     * @return
     */
    public int size() {
        try {
            readLock.lock();
            return internalMap.size();
        } finally {
            readLock.unlock();
        }

    }

    /**
     * Remove the resource with specific key from the cache and close it
     * synchronously afterwards.
     *
     * @param key the key of the cached resource
     * @return the removed resource if exists
     * @throws IOException exception thrown if there is any IO error
     */
    @Override
    public V remove(final K key) throws IOException {
        try {
            writeLock.lock();
            ttlMap.remove(key);
            V val = internalMap.remove(key);
            if (val != null) {
                val.close();
            }
            return val;
        } finally {
            writeLock.unlock();
        }

    }

    /**
     * Remove all cached resource from the cache and close them asynchronously
     * afterwards.
     *
     * @throws IOException exception thrown if there is any IO error
     */
    @Override
    public void removeAll() throws IOException {
        try {
            writeLock.lock();
            Collection<V> valsToClose = new HashSet<>();
            valsToClose.addAll(internalMap.values());

            if (valsToClose.size() > 0) {
                for (V v : valsToClose) {
                    v.close();
                }
            }
            internalMap.clear();
            ttlMap.clear();

        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Release the cached resource with specific key
     *     
     * This call will decrement the reference counter of the keyed resource.
     *     
     * @param key
     */
    @Override
    public void release(K key) {
        try {
            readLock.lock();
            TTLValue ttl = ttlMap.get(key);
            if (ttl != null) {
            // since the resource is released by calling thread
            // let's decrement the reference counting
                ttl.refCount.decrementAndGet();
            }
        } finally {
            readLock.unlock();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Internal classes & methods
    //
    ////////////////////////////////////////////////////////////////////////////
    /**
     * A lazy mark and sweep,
     *
     * a separate thread can also do this.
     */
    private Collection<V> findEntriesToEvict() {
        Collection<V> valuesToClose = null;
        evictionSet.clear();
        Set<K> keys = ttlMap.keySet();
        long currentTS = System.currentTimeMillis();
        keys.stream().forEach((key) -> {
            TTLValue ttl = ttlMap.get(key);
            if (ttl.refCount.get() <= 0 && (currentTS - ttl.lastAccessedTimestamp.get()) > ttl.ttl) {
                evictionSet.add(key);
            }
        });
        if (evictionSet.size() > 0) {
            valuesToClose = new HashSet<>();
            for (K key : evictionSet) {
                V v = internalMap.remove(key);
                valuesToClose.add(v);
                ttlMap.remove(key);
            }
        }
        return valuesToClose;
    }

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
