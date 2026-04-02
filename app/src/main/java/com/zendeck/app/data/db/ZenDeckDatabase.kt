package com.zendeck.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LinkItemEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ZenDeckDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao

    companion object {
        @Volatile
        private var INSTANCE: ZenDeckDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE link_items ADD COLUMN archivedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_link_items_url ON link_items (url)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE link_items ADD COLUMN contentType TEXT NOT NULL DEFAULT 'link'")
                db.execSQL("ALTER TABLE link_items ADD COLUMN localImagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): ZenDeckDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                ZenDeckDatabase::class.java,
                "zendeck.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
