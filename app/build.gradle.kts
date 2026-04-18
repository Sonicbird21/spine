import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.spine.projectspine"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spine.projectspine"
        minSdk = 27
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
