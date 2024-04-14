package live.einfachgustaf.punishments.cache

import com.mongodb.client.model.Filters
import live.einfachgustaf.punishments.models.data.PunishmentPlayer
import live.einfachgustaf.punishments.utils.databaseConnector

class PlayerCache : Cache<String, PunishmentPlayer>("punishment_players") {

    private val collection = databaseConnector.database.getCollection<PunishmentPlayer>(collectionName)
    private val isInDatabase: ArrayList<String> = arrayListOf()

    override fun push(ident: String?) {
        if (ident == null) {
            cacheData.forEach { (ident, data) ->
                if (isInDatabase.contains(ident))
                    collection.replaceOne(Filters.eq("uuid", ident.toString()), data)
                else {
                    collection.insertOne(data)
                    isInDatabase.add(ident)
                }
            }
        } else if (cacheData.containsKey(ident) && cacheData[ident] != null) {
            if (isInDatabase.contains(ident))
                collection.replaceOne(Filters.eq("uuid", ident.toString()), cacheData[ident]!!)
            else {
                collection.insertOne(cacheData[ident]!!)
                isInDatabase.add(ident)
            }
        }
    }

    override fun pull(ident: String): PunishmentPlayer? {
        val result = collection.find(Filters.eq("uuid", ident.toString())).firstOrNull()
        if (result != null) {
            isInDatabase.add(ident)

            // Set the data in the cache
            set(ident, result)
        }
        return result
    }


}