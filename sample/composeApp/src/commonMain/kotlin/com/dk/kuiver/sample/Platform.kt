package com.dk.kuiver.sample

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform