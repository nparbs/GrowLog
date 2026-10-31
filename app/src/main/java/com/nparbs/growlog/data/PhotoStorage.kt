package com.nparbs.growlog.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Copies photos into private storage so they survive the source being moved or deleted. */
class PhotoStorage(private val context: Context) {
    private val dir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        val target = File(dir, "${UUID.randomUUID()}.jpg")
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        target.absolutePath
    }

    suspend fun delete(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }
}
