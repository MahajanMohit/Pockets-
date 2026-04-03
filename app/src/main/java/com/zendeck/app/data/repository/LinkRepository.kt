package com.zendeck.app.data.repository

import android.content.Context
import com.zendeck.app.data.db.LinkItemEntity
import com.zendeck.app.data.db.ZenDeckDatabase
import com.zendeck.app.domain.model.LinkItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class LinkRepository private constructor(context: Context) {

    private val dao = ZenDeckDatabase.getInstance(context).linkDao()

    fun getActiveLinks(): Flow<List<LinkItem>> =
        dao.getActiveLinks().map { list -> list.map { it.toDomain() } }

    fun getTopFiveActive(): Flow<List<LinkItem>> =
        dao.getTopFiveActive().map { list -> list.map { it.toDomain() } }

    fun getArchivedLinks(): Flow<List<LinkItem>> =
        dao.getArchivedLinks().map { list -> list.map { it.toDomain() } }

    fun getMostUrgentActive(): Flow<LinkItem?> =
        dao.getMostUrgentActive().map { it?.toDomain() }

    fun getActiveLinkCount(): Flow<Int> = dao.getActiveLinkCount()

    /**
     * Inserts a link. Returns the ID of the newly created or already-existing item.
     * If the URL already exists, skips insertion and returns the existing ID.
     */
    suspend fun addLink(
        url: String,
        title: String,
        description: String,
        domain: String,
        faviconUrl: String,
        ttlHours: Long = 72L,
        contentType: String = "link",
        localImagePath: String = "",
        summaryStatus: String = "done"
    ): Pair<String, Boolean> {
        // Duplicate check — return existing ID without re-inserting
        dao.findIdByUrl(url)?.let { existingId -> return Pair(existingId, false) }

        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val entity = LinkItemEntity(
            id = id,
            url = url,
            title = title,
            description = description,
            summary = "",
            tags = emptyList(),
            isPinned = false,
            addedAt = now,
            expiresAt = now + ttlHours * 3_600_000L,
            isArchived = false,
            domain = domain,
            faviconUrl = faviconUrl,
            archivedAt = 0L,
            contentType = contentType,
            localImagePath = localImagePath,
            summaryStatus = summaryStatus
        )
        dao.insertLink(entity)
        return Pair(id, true)
    }

    suspend fun updateContentMeta(id: String, type: String, path: String) =
        dao.updateContentMeta(id, type, path)

    suspend fun updateSummaryStatus(id: String, status: String) =
        dao.updateSummaryStatus(id, status)

    suspend fun updateSummary(id: String, summary: String) =
        dao.updateSummary(id, summary)

    suspend fun updateLinkMetadata(id: String, title: String, description: String, domain: String, faviconUrl: String) =
        dao.updateLinkMetadata(id, title, description, domain, faviconUrl)

    suspend fun archiveLink(id: String) =
        dao.archiveLink(id, System.currentTimeMillis())

    suspend fun togglePin(id: String, pinned: Boolean) =
        dao.updatePinned(id, pinned)

    suspend fun updateTags(id: String, tags: List<String>) =
        dao.updateTags(id, tags)

    suspend fun deleteLink(id: String) {
        val entity = dao.getLinkById(id)
        if (entity != null && entity.localImagePath.isNotBlank()) {
            File(entity.localImagePath).delete()
        }
        dao.deleteLink(id)
    }

    suspend fun deleteLocalFilesForExpiredArchived(ttlHours: Long = 72L) {
        val now = System.currentTimeMillis()
        val ttlMs = ttlHours * 3_600_000L
        dao.getExpiredArchivedImagePaths(now, ttlMs).forEach { path ->
            if (path.isNotBlank()) File(path).delete()
        }
    }

    suspend fun archiveExpired() =
        dao.archiveExpiredLinks(System.currentTimeMillis())

    suspend fun deleteExpiredArchived(ttlHours: Long = 72L) =
        dao.deleteExpiredArchived(System.currentTimeMillis(), ttlHours * 3_600_000L)

    suspend fun restoreLink(id: String) =
        dao.restoreLink(id)

    suspend fun getAllLinks(): List<LinkItem> =
        dao.getAllLinks().map { it.toDomain() }

    /** One-shot reads for the LAN server (called via runBlocking from NanoHTTPD threads). */
    suspend fun getInboxLinksSnapshot(): List<LinkItem> =
        dao.getActiveLinksOnce().map { it.toDomain() }

    suspend fun getArchivedLinksSnapshot(): List<LinkItem> =
        dao.getArchivedLinksOnce().map { it.toDomain() }

    suspend fun getTagsForLink(id: String): List<String> =
        dao.getLinkById(id)?.tags ?: emptyList()

    suspend fun importLinks(links: List<LinkItem>) =
        links.forEach { dao.insertLink(LinkItemEntity.fromDomain(it)) }

    companion object {
        @Volatile
        private var INSTANCE: LinkRepository? = null

        fun getInstance(context: Context): LinkRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LinkRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
