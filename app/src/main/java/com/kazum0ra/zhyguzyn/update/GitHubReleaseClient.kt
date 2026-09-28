package com.kazum0ra.zhyguzyn.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Опис останнього релізу на GitHub. */
data class ReleaseInfo(
    val tagName: String,
    val notes: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val apkName: String?,
)

/** Помилка, яку можна пояснити користувачу. */
class UpdateException(val reason: UpdateError, cause: Throwable? = null) : Exception(reason.name, cause)

enum class UpdateError {
    NETWORK,
    NO_RELEASES,
    RATE_LIMIT,
    BAD_RESPONSE,
    NO_APK,
    STORAGE,
    DOWNLOAD_FAILED,
}

/** Мінімальний клієнт GitHub API без сторонніх бібліотек. */
class GitHubReleaseClient(
    private val latestReleaseUrl: String,
    private val userAgent: String,
) {

    /** Блокуючий виклик — запускати в Dispatchers.IO. */
    fun fetchLatest(): ReleaseInfo {
        val connection = try {
            (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", userAgent)
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateError.NETWORK, e)
        }
        try {
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw UpdateException(UpdateError.NETWORK, e)
            }
            when (code) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException(UpdateError.NO_RELEASES)
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> throw UpdateException(UpdateError.RATE_LIMIT)
                else -> throw UpdateException(UpdateError.BAD_RESPONSE)
            }
            val body = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw UpdateException(UpdateError.NETWORK, e)
            }
            return parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): ReleaseInfo = try {
        val root = JSONObject(json)
        val assets = root.optJSONArray("assets")
        var apkUrl: String? = null
        var apkName: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkName = name
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
        }
        ReleaseInfo(
            tagName = root.getString("tag_name"),
            notes = root.optString("body").takeUnless { root.isNull("body") }.orEmpty().trim(),
            htmlUrl = root.optString("html_url"),
            apkUrl = apkUrl,
            apkName = apkName,
        )
    } catch (e: Exception) {
        throw UpdateException(UpdateError.BAD_RESPONSE, e)
    }
}
