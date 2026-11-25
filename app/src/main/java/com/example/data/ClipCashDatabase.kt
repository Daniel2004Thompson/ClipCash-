package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WalletStateEntity::class,
        ClipProgressEntity::class,
        TransactionEntity::class,
        ClaimedQuestEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ClipCashDatabase : RoomDatabase() {
    abstract fun clipCashDao(): ClipCashDao

    companion object {
        @Volatile
        private var instance: ClipCashDatabase? = null

        fun getInstance(context: Context): ClipCashDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClipCashDatabase::class.java,
                    "clipcash_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
