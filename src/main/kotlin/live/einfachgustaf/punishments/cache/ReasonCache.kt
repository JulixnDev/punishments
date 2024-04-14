package live.einfachgustaf.punishments.cache

import com.mongodb.client.model.Collation
import com.mongodb.client.model.CollationStrength
import com.mongodb.client.model.Filters
import live.einfachgustaf.punishments.models.data.PunishmentReason
import live.einfachgustaf.punishments.utils.databaseConnector

class ReasonCache : Cache<String, PunishmentReason>("punishment_reasons", true) {

    private val collection = databaseConnector.database.getCollection<PunishmentReason>(collectionName)
    private val isInDatabase: ArrayList<String> = arrayListOf()
    private val inDeletion: ArrayList<String> = arrayListOf()

    override fun push(ident: String?) {
        if (ident == null) {
            cacheData.forEach { (ident, data) ->
                if (inDeletion.contains(ident)) {
                    deleteInMongo(ident, data)
                    return@forEach
                }

                if (isInDatabase.contains(ident))
                    replaceInMongo(ident, data)
                else {
                    collection.insertOne(data)
                    isInDatabase.add(ident)
                }
            }
        } else if (cacheData.containsKey(ident) && cacheData[ident] != null) {
            if (inDeletion.contains(ident)) {
                deleteInMongo(ident, cacheData[ident]!!)
                return
            }

            if (isInDatabase.contains(ident))
                replaceInMongo(ident, cacheData[ident]!!)
            else {
                collection.insertOne(cacheData[ident]!!)
                isInDatabase.add(ident)
            }
        }
    }

    override fun pull(ident: String): PunishmentReason? {
        val result = getFromMongo(ident)
        if (result != null) {
            isInDatabase.add(ident)

            // Set the data in the cache
            set(result.id.toString(), result)
            set(result.name, result)
        }
        return result
    }

    fun loadAllIntoCache() {
        collection.find().forEach {
            cacheData[it.name] = it
            cacheData[it.id.toString()] = it
            isInDatabase.add(it.name)
            isInDatabase.add(it.id.toString())
        }
    }

    fun delete(ident: String) {
        inDeletion.add(ident)
    }

    private fun getFromMongo(ident: String): PunishmentReason? {
        return runCatching {
            val id = ident.toInt()
            collection.find(Filters.eq("id", id)).firstOrNull()
        }.getOrElse {
            val collation = Collation.builder().locale("de").collationStrength(CollationStrength.SECONDARY).build()
            collection.find(Filters.eq("name", ident)).collation(collation).firstOrNull()
        }
    }

    private fun replaceInMongo(ident: String, data: PunishmentReason) {
        runCatching {
            val id = ident.toInt()
            collection.replaceOne(Filters.eq("id", id), data)
        }.onFailure {
            collection.replaceOne(Filters.eq("name", ident), data)
        }
    }

    private fun deleteInMongo(ident: String, data: PunishmentReason) {
        runCatching {
            val id = ident.toInt()
            collection.deleteOne(Filters.eq("id", id))

            // Remove all references
            inDeletion.remove(ident)
            isInDatabase.remove(ident)
            inDeletion.remove(data.name)
            isInDatabase.remove(data.name)
        }.onFailure {
            collection.deleteOne(Filters.eq("name", ident))

            // Remove all references
            inDeletion.remove(ident)
            isInDatabase.remove(ident)
            inDeletion.remove(data.id.toString())
            isInDatabase.remove(data.id.toString())
        }
    }
}