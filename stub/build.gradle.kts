plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.sys.config.stub"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
