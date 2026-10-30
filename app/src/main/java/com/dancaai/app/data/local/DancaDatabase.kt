package com.dancaai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class DancaDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        /**
         * Nome do arquivo do banco. Exposto porque o Perfil mostra o espaço que o
         * histórico ocupa, e precisa localizar o arquivo (e os auxiliares do
         * SQLite) sem repetir a string.
         */
        const val NAME = "danca.db"

        @Volatile
        private var instance: DancaDatabase? = null

        fun get(context: Context): DancaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DancaDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
