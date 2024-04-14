package live.einfachgustaf.punishments.cache

abstract class Cache<Ident, Data>(val collectionName: String, private val compareIdentsInLowercase: Boolean = false) {

    internal val cacheData: HashMap<Ident, Data> = hashMapOf()

    /**
     * Pushes the cache to the database
     */
    abstract fun push(ident: Ident? = null)

    /**
     * Pulls the data from the database to the cache
     */
    abstract fun pull(ident: Ident): Data?

    /**
     * Sets the data in the cache
     */
    fun set(ident: Ident, data: Data) {
        cacheData[ident] = data
    }

    /**
     * Gets the data from the cache or tries to load it from the database
     */
    fun get(ident: Ident): Data? {
        if (compareIdentsInLowercase && ident is String) {
            val lowercaseCacheData = cacheData.keys.filterIsInstance<String>().map { it.lowercase() }
            if (lowercaseCacheData.contains(ident.lowercase()))
                return cacheData[cacheData.keys.first { it is String && it.lowercase() == ident.lowercase() }]
        } else if (cacheData.containsKey(ident))
            return cacheData[ident]

        return pull(ident)
    }

    /**
     * Removes the data from the cache
     */
    fun remove(ident: Ident) {
        cacheData.remove(ident)
    }


}