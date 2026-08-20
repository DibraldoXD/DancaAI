package com.dancaai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class DancaDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: DancaDatabase? = null

        fun get(context: Context): DancaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DancaDatabase::class.java,
                    "danca.db",
                ).build().also { instance = it }
            }
    }
}
