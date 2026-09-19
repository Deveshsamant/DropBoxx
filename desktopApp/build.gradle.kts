import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs) {
        // The UI is Material 3 only; Material 2 comes along with the desktop bundle otherwise.
        exclude(group = "org.jetbrains.compose.material", module = "material")
    }
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.kermit)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.multiplatform.settings)
    testImplementation(libs.okio)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = false }
}

compose.desktop {
    application {
        mainClass = "com.dropnest.desktop.MainKt"

        // Small heap, full tiered JIT (C1 alone made the 60 fps nest/orbit stutter on laptops),
        // and a parallel GC that keeps pauses short at this heap size.
        jvmArgs += listOf(
            "-Xms96m",
            "-Xmx512m",
            "-XX:+UseParallelGC",
            "-XX:MaxGCPauseMillis=20",
            "-Xss1m",
            "-XX:+UseCompressedOops",
            "-Dfile.encoding=UTF-8",
            "-Dsun.java2d.uiScale.enabled=true",
            "-Dskiko.vsync.enabled=true",
        )

        nativeDistributions {
            // MSI for direct download; the Microsoft Store MSIX is produced from
            // the app-image by packaging/windows/build-msix.ps1.
            targetFormats(TargetFormat.Msi, TargetFormat.AppImage)
            packageName = "DropNest"
            packageVersion = "1.1.0"
            description = "Drop anything. Share it with every device on your Wi-Fi."
            vendor = "DropNest"
            copyright = "(c) 2026 DropNest"

            // jlink modules the runtime needs but that static analysis misses:
            //  - jdk.crypto.ec: TLS ECDHE ciphers (handshakes fail without it)
            //  - jdk.unsupported: Netty/OkHttp use sun.misc.Unsafe
            //  - java.naming / java.management: Netty + logging lookups
            modules("jdk.crypto.ec", "jdk.unsupported", "java.naming", "java.management", "java.sql", "java.net.http")

            windows {
                menuGroup = "DropNest"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "7e6c8a2e-4c39-4d2e-9d55-9a3f2b1e6c11"
                iconFile.set(project.file("src/main/resources/icons/dropnest.ico"))
            }
        }

        buildTypes.release.proguard {
            // Netty / Ktor rely on reflection; shrinking is not worth the risk on desktop.
            isEnabled.set(false)
        }
    }
}
