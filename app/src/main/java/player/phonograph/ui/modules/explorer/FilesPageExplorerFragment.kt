/*
 *  Copyright (c) 2022~2024 chr_56
 */

package player.phonograph.ui.modules.explorer

import com.github.chr56.android.menu_dsl.attach
import com.github.chr56.android.menu_dsl.menuItem
import player.phonograph.R
import player.phonograph.mechanism.folder.FolderIconStore
import player.phonograph.model.file.FileItem
import player.phonograph.settings.Keys
import player.phonograph.settings.SettingsObserver
import player.phonograph.ui.actions.ActionMenuProviders
import player.phonograph.ui.actions.ClickActionProviders
import player.phonograph.ui.modules.panel.PanelViewModel
import player.phonograph.ui.util.BottomViewWindowInsetsController
import player.phonograph.ui.util.applyControllableWindowInsetsAsBottomView
import player.phonograph.ui.util.observe
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FilesPageExplorerFragment : AbsFilesExplorerFragment() {

    override val allowMultiSelection: Boolean = true

    override fun createClickActionProvider(): ClickActionProviders.ClickActionProvider<FileItem> =
        FilesPageClickActionProvider(::onSwitch)

    override fun createMenuProvider(): ActionMenuProviders.ActionMenuProvider<FileItem>? =
        FolderIconMenuProvider(ActionMenuProviders.FileItemActionMenuProvider, ::requestChangeFolderIcon, ::resetFolderIcon)

    //region Folder icon
    private var pendingIconFolder: FileItem? = null

    private val pickFolderIcon =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val folder = pendingIconFolder
            pendingIconFolder = null
            if (uri != null && folder != null) applyFolderIcon(folder, uri)
        }

    private fun requestChangeFolderIcon(folder: FileItem) {
        pendingIconFolder = folder
        try {
            pickFolderIcon.launch("image/*")
        } catch (_: ActivityNotFoundException) {
            pendingIconFolder = null
            Toast.makeText(requireContext(), R.string.err_folder_icon_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFolderIcon(folder: FileItem, uri: Uri) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val failure = FolderIconStore.set(appContext, folder.path, uri)
            if (failure == null) {
                Toast.makeText(appContext, R.string.msg_folder_icon_changed, Toast.LENGTH_SHORT).show()
                refreshIcons()
            } else {
                val message = appContext.getString(R.string.err_folder_icon_failed) + "\n" + failure
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetFolderIcon(folder: FileItem) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            FolderIconStore.remove(appContext, folder.path)
            Toast.makeText(appContext, R.string.msg_folder_icon_reset, Toast.LENGTH_SHORT).show()
            refreshIcons()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshIcons() {
        if (isAdded && view != null) adapter.notifyDataSetChanged()
    }

    /**
     * Add "change/reset folder icon" to the menu of folders
     */
    private class FolderIconMenuProvider(
        private val delegate: ActionMenuProviders.ActionMenuProvider<FileItem>,
        private val onChangeIcon: (FileItem) -> Unit,
        private val onResetIcon: (FileItem) -> Unit,
    ) : ActionMenuProviders.ActionMenuProvider<FileItem> {
        override fun inflateMenu(menu: Menu, context: Context, item: FileItem, position: Int) {
            delegate.inflateMenu(menu, context, item, position)
            if (item.isFolder) {
                context.attach(menu) {
                    menuItem(title = context.getString(R.string.action_change_folder_icon)) {
                        showAsActionFlag = MenuItem.SHOW_AS_ACTION_NEVER
                        onClick { onChangeIcon(item); true }
                    }
                    if (FolderIconStore.get(context, item.path) != null) {
                        menuItem(title = context.getString(R.string.action_reset_folder_icon)) {
                            showAsActionFlag = MenuItem.SHOW_AS_ACTION_NEVER
                            onClick { onResetIcon(item); true }
                        }
                    }
                }
            }
        }
    }
    //endregion

    class FilesPageClickActionProvider(private val onSwitch: (FileItem) -> Unit) :
            ClickActionProviders.ClickActionProvider<FileItem> {

        private val provider = ClickActionProviders.FileEntityClickActionProvider()

        override fun listClick(
            list: List<FileItem>,
            position: Int,
            context: Context,
            imageView: ImageView?,
        ): Boolean {
            val item = list[position]
            when {
                item.isFolder -> onSwitch(item)
                else          -> provider.listClick(list, position, context, imageView)
            }
            return true
        }
    }

    //region WindowInsets
    private val panelViewModel: PanelViewModel by viewModels(ownerProducer = { requireActivity() })
    private lateinit var bottomViewWindowInsetsController: BottomViewWindowInsetsController
    //endregion

    private var jobs = mutableListOf<Job>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bottomViewWindowInsetsController = binding.recyclerView.applyControllableWindowInsetsAsBottomView()
        observe(panelViewModel.isMiniPlayerHidden) { hidden -> bottomViewWindowInsetsController.enabled = hidden }

        SettingsObserver(view.context, lifecycleScope).apply {
            jobs += collect(Keys.showFileImages) { value ->
                (adapter.presenter as? FileItemPresenter)?.loadCover = value
                @SuppressLint("NotifyDataSetChanged")
                adapter.notifyDataSetChanged()
            }
            jobs += collect(Keys.useLegacyListFilesImpl) { value ->
                model.optionUseLegacyListFile = value
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        for (it in jobs) {
            it.cancel()
        }
    }
}