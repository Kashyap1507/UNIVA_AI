package com.kashyap.wizlyai.data.models

import kotlinx.serialization.Serializable

/**
 * The signed-in student (display name and email)
 */
@Serializable
data class User(
    val id: String = "",
    val name: String,
    val email: String,
)
