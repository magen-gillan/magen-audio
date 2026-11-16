/*
 *  Copyright (c) 2022~2026 chr_56
 */

package player.phonograph.mechanism.folder

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderIconStoreTest {

    @Test
    fun primaryStoragePrefixIsRemoved() {
        assertEquals("/Music/Rock", FolderIconStore.normalizePath("/storage/emulated/0/Music/Rock"))
    }

    @Test
    fun trailingSlashIsRemoved() {
        assertEquals("/Music/Rock", FolderIconStore.normalizePath("/Music/Rock/"))
    }

    @Test
    fun mediastorePathAndExplorerPathMatch() {
        assertEquals(
            FolderIconStore.normalizePath("/Music/Rock"),
            FolderIconStore.normalizePath("/storage/emulated/0/Music/Rock/"),
        )
    }

    @Test
    fun otherEmulatedUserIsNotPrimaryStorage() {
        assertEquals("/emulated/01/Music", FolderIconStore.normalizePath("/storage/emulated/01/Music"))
    }

    @Test
    fun removableStorageKeepsItsVolumeId() {
        assertEquals("/ABCD-1234/Music", FolderIconStore.normalizePath("/storage/ABCD-1234/Music"))
    }
}
