package com.dk.kuiver.sample

// Enum for node color selection
enum class NodeColorType {
    PINK,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE;

    companion object {
        val ALL = listOf(PINK, ORANGE, YELLOW, GREEN, BLUE)
    }
}
