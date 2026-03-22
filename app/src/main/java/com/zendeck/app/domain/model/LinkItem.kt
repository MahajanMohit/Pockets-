package com.zendeck.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    val faviconUrl: String
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
}
