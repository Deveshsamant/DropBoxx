import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvm()

    android {
        namespace = "com.dropnest.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
    }

    // Both targets run on a JVM (Android ART + desktop JVM), so the whole
    // networking / transfer engine lives in one shared "jvmShared" source set
    // and only OS-specific glue (hotspot, MediaStore, tray...) is per-platform.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmShared") {
                withJvm()
                withAndroidTarget()
            }
        }
    }

    sourceSets {
        // The AGP 9 android target is created after the hierarchy template runs, so it is
        // not matched by withAndroidTarget() above: wire the intermediate set explicitly.
        getByName("androidMain").dependsOn(getByName("jvmSharedMain"))

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)

            implementation(libs.navigation.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
            implementation(libs.kermit)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noarg)

            implementation(libs.coil.compose)
        }
        getByName("jvmSharedMain").dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.netty)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.network.tls.certificates)
        }
        getByName("jvmSharedTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
