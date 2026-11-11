/*
 *  Copyright (c) 2022~2025 chr_56
 */

package player.phonograph.mechanism.metadata.edit

import player.phonograph.App
import player.phonograph.R
import player.phonograph.foundation.error.warning
import player.phonograph.foundation.notification.Notifications
import player.phonograph.mechanism.coil.cache.CacheStore
import player.phonograph.mechanism.coil.cache.MemeryCacheInterceptor
import player.phonograph.mechanism.scanner.MediaStoreScanner
import player.phonograph.model.metadata.EditAction
import coil.Coil
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

abstract class AudioMetadataEditor(
    protected val songFiles: List<File>,
    protected val editRequest: List<EditAction>,
) {
    /**
     * execute [editRequest] on [songFiles]
     */
    suspend fun execute(context: Context) {
        if (editRequest.isEmpty()) return
        withContext(Dispatchers.Default) {
            val notifications = Notifications.BackgroundTasks.Default
            // notify user first
            notifications.post(
                context,
                title = App.instance.getString(R.string.action_tag_editor),
                msg = App.instance.getString(R.string.state_saving_changes),
                id = TAG_EDITOR_NOTIFICATION_CODE
            )
            // process
            val failedFiles = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                for (songFile in songFiles) {
                    if (songFile.canWrite()) {
                        if (!applyWithBackup(context, songFile)) failedFiles.add(songFile.name)
                    } else {
                        logs.add("Could not write file ${songFile.path}, please check file or storage permission")
                        failedFiles.add(songFile.name)
                    }
                }
            }
            // notify user
            notifications.cancel(context, TAG_EDITOR_NOTIFICATION_CODE)
            if (logs.isNotEmpty()) warning(context, LOG_TAG, logs.joinToString(separator = "\n"))
            // Covers/tags changed: drop cached images, otherwise old covers keep being displayed
            invalidateImageCaches(context)
            if (failedFiles.isNotEmpty()) reportFailure(context, failedFiles)
            yield()
            // refresh media store
            val paths = songFiles.map { it.path }.toTypedArray()
            MediaStoreScanner(context).scan(paths)
        }
    }

    /**
     * actual execute [editRequest] on [songFiles]
     * @return true if all changes are written (committed) into the file
     */
    abstract fun applyEditActions(file: File, requests: List<EditAction>): Boolean

    /**
     * Edit [file] with a safety copy: if writing fails half way (the file may be corrupted),
     * the original content is restored.
     * @return true if succeeded
     */
    private fun applyWithBackup(context: Context, file: File): Boolean {
        val backup = createBackup(context, file)
        val succeeded = try {
            applyEditActions(file, editRequest)
        } catch (e: Throwable) {
            logs.add("Unexpected error while editing ${file.path}: ${e.javaClass.name}: ${e.message}")
            false
        }
        if (!succeeded && backup != null) restoreBackup(file, backup)
        backup?.delete()
        return succeeded
    }

    private fun createBackup(context: Context, file: File): File? = try {
        val dir = File(context.cacheDir, "tag_backup").also { it.mkdirs() }
        if (dir.usableSpace < file.length() * 2) {
            logs.add("Not enough space to back up ${file.name}, edit without backup")
            null
        } else {
            File(dir, "${System.currentTimeMillis()}_${file.name}").also { file.copyTo(it, overwrite = true) }
        }
    } catch (e: Exception) {
        logs.add("Failed to back up ${file.name}: ${e.message}")
        null
    }

    private fun restoreBackup(file: File, backup: File) {
        try {
            // only if the file was actually touched
            if (file.length() != backup.length() || file.lastModified() != backup.lastModified()) {
                backup.copyTo(file, overwrite = true)
                logs.add("${file.name} was restored from backup after a failed write")
            }
        } catch (e: Exception) {
            logs.add("Failed to restore ${file.name} from backup: ${e.message}")
        }
    }

    private suspend fun reportFailure(context: Context, files: List<String>) = withContext(Dispatchers.Main) {
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.err_tag_save_failed, files.size, files.first()),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun invalidateImageCaches(context: Context) {
        MemeryCacheInterceptor.clear()
        Coil.imageLoader(context).memoryCache?.clear()
        CacheStore.clear(context.applicationContext) // persistent image cache (also "no image" marks)
    }

    protected val logs: MutableList<String> = mutableListOf()

    companion object{
        private const val LOG_TAG = "AudioMetadataEditor"

        private const val TAG_EDITOR_NOTIFICATION_CODE = 824_3348
    }
}