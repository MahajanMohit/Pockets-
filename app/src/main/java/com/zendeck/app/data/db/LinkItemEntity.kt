package com.zendeck.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zendeck.app.domain.model.LinkItem

@Entity(
    tableName = "link_items",
    indices = [
        Index(value = ["isArchived"]),
        Index(value = ["expiresAt"]),
        Index(value = ["isPinned"]),
        Index(value = ["url"])
    ]
)
data class LinkItemEntity(
    @PrimaryKey val id: String,
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
    // "pending" = OCR/LLM in progress, "done" = generated, "unavailable" = couldn't generate
    val summaryStatus: String = "done"
) {
    fun toDomain() = LinkItem(
        id = id,
        url = url,
        title = title,
        description = description,
        summary = summary,
        tags = tags,
        isPinned = isPinned,
        addedAt = addedAt,
        expiresAt = expiresAt,
        isArchived = isArchived,
        domain = domain,
        faviconUrl = faviconUrl,
        archivedAt = archivedAt,
        contentType = contentType,
        localImagePath = localImagePath,
        summaryStatus = summaryStatus
    )

    companion object {
        fun fromDomain(item: LinkItem) = LinkItemEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            description = item.description,
            summary = item.summary,
            tags = item.tags,
            isPinned = item.isPinned,
            addedAt = item.addedAt,
            expiresAt = item.expiresAt,
            isArchived = item.isArchived,
            domain = item.domain,
            faviconUrl = item.faviconUrl,
            archivedAt = item.archivedAt,
            contentType = item.contentType,
            localImagePath = item.localImagePath,
            summaryStatus = item.summaryStatus
        )
    }
}
