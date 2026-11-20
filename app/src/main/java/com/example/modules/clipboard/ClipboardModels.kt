package com.example.modules.clipboard

data class ClipboardEntry(
    val id: String,
    val text: String,
    val timestampMs: Long,
    val pinned: Boolean = false
)

data class ClipboardConfig(
    val enabled: Boolean = true,
    val entries: List<ClipboardEntry> = emptyList()
)
