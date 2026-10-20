package com.kashyap.wizlyai

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
