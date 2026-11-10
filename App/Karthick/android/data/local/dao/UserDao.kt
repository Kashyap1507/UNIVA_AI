package com.kashyap.wizlyai.data.local.dao

import androidx.room.*
import com.kashyap.wizlyai.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for User operations
 */
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Delete
    suspend fun delete(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getByIdFlow(userId: String): Flow<UserEntity?>

    @Query("UPDATE users SET last_login = :lastLogin WHERE id = :userId")
    suspend fun updateLastLogin(
        userId: String,
        lastLogin: String,
    )

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getCount(): Int
}
