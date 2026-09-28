package com.kazum0ra.zhyguzyn.domain

/** Порівняння версій у форматі 1.2.3 / v1.2.3. */
object AppVersion {

    private val pattern = Regex("""^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

    /** [major, minor, patch] або null, якщо рядок не схожий на версію. */
    fun parse(version: String): List<Int>? {
        val match = pattern.find(version.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
    }

    /** true, якщо [remoteTag] новіший за [currentVersion]. */
    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = parse(remoteTag) ?: return false
        val current = parse(currentVersion) ?: return true
        for (i in 0 until 3) {
            if (remote[i] != current[i]) return remote[i] > current[i]
        }
        return false
    }
}
