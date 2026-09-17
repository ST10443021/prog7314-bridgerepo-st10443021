package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.devledger.app.data.local.entity.DeletedEntity

@Dao
interface DeletedDao {
    @Query("SELECT * FROM deleted_entities WHERE userId = :userId ORDER BY deletedAt ASC")
    suspend fun getForUser(userId: String): List<DeletedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DeletedEntity)

    @Query("DELETE FROM deleted_entities WHERE entityId = :id")
    suspend fun delete(id: String)
}
