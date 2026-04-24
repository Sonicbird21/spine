import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.GradleException
import java.util.UUID

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

fun envOrNull(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

fun randomApplicationId(baseId: String): String {
    val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(10)
    return "$baseId.r$randomSuffix"
}

val baseApplicationId = envOrNull("SYS_CONFIG_BASE_APPLICATION_ID") ?: "com.spotify.music"
val randomizeApplicationId = envOrNull("SYS_CONFIG_RANDOM_APP_ID")?.toBooleanStrictOrNull() ?: true
val resolvedApplicationId = if (randomizeApplicationId) randomApplicationId(baseApplicationId) else baseApplicationId

println("[Sp1ne] Using applicationId: $resolvedApplicationId")

android {
    namespace = "com.spotify.music"
    compileSdk = 36

    defaultConfig {
        applicationId = resolvedApplicationId
        minSdk = 27
        versionCode = 1
        versionName = "tomato"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = envOrNull("ANDROID_KEYSTORE_PATH")
            val keystorePassword = envOrNull("ANDROID_KEYSTORE_PASSWORD")
            val keyAlias = envOrNull("ANDROID_KEY_ALIAS")
            val keyPassword = envOrNull("ANDROID_KEY_PASSWORD")

            if (keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf("META-INF/**", "**.bin")
        }
    }
}

val releaseTaskNames = gradle.startParameter.taskNames.filter {
    it.contains("release", ignoreCase = true)
}

if (releaseTaskNames.isNotEmpty()) {
    val missingCredentials = listOf(
        "ANDROID_KEYSTORE_PATH" to envOrNull("ANDROID_KEYSTORE_PATH"),
        "ANDROID_KEYSTORE_PASSWORD" to envOrNull("ANDROID_KEYSTORE_PASSWORD"),
        "ANDROID_KEY_ALIAS" to envOrNull("ANDROID_KEY_ALIAS"),
        "ANDROID_KEY_PASSWORD" to envOrNull("ANDROID_KEY_PASSWORD"),
    ).filter { it.second.isNullOrBlank() }.map { it.first }

    if (missingCredentials.isNotEmpty()) {
        throw GradleException(
            "Release signing credentials are missing: ${missingCredentials.joinToString(", ")}."
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-call-assertions"
        )
    }
}

dependencies {
    implementation(libs.annotation)
    implementation(libs.dexkit)
    implementation(libs.flatbuffers.java)
    compileOnly(libs.xposed)
    compileOnly(project(":stub"))
}
