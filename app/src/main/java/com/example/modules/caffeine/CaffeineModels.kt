package com.example.modules.caffeine

enum class CaffeineTimeoutMode(
    val label: String,
    val shortLabel: String,
    val durationMs: Long?
) {
    INDEFINITE(
        label = "Illimité (manuel ou bouton Power)",
        shortLabel = "Illimité",
        durationMs = null
    ),
    FIVE_MINUTES(
        label = "5 minutes maximum",
        shortLabel = "5 min",
        durationMs = 5 * 60 * 1000L
    ),
    FIFTEEN_MINUTES(
        label = "15 minutes maximum",
        shortLabel = "15 min",
        durationMs = 15 * 60 * 1000L
    ),
    THIRTY_MINUTES(
        label = "30 minutes maximum",
        shortLabel = "30 min",
        durationMs = 30 * 60 * 1000L
    )
}

data class CaffeineConfig(
    val isActive: Boolean = false,
    val keepBright: Boolean = true,
    val disableOnScreenOff: Boolean = true,
    val showStatusBarIcon: Boolean = true,
    val timeoutMode: CaffeineTimeoutMode = CaffeineTimeoutMode.INDEFINITE,
    val activeSinceEpochMs: Long? = null
)
