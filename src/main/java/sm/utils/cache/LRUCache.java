/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sm.utils.cache;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

/**
 *
 * @author smdeveloper
 * @param <K>
 * @param <V>
 */
public interface LRUCache< K, V extends Closeable> {

    /**
     * Put an entry in cache.
     *
     * @param key
     * @param value
     */
    public void put(final K key, final V value);

    /**
     * Put a entry in the cache with a specified time to live.
     *
     * @param key
     * @param value
     * @param timeToLiveInMillis
     */
    public void put(final K key, final V value, final long timeToLiveInMillis);

    /**
     * Returns the size of the cache.
     *
     * @return
     */
    public int size();

    /**
     * Gets a value from cache, given its key.
     *
     * @param key
     * @return
     */
    public V get(final K key);

    /**
     * All values cached
     *
     * @return a collection
     */
    public Collection<V> getValues();

    /**
     * Remove the resource with specific key from the cache and close it
     * synchronously afterwards.
     *
     * @param key the key of the cached resource
     * @return the removed resource if exists
     * @throws IOException exception thrown if there is any IO error
     */
    public V remove(final K key) throws IOException;

    /**
     * Remove all cached resource from the cache and close them asynchronously
     * afterwards.
     *
     * @throws IOException exception thrown if there is any IO error
     */
    public void removeAll() throws IOException;

    /**
     * Release the cached resource with specific key
     *     
     * This call will decrement the reference counter of the keyed resource.
     *     
     * @param key
     */
    public void release(final K key);

}
