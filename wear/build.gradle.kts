import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// La app no lleva claves de API. Solo conoce la URL del servidor hidrasmart-api y los datos públicos
// del proyecto de Firebase (para App Check). Se leen de variables de entorno (CI) o de local.properties
// (este archivo NO se sube a GitHub). Vacíos = la app usa solo su IA local.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun setting(name: String): String = System.getenv(name)?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty(name)
    ?: ""

// Versión común de reloj y celular (gradle.properties). Cada uno tiene su propio rango de versionCode
// para que Google Play acepte los dos paquetes en el mismo listado: reloj 2000xxx, celular 1000xxx.
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()
val appVersionName = providers.gradleProperty("appVersionName").get()

android {
    namespace = "co.edu.ucc.hidrasmart"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.edu.ucc.hidrasmart"
        minSdk = 30 // Wear OS 3
        targetSdk = 35 // Google Play exige API 35 para Wear OS
        versionCode = 2_000_000 + appVersionCode
        versionName = appVersionName
        buildConfigField("String", "API_BASE_URL", "\"${setting("API_BASE_URL")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${setting("FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${setting("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${setting("FIREBASE_PROJECT_ID")}\"")
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

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Tile, complicación de la esfera y actividad en curso durante el entrenamiento
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.complications.data.source.ktx)
    implementation(libs.wear.ongoing)
    implementation(libs.androidx.concurrent.futures)

    testImplementation(libs.junit)
}
