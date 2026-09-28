import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Версія застосунку береться з git-тегу формату v1.2.3.
 * У CI тег передається як -PappVersion=v1.2.3 (див. .github/workflows/release.yml).
 * Локально без параметра збирається версія 0.0.1.
 */
val appVersionTag: String = (findProperty("appVersion") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: "v0.0.1"

val appVersionName: String = appVersionTag.removePrefix("v").removePrefix("V")

val appVersionCode: Int = run {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(appVersionName)
        ?: error("Тег версії має бути у форматі v1.2.3, отримано: $appVersionTag")
    val (major, minor, patch) = match.destructured.toList().map { it.toInt() }
    require(minor < 1000 && patch < 1000) { "minor і patch мають бути меншими за 1000" }
    // 1.2.3 -> 1002003: кожна новіша версія має більший versionCode.
    major * 1_000_000 + minor * 1_000 + patch
}

/**
 * Параметри підпису: спершу змінні середовища (CI), потім keystore.properties (локально).
 * Якщо ключа немає — release підписується debug-ключем, щоб ./gradlew assembleRelease
 * працював без ручних кроків. У CI відсутність ключа — помилка (перевіряється у workflow).
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)

val releaseStoreFile = signingValue("KEYSTORE_FILE", "storeFile")
val releaseStorePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword")
val hasReleaseKey = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() } && rootProject.file(releaseStoreFile!!).exists()

android {
    namespace = "com.kazum0ra.zhyguzyn"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kazum0ra.zhyguzyn"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("⚠ Ключ підпису не знайдено — release APK буде підписано debug-ключем.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
