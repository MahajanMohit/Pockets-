package com.zendeck.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * [Immutable] lets Compose treat this class as stable (the `tags` List would
 * otherwise mark it unstable), so LinkCards skip recomposition during scroll
 * unless their own item actually changed.
 */
@Immutable
@Serializable
data class LinkItem(
    val id: String,
    val url: String,
    val title: String,
    val description: String,
    val summary: String,
    val tags: List<String>,
    val isPinned: Boolean,
    val addedAt: Long,
    val expiresAt: Long,
    val isArchived: Boolean,
    val domain: String,
    val faviconUrl: String,
    val archivedAt: Long = 0L,
    val contentType: String = "link",
    val localImagePath: String = "",
    val summaryStatus: String = "done"
) {
    /** 0.0 = just added, 1.0 = exactly expired */
    @Transient val urgencyFraction: Float
        get() {
            val now = System.currentTimeMillis()
            val total = (expiresAt - addedAt).toFloat()
            val elapsed = (now - addedAt).toFloat()
            return (elapsed / total).coerceIn(0f, 1f)
        }

    @Transient val summaryBullets: List<String>
        get() = summary
            .split("\n")
            .filter { it.isNotBlank() }
            .take(3)

    @Transient val timeUntilExpiry: String
        get() {
            val now = System.currentTimeMillis()
            val remaining = expiresAt - now
            if (remaining <= 0) return "Expired"
            val hours = remaining / 3_600_000
            val minutes = (remaining % 3_600_000) / 60_000
            return when {
                hours >= 24 -> "${hours / 24}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes}m"
                else -> "${minutes}m"
            }
        }

    /** How long until auto-deletion from archive (72h after archiving). Null if not archived. */
    @Transient val timeUntilArchiveDeletion: String?
        get() {
            if (!isArchived || archivedAt == 0L) return null
            val deleteAt = archivedAt + 72 * 3_600_000L
            val remaining = deleteAt - System.currentTimeMillis()
            if (remaining <= 0) return "Deleting soon"
            val hours = remaining / 3_600_000
            val minutes = (remaining % 3_600_000) / 60_000
            return when {
                hours >= 24 -> "Deletes in ${hours / 24}d ${hours % 24}h"
                hours > 0   -> "Deletes in ${hours}h ${minutes}m"
                else        -> "Deletes in ${minutes}m"
            }
        }
}
