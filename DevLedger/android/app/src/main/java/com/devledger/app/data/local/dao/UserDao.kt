package com.devledger.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.devledger.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Transaction

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun observeCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clear()

    @Transaction
    suspend fun replaceCurrentUser(user: UserEntity) {
        clear()
        upsert(user)
    }
}
