package com.fox.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NodeEntity::class,
        EdgeEntity::class,
        ContentEntity::class,
        SyncStateEntity::class,
        AppUsageLogEntity::class,
        NodeSearchFtsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FoxDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun edgeDao(): EdgeDao
    abstract fun contentDao(): ContentDao
    abstract fun nodeSearchFtsDao(): NodeSearchFtsDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun appUsageLogDao(): AppUsageLogDao

    companion object {
        @Volatile
        private var instance: FoxDatabase? = null

        fun getInstance(context: Context): FoxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoxDatabase::class.java,
                    "fox-knowledge.db",
                ).build().also { instance = it }
            }
    }
}
