package com.dropnest.engine.store

import co.touchlab.kermit.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Tiny durable store: one JSON file, atomic replace on write.
 * Plenty for trusted-device lists and a few hundred history rows; swap for SQLDelight if it ever grows.
 */
class JsonFileStore<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) {

    private val log = Logger.withTag("JsonStore")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun load(): T {
        if (!file.exists()) return default()
        return runCatching { json.decodeFromString(serializer, file.readText()) }
            .onFailure { log.w(it) { "Could not read ${file.name}, starting empty" } }
            .getOrElse { default() }
    }

    @Synchronized
    fun save(value: T) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { log.e(it) { "Could not write ${file.name}" } }
    }
}
