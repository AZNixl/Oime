package com.azime.input.data.model

data class KeyboardLayout(
    val name: String,
    val rows: List<KeyboardRow>
)

data class KeyboardRow(
    val keys: List<Key>
)

data class Key(
    val label: String,
    val code: String,
    val width: Float = 1.0f,
    val type: KeyType = KeyType.CHARACTER
)

enum class KeyType {
    CHARACTER,
    FUNCTION,
    MODIFIER,
    SPACE,
    ENTER,
    DELETE
}
