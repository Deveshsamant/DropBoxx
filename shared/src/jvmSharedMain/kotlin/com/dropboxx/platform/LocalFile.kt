package com.dropboxx.platform

import com.dropboxx.core.MimeTypes
import com.dropboxx.model.PlatformFile
import okio.Source
import okio.source
import java.io.File

/** A plain file on this device's filesystem (desktop files, Android box copies). */
class LocalFile(val file: File) : PlatformFile {
    override val name: String = file.name
    override val size: Long = file.length()
    override val mimeType: String = MimeTypes.fromFileName(file.name)
    override val previewModel: Any get() = file
    override fun open(): Source = file.source()
}
