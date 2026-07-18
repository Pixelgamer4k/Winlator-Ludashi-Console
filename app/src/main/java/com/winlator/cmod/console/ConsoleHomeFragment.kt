package com.winlator.cmod.console

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.winlator.cmod.MainActivity
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.XrActivity
import com.winlator.cmod.console.compat.CompatApplier
import com.winlator.cmod.console.compat.LaunchFailureClassifier
import com.winlator.cmod.console.compat.SessionExit
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.box64.Box64Preset
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen Console app host: library home + modern System screens.
 */
class ConsoleHomeFragment : Fragment() {

    private lateinit var containerManager: ContainerManager
    private val games = mutableStateListOf<ConsoleGameItem>()
    private var settingUp by mutableStateOf(false)
    private var setupMessage by mutableStateOf("Setting up your console…")
    private var importing by mutableStateOf(false)
    private var importMessage by mutableStateOf("Adding game…")
    private var browsingFiles by mutableStateOf(false)
    private var menuOpen by mutableStateOf(false)
    private var reopenSystemMenu by mutableStateOf(false)
    private var destination by mutableStateOf(ConsoleDestination.HOME)
    private var editingContainerId by mutableStateOf<Int?>(null)
    private var menuNavJob: Job? = null

    /** Child screens (sheets, file browser folders) register here to consume back first. */
    @Volatile
    private var nestedBackHandler: (() -> Boolean)? = null

    private val dispatcherBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (!handleBackPress()) {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = syncBackCallbackEnabled()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        containerManager = ContainerManager(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, dispatcherBackCallback)
        syncBackCallbackEnabled()
    }

    /**
     * Central back navigation for the console stack.
     * System destinations return to the side panel (not a bare library).
     * @return true if consumed; false if the activity should exit.
     */
    fun handleBackPress(): Boolean {
        nestedBackHandler?.let { handler ->
            if (handler()) {
                syncBackCallbackEnabled()
                return true
            }
        }
        if (browsingFiles) {
            browsingFiles = false
            syncBackCallbackEnabled()
            return true
        }
        if (menuOpen) {
            menuOpen = false
            syncBackCallbackEnabled()
            return true
        }
        return when (destination) {
            ConsoleDestination.HOME -> false
            ConsoleDestination.CONTAINER_EDITOR -> {
                editingContainerId = null
                destination = ConsoleDestination.CONTAINERS
                syncBackCallbackEnabled()
                true
            }
            else -> {
                backToSystemMenu()
                true
            }
        }
    }

    /** Leave a System screen and reopen the side panel on Library. */
    private fun backToSystemMenu() {
        editingContainerId = null
        reopenSystemMenu = true
        destination = ConsoleDestination.HOME
        menuOpen = false
        syncBackCallbackEnabled()
    }

    private fun bindNestedBack(handler: (() -> Boolean)?) {
        nestedBackHandler = handler
        syncBackCallbackEnabled()
    }

    private fun syncBackCallbackEnabled(): Boolean {
        val enabled = browsingFiles ||
            menuOpen ||
            destination != ConsoleDestination.HOME ||
            nestedBackHandler != null
        dispatcherBackCallback.isEnabled = enabled
        return enabled
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        hideChrome(true)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LaunchedEffect(destination, browsingFiles, menuOpen) {
                    syncBackCallbackEnabled()
                }
                LaunchedEffect(destination, reopenSystemMenu) {
                    if (destination == ConsoleDestination.HOME && reopenSystemMenu) {
                        reopenSystemMenu = false
                        // Let the pop transition start, then slide the System panel in.
                        kotlinx.coroutines.delay(220)
                        menuOpen = true
                        syncBackCallbackEnabled()
                    }
                }
                DisposableEffect(destination) {
                    bindNestedBack(null)
                    onDispose { }
                }

                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val poppingToHome = targetState == ConsoleDestination.HOME
                        val poppingEditor =
                            initialState == ConsoleDestination.CONTAINER_EDITOR &&
                                targetState == ConsoleDestination.CONTAINERS
                        if (poppingToHome || poppingEditor) {
                            consoleScreenPopTransform()
                        } else {
                            consoleScreenPushTransform()
                        }
                    },
                    label = "consoleDestination",
                ) { dest ->
                    ConsoleDisableOverscroll {
                    when (dest) {
                        ConsoleDestination.HOME -> ConsoleHomeScreen(
                            games = games.toList(),
                            settingUp = settingUp,
                            setupMessage = setupMessage,
                            importing = importing,
                            importMessage = importMessage,
                            browsingFiles = browsingFiles,
                            menuOpen = menuOpen,
                            onOpenMenu = {
                                menuOpen = true
                                syncBackCallbackEnabled()
                            },
                            onCloseMenu = {
                                menuOpen = false
                                syncBackCallbackEnabled()
                            },
                            onMenuAction = { action -> handleMenu(action) },
                            onAddGame = { startAddGame() },
                            onCancelBrowse = {
                                browsingFiles = false
                                syncBackCallbackEnabled()
                            },
                            onPickExe = { file -> importExe(file) },
                            onPlay = { play(it) },
                            onLongPress = { confirmRemove(it) },
                            onBindNestedBack = { bindNestedBack(it) },
                        )
                        ConsoleDestination.CONTAINERS -> ConsoleContainersScreen(
                            onBack = { backToSystemMenu() },
                            onOpenEditor = { id ->
                                editingContainerId = id
                                destination = ConsoleDestination.CONTAINER_EDITOR
                                syncBackCallbackEnabled()
                            },
                            onBindNestedBack = { bindNestedBack(it) },
                        )
                        ConsoleDestination.CONTAINER_EDITOR -> ConsoleContainerEditorScreen(
                            containerId = editingContainerId,
                            onBack = {
                                editingContainerId = null
                                destination = ConsoleDestination.CONTAINERS
                                syncBackCallbackEnabled()
                            },
                        )
                        ConsoleDestination.FILES -> {
                            GameFileBrowser(
                                onCancel = { backToSystemMenu() },
                                onPickExe = { file ->
                                    destination = ConsoleDestination.HOME
                                    menuOpen = false
                                    syncBackCallbackEnabled()
                                    importExe(file)
                                },
                                onBindNestedBack = { bindNestedBack(it) },
                            )
                        }
                        ConsoleDestination.CONTROLS -> ConsoleChassisScreen(
                            onBack = { backToSystemMenu() },
                        )
                        ConsoleDestination.SETTINGS -> ConsoleSettingsScreen(
                            onBack = { backToSystemMenu() },
                        )
                        ConsoleDestination.ABOUT -> ConsoleAboutScreen(
                            onBack = { backToSystemMenu() },
                        )
                        ConsoleDestination.SHORTCUTS -> ConsoleShortcutsScreen(
                            onBack = { backToSystemMenu() },
                        )
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideChrome(true)
        (activity as? AppCompatActivity)?.let { ConsoleWindowChrome.enter(it) }
        ensureReadyThenLoad()
        maybeShowSessionExit()
        syncBackCallbackEnabled()
    }

    override fun onDestroyView() {
        // Keep edge-to-edge if another console-era screen replaces us (container detail).
        // Only exit when activity is finishing.
        if (activity?.isFinishing == true) {
            (activity as? AppCompatActivity)?.let { ConsoleWindowChrome.exit(it) }
            hideChrome(false)
        }
        super.onDestroyView()
    }

    private fun hideChrome(hide: Boolean) {
        val act = activity as? AppCompatActivity ?: return
        if (hide) {
            act.supportActionBar?.hide()
            (act as? MainActivity)?.setDrawerLocked(true)
        } else {
            act.supportActionBar?.show()
            (act as? MainActivity)?.setDrawerLocked(false)
        }
    }

    private fun maybeShowSessionExit() {
        val pending = SessionExit.consume(requireContext()) ?: return
        if (!pending.isError) return

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(pending.title)
            .setMessage(pending.message)
            .setNegativeButton("OK", null)

        when (pending.retryAction) {
            LaunchFailureClassifier.RetryAction.NONE -> Unit
            else -> {
                val label = when (pending.retryAction) {
                    LaunchFailureClassifier.RetryAction.FORCE_BOX64 -> "Retry with Box64"
                    LaunchFailureClassifier.RetryAction.FORCE_UNITY_D3D11 -> "Retry with Unity fix"
                    LaunchFailureClassifier.RetryAction.FORCE_CRT_BUILTIN -> "Retry with CRT fix"
                    else -> "Retry"
                }
                builder.setPositiveButton(label) { _, _ ->
                    applyRetryAndPlay(pending)
                }
            }
        }
        builder.show()
    }

    private fun applyRetryAndPlay(pending: SessionExit.Pending) {
        val path = pending.shortcutPath ?: return
        val container = containerManager.getContainerById(pending.containerId)
            ?: EasySetup.primaryContainer(containerManager)
            ?: run {
                Toast.makeText(requireContext(), "Container missing", Toast.LENGTH_SHORT).show()
                return
            }
        val desktop = File(path)
        if (!desktop.isFile) {
            Toast.makeText(requireContext(), "Shortcut missing", Toast.LENGTH_SHORT).show()
            return
        }
        val shortcut = Shortcut(container, desktop)
        when (pending.retryAction) {
            LaunchFailureClassifier.RetryAction.FORCE_BOX64 -> {
                shortcut.putExtra("emulator", "Box64")
                shortcut.putExtra("box64Preset", Box64Preset.COMPATIBILITY)
            }
            LaunchFailureClassifier.RetryAction.FORCE_CRT_BUILTIN -> {
                shortcut.putExtra("emulator", "Box64")
                shortcut.putExtra("box64Preset", Box64Preset.COMPATIBILITY)
                val existing = shortcut.getExtra("envVars")
                val crt = "WINEDLLOVERRIDES=msvcp100,msvcr100=builtin"
                shortcut.putExtra(
                    "envVars",
                    if (existing.isBlank()) crt
                    else if (existing.contains("WINEDLLOVERRIDES=")) existing
                    else "$existing $crt",
                )
            }
            LaunchFailureClassifier.RetryAction.FORCE_UNITY_D3D11 -> {
                shortcut.putExtra("emulator", "Box64")
                shortcut.putExtra("box64Preset", Box64Preset.COMPATIBILITY)
                val args = shortcut.getExtra("execArgs")
                if (!args.contains("-force-d3d11", ignoreCase = true)) {
                    shortcut.putExtra("execArgs", (args.trim() + " -force-d3d11").trim())
                }
                if (shortcut.getExtra("dxwrapper").isEmpty()) {
                    shortcut.putExtra("dxwrapper", "dxvk+vkd3d")
                }
            }
            LaunchFailureClassifier.RetryAction.NONE -> Unit
        }
        shortcut.saveData()
        CompatApplier.ensureMigrated(shortcut)
        play(
            ConsoleGameItem(
                id = shortcut.file.absolutePath,
                name = shortcut.name,
                icon = shortcut.icon,
                shortcut = Shortcut(container, desktop),
            ),
        )
    }

    private fun ensureReadyThenLoad() {
        val existing = EasySetup.primaryContainer(containerManager)
        if (existing != null) {
            settingUp = false
            reloadGames()
            return
        }
        settingUp = true
        setupMessage = "Setting up your console…"
        EasySetup.ensureContainer(
            requireContext(),
            containerManager,
            onProgress = { msg -> activity?.runOnUiThread { setupMessage = msg } },
            onDone = { created ->
                activity?.runOnUiThread {
                    settingUp = false
                    if (created == null) {
                        Toast.makeText(requireContext(), "Could not set up console. Check Settings.", Toast.LENGTH_LONG).show()
                    }
                    containerManager = ContainerManager(requireContext())
                    reloadGames()
                }
            },
        )
    }

    private fun reloadGames() {
        Executors.newSingleThreadExecutor().execute {
            val list = containerManager.loadShortcuts().map { sc ->
                ConsoleGameItem(
                    id = sc.file.absolutePath,
                    name = sc.name,
                    icon = sc.icon,
                    shortcut = sc,
                )
            }
            activity?.runOnUiThread {
                games.clear()
                games.addAll(list)
            }
        }
    }

    private fun startAddGame() {
        val container = EasySetup.primaryContainer(containerManager)
        if (container == null) {
            settingUp = true
            EasySetup.ensureContainer(requireContext(), containerManager, onDone = { created ->
                activity?.runOnUiThread {
                    settingUp = false
                    containerManager = ContainerManager(requireContext())
                    if (created != null) {
                        browsingFiles = true
                    } else {
                        Toast.makeText(requireContext(), "Create a container first (System → Containers)", Toast.LENGTH_LONG).show()
                    }
                }
            })
            return
        }
        browsingFiles = true
    }

    private fun importExe(file: File) {
        val container = EasySetup.primaryContainer(containerManager) ?: run {
            Toast.makeText(requireContext(), "No container ready", Toast.LENGTH_SHORT).show()
            return
        }
        browsingFiles = false
        importing = true
        importMessage = "Adding ${file.name}…"
        Executors.newSingleThreadExecutor().execute {
            val result = ShortcutImporter.importFromFile(
                requireContext(),
                file,
                container,
                onProgress = { msg ->
                    activity?.runOnUiThread { importMessage = msg }
                },
            )
            activity?.runOnUiThread {
                importing = false
                Toast.makeText(
                    requireContext(),
                    result.message,
                    if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                ).show()
                if (result.success) reloadGames()
            }
        }
    }

    private fun play(item: ConsoleGameItem) {
        val shortcut = item.shortcut
        val act = activity ?: return
        if (!XrActivity.isEnabled(requireContext())) {
            val intent = Intent(act, XServerDisplayActivity::class.java)
            intent.putExtra("container_id", shortcut.container.id)
            intent.putExtra("shortcut_path", shortcut.file.path)
            intent.putExtra("shortcut_name", shortcut.name)
            intent.putExtra("disableXinput", shortcut.getExtra("disableXinput", "0"))
            intent.putExtra("native_rendering", shortcut.rendererNative)
            startActivity(intent)
        } else {
            XrActivity.openIntent(act, shortcut.container.id, shortcut.file.path)
        }
    }

    private fun confirmRemove(item: ConsoleGameItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(item.name)
            .setItems(arrayOf("Remove from library", "Cancel")) { _, which ->
                if (which == 0) {
                    item.shortcut.file.delete()
                    reloadGames()
                    Toast.makeText(requireContext(), "Removed", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun handleMenu(action: ConsoleMenuAction) {
        menuNavJob?.cancel()
        menuOpen = false
        syncBackCallbackEnabled()
        // Let the System panel finish its iOS slide-out, then push the destination.
        menuNavJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(340)
            if (!isAdded) return@launch
            destination = when (action) {
                ConsoleMenuAction.CONTAINERS -> ConsoleDestination.CONTAINERS
                ConsoleMenuAction.FILE_MANAGER -> ConsoleDestination.FILES
                ConsoleMenuAction.INPUT_CONTROLS -> ConsoleDestination.CONTROLS
                ConsoleMenuAction.SETTINGS -> ConsoleDestination.SETTINGS
                ConsoleMenuAction.SHORTCUTS -> ConsoleDestination.SHORTCUTS
                ConsoleMenuAction.ABOUT -> ConsoleDestination.ABOUT
            }
            syncBackCallbackEnabled()
        }
    }
}
