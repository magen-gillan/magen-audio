/*
 *  Copyright (c) 2022~2026 chr_56
 */

package player.phonograph.mechanism.folder

import player.phonograph.model.SongCollection
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Persistent store of custom folder icons.
 *
 * The picked image is **copied** into the app-private storage (`filesDir/folder_icons`),
 * so it never depends on the (temporary) URI permission of the gallery app and survives restarts.
 * The folder is identified by its full path ([SongCollection.folderKey]), not by its (non-unique) short name.
 */
object FolderIconStore {

    private const val TAG = "FolderIconStore"
    private const val PREFS_NAME = "custom_folder_icons"
    private const val DIRECTORY = "folder_icons"
    private const val MAX_SIZE = 768

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun directory(context: Context): File =
        File(context.applicationContext.filesDir, DIRECTORY).also { it.mkdirs() }

    /**
     * @return the saved icon file for [folderKey], null if not set (or file is lost)
     */
    fun get(context: Context, folderKey: String): File? {
        val fileName = preferences(context).getString(normalizePath(folderKey), null) ?: return null
        val file = File(directory(context), fileName)
        return if (file.isFile && file.length() > 0) file else null
    }

    fun get(context: Context, folder: SongCollection): File? = get(context, folder.folderKey)

    /**
     * Copy and save image from [uri] as the icon of [folderKey]
     * @return null if succeeded, otherwise the reason of failure
     */
    suspend fun set(context: Context, rawFolderKey: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        val folderKey = normalizePath(rawFolderKey)
        val app = context.applicationContext
        try {
            val bitmap = decodeScaled(app, uri) ?: return@withContext "Unsupported or unreadable image"
            val dir = directory(app)
            // a new file name for each change so that image caches never serve the old image
            val newFile = File(dir, "${digest(folderKey)}_${System.currentTimeMillis()}.jpg")
            try {
                newFile.outputStream().buffered().use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) return@withContext "Failed to encode the image"
                }
            } finally {
                bitmap.recycle()
            }
            val prefs = preferences(app)
            val old = prefs.getString(folderKey, null)
            // commit() (synchronous) to make sure it is really written before reporting success
            val ok = prefs.edit().putString(folderKey, newFile.name).commit()
            if (ok && old != null && old != newFile.name) File(dir, old).delete()
            if (!ok) newFile.delete()
            if (ok) null else "Failed to write settings"
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the image", e)
            "No permission to read the image"
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Image is too large", e)
            "Image is too large"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save folder icon", e)
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Remove the custom icon of [folderKey]
     */
    suspend fun remove(context: Context, rawFolderKey: String): Boolean = withContext(Dispatchers.IO) {
        val folderKey = normalizePath(rawFolderKey)
        val app = context.applicationContext
        val prefs = preferences(app)
        val old = prefs.getString(folderKey, null)
        val committed = prefs.edit().remove(folderKey).commit()
        if (!committed) return@withContext false
        if (old != null) {
            val file = File(directory(app), old)
            if (file.exists() && !file.delete()) return@withContext false
        }
        true
    }

    /**
     * Decode the image at [uri], scaled down to at most [MAX_SIZE]
     * (EXIF rotation and modern formats like HEIC are handled by [ImageDecoder] on Android 9+)
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(resolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // software, so that it can be compressed
                    val largest = maxOf(info.size.width, info.size.height)
                    if (largest > MAX_SIZE) {
                        val scale = MAX_SIZE.toFloat() / largest
                        decoder.setTargetSize(
                            maxOf(1, (info.size.width * scale).toInt()),
                            maxOf(1, (info.size.height * scale).toInt())
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ImageDecoder failed, fallback to BitmapFactory", e)
            }
        }

        // NOTICE: decodeStream() with inJustDecodeBounds always returns null, its result must not be used!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIZE && bounds.outHeight / (sample * 2) >= MAX_SIZE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * Make the same folder always have the same key, whether it comes from the media library
     * (`/Music/Rock`) or from the file explorer (`/storage/emulated/0/Music/Rock`)
     */
    fun normalizePath(path: String): String {
        val stripped = when {
            path.startsWith("/storage/emulated/0") -> path.removePrefix("/storage/emulated/0")
            path.startsWith("/storage/")           -> path.removePrefix("/storage")
            else                                   -> path
        }
        return if (stripped.length > 1) stripped.trimEnd('/') else stripped
    }

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
}

/**
 * Unique key of a folder: its full path, or its name if the path is unknown
 */
val SongCollection.folderKey: String get() = detail?.takeIf { it.isNotEmpty() } ?: name
