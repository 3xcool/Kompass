package com.tekmoon.kompasskmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform