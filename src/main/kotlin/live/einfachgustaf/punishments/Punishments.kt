package live.einfachgustaf.punishments

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import jakarta.inject.Inject
import live.einfachgustaf.punishments.utils.reasonCache

@Plugin(
    id = "punishments",
    name = "punishments",
    version = "1.0",
    authors = ["DinoMarlir"],
    description = "Punishment plugin for Velocity."
)
class Punishments @Inject constructor(private val server: ProxyServer) {

    companion object {
        lateinit var proxyServer: ProxyServer
    }

    @Subscribe
    fun onProxyIntitialization(event: ProxyInitializeEvent) {
        proxyServer = server

        // Load all reasons into cache
        reasonCache.loadAllIntoCache()
    }
}