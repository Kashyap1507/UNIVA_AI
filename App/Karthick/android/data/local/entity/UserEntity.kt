package com.kashyap.wizlyai.data.local.entity

import androidx.room.*

/**
 * Room entity for users (students)
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
    ],
)
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "email")
    val email: String,
    @ColumnInfo(name = "name")
    val name: String? = null,
    @ColumnInfo(name = "auth_provider")
    val authProvider: String = "email",
    @ColumnInfo(name = "created_at")
    val createdAt: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: String = "",
    @ColumnInfo(name = "last_login")
    val lastLogin: String? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "synced",

    // Subscription fields
    @ColumnInfo(name = "subscription_tier")
    val subscriptionTier: String = "free",
    @ColumnInfo(name = "subscription_status")
    val subscriptionStatus: String = "active",
    @ColumnInfo(name = "account_type")
    val accountType: String = "full",

    // Referral fields
    @ColumnInfo(name = "referral_code")
    val referralCode: String? = null,
    @ColumnInfo(name = "referral_limit")
    val referralLimit: Int = 5,
    @ColumnInfo(name = "referrals_remaining")
    val referralsRemaining: Int = 5,
)
