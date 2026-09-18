import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.foundation)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.kermit)
    implementation(libs.kotlinx.coroutines.android)
}

// Release signing: androidApp/keystore.properties (git-ignored) with storeFile/storePassword/keyAlias/keyPassword.
val keystoreProps = Properties().apply {
    val f = rootProject.file("androidApp/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.dropnest.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.dropnest.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.1.0"
    }
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
    buildTypes {
        getByName("release") {
            // Shrinking is deliberately off for v1: Netty + Ktor need a curated
            // keep-rule set before R8 can be enabled safely. See proguard-rules.pro.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
