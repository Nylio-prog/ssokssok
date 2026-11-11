package com.nylio.ssokssok.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [DeckEntity::class, CardEntity::class, ProgressEntity::class, ReviewLogEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = AppDatabase.DropRomanization::class)],
)
abstract class AppDatabase : RoomDatabase() {
    /** v2: romanization removed, hanja added. */
    @DeleteColumn(tableName = "cards", columnName = "romanization")
    class DropRomanization : AutoMigrationSpec

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
