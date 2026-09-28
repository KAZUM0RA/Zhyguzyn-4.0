package com.kazum0ra.zhyguzyn.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.kazum0ra.zhyguzyn.AppConfig
import com.kazum0ra.zhyguzyn.BuildConfig
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.data.SettingsRepository
import com.kazum0ra.zhyguzyn.domain.AppVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    /** progress — від 0 до 1, або null, якщо розмір ще невідомий. */
    data class Downloading(val release: ReleaseInfo, val progress: Float?) : UpdateState
    data class ReadyToInstall(
        val release: ReleaseInfo,
        val file: File,
        /** Користувач ще не дозволив встановлення з цього застосунку. */
        val needsPermission: Boolean,
    ) : UpdateState
    data class Failed(val error: UpdateError, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Перевірка, завантаження та запуск встановлення оновлень із GitHub Releases.
 * Живе весь час роботи застосунку (див. AppContainer).
 */
class UpdateManager(
    context: Context,
    private val settings: SettingsRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = GitHubReleaseClient(
        latestReleaseUrl = AppConfig.LATEST_RELEASE_URL,
        userAgent = "Zhyguzyn/${BuildConfig.VERSION_NAME} (Android)",
    )

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Чи показувати діалог оновлення. */
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Автоматична перевірка при запуску — не частіше ніж раз на добу, помилки мовчки ігноруються. */
    fun checkOnLaunch() {
        scope.launch {
            val last = settings.lastUpdateCheckMillis()
            val now = System.currentTimeMillis()
            if (now - last in 0 until AppConfig.UPDATE_CHECK_INTERVAL_MILLIS) return@launch
            check(manual = false)
        }
    }

    /** Ручна перевірка з налаштувань. */
    fun checkNow() {
        check(manual = true)
    }

    private fun check(manual: Boolean) {
        if (checkJob?.isActive == true || isBusy()) return
        checkJob = scope.launch {
            if (manual) _state.value = UpdateState.Checking
            try {
                val release = withContext(Dispatchers.IO) { client.fetchLatest() }
                settings.setLastUpdateCheckMillis(System.currentTimeMillis())
                if (AppVersion.isNewer(release.tagName, currentVersion)) {
                    _state.value = UpdateState.Available(release)
                    _dialogVisible.value = true
                } else {
                    _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                }
            } catch (e: UpdateException) {
                if (e.reason == UpdateError.NO_RELEASES) {
                    settings.setLastUpdateCheckMillis(System.currentTimeMillis())
                }
                _state.value = if (manual) UpdateState.Failed(e.reason) else UpdateState.Idle
            }
        }
    }

    private fun isBusy() = _state.value is UpdateState.Downloading

    fun showDialog() {
        _dialogVisible.value = true
    }

    fun dismissDialog() {
        _dialogVisible.value = false
        // Незавершене завантаження продовжується у фоні, решту станів скидаємо.
        _state.update { current ->
            when (current) {
                is UpdateState.Downloading, is UpdateState.ReadyToInstall -> current
                is UpdateState.Available -> current
                else -> UpdateState.Idle
            }
        }
    }

    /** Кнопка «Оновити»: завантажити APK через DownloadManager і запустити встановлення. */
    fun download(release: ReleaseInfo) {
        val url = release.apkUrl
        if (url == null) {
            _state.value = UpdateState.Failed(UpdateError.NO_APK, release)
            return
        }
        if (downloadJob?.isActive == true) return

        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null) {
            _state.value = UpdateState.Failed(UpdateError.STORAGE, release)
            return
        }
        // Прибираємо старі завантаження.
        dir.listFiles { file -> file.name.endsWith(".apk") }?.forEach { it.delete() }
        val fileName = "zhyguzyn-${release.tagName}.apk"
        val target = File(dir, fileName)

        val downloadManager = appContext.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(url.toUri())
            .setTitle(appContext.getString(R.string.update_download_title, release.tagName))
            .setDescription(appContext.getString(R.string.update_download_description))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(UpdateError.DOWNLOAD_FAILED, release)
            return
        }

        _state.value = UpdateState.Downloading(release, null)
        downloadJob = scope.launch {
            while (true) {
                val status = withContext(Dispatchers.IO) { queryStatus(downloadManager, id) }
                when (status) {
                    is DownloadStatus.Running -> _state.value = UpdateState.Downloading(release, status.progress)
                    DownloadStatus.Success -> {
                        _state.value = UpdateState.ReadyToInstall(release, target, needsPermission = false)
                        _dialogVisible.value = true
                        install(appContext)
                        return@launch
                    }
                    DownloadStatus.Failed -> {
                        _state.value = UpdateState.Failed(UpdateError.DOWNLOAD_FAILED, release)
                        return@launch
                    }
                }
                delay(500)
            }
        }
    }

    /**
     * Запускає системний інсталятор. Якщо користувач ще не дозволив встановлення
     * з цього застосунку — відкриває відповідний екран налаштувань.
     */
    fun install(context: Context) {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        if (!ready.file.exists()) {
            _state.value = UpdateState.Failed(UpdateError.DOWNLOAD_FAILED, ready.release)
            return
        }
        val packageManager = context.packageManager
        if (!packageManager.canRequestPackageInstalls()) {
            _state.value = ready.copy(needsPermission = true)
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startSafely(context, intent)
            return
        }
        _state.value = ready.copy(needsPermission = false)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent)
    }

    private fun startSafely(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }

    private sealed interface DownloadStatus {
        data class Running(val progress: Float?) : DownloadStatus
        data object Success : DownloadStatus
        data object Failed : DownloadStatus
    }

    private fun queryStatus(manager: DownloadManager, id: Long): DownloadStatus {
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return DownloadStatus.Failed
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Success
                DownloadManager.STATUS_FAILED -> DownloadStatus.Failed
                else -> {
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    DownloadStatus.Running(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
                }
            }
        }
        return DownloadStatus.Failed
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
