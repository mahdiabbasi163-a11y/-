package com.example.data.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Clean In-Memory and Configurable Cache Manager for Kodyar.
 * 
 * Implements precise TTL (Time-To-Live) cache rules according to user specifications:
 * 1) Error Codes: 12 - 24 hours
 * 2) Common Problems / Troubleshooting: 12 - 24 hours
 * 3) Brands, Categories, Models: 24 hours
 * 4) Technicians List: 6 - 12 hours
 * 5) Subscription Plans: 1 - 3 hours
 * 6) Payment / Card Info: 1 - 3 hours
 * 7) Spare Parts: 10 minutes (short-term)
 * 
 * Real-time / Dynamic endpoints are NEVER cached:
 * - User subscription status & free usage quota
 * - User login / account info
 * - Payments & payment verification
 * - Store orders / repair requests
 */
object KodyarCacheManager {

    // TTL Constants in Milliseconds
    val TTL_ERROR_CODES_MS: Long = TimeUnit.HOURS.toMillis(18) // 18 hours (12 to 24 hours)
    val TTL_COMMON_PROBLEMS_MS: Long = TimeUnit.HOURS.toMillis(18) // 18 hours (12 to 24 hours)
    val TTL_BRANDS_CATEGORIES_MS: Long = TimeUnit.HOURS.toMillis(24) // 24 hours
    val TTL_TECHNICIANS_MS: Long = TimeUnit.HOURS.toMillis(8) // 8 hours (6 to 12 hours)
    val TTL_SUBSCRIPTION_PLANS_MS: Long = TimeUnit.HOURS.toMillis(2) // 2 hours (1 to 3 hours)
    val TTL_CARD_INFO_MS: Long = TimeUnit.HOURS.toMillis(2) // 2 hours (1 to 3 hours)
    val TTL_SPARE_PARTS_MS: Long = TimeUnit.MINUTES.toMillis(10) // 10 minutes (5 to 15 minutes)

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMillis: Long
    ) {
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - timestamp) > ttlMillis
        }
    }

    private val memoryStore = ConcurrentHashMap<String, CacheEntry<*>>()

    /**
     * Put an item into memory cache with custom TTL.
     */
    fun <T> put(key: String, data: T, ttlMillis: Long) {
        memoryStore[key] = CacheEntry(
            data = data,
            timestamp = System.currentTimeMillis(),
            ttlMillis = ttlMillis
        )
    }

    /**
     * Retrieve an item if present and not expired.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val entry = memoryStore[key] as? CacheEntry<T> ?: return null
        if (entry.isExpired()) {
            memoryStore.remove(key)
            return null
        }
        return entry.data
    }

    /**
     * Check if a cache key is valid and fresh.
     */
    fun isValid(key: String): Boolean {
        val entry = memoryStore[key] ?: return false
        return !entry.isExpired()
    }

    /**
     * Invalidate specific key or parts of cache
     */
    fun invalidate(key: String) {
        memoryStore.remove(key)
    }

    /**
     * Invalidate spare parts cache on demand (e.g. after ordering a part or manual refresh)
     */
    fun invalidateSpareParts() {
        memoryStore.remove("spare_parts")
        memoryStore.remove("kodyar_database")
    }

    fun clearAll() {
        memoryStore.clear()
    }
}
