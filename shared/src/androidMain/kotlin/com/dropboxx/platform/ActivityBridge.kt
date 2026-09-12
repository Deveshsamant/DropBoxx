package com.dropboxx.platform

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import java.lang.ref.WeakReference

/**
 * The engine lives longer than any Activity, but pickers and permission prompts need one.
 * The foreground Activity registers itself here; callers suspend until the user answers.
 */
object ActivityBridge {

    interface Host {
        fun launchFilePicker()
        fun launchPermissions(permissions: Array<String>)
    }

    private var host: WeakReference<Host>? = null
    private var pickerResult: CompletableDeferred<List<Uri>>? = null
    private var permissionResult: CompletableDeferred<Map<String, Boolean>>? = null

    fun attach(h: Host) { host = WeakReference(h) }
    fun detach(h: Host) { if (host?.get() === h) host = null }

    suspend fun pickFiles(): List<Uri> {
        val h = host?.get() ?: return emptyList()
        pickerResult?.cancel()
        val d = CompletableDeferred<List<Uri>>()
        pickerResult = d
        h.launchFilePicker()
        return d.await()
    }

    fun onFilesPicked(uris: List<Uri>) {
        pickerResult?.complete(uris)
        pickerResult = null
    }

    suspend fun requestPermissions(permissions: Array<String>): Map<String, Boolean> {
        val h = host?.get() ?: return permissions.associateWith { false }
        permissionResult?.cancel()
        val d = CompletableDeferred<Map<String, Boolean>>()
        permissionResult = d
        h.launchPermissions(permissions)
        return d.await()
    }

    fun onPermissionsResult(result: Map<String, Boolean>) {
        permissionResult?.complete(result)
        permissionResult = null
    }
}
