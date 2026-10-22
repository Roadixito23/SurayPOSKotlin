import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Versión por fecha, como la app Flutter (`25.05.26` = año.mes.día): versionName `yy.MM.dd`, versionCode `yyMMdd`.
val buildDate = Date()
val dateVersionName = SimpleDateFormat("yy.MM.dd").format(buildDate)
val dateVersionCode = SimpleDateFormat("yyMMdd").format(buildDate).toInt()

// Firma de release: `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) fuera del repositorio.
// Ruta por defecto ~/.config/suray-signing/keystore.properties, o la variable SURAY_KEYSTORE_PROPERTIES.
val keystoreProps = Properties().also { props ->
    val path = System.getenv("SURAY_KEYSTORE_PROPERTIES") ?: "${System.getProperty("user.home")}/.config/suray-signing/keystore.properties"
    file(path).takeIf { it.isFile }?.inputStream()?.use(props::load)
}

android {
    namespace = "cl.suray.posbus"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.suray.posbus"
        minSdk = 23
        targetSdk = 34
        versionCode = dateVersionCode
        versionName = dateVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // El Sunmi V1s es de 32 bits (armeabi-v7a); el release no necesita otras ABI.
            ndk { abiFilters += "armeabi-v7a" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
