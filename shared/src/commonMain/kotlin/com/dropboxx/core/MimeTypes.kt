package com.dropboxx.core

object MimeTypes {
    private val byExtension = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
        "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "mkv" to "video/x-matroska", "mov" to "video/quicktime", "webm" to "video/webm",
        "avi" to "video/x-msvideo", "3gp" to "video/3gpp",
        "mp3" to "audio/mpeg", "wav" to "audio/wav", "flac" to "audio/flac", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
        "aac" to "audio/aac", "opus" to "audio/opus",
        "pdf" to "application/pdf", "zip" to "application/zip", "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed",
        "apk" to "application/vnd.android.package-archive", "exe" to "application/vnd.microsoft.portable-executable",
        "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint", "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "txt" to "text/plain", "md" to "text/markdown", "csv" to "text/csv", "json" to "application/json", "xml" to "application/xml",
        "html" to "text/html", "kt" to "text/plain", "java" to "text/plain", "py" to "text/plain", "js" to "text/javascript",
    )

    fun fromFileName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return byExtension[ext] ?: "application/octet-stream"
    }

    fun isImage(mime: String) = mime.startsWith("image/")
    fun isVideo(mime: String) = mime.startsWith("video/")
    fun isAudio(mime: String) = mime.startsWith("audio/")
}
