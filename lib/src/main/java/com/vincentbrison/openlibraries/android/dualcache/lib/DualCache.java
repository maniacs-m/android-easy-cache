/*
 * Copyright 2014 Vincent Brison.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vincentbrison.openlibraries.android.dualcache.lib;

import android.support.v4.util.LruCache;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.disklrucache.DiskLruCache;

import java.io.File;
import java.io.IOException;

/**
 * Created by Vincent Brison.
 * This class intent to provide a very easy to use, reliable, highly configurable, performance driven
 * cache library for Android.
 *
 * @param <T> is the Class of object to cache.
 */
public class DualCache<T> {

    /**
     * Define the behaviour of the RAM layer.
     */
    public enum DualCacheRAMMode {

        /**
         * Means that object will be serialized with a default serializer is in RAM.
         * For now provided through Jackson json lib.
         */
        ENABLE_WITH_DEFAULT_SERIALIZER,

        /**
         * Means that only reference to objects will be store in the RAM layer.
         */
        ENABLE_WITH_REFERENCE,

        /**
         * The RAM layer is not used.
         */
        DISABLE
    }

    /**
     * Define the behaviour of the disk layer.
     */
    public enum DualCacheDiskMode {

        /**
         * Means that object will be serialized with a default serializer on DISK.
         */
        ENABLE_WITH_DEFAULT_SERIALIZER,

        /**
         * Enable with custom mSerializer
         */
        ENABLE_WITH_CUSTOM_SERIALIZER,

        /**
         * The disk layer is not used.
         */
        DISABLE
    }

    /**
     * Defined the sub folder from {@link android.content.Context#getCacheDir()} used to store all
     * the data generated from the use of this library.
     */
    protected static final String CACHE_FILE_PREFIX = "dualcache";

    private static final String LOG_PREFIX = "Entry for ";

    /**
     * Gson serializer used to save data and load data. Can be used by multiple threads.
     */
    private static ObjectMapper sMapper;

    /**
     * Unique ID which define a cache.
     */
    private String mId;

    /**
     * RAM cache.
     */
    private LruCache mRamCacheLru;

    /**
     * Disk cache.
     */
    private DiskLruCache mDiskLruCache;

    /**
     * Define the class store in this cache.
     */
    private Class<T> mClazz;

    /**
     * Hold the max size in bytes of the disk cache.
     */
    private int mDiskCacheSizeInBytes;

    /**
     * Define the app version of the application (allow you to automatically invalidate data
     * from different app version on disk).
     */
    private int mAppVersion;

    /**
     * By default the RAM layer use JSON serialization to store cached object.
     */
    private DualCacheRAMMode mRAMMode = DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER;

    /**
     * By default the disk layer use JSON serialization to store cached object.
     */
    private DualCacheDiskMode mDiskMode = DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER;

    /**
     * The handler used when the ram cache is enable with {@link com.vincentbrison.openlibraries.android.dualcache.lib.DualCache.DualCacheRAMMode#ENABLE_WITH_REFERENCE}
     */
    private SizeOf<T> mHandlerSizeOf;

    private Serializer<T> mSerializer;

    static {
        sMapper = new ObjectMapper();
        sMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        sMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        sMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }

    /**
     * Constructor which only set global parameter of the cache.
     *
     * @param id         is the id of the cache.
     * @param appVersion is the app version of the app. (Data in disk cache will be invalidate if their app version is inferior than this app version.
     * @param clazz      is the Class of object to store in cache.
     */
    protected DualCache(String id, int appVersion, Class clazz) {
        mId = id;
        mAppVersion = appVersion;
        mClazz = clazz;
    }

    protected int getAppVersion() {
        return mAppVersion;
    }

    protected String getCacheId() {
        return mId;
    }

    protected void setDiskLruCache(DiskLruCache diskLruCache) {
        mDiskLruCache = diskLruCache;
    }

    protected void setSerializer(Serializer serializer) {
        mSerializer = serializer;
    }

    protected void setRamCacheLru(LruCache ramLruCache) {
        mRamCacheLru = ramLruCache;
    }

    /**
     * Return the mapper if you want to do more configuration on it.
     *
     * @return the mapper if you want to do more configuration on it.
     */
    public static ObjectMapper getMapper() {
        return sMapper;
    }

    /**
     * Return the size used in bytes of the RAM cache.
     *
     * @return the size used in bytes of the RAM cache.
     */
    public long getRamSize() {
        if (mRamCacheLru == null) {
            return -1;
        } else {
            return mRamCacheLru.size();
        }
    }

    /**
     * Return the size used in bytes of the disk cache.
     *
     * @return the size used in bytes of the disk cache.
     */
    public long getDiskSize() {
        if (mDiskLruCache == null) {
            return -1;
        } else {
            return mDiskLruCache.size();
        }

    }

    /**
     * Return the way objects are cached in RAM layer.
     *
     * @return the way objects are cached in RAM layer.
     */
    public DualCacheRAMMode getRAMMode() {
        return mRAMMode;
    }

    /**
     * Set the way objects are stored in the RAM layer.
     *
     * @param ramMode is the value to set.
     */
    public void setRAMMode(DualCacheRAMMode ramMode) {
        this.mRAMMode = ramMode;
    }

    /**
     * Return the way objects are cached in disk layer.
     *
     * @return the way objects are cached in disk layer.
     */
    public DualCacheDiskMode getDiskMode() {
        return mDiskMode;
    }

    /**
     * Put an object in cache.
     *
     * @param key    is the key of the object.
     * @param object is the object to put in cache.
     */
    public void put(String key, T object) {
        String jsonStringObject = null;
        if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_REFERENCE)) {
            mRamCacheLru.put(key, object);
            logEntrySavedForKey(key);
        }

        if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_CUSTOM_SERIALIZER)) {
            try {
                DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                editor.set(0, mSerializer.toString(object));
                editor.commit();
                logEntrySavedForKey(key);
            } catch (IOException e) {
                DualCacheLogUtils.logError(e);
            }
        }

        if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER) || mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
            try {
                jsonStringObject = sMapper.writeValueAsString(object);
            } catch (JsonProcessingException e) {
                DualCacheLogUtils.logError(e);
            }

            if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                mRamCacheLru.put(key, jsonStringObject);
                logEntrySavedForKey(key);
            }

            if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                try {
                    DiskLruCache.Editor editor = mDiskLruCache.edit(key);
                    editor.set(0, jsonStringObject);
                    editor.commit();
                    logEntrySavedForKey(key);
                } catch (IOException e) {
                    DualCacheLogUtils.logError(e);
                }
            }
        }
    }

    /**
     * Return the object of the corresponding key from the cache. In no object is available, return null.
     *
     * @param key is the key of the object.
     * @return the object of the corresponding key from the cache. In no object is available, return null.
     */
    public T get(String key) {

        Object ramResult = null;
        String diskResult = null;
        DiskLruCache.Snapshot snapshotObject = null;

        // Try to get the object from RAM.
        if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER) || mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_REFERENCE)) {
            ramResult = mRamCacheLru.get(key);
        }

        if (ramResult == null) {
            // Try to get the cached object from disk.
            logEntryForKeyIsNotInRam(key);
            if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER) || mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_CUSTOM_SERIALIZER)) {
                try {
                    snapshotObject = mDiskLruCache.get(key);
                } catch (IOException e) {
                    DualCacheLogUtils.logError(e);
                }
                if (snapshotObject != null) {
                    logEntryForKeyIsOnDisk(key);
                    try {
                        diskResult = snapshotObject.getString(0);
                    } catch (IOException e) {
                        DualCacheLogUtils.logError(e);
                    }
                } else {
                    logEntryForKeyIsNotOnDisk(key);
                }
            }

            T objectFromJsonDisk = null;
            T objectFromStringDisk = null;

            if (diskResult != null) {
                // Refresh object in ram.
                if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                    if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                        // Need to copy json string into ram.
                        mRamCacheLru.put(key, diskResult);
                    } else if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_CUSTOM_SERIALIZER)) {
                        // Need to convert string to json.
                        try {
                            objectFromStringDisk = mSerializer.fromString(diskResult);
                            mRamCacheLru.put(key,
                                    sMapper.writeValueAsString(objectFromStringDisk
                                    ));
                        } catch (JsonProcessingException e) {
                            DualCacheLogUtils.logError(e);
                        }
                    }
                } else if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_REFERENCE)) {
                    if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                        // Need to get an instance from json string.
                        try {
                            objectFromJsonDisk = sMapper.readValue(diskResult, mClazz);
                            mRamCacheLru.put(key, objectFromJsonDisk);
                        } catch (IOException e) {
                            DualCacheLogUtils.logError(e);
                        }
                    } else if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_CUSTOM_SERIALIZER)) {
                        // Need to get an instance from string.
                        if (objectFromStringDisk == null) {
                            objectFromStringDisk = mSerializer.fromString(diskResult);
                        }
                        mRamCacheLru.put(key, objectFromStringDisk);
                    }
                }
            }

            if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                try {
                    if (objectFromJsonDisk == null) {
                        objectFromJsonDisk = sMapper.readValue(diskResult, mClazz);
                    }
                    return objectFromJsonDisk;
                } catch (IOException e) {
                    DualCacheLogUtils.logError(e);
                }
            } else if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_CUSTOM_SERIALIZER)) {
                if (objectFromStringDisk == null) {
                    objectFromStringDisk = mSerializer.fromString(diskResult);
                }
                return objectFromStringDisk;
            }
        } else {
            logEntryForKeyIsInRam(key);
            if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
                try {
                    return sMapper.readValue((String) ramResult, mClazz);
                } catch (IOException e) {
                    DualCacheLogUtils.logError(e);
                }
            } else if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_REFERENCE)) {
                return (T) ramResult;
            }
        }

        // No data are available.
        return null;
    }

    /**
     * Delete the corresponding object in cache.
     *
     * @param key is the key of the object.
     */
    public void delete(String key) {

        if (mRAMMode.equals(DualCacheRAMMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
            mRamCacheLru.remove(key);
        }

        if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
            try {
                mDiskLruCache.remove(key);
            } catch (IOException e) {
                DualCacheLogUtils.logError(e);
            }
        }
    }

    /**
     * Remove all objects from cache (both RAM and disk).
     */
    public void invalidate() {
        if (mDiskMode.equals(DualCacheDiskMode.ENABLE_WITH_DEFAULT_SERIALIZER)) {
            invalidateDisk();
        }
        invalidateRAM();
    }

    /**
     * Remove all objects from RAM.
     */
    public void invalidateRAM() {
        mRamCacheLru.evictAll();
    }

    /**
     * Remove all objects from Disk.
     */
    public void invalidateDisk() {
        try {
            mDiskLruCache.delete();
            File folder = new File(DualCacheContextUtils.getContext().getCacheDir().getPath() + "/" + CACHE_FILE_PREFIX + "/" + mId);
            mDiskLruCache = DiskLruCache.open(folder, mAppVersion, 1, mDiskCacheSizeInBytes);
        } catch (IOException e) {
            DualCacheLogUtils.logError(e);
        }
    }

    /**
     * Set the way objects are stored in the disk layer.
     *
     * @param diskMode is the value to set.
     */
    public void setDiskMode(DualCacheDiskMode diskMode) {
        this.mDiskMode = diskMode;
    }

    // Logging helpers
    private void logEntrySavedForKey(String key) {
        DualCacheLogUtils.logInfo(LOG_PREFIX + key + " is saved in cache.");
    }

    private void logEntryForKeyIsInRam(String key) {
        DualCacheLogUtils.logInfo(LOG_PREFIX + key + " is in RAM.");
    }

    private void logEntryForKeyIsNotInRam(String key) {
        DualCacheLogUtils.logInfo(LOG_PREFIX + key + " is not in RAM.");
    }

    private void logEntryForKeyIsOnDisk(String key) {
        DualCacheLogUtils.logInfo(LOG_PREFIX + key + " is on disk.");
    }

    private void logEntryForKeyIsNotOnDisk(String key) {
        DualCacheLogUtils.logInfo(LOG_PREFIX + key + " is not on disk.");
    }
}
