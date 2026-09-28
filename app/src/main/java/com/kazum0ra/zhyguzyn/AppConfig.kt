package com.kazum0ra.zhyguzyn

/**
 * Репозиторій GitHub, з релізів якого застосунок бере оновлення.
 * Щоб перейти на свій репозиторій — змініть лише цей рядок у форматі "OWNER/REPO".
 */
const val GITHUB_REPOSITORY = "KAZUM0RA/Zhyguzyn-4.0"

object AppConfig {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_REPOSITORY/releases/latest"

    /** Автоматична перевірка оновлень — не частіше ніж раз на добу. */
    const val UPDATE_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
}
