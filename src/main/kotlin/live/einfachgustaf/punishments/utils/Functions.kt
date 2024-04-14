package live.einfachgustaf.punishments.utils

import net.kyori.adventure.text.Component
import java.util.*

fun appendMessageToPrefix(message: Component): Component {
    return prefix.append(message)
}

fun generatePunishmentId(): String {
    return UUID.randomUUID().toString().replace("-", "").substring(0..8)
}