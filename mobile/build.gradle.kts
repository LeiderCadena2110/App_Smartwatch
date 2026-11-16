import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// La app no lleva claves de API: solo conoce la URL del servidor hidrasmart-api.
// Se lee de la variable de entorno API_BASE_URL (CI) o de local.properties (este archivo NO se sube a GitHub).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val apiBaseUrl: String = System.getenv("API_BASE_URL")
    ?: localProperties.getProperty("API_BASE_URL")
    ?: ""

// Versión común de reloj y celular (gradle.properties). Cada uno tiene su propio rango de versionCode
// para que Google Play acepte los dos paquetes en el mismo listado: reloj 2000xxx, celular 1000xxx.
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()
val appVersionName = providers.gradleProperty("appVersionName").get()

android {
    namespace = "co.edu.ucc.hidrasmart.mobile"
    compileSdk = 36

    defaultConfig {
        // Mismo applicationId que el reloj: en Google Play son una sola app con dos paquetes
        applicationId = "co.edu.ucc.hidrasmart"
        minSdk = 26
        targetSdk = 36 // Google Play exige API 36 para apps de teléfono desde el 31 de agosto de 2026
        versionCode = 1_000_000 + appVersionCode
        versionName = appVersionName
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    // Firma: en GitHub Actions se usa el keystore de los secretos; en local, la firma de depuración.
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
            // R8: reduce y ofusca el código y quita recursos sin usar
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("HIDRASMART_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        // Un error de lint detiene la compilación de release y el job de lint en CI
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
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
