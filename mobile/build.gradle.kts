import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Same keys as the watch app, read from local.properties (never committed)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "co.edu.ucc.hidrasmart.mobile"
    compileSdk = 36

    defaultConfig {
        // Same application id as the watch app, so both are the same app on Google Play
        applicationId = "co.edu.ucc.hidrasmart"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "2.0.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${localProperties.getProperty("GEMINI_MODEL", "")}\"")
    }

    // Firma: en GitHub Actions se usa el keystore de los secretos; en local, la firma de depuración.
    // Así el APK de release siempre se puede instalar.
    signingConfigs {
        create("release") {
            val keystore = System.getenv("HIDRASMART_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("HIDRASMART_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HIDRASMART_KEY_ALIAS")
                keyPassword = System.getenv("HIDRASMART_KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("HIDRASMART_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        // Los avisos de lint no bloquean la compilación del APK en CI
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
}
