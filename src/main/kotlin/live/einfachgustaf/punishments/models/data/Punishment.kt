package live.einfachgustaf.punishments.models.data

import kotlinx.serialization.Serializable
import live.einfachgustaf.punishments.types.PunishmentType
import live.einfachgustaf.punishments.utils.generatePunishmentId

@Serializable
data class Punishment(
    val id: String = generatePunishmentId(),
    val reason: String,
    val type: PunishmentType,
    val expiresAt: Long,
    val date: Long,
    val moderator: String,
    var unbanModerator: String? = null,
    var unbanReason: String? = null,
    var unbanDate: Long? = null,
    val annotation: String? = null,
)