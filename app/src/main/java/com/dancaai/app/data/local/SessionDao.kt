package com.dancaai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** Todas as sessões, da mais recente para a mais antiga. */
    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMs DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    /** Sessão imediatamente anterior à data dada, base da comparação na tela de Resultado. */
    @Query(
        "SELECT * FROM sessions WHERE startedAtEpochMs < :beforeEpochMs " +
            "ORDER BY startedAtEpochMs DESC LIMIT 1"
    )
    suspend fun findPrevious(beforeEpochMs: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun findById(id: Long): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}
