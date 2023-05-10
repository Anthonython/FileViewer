package com.example.fileviewer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FileEntity::class], version = 1)

abstract class AppDatabase : RoomDatabase() {

    abstract fun getDao(): Dao

    companion object {
        fun appDatabase(context: Context): AppDatabase{
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "hash.db").build()
        }
    }
}