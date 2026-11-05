/*
 *  Copyright (c) 2022~2024 chr_56
 */

package player.phonograph.ui.modules.main.pages

import com.github.chr56.android.menu_model.MenuContext
import player.phonograph.R
import player.phonograph.foundation.mediastore.naturalCompare
import player.phonograph.mechanism.coil.loadImage
import player.phonograph.mechanism.folder.FolderIconStore
import player.phonograph.mechanism.folder.folderKey
import player.phonograph.model.Song
import player.phonograph.model.SongCollection
import player.phonograph.model.service.ShuffleMode
import player.phonograph.model.sort.SortMode
import player.phonograph.model.sort.SortRef
import player.phonograph.model.ui.ItemLayoutStyle
import player.phonograph.repo.mediastore.MediaStoreSongCollections
import player.phonograph.settings.Keys
import player.phonograph.settings.Settings
import player.phonograph.ui.actions.ActionMenuProviders
import player.phonograph.ui.actions.ClickActionProviders
import player.phonograph.ui.actions.actionPlay
import player.phonograph.ui.adapter.DisplayAdapter
import player.phonograph.ui.adapter.DisplayPresenter
import player.phonograph.ui.adapter.SongBasicDisplayPresenter
import player.phonograph.ui.adapter.SongCollectionBasicDisplayPresenter
import player.phonograph.ui.modules.panel.PanelViewModel
import player.phonograph.ui.theme.ThemeSettingsDelegate.accentColor
import player.phonograph.ui.theme.getTintedDrawable
import player.phonograph.ui.theme.setUpFastScrollRecyclerViewColor
import player.phonograph.ui.theme.themeIconColor
import player.phonograph.ui.util.BottomViewWindowInsetsController
import player.phonograph.ui.util.applyControllableWindowInsetsAsBottomView
import player.phonograph.ui.util.observe
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FoldersPage : AbsPanelPage() {

    //region Content

    private val viewModel: FoldersPageViewModel by viewModels()

    override fun onPrefetchContentDataset() {
        viewModel.loadFolders(requireContext(), true)
    }

    override fun onContentChanged() {
        viewModel.loadFolders(requireContext(), false)
    }

    override fun prepareContentView() {
        prepareRecyclerView()
        prepareAdaptersAndData()

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                navigateUpBackPressedCallback.remove()
            }

            override fun onResume(owner: LifecycleOwner) {
                updateNavigateUpBackPressedCallback(viewModel.mainViewMode.value)
            }
        })
    }


    private lateinit var songCollectionDisplayAdapter: DisplayAdapter<SongCollection>
    private lateinit var songAdapter: DisplayAdapter<Song>
    private lateinit var layoutManager: GridLayoutManager

    private val panelViewModel: PanelViewModel by viewModels(ownerProducer = { requireActivity() })
    private lateinit var bottomViewWindowInsetsController: BottomViewWindowInsetsController


    private fun prepareRecyclerView() {
        layoutManager = GridLayoutManager(requireContext(), folderPageDisplayConfig.gridSize)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.setUpFastScrollRecyclerViewColor(requireContext(), accentColor())

        binding.mainContentContainer.apply {
            setColorSchemeColors(accentColor())
            setDistanceToTriggerSync(480)
            setProgressViewOffset(false, 10, 120)
            setOnRefreshListener {
                viewModel.loadFolders(requireContext(), false)
                isRefreshing = false
            }
        }

        bottomViewWindowInsetsController = binding.recyclerView.applyControllableWindowInsetsAsBottomView()
        observe(panelViewModel.isMiniPlayerHidden) { hidden -> bottomViewWindowInsetsController.enabled = hidden }
    }

    private fun prepareAdaptersAndData() {

        songCollectionDisplayAdapter = DisplayAdapter(
            activity = requireActivity(),
            presenter = buildFolderDisplayPresenter(),
            // Pass folder icon callbacks so the multi-selection menu can offer
            // "Change folder icon" / "Reset folder icon" when a folder is selected.
            onChangeFolderIcon = ::requestChangeFolderIcon,
            onResetFolderIcon = ::resetFolderIcon,
        )

        songAdapter = DisplayAdapter(requireActivity(), buildSongDisplayPresenter())
        binding.recyclerView.adapter = songCollectionDisplayAdapter

        observe(viewLifecycleOwner.lifecycle, viewModel.folders) { data ->

            if (data.isEmpty()) {
                statusFragment.show(R.string.msg_empty)
            } else {
                statusFragment.hide()
            }

            songCollectionDisplayAdapter.dataset = data

        }
        observe(viewLifecycleOwner.lifecycle, viewModel.currentSongs) { data ->
            songAdapter.dataset = data
        }
        observe(viewLifecycleOwner.lifecycle, viewModel.mainViewMode) { mode ->
            updateNavigateUpBackPressedCallback(mode)
            binding.recyclerView.adapter = if (mode) songCollectionDisplayAdapter else songAdapter
            layoutManager.spanCount = if (mode) folderPageDisplayConfig.gridSize else songPageDisplayConfig.gridSize
            layoutManager.scrollToPosition(if (mode) viewModel.historyFolderPosition else viewModel.historyPosition)
        }
    }

    //endregion




    //region Popup

    private val folderPageDisplayConfig: PageDisplayConfig get() = FolderPageDisplayConfig(requireContext())
    private val songPageDisplayConfig: PageDisplayConfig get() = FolderSongsPageDisplayConfig(requireContext())

    override val displayConfig: PageDisplayConfig
        get() = if (viewModel.mainViewMode.value) folderPageDisplayConfig else songPageDisplayConfig

    override fun updateContentSetting(
        sortMode: SortMode,
        layout: ItemLayoutStyle,
        gridSize: Int,
        coloredFooter: Boolean,
        shouldRecreate: Boolean,
        shouldReload: Boolean,
    ) {
        if (viewModel.mainViewMode.value) {
            if (shouldReload) {
                viewModel.loadFolders(requireContext(), true)
            }
            val presenter = buildFolderDisplayPresenter()
            if (shouldRecreate) {
                songCollectionDisplayAdapter = DisplayAdapter(requireActivity(), presenter)
                songCollectionDisplayAdapter.dataset = viewModel.folders.value
                binding.recyclerView.adapter = songCollectionDisplayAdapter
            } else {
                songCollectionDisplayAdapter.presenter = presenter
            }
        } else {
            if (shouldReload) {
                // songs are sorted again from the already loaded folders
                viewModel.loadSongs(requireContext(), true)
            }
            val presenter = buildSongDisplayPresenter()
            if (shouldRecreate) {
                songAdapter = DisplayAdapter(requireActivity(), presenter)
                songAdapter.dataset = viewModel.currentSongs.value
                binding.recyclerView.adapter = songAdapter
            } else {
                songAdapter.presenter = presenter
            }
        }
        layoutManager.spanCount = gridSize
    }
    //endregion

    //region Navigation
    private fun updateNavigateUpBackPressedCallback(mainViewMode: Boolean) {
        if (mainViewMode || !isVisible) {
            navigateUpBackPressedCallback.remove()
        } else {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                navigateUpBackPressedCallback
            )
        }
    }

    private val navigateUpBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            navigateUp()
        }
    }

    private fun navigateUp(): Boolean =
        viewModel.navigateUp(requireContext(), layoutManager.findFirstVisibleItemPosition())
    //endregion


    //region Folder icon
    private val pickFolderIcon =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            val folder = viewModel.pendingIconFolder
            viewModel.pendingIconFolder = null
            if (uri != null && folder != null) applyFolderIcon(folder, uri)
        }

    private fun requestChangeFolderIcon(folder: SongCollection) {
        viewModel.pendingIconFolder = folder
        try {
            pickFolderIcon.launch("image/*")
        } catch (_: android.content.ActivityNotFoundException) {
            viewModel.pendingIconFolder = null
            Toast.makeText(requireContext(), R.string.err_folder_icon_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFolderIcon(folder: SongCollection, uri: Uri) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            val failure = FolderIconStore.set(appContext, folder.folderKey, uri)
            if (failure == null) {
                Toast.makeText(appContext, R.string.msg_folder_icon_changed, Toast.LENGTH_SHORT).show()
                refreshFolderIcons()
            } else {
                val message = appContext.getString(R.string.err_folder_icon_failed) + "\n" + failure
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetFolderIcon(folder: SongCollection) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            FolderIconStore.remove(appContext, folder.folderKey)
            Toast.makeText(appContext, R.string.msg_folder_icon_reset, Toast.LENGTH_SHORT).show()
            refreshFolderIcons()
        }
    }

    private fun refreshFolderIcons() {
        if (::songCollectionDisplayAdapter.isInitialized) {
            // Create a NEW list instance so the adapter's dataset setter
            // detects the change and calls notifyDataSetChanged().
            // Also, reloading from viewModel ensures any folderKey changes
            // are picked up. Using toList() creates a new list reference.
            songCollectionDisplayAdapter.dataset = viewModel.folders.value.toList()
            // Force image reload by invalidating Coil cache for folder items
            coil.Coil.imageLoader(requireContext()).memoryCache?.clear()
        }
    }
    //endregion


    //region Presenters

    private fun buildSongDisplayPresenter(): DisplayPresenter<Song> =
        with(songPageDisplayConfig) { EmbedSongDisplayPresenter(sortMode, colorFooter, layout) }

    private class EmbedSongDisplayPresenter(
        sortMode: SortMode,
        override val usePalette: Boolean,
        override val layoutStyle: ItemLayoutStyle,
    ) : SongBasicDisplayPresenter(sortMode) {
        override val imageType: Int = DisplayPresenter.IMAGE_TYPE_IMAGE
    }

    private fun buildFolderDisplayPresenter(): DisplayPresenter<SongCollection> =
        FolderDisplayPresenter(
            sortMode = folderPageDisplayConfig.sortMode,
            layoutStyle = folderPageDisplayConfig.layout,
            onClick = { context, folder ->
                // the scroll position is read when the user clicks (not when the presenter is built)
                viewModel.browseFolder(context, folder, true, layoutManager.findFirstVisibleItemPosition())
                true
            },
            onChangeIcon = ::requestChangeFolderIcon,
            onResetIcon = ::resetFolderIcon,
        )

    private class FolderDisplayPresenter(
        sortMode: SortMode,
        override val layoutStyle: ItemLayoutStyle,
        val onClick: (Context, SongCollection) -> Boolean,
        onChangeIcon: (SongCollection) -> Unit,
        onResetIcon: (SongCollection) -> Unit,
    ) : SongCollectionBasicDisplayPresenter(sortMode) {
        override val usePalette: Boolean = false
        override val imageType: Int = DisplayPresenter.IMAGE_TYPE_IMAGE

        // Show ONLY the folder name — no song count, no path, in any layout style.
        override fun getDescription(context: Context, item: SongCollection): CharSequence = ""
        override fun getSecondaryText(context: Context, item: SongCollection): CharSequence = ""
        override fun getTertiaryText(context: Context, item: SongCollection): CharSequence? = null

        // stable and cheap (the default one hashes every song in the folder)
        override fun getItemID(item: SongCollection): Long = item.folderKey.hashCode().toLong()

        override fun startLoadingImage(
            context: Context,
            item: SongCollection,
            target: coil.target.Target,
        ): coil.request.Disposable? {
            val defaultIcon = context.getTintedDrawable(R.drawable.ic_folder_white_24dp, themeIconColor(context))
            val custom = FolderIconStore.get(context, item)
            return if (custom != null) {
                loadImage(context).from(custom).default(defaultIcon).into(target).enqueue()
            } else {
                target.onSuccess(defaultIcon!!)
                null
            }
        }

        override val clickActionProvider: ClickActionProviders.ClickActionProvider<SongCollection> =
            object : ClickActionProviders.ClickActionProvider<SongCollection> {
                override fun listClick(
                    list: List<SongCollection>,
                    position: Int,
                    context: Context,
                    imageView: ImageView?,
                ): Boolean = onClick.invoke(context, list[position])
            }

        override val menuProvider: ActionMenuProviders.ActionMenuProvider<SongCollection> =
            ActionMenuProviders.SongCollectionActionMenuProvider(
                onChangeIcon = onChangeIcon,
                onResetIcon = onResetIcon,
                hasCustomIcon = { context, folder -> FolderIconStore.get(context, folder) != null },
            )
    }
    //endregion

    class FoldersPageViewModel : ViewModel() {

        /**
         * true if browsing folders
         */
        private val _mainViewMode: MutableStateFlow<Boolean> = MutableStateFlow(true)
        val mainViewMode = _mainViewMode.asStateFlow()

        private val _folders: MutableStateFlow<List<SongCollection>> = MutableStateFlow(emptyList())
        val folders = _folders.asStateFlow()

        private val _currentSongs: MutableStateFlow<List<Song>> = MutableStateFlow(emptyList())
        val currentSongs = _currentSongs.asStateFlow()

        /**
         * the opened folder is identified by its path rather than by its position,
         * so it stays correct when the folder list is reloaded or re-sorted
         */
        private var _currentFolderKey: String? = null
        val currentFolder: SongCollection?
            get() = _currentFolderKey?.let { key -> _folders.value.firstOrNull { it.folderKey == key } }

        /**
         * folder that is waiting for an image from the picker
         */
        var pendingIconFolder: SongCollection? = null

        private var _bannerText: MutableStateFlow<String> = MutableStateFlow("")
        val bannerText = _bannerText.asStateFlow()

        var historyFolderPosition: Int = 0
            private set
        private val historyPositions: MutableMap<String, Int> = HashMap()
        val historyPosition: Int get() = _currentFolderKey?.let { historyPositions[it] } ?: 0

        fun updateBannerText(context: Context, mode: Boolean) {
            _bannerText.tryEmit(
                if (mode) {
                    context.resources.getQuantityString(
                        R.plurals.item_files, folders.value.size, folders.value.size
                    )
                } else {
                    context.resources.getQuantityString(
                        R.plurals.item_songs, currentSongs.value.size, currentSongs.value.size
                    )
                }
            )
        }

        private var loadFoldersJob: Job? = null

        /**
         * update folders (and the songs of the opened folder, if any)
         */
        fun loadFolders(context: Context, updateBanner: Boolean) {
            loadFoldersJob?.cancel()
            loadFoldersJob = viewModelScope.launch(Dispatchers.IO) {
                val loaded = sortFolders(context, MediaStoreSongCollections.all(context = context))
                _folders.emit(loaded)
                if (!_mainViewMode.value) {
                    if (currentFolder == null) {
                        // the opened folder does not exist anymore (e.g. all its songs were deleted/moved)
                        _currentFolderKey = null
                        _currentSongs.emit(emptyList())
                        _mainViewMode.emit(true)
                    } else {
                        publishSongs(context)
                    }
                }
                if (updateBanner) updateBannerText(context, _mainViewMode.value)
            }
        }

        /**
         * browse [folder]
         */
        fun browseFolder(context: Context, folder: SongCollection, updateBanner: Boolean, lastScrollPosition: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                historyFolderPosition = lastScrollPosition
                _currentFolderKey = folder.folderKey
                publishSongs(context)               // songs are ready before switching the view
                if (updateBanner) updateBannerText(context, false)
                _mainViewMode.emit(false)
            }
        }

        /**
         * update (and re-sort) songs of the opened folder
         */
        fun loadSongs(context: Context, updateBanner: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                publishSongs(context)
                if (updateBanner) updateBannerText(context, false)
            }
        }

        private suspend fun publishSongs(context: Context) {
            val songs = currentFolder?.songs.orEmpty()
            _currentSongs.emit(sortSongs(context, songs))
        }

        /**
         * try to get back to upper level
         * @return reached top
         */
        fun navigateUp(context: Context, lastScrollPosition: Int): Boolean =
            if (mainViewMode.value) {
                true
            } else {
                _currentFolderKey?.let { historyPositions[it] = lastScrollPosition }
                _mainViewMode.value = true
                _currentFolderKey = null
                _currentSongs.tryEmit(emptyList())
                updateBannerText(context, true)
                false
            }

        private fun sortFolders(context: Context, folders: List<SongCollection>): List<SongCollection> {
            val mode = Settings(context)[Keys.collectionSortMode].data
            val byName = Comparator<SongCollection> { a, b -> naturalCompare(a.name, b.name) }
            val byKey = Comparator<SongCollection> { a, b -> a.folderKey.compareTo(b.folderKey) }
            val comparator: Comparator<SongCollection> = when (mode.sortRef) {
                SortRef.SONG_COUNT -> Comparator<SongCollection> { a, b -> a.songs.size.compareTo(b.songs.size) }
                    .thenComparing(byName).thenComparing(byKey)
                else               -> byName.thenComparing(byKey)
            }
            return folders.sortedWith(if (mode.revert) comparator.reversed() else comparator)
        }

        /**
         * Sort songs by user's song sort mode.
         * (songs of a folder are grouped from the whole library, so they have to be sorted explicitly)
         */
        private fun sortSongs(context: Context, songs: List<Song>): List<Song> {
            if (songs.size < 2) return songs
            val mode = Settings(context)[Keys.songSortMode].data
            val text: (String?) -> String = { it.orEmpty() }
            val comparator: Comparator<Song> = when (mode.sortRef) {
                SortRef.SONG_NAME         -> Comparator<Song> { a, b -> naturalCompare(a.title, b.title) }
                SortRef.ALBUM_NAME        -> Comparator<Song> { a, b -> naturalCompare(text(a.albumName), text(b.albumName)) }
                    .thenComparingInt { it.trackNumber }
                SortRef.ARTIST_NAME       -> Comparator<Song> { a, b -> naturalCompare(text(a.artistName), text(b.artistName)) }
                SortRef.ALBUM_ARTIST_NAME -> Comparator<Song> { a, b -> naturalCompare(text(a.albumArtistName), text(b.albumArtistName)) }
                SortRef.COMPOSER          -> Comparator<Song> { a, b -> naturalCompare(text(a.composer), text(b.composer)) }
                SortRef.YEAR              -> compareBy<Song> { it.year }
                SortRef.ADDED_DATE        -> compareBy<Song> { it.dateAdded }
                SortRef.MODIFIED_DATE     -> compareBy<Song> { it.dateModified }
                SortRef.DURATION          -> compareBy<Song> { it.duration }
                else                      -> return songs
            }
            val withTieBreak = comparator.thenComparing(Comparator<Song> { a, b -> naturalCompare(a.title, b.title) })
            return songs.sortedWith(if (mode.revert) withTieBreak.reversed() else withTieBreak)
        }
    }
}
