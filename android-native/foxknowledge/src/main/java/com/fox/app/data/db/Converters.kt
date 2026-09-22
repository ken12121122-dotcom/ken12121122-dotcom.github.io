package com.fox.app.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun usageActionToString(value: UsageAction): String = value.name

    @TypeConverter
    fun stringToUsageAction(value: String): UsageAction = UsageAction.valueOf(value)
}
