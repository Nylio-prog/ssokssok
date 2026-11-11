package com.nylio.ssokssok.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DeckEntity::class, CardEntity::class, ProgressEntity::class, ReviewLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun progressDao(): ProgressDao
    abstract fun reviewDao(): ReviewDao
    abstract fun importDao(): ImportDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ssokssok.db").build()
    }
}
