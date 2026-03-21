package com.zendeck.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    @Query("SELECT * FROM link_items WHERE isArchived = 0 ORDER BY isPinned DESC, expiresAt ASC")
    fun getActiveLinks(): Flow<List<LinkItemEntity>>

    @Query("SELECT * FROM link_items WHERE isArchived = 0 ORDER BY isPinned DESC, expiresAt ASC LIMIT 5")
    fun getTopFiveActive(): Flow<List<LinkItemEntity>>

    @Query("SELECT * FROM link_items WHERE isArchived = 1 ORDER BY expiresAt DESC")
    fun getArchivedLinks(): Flow<List<LinkItemEntity>>

    @Query("SELECT * FROM link_items WHERE isArchived = 0 ORDER BY expiresAt ASC LIMIT 1")
    fun getMostUrgentActive(): Flow<LinkItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: LinkItemEntity)

    @Update
    suspend fun updateLink(link: LinkItemEntity)

    @Query("SELECT * FROM link_items WHERE id = :id")
    suspend fun getLinkById(id: String): LinkItemEntity?

    @Query("UPDATE link_items SET isArchived = 1 WHERE expiresAt <= :now AND isArchived = 0")
    suspend fun archiveExpiredLinks(now: Long)

    @Query("UPDATE link_items SET isArchived = 1 WHERE id = :id")
    suspend fun archiveLink(id: String)

    @Query("UPDATE link_items SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)

    @Query("UPDATE link_items SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: String, tags: List<String>)

    @Query("UPDATE link_items SET summary = :summary WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String)

    @Query("DELETE FROM link_items WHERE id = :id")
    suspend fun deleteLink(id: String)

    @Query("SELECT COUNT(*) FROM link_items WHERE isArchived = 0")
    fun getActiveLinkCount(): Flow<Int>
}
