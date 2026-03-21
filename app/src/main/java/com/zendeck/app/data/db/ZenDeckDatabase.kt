package com.zendeck.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LinkItemEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ZenDeckDatabase : RoomDatabase() {
    abstract fun linkDao(): LinkDao

    companion object {
        @Volatile
        private var INSTANCE: ZenDeckDatabase? = null

        fun getInstance(context: Context): ZenDeckDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                ZenDeckDatabase::class.java,
                "zendeck.db"
            ).build()
    }
}
