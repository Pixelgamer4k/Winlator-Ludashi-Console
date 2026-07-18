package com.winlator.cmod;

import static com.winlator.cmod.core.AppUtils.showToast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Rational;
import android.util.TypedValue;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.DebugDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.WineD3DConfigDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.console.compat.CompatApplier;
import com.winlator.cmod.console.compat.LaunchFailureClassifier;
import com.winlator.cmod.console.compat.LaunchLogBuffer;
import com.winlator.cmod.console.compat.SessionExit;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineRequestHandler;
import com.winlator.cmod.core.WineStartMenuCreator;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.math.XForm;
import com.winlator.cmod.midi.MidiHandler;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.widget.FrameRating;
import com.winlator.cmod.widget.SeekBar;
import com.winlator.cmod.widget.WinlatorHUD;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.LogView;
import com.winlator.cmod.widget.MagnifierView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.widget.XServerView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.console.SessionLogBuffer;
import com.winlator.cmod.console.SessionMenuActionsAdapter;
import com.winlator.cmod.console.SessionMenuController;
import com.winlator.cmod.console.SessionMenuModel;
import com.winlator.cmod.delta.ChassisEditState;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.XEnvironment;
import com.winlator.cmod.xenvironment.components.ALSAServerComponent;
import com.winlator.cmod.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.cmod.xenvironment.components.PulseAudioComponent;
import com.winlator.cmod.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.cmod.xenvironment.components.XServerComponent;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.Property;
import com.winlator.cmod.xserver.ScreenInfo;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.WindowManager;
import com.winlator.cmod.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.sherlock.com.sun.media.sound.SF2Soundbank;

public class XServerDisplayActivity extends AppCompatActivity {

    private static final boolean DISABLE_TOUCHSCREEN_AUTO_HIDE = true;

    public static String NOTIFICATION_CHANNEL_ID = "Winlator";
    public static int NOTIFICATION_ID = -1;
    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private ContainerManager containerManager;
    protected Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private ImageFs imageFs;
    private FrameRating classicHud = null;   
    private WinlatorHUD modernHud = null;     
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    /** Wall-clock when guest environment started; used to classify short/failed sessions. */
    private long sessionStartMs = 0L;
    /** True when the user tapped Exit in the drawer (not a guest crash). */
    private boolean userRequestedExit = false;
    private boolean exiting = false;
    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;
    private HashMap<String, String> graphicsDriverConfig;
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String emulator = Container.DEFAULT_EMULATOR;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private KeyValueSet dxwrapperConfig;
    private String startupSelection;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private boolean firstTimeBoot = false;
    private SharedPreferences preferences;
    private OnExtractFileListener onExtractFileListener;
    private WinHandler winHandler;
    private WineRequestHandler wineRequestHandler;
    private float globalCursorSpeed = 1.0f;
    private MagnifierView magnifierView;
    private boolean softStretchEnabled = false;
    private DebugDialog debugDialog;
    private SessionMenuController sessionMenuController;
    private boolean sessionLogsEnabled = false;
    private int sessionFpsLimitIndex = 0;
    private boolean sessionFsrEnabled = false;
    private int sessionPostFxIndex = 0;
    private float sessionSharpness = 50f;
    private final int[] sessionFpsValues = {0, 30, 60, 90, 120};
    private short taskAffinityMask = 0;
    private short taskAffinityMaskWoW64 = 0;
    private String wineCpuTopologyValue = "";
    private int frameRatingWindowId = -1;

    private int activeRendererWindowId = -1;
    private String lastRendererName = null;
    private boolean cursorLock; 
    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private boolean navigationFocused = false;
    private MidiHandler midiHandler;
    private String midiSoundFont = "";
    private String lc_all = "";
    private String vkbasaltConfig = "";
    PreloaderDialog preloaderDialog = null;
    private Runnable configChangedCallback = null;
    private boolean isPaused = false;
    private boolean isRelativeMouseMovement = false;
    private boolean isMouseDisabled = false;
    private boolean simulateTouchScreen = false;

    private SensorManager sensorManager;

    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    private static final long SAVE_INTERVAL_MS = 1000;

    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    private boolean isDarkMode;

    private String screenEffectProfile;

    private GuestProgramLauncherComponent guestProgramLauncherComponent;
    private EnvVars overrideEnvVars;

    private void createNotifcationChannel() {
        String name = "Winlator";
        String description = "Winlator XServer Messages";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (configChangedCallback != null) {
            configChangedCallback.run();
            configChangedCallback = null;
        }
    }

    private float pickHighestRefreshRate() {
        android.view.Display display = getWindowManager().getDefaultDisplay();
        android.view.Display.Mode[] modes = display.getSupportedModes();

        float maxRefresh = 0f;

        for (android.view.Display.Mode mode : modes) {
            if (mode.getRefreshRate() > maxRefresh)
                maxRefresh = mode.getRefreshRate();
        }

        Log.d("XServerDisplayActivity", "Picking refresh rate " + maxRefresh);

        return maxRefresh;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // PC Emu Delta chassis is portrait-first (4:3 game + pad below).
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);

        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredRefreshRate = pickHighestRefreshRate();
        getWindow().setAttributes(params);

        setContentView(R.layout.xserver_display_activity);

        preloaderDialog = new PreloaderDialog(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        cursorLock = preferences.getBoolean("cursor_lock", true);

        isDarkMode = preferences.getBoolean("dark_mode", false);

        boolean isOpenWithAndroidBrowser = preferences.getBoolean("open_with_android_browser", false);
        boolean isShareAndroidClipboard = preferences.getBoolean("share_android_clipboard", false);

        boolean xinputDisabledFromShortcut = false;

        startTime = System.currentTimeMillis();

        handler = new Handler(Looper.getMainLooper());
        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);

        hideControlsRunnable = () -> {
            if (DISABLE_TOUCHSCREEN_AUTO_HIDE) {
                return;
            }

            if (preferences.getBoolean("touchscreen_timeout_enabled", false)
                    && inputControlsView != null
                    && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.GONE);
                Log.d("XServerDisplayActivity", "Touchscreen controls hidden after timeout.");
            }
        };

        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);
        drawerLayout.setOnApplyWindowInsetsListener(
                (view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (sessionMenuController != null) {
                    sessionMenuController.attachWinHandler(winHandler);
                    refreshSessionMenu();
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                if (sessionMenuController != null) {
                    sessionMenuController.onDrawerClosed();
                }
            }
        });

        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false)
                || preferences.getBoolean("enable_box64_logs", false);
        sessionLogsEnabled = enableLogs;

        bindSessionMenu();

        imageFs = ImageFs.find(this);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {
            for (int i = 0; i < 4; i++) {
                File eventFile = new File(devInputDir, "event" + i);
                if (eventFile.exists())
                    eventFile.delete();
            }
        }

        winHandler = new WinHandler(this);
        winHandler.setFakeInputPath(devInputDir.getAbsolutePath());
        if (sessionMenuController != null) {
            sessionMenuController.attachWinHandler(winHandler);
        }

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        containerManager = new ContainerManager(this);
        container = containerManager.getContainerById(getIntent().getIntExtra("container_id", 0));

        String shortcutPath = getIntent().getStringExtra("shortcut_path");
        Log.d("XServerDisplayActivity", "Shortcut Path: " + shortcutPath);

        int containerId = getIntent().getIntExtra("container_id", 0);
        Log.d("XServerDisplayActivity", "Container ID from Intent: " + containerId);
        if (containerId == 0) {
            Log.d("XServerDisplayActivity", "Container ID is 0, attempting to parse from .desktop file");

        }

        if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {
            File shortcutFile = new File(shortcutPath);
            containerId = parseContainerIdFromDesktopFile(shortcutFile);
            Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);
        }

        playtimePrefs = getSharedPreferences("playtime_stats", MODE_PRIVATE);
        shortcutName = getIntent().getStringExtra("shortcut_name");

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            if (shortcutName == null || shortcutName.isEmpty()) {
                shortcutName = parseShortcutNameFromDesktopFile(new File(shortcutPath));
                Log.d("XServerDisplayActivity", "Parsed Shortcut Name from .desktop file: " + shortcutName);
            }
        } else {
            Log.d("XServerDisplayActivity", "No shortcut path provided, skipping shortcut parsing.");
        }

        incrementPlayCount();

        Log.d("XServerDisplayActivity", "Final Container ID: " + containerId);

        container = containerManager.getContainerById(containerId);

        if (container == null) {
            Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
            finish(); 
            return;
        }

        containerManager.activateContainer(container);

        if (shortcutPath != null && !shortcutPath.isEmpty()) {
            shortcut = new Shortcut(container, new File(shortcutPath));
            // One-shot Extra Data migration for bare console shortcuts.
            try {
                CompatApplier.INSTANCE.ensureMigrated(shortcut);
                // Reload so putExtra/saveData values are visible this session.
                shortcut = new Shortcut(container, new File(shortcutPath));
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Compat migration failed", e);
            }
        }

        taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));

        String affinityCpuList = container.getCPUList(true);

        if (shortcut != null) {
            affinityCpuList = shortcut.getExtra("cpuList", container.getCPUList(true));
            taskAffinityMask = (short) ProcessHelper.getAffinityMask(affinityCpuList);
            taskAffinityMaskWoW64 = taskAffinityMask;
        }

        boolean syncCpuTopology = shortcut != null
                ? shortcut.getExtra("syncCpuTopology", container.isSyncCpuTopology() ? "1" : "").equals("1")
                : container.isSyncCpuTopology();

        wineCpuTopologyValue = "";
        if (syncCpuTopology && affinityCpuList != null && !affinityCpuList.isEmpty()) {
            int coreCount = affinityCpuList.split(",").length;
            wineCpuTopologyValue = coreCount + ":" + affinityCpuList;
        }

        String wmClass = shortcut != null ? shortcut.getExtra("wmClass", "") : "";
        Log.d("XServerDisplayActivity", "Startup wmClass: " + wmClass);

        firstTimeBoot = container.getExtra("imgVersion").isEmpty();

        String wineVersion = container.getWineVersion();
        wineInfo = WineInfo.fromIdentifier(this, contentsManager, wineVersion);

        imageFs.setWinePath(wineInfo.path);

        ProcessHelper.removeAllDebugCallbacks();
        // Always capture a ring buffer so early Wine crashes can be classified.
        LaunchLogBuffer.INSTANCE.clear();
        ProcessHelper.addDebugCallback(LaunchLogBuffer.INSTANCE);
        SessionLogBuffer.INSTANCE.clear();
        ProcessHelper.addDebugCallback(SessionLogBuffer.INSTANCE);
        if (enableLogs) {
            LogView.setFilename(getExecutable());
            ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        }

        graphicsDriver = container.getGraphicsDriver();
        String graphicsDriverConfig = container.getGraphicsDriverConfig();
        audioDriver = container.getAudioDriver();
        emulator = container.getEmulator();
        midiSoundFont = container.getMIDISoundFont();
        dxwrapper = container.getDXWrapper();
        String dxwrapperConfig = container.getDXWrapperConfig();
        screenSize = container.getScreenSize();
        winHandler.setInputType((byte) container.getInputType());
        lc_all = container.getLC_ALL();

        Intent intent = getIntent();
        Log.d("XServerDisplayActivity", "Intent Extras: " + intent.getExtras());

        if (shortcut != null) {
            graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig());
            audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
            emulator = shortcut.getExtra("emulator", container.getEmulator());
            dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
            screenSize = shortcut.getExtra("screenSize", container.getScreenSize());
            lc_all = shortcut.getExtra("lc_all", container.getLC_ALL());
            String inputType = shortcut.getExtra("inputType");
            if (!inputType.isEmpty())
                winHandler.setInputType(Byte.parseByte(inputType));
            String xinputDisabledString = shortcut.getExtra("disableXinput", "false");
            xinputDisabledFromShortcut = parseBoolean(xinputDisabledString);

            winHandler.setXInputDisabled(xinputDisabledFromShortcut);
            String sharpnessEffect = shortcut.getExtra("sharpnessEffect", "None");
            if (!sharpnessEffect.equals("None")) {
                double sharpnessLevel = Double.parseDouble(shortcut.getExtra("sharpnessLevel", "100"));
                double sharpnessDenoise = Double.parseDouble(shortcut.getExtra("sharpnessDenoise", "100"));
                vkbasaltConfig = "effects=" + sharpnessEffect.toLowerCase() + ";" + "casSharpness="
                        + sharpnessLevel / 100 + ";" + "dlsSharpness=" + sharpnessLevel / 100 + ";" + "dlsDenoise="
                        + sharpnessDenoise / 100 + ";" + "enableOnLaunch=True";
            }
            Log.d("XServerDisplayActivity", "XInput Disabled from Shortcut: " + xinputDisabledFromShortcut);

            simulateTouchScreen = shortcut.getExtra("simTouchScreen").equals("1");
        }

        this.graphicsDriverConfig = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(graphicsDriverConfig);
        this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

        if (!wineInfo.isWin64()) {
            onExtractFileListener = (file, size) -> {
                String path = file.getPath();
                if (path.contains("system32/"))
                    return null;
                return new File(path.replace("syswow64/", "system32/"));
            };
        }

        boolean removeLoadingBarWhenBootingGames = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("remove_loading_bar_when_booting_games", false);
        if (!removeLoadingBarWhenBootingGames) preloaderDialog.show(R.string.starting_up);

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);

        boolean[] winStarted = { false };

        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    if (!simulateTouchScreen) {
                        xServerView.getRenderer().setCursorVisible(true);
                    }
                    preloaderDialog.closeOnUiThread();
                    winStarted[0] = true;
                }

                if (frameRatingWindowId == window.id) {
                    if (classicHud != null) classicHud.update();
                    if (modernHud != null) modernHud.onFrame();
                } else if (frameRatingWindowId == -1 && lastRendererName != null
                        && window.isApplicationWindow()
                        && ((modernHud != null && modernHud.isUserEnabled())
                         || (classicHud != null && classicHud.getVisibility() == View.VISIBLE))) {

                    frameRatingWindowId = window.id;
                    activeRendererWindowId = window.id;
                    if (xServerView != null) xServerView.getRenderer().setFpsWindowId(window.id);
                    if (classicHud != null) classicHud.update();
                    if (modernHud != null) modernHud.onFrame();
                }
            }

            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                changeFrameRatingVisibility(window, property);
            }

            @Override
            public void onDestroyWindow(Window window) {
                changeFrameRatingVisibility(window, null);
            }
        });

        if (!midiSoundFont.equals("")) {
            try {
                final InputStream in;
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    in = getAssets().open(MidiManager.SF2_ASSETS_DIR + "/" + midiSoundFont);
                } else {
                    in = null;
                }
                MidiManager.OnMidiLoadedCallback callback = new MidiManager.OnMidiLoadedCallback() {
                    @Override
                    public void onSuccess(SF2Soundbank soundbank) {
                        midiHandler = new MidiHandler();
                        midiHandler.setSoundBank(soundbank);
                        midiHandler.start();
                    }

                    @Override
                    public void onFailed(Exception e) {
                        if (in != null) {
                            try {
                                in.close();
                            } catch (Exception e2) {
                            }
                        }
                    }
                };
                if (midiSoundFont.equals(MidiManager.DEFAULT_SF2_FILE)) {
                    MidiManager.load(in, callback);
                } else {
                    MidiManager.load(new File(MidiManager.getSoundFontDir(this), midiSoundFont), callback);
                }
            } catch (Exception e) {
            }
        }

        String controlsProfile = shortcut != null ? shortcut.getExtra("controlsProfile", "") : "";

        createNotifcationChannel();

        Intent notificationIntent = new Intent(this, XServerDisplayActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ab_gear_0011)
                .setContentTitle("Winlator")
                .setContentText("Winlator is running, do not kill or swipe this notification")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());

        Runnable runnable = () -> {
            setupUI();
            applySessionInputDefaults();
            if (controlsProfile.isEmpty()) {

                simulateConfirmInputControlsDialog();
            }
            Executors.newSingleThreadExecutor().execute(() -> {
                setupWineSystemFiles();
                extractGraphicsDriverFiles();
                changeWineAudioDriver();
                try {
                    setupXEnvironment();
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        };

        if (xServer.screenInfo.height > xServer.screenInfo.width) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            configChangedCallback = runnable;
        } else
            runnable.run();
    }

    private int parseContainerIdFromDesktopFile(File desktopFile) {
        int containerId = 0;
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        containerId = Integer.parseInt(line.split(":")[1].trim());
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);
            }
        }
        return containerId;
    }

    private boolean parseBoolean(String value) {

        if ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) {
            return true;
        }

        return false;
    }

    private void handleCapturedPointer(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS: {
                int button = event.getActionButton();
                if (button == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.LEFTDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                } else if (button == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.RIGHTDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                } else if (button == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.MIDDLEDOWN, 0, 0, 0);
                    else xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE);
                }
                break;
            }
            case MotionEvent.ACTION_BUTTON_RELEASE: {
                int button = event.getActionButton();
                if (button == MotionEvent.BUTTON_PRIMARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.LEFTUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                } else if (button == MotionEvent.BUTTON_SECONDARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.RIGHTUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                } else if (button == MotionEvent.BUTTON_TERTIARY) {
                    if (xServer.isRelativeMouseMovement()) winHandler.mouseEvent(MouseEventFlags.MIDDLEUP, 0, 0, 0);
                    else xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE: {
                float[] p = XForm.transformPoint(xform, event.getX(), event.getY());
                int dx = (int) p[0];
                int dy = (int) p[1];
                if (xServer.isRelativeMouseMovement())
                    winHandler.mouseEvent(MouseEventFlags.MOVE, dx, dy, 0);
                else
                    xServer.injectPointerMoveDelta(dx, dy);
                break;
            }
            case MotionEvent.ACTION_SCROLL: {
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY <= -1.0f) {
                    if (xServer.isRelativeMouseMovement()) {
                        winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int) scrollY * 270);
                    } else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN);
                    }
                } else if (scrollY >= 1.0f) {
                    if (xServer.isRelativeMouseMovement()) {
                        winHandler.mouseEvent(MouseEventFlags.WHEEL, 0, 0, (int) scrollY * 270);
                    } else {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP);
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP);
                    }
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        startTime = System.currentTimeMillis();
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
        if (!isInPictureInPictureMode())
            ProcessHelper.resumeAllWineProcesses();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!isInPictureInPictureMode()) {
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }

            ProcessHelper.pauseAllWineProcesses();
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    private void enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        int w = xServer.screenInfo.width;
        int h = xServer.screenInfo.height;
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(w, h));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && xServerView != null) {
            int[] loc = new int[2];
            xServerView.getLocationOnScreen(loc);
            Rect hint = new Rect(loc[0], loc[1],
                    loc[0] + xServerView.getWidth(), loc[1] + xServerView.getHeight());
            builder.setSourceRectHint(hint);
        }
        enterPictureInPictureMode(builder.build());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (xServerView == null) return;
        xServerView.getRenderer().setPipMode(isInPictureInPictureMode);
        if (!isInPictureInPictureMode) {
            xServerView.post(() -> {
                if (xServerView == null) return;
                int w = xServerView.getWidth();
                int h = xServerView.getHeight();
                if (w > 0 && h > 0) xServerView.getRenderer().onSurfaceChanged(w, h);
            });
        }
    }

    private void savePlaytimeData() {
        long endTime = System.currentTimeMillis();
        long playtime = endTime - startTime;

        if (playtime < 0) {
            playtime = 0;
        }

        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playtimeKey = shortcutName + "_playtime";

        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;
        editor.putLong(playtimeKey, totalPlaytime);
        editor.apply();

        startTime = System.currentTimeMillis();
    }

    private void incrementPlayCount() {
        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playCountKey = shortcutName + "_play_count";
        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;
        editor.putInt(playCountKey, playCount);
        editor.apply();
    }

    private void exit() {
        userRequestedExit = true;
        exitSession(0, /*fromGuest*/ false);
    }

    private void exitFromGuest(int status) {
        exitSession(status, /*fromGuest*/ true);
    }

    private void exitSession(int status, boolean fromGuest) {
        if (exiting) return;
        exiting = true;

        long sessionMs = sessionStartMs > 0 ? System.currentTimeMillis() - sessionStartMs : 0L;

        if (fromGuest && !userRequestedExit) {
            try {
                LaunchFailureClassifier.Verdict verdict = LaunchFailureClassifier.INSTANCE.classify(
                        LaunchLogBuffer.INSTANCE.snapshot(),
                        status,
                        sessionMs,
                        shortcutName,
                        shortcut != null ? shortcut.getExtra("compatTags") : null);
                String path = shortcut != null ? shortcut.file.getPath() : getIntent().getStringExtra("shortcut_path");
                int cid = container != null ? container.id : getIntent().getIntExtra("container_id", 0);
                SessionExit.INSTANCE.store(this, verdict, path, cid, shortcutName);
                Log.i("XServerDisplayActivity",
                        "Session end status=" + status + " ms=" + sessionMs + " error=" + verdict.isError()
                                + " msg=" + verdict.getMessage());
            } catch (Exception e) {
                Log.w("XServerDisplayActivity", "Failed to classify session exit", e);
            }
        }

        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        boolean removeLoadingBar = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("remove_loading_bar_when_booting_games", false);
        if (!removeLoadingBar) preloaderDialog.showOnUiThread(R.string.shutdown);

        if (xServerView != null) {
            xServerView.getRenderer().forceCleanup();
            xServerView.setVisibility(View.GONE);
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);

        if (midiHandler != null)
            midiHandler.stop();

        if (environment != null)
            environment.stopEnvironmentComponents();
        if (winHandler != null)
            winHandler.stop();
        if (wineRequestHandler != null)
            wineRequestHandler.stop();

        Executors.newSingleThreadExecutor().execute(() -> {

            ProcessHelper.terminateAllWineProcesses();

            long start = System.currentTimeMillis();
            while (!ProcessHelper.listRunningWineProcesses().isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed >= 1500) {

                    for (String pid : ProcessHelper.listRunningWineProcesses()) {
                        ProcessHelper.killProcess(Integer.parseInt(pid));
                    }
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            preloaderDialog.closeOnUiThread();
            AppUtils.restartApplication(getApplicationContext());
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else
                drawerLayout.closeDrawers();
        }
    }

    private void showVibrationDialog() {
        if (winHandler == null)
            return;

        Context context = this;
        int maxControllers = winHandler.getMaxControllers();
        boolean[] checkedItems = new boolean[maxControllers];
        String[] items = new String[maxControllers];

        for (int i = 0; i < maxControllers; i++) {
            items[i] = getString(R.string.vibration_slot, i + 1);
            checkedItems[i] = winHandler.isVibrationEnabledForSlot(i);
        }

        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.vibration)
                .setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
                    winHandler.setVibrationEnabledForSlot(which, isChecked);
                })
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && cursorLock)
            touchpadView.requestPointerCapture();
        else if (!hasFocus)
            touchpadView.releasePointerCapture();
    }

    private void setupWineSystemFiles() {
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        if (!container.getExtra("imgVersion").equals(imgVersion)) {
            applyGeneralPatches(container);
            container.putExtra("imgVersion", imgVersion);
            containerDataChanged = true;
        }

        String dxwrapper = this.dxwrapper;

        if (dxwrapper.contains("dxvk")) {
            String dxvkWrapper = "dxvk-" + dxwrapperConfig.get("version");
            String vkd3dWrapper = "vkd3d-" + dxwrapperConfig.get("vkd3dVersion");
            String ddrawrapper = dxwrapperConfig.get("ddrawrapper");
            dxwrapper = dxvkWrapper + ";" + vkd3dWrapper + ";" + ddrawrapper;
        }

        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {
            extractDXWrapperFiles(dxwrapper);
            container.putExtra("dxwrapper", dxwrapper);
            containerDataChanged = true;
        }

        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents())
                : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme + "," + xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme + "," + xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container);

        int inputType = container.getInputType();
        if (shortcut != null) {
            String shortcutInputType = shortcut.getExtra("inputType");
            if (!shortcutInputType.isEmpty()) {
                inputType = Byte.parseByte(shortcutInputType);
            }
        }
        boolean dinputEnabled = (inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT;

        boolean exclusiveXInput = container.isExclusiveXInput();
        if (shortcut != null) {
            String extra = shortcut.getExtra("exclusiveXInput");
            if (!extra.isEmpty())
                exclusiveXInput = extra.equals("1");
        }

        WineUtils.setJoystickRegistryKeys(container, dinputEnabled, exclusiveXInput);

        if (shortcut != null)
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(container.getStartupSelection()));
        else
            startupSelection = String.valueOf(container.getStartupSelection());

        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, startupSelection);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }
        if (containerDataChanged)
            container.saveData();
    }

    private void setupXEnvironment() throws PackageManager.NameNotFoundException {

        envVars.put("LC_ALL", lc_all);
        envVars.put("WINEPREFIX", imageFs.wineprefix);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels",
                SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty()
                ? "+" + wineDebugChannels.replace(",", ",+")
                : "-all");

        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());

        guestProgramLauncherComponent = new GuestProgramLauncherComponent(
                contentsManager,
                contentsManager.getProfileByEntryName(container.getWineVersion()),
                shortcut);

        if (container != null) {
            if (Byte.parseByte(startupSelection) == Container.STARTUP_SELECTION_AGGRESSIVE) {

            }
            guestProgramLauncherComponent.setContainer(this.container);
            guestProgramLauncherComponent.setWineInfo(this.wineInfo);

            String guestExecutable = "wine explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();

            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());

            if (shortcut != null)
                envVars.putAll(shortcut.getExtra("envVars"));

            // DXVK HUD overlay stays off unless the user enabled HUD/FPS or set a custom DXVK_HUD.
            applyDxvkHudPolicy();

            if (!wineCpuTopologyValue.isEmpty()) {
                envVars.put("WINE_CPU_TOPOLOGY", wineCpuTopologyValue);
            }

            if (!envVars.has("WINEESYNC")) {
                envVars.put("WINEESYNC", "1");
            }

            ArrayList<String> bindingPaths = new ArrayList<>();
            for (String[] drive : container.drivesIterator()) {
                bindingPaths.add(drive[1]);
            }

            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));

            guestProgramLauncherComponent.setBox64Preset(
                    shortcut != null
                            ? shortcut.getExtra("box64Preset", container.getBox64Preset())
                            : container.getBox64Preset());

            guestProgramLauncherComponent.setFEXCorePreset(
                    shortcut != null
                            ? shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())
                            : container.getFEXCorePreset());
        }

        if (overrideEnvVars != null) {
            envVars.putAll(overrideEnvVars);
            overrideEnvVars.clear(); 
        }

        environment = new XEnvironment(this, imageFs);
        environment.addComponent(
                new SysVSharedMemoryComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(
                new XServerComponent(
                        xServer,
                        UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)));

        if (audioDriver.equals("alsa")) {
            envVars.put("ANDROID_ALSA_SERVER", rootPath + UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", "true");
            environment.addComponent(
                    new ALSAServerComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)));
        } else if (audioDriver.equals("pulseaudio")) {
            envVars.put("PULSE_SERVER", rootPath + UnixSocketConfig.PULSE_SERVER_PATH);
            environment.addComponent(
                    new PulseAudioComponent(
                            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)));
        }

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> runOnUiThread(() -> {
            Integer code = status != null ? status : -1;
            exitFromGuest(code);
        }));

        environment.addComponent(guestProgramLauncherComponent);

        File devInputDir = new File(imageFs.getRootDir(), "dev/input");
        if (devInputDir.exists() || devInputDir.mkdirs()) {

        }

        sessionStartMs = System.currentTimeMillis();
        environment.startEnvironmentComponents();

        winHandler.start();

        if (wineRequestHandler != null)
            wineRequestHandler.start();

        dxwrapperConfig = null;

    }

    private void createWrapperScript(String path, String content) {
        File scriptFile = new File(path);
        FileUtils.writeString(scriptFile, content);
        scriptFile.setExecutable(true);
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        final VulkanRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);

        String rendererDriverId = shortcut != null ? shortcut.getRendererDriverId()
                : (container != null ? container.getRendererDriverId() : "");
        if (rendererDriverId == null || rendererDriverId.isEmpty()) {
            rendererDriverId = graphicsDriverConfig != null ? graphicsDriverConfig.get("version") : null;
        }
        if (rendererDriverId != null && !rendererDriverId.isEmpty() && !rendererDriverId.equalsIgnoreCase("system")) {
            try {
                String driverPath = getFilesDir().getAbsolutePath() + "/contents/adrenotools/" + rendererDriverId + "/";
                AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
                String libraryName = adrenotoolsManager.getLibraryName(rendererDriverId);
                String nativeLibDir = AppUtils.getNativeLibDir(this);
                if (!libraryName.isEmpty())
                    renderer.setDriverInfo(driverPath, libraryName, nativeLibDir);
            } catch (Exception ignored) {
            }
        }

        String presentMode = shortcut != null ? shortcut.getRendererPresentMode()
                : (container != null ? container.getRendererPresentMode() : "fifo");
        renderer.setVkPresentMode(com.winlator.cmod.contentdialog.RendererOptionsDialog.toVkPresentMode(presentMode));
        renderer.setFilterMode(shortcut != null ? shortcut.getRendererFilterMode()
                : (container != null ? container.getRendererFilterMode() : 0));
        renderer.setSwapRB(shortcut != null ? shortcut.getRendererSwapRB()
                : (container != null && container.getRendererSwapRB()));

        if (shortcut != null) {
            renderer.setUnviewableWMClasses("explorer.exe");
        }

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        touchpadView = new TouchpadView(this, xServer, timeoutHandler, hideControlsRunnable);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMouseEnabled(!isMouseDisabled);
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START))
                drawerLayout.openDrawer(GravityCompat.START);
        });
        View.OnCapturedPointerListener capturedPointerListener = new View.OnCapturedPointerListener() {
            @Override
            public boolean onCapturedPointer(View view, MotionEvent event) {
                handleCapturedPointer(event);
                return true;
            }
        };
        touchpadView.setOnCapturedPointerListener(cursorLock ? capturedPointerListener : null);
        touchpadView.setFocusable(true);
        touchpadView.setFocusableInTouchMode(true);
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this, timeoutHandler, hideControlsRunnable);
        inputControlsView
                .setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        // PC Emu Delta chassis — Jetpack Compose UI → FakeInputWriter virtual pad
        androidx.compose.ui.platform.ComposeView deltaChassis = findViewById(R.id.DeltaChassis);
        androidx.compose.ui.platform.ComposeView deltaEdit = findViewById(R.id.DeltaEditOverlay);
        androidx.compose.ui.platform.ComposeView deltaEditBtn = findViewById(R.id.DeltaEditButton);
        if (deltaChassis != null) {
            com.winlator.cmod.delta.DeltaChassisHost.install(
                    deltaChassis, winHandler, this, null, deltaEdit, deltaEditBtn);
            Log.i("XServerDisplayActivity", "Delta Compose chassis + top-right EDIT + display tools bound");
        }

        // Delta shell: exact 4:3 (800:600) rounded screen + camera gap (letterbox, no stretch).
        android.view.View deltaRoot = findViewById(R.id.DeltaRootColumn);
        if (deltaRoot != null) {
            Runnable applyDeltaLayout = new Runnable() {
                @Override
                public void run() {
                    int totalW = deltaRoot.getWidth();
                    int totalH = deltaRoot.getHeight();
                    if (totalW <= 0 || totalH <= 0) return;

                    // Strict 4:3 = 800×600
                    final float ASPECT = 4f / 3f;

                    float density = getResources().getDisplayMetrics().density;

                    // Safe top (status / cutout only — no extra downward push)
                    int topSafe = Math.round(24f * density);
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                            android.view.WindowInsets wi = deltaRoot.getRootWindowInsets();
                            if (wi != null && wi.getDisplayCutout() != null) {
                                int cut = wi.getDisplayCutout().getSafeInsetTop();
                                if (cut > topSafe) topSafe = cut;
                            }
                        }
                        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                        if (resId > 0) {
                            int status = getResources().getDimensionPixelSize(resId);
                            if (status > topSafe) topSafe = status;
                        }
                    } catch (Throwable ignored) {}
                    int minTop = Math.round(20f * density);
                    if (topSafe < minTop) topSafe = minTop;

                    // Symmetric chrome: slightly less than full screen on all sides
                    // ~2.5% of the shorter screen edge, clamped for phones
                    int chrome = Math.round(Math.min(totalW, totalH) * 0.025f);
                    chrome = Math.max(Math.round(8f * density), Math.min(chrome, Math.round(14f * density)));

                    int bezel = Math.round(3f * density); // matches drawable padding
                    int slotPadV = Math.round(4f * density);

                    // Top padding = max(safe area, chrome) so display clears the notch evenly
                    int topPad = Math.max(topSafe, chrome);
                    // Bottom chrome matches side chrome for balance
                    int bottomPad = chrome;
                    // Gap between display and pad = same chrome unit (full symmetry)
                    int midGap = chrome;

                    deltaRoot.setPadding(0, topPad, 0, bottomPad);
                    int usableH = Math.max(totalH - topPad - bottomPad, totalH / 2);
                    int usableW = totalW - chrome * 2;

                    // Nearly full width (only chrome left/right)
                    int maxInnerW = usableW - bezel * 2;
                    if (maxInnerW < 64) maxInnerW = totalW - bezel * 2;

                    // Reserve enough height for a comfortable pad + mid gap
                    int chassisFloor = Math.round(usableH * 0.36f);
                    int maxSlotH = usableH - chassisFloor - midGap;
                    int maxInnerH = maxSlotH - slotPadV * 2 - bezel * 2;
                    if (maxInnerH < 48) maxInnerH = Math.max(48, maxSlotH - bezel * 2);

                    // Largest exact 4:3 that fits (prefer width-driven for near-full screen)
                    int innerW, innerH;
                    if (maxInnerW / ASPECT <= maxInnerH) {
                        innerW = maxInnerW;
                        innerH = Math.round(innerW * 3f / 4f);
                    } else {
                        innerH = maxInnerH;
                        innerW = Math.round(innerH * 4f / 3f);
                    }
                    if (innerH > maxInnerH) {
                        innerH = maxInnerH;
                        innerW = Math.round(innerH * 4f / 3f);
                    }
                    if (innerW > maxInnerW) {
                        innerW = maxInnerW;
                        innerH = Math.round(innerW * 3f / 4f);
                    }

                    int bezelW = innerW + bezel * 2;
                    int bezelH = innerH + bezel * 2;
                    int slotH = bezelH + slotPadV * 2;
                    // Pad takes remaining height after mid gap (top/bottom chrome already in root padding)
                    int chassisH = Math.max(1, usableH - slotH - midGap);

                    // If pad got squeezed, rebalance so both stay large
                    if (chassisH < Math.round(usableH * 0.32f)) {
                        chassisH = Math.round(usableH * 0.36f);
                        maxSlotH = usableH - chassisH - midGap;
                        maxInnerH = Math.max(48, maxSlotH - slotPadV * 2 - bezel * 2);
                        if (maxInnerW / ASPECT <= maxInnerH) {
                            innerW = maxInnerW;
                            innerH = Math.round(innerW * 3f / 4f);
                        } else {
                            innerH = maxInnerH;
                            innerW = Math.round(innerH * 4f / 3f);
                        }
                        bezelW = innerW + bezel * 2;
                        bezelH = innerH + bezel * 2;
                        slotH = bezelH + slotPadV * 2;
                        chassisH = Math.max(1, usableH - slotH - midGap);
                    }

                    // Keep a tiny equal breathing room under pad if leftover pixels remain
                    int leftover = usableH - slotH - midGap - chassisH;
                    int panelTopGap = midGap;
                    int panelBottomGap = Math.max(0, leftover);

                    FrameLayout gameSlot = findViewById(R.id.GameSlot);
                    FrameLayout gameBezel = findViewById(R.id.GameBezel);
                    FrameLayout gamePane = findViewById(R.id.FLXServerDisplay);
                    android.view.View chassis = findViewById(R.id.DeltaChassis);

                    if (gameSlot != null) {
                        android.widget.LinearLayout.LayoutParams sp =
                                (android.widget.LinearLayout.LayoutParams) gameSlot.getLayoutParams();
                        sp.width = android.widget.LinearLayout.LayoutParams.MATCH_PARENT;
                        sp.height = slotH;
                        sp.weight = 0f;
                        sp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                        gameSlot.setLayoutParams(sp);
                        // Horizontal only — vertical chrome handled by mid/bottom gaps
                        gameSlot.setPadding(chrome, slotPadV, chrome, 0);
                    }
                    if (gameBezel != null) {
                        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(bezelW, bezelH);
                        bp.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP;
                        gameBezel.setLayoutParams(bp);
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 21) {
                                final float radius = 18f * density;
                                gameBezel.setClipToOutline(true);
                                gameBezel.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                                    }
                                });
                            }
                        } catch (Throwable ignored) {}
                    }
                    if (gamePane != null) {
                        // Fill bezel completely — game covers the whole inner box
                        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT);
                        gamePane.setLayoutParams(gp);
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= 21) {
                                final float radius = 16f * density;
                                gamePane.setClipToOutline(true);
                                gamePane.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                    @Override
                                    public void getOutline(android.view.View view, android.graphics.Outline outline) {
                                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                                    }
                                });
                            }
                        } catch (Throwable ignored) {}
                    }
                    // Same width as display; equal mid/bottom chrome for symmetry
                    if (chassis != null) {
                        android.widget.LinearLayout.LayoutParams cp =
                                (android.widget.LinearLayout.LayoutParams) chassis.getLayoutParams();
                        cp.width = bezelW;
                        cp.height = Math.max(1, chassisH);
                        cp.weight = 0f;
                        cp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                        cp.topMargin = panelTopGap;
                        cp.bottomMargin = panelBottomGap;
                        chassis.setLayoutParams(cp);
                    }

                    // Keep cameraGap name for log below
                    final int cameraGap = topPad;

                    if (xServerView != null && gamePane != null) {
                        final int innerWFinal = innerW;
                        final int innerHFinal = innerH;
                        final int cameraGapFinal = cameraGap;
                        final int chassisHFinal = chassisH;
                        gamePane.post(() -> {
                            int gw = gamePane.getWidth() > 0 ? gamePane.getWidth() : innerWFinal;
                            int gh = gamePane.getHeight() > 0 ? gamePane.getHeight() : innerHFinal;
                            if (gw <= 0 || gh <= 0) return;
                            try {
                                // Letterbox / aspect-fit only — no soft-stretch (reverted fill mode)
                                if (xServerView.getRenderer().isFullscreen()) {
                                    xServerView.getRenderer().toggleFullscreen();
                                }
                                xServerView.getRenderer().setStretchMode(0);
                            } catch (Throwable ignored) {}
                            xServerView.getRenderer().onSurfaceChanged(gw, gh);
                            Log.i("XServerDisplayActivity",
                                    "Delta 4:3 surface=" + gw + "x" + gh
                                            + " aspect=" + String.format("%.4f", gw / (float) gh)
                                            + " target=1.3333 cameraGap=" + cameraGapFinal
                                            + " chassisH=" + chassisHFinal);
                        });
                    }
                }
            };
            deltaRoot.addOnLayoutChangeListener(
                    (v, l, t, r, b, ol, ot, orr, ob) -> applyDeltaLayout.run());
            deltaRoot.post(applyDeltaLayout);
            deltaRoot.postDelayed(applyDeltaLayout, 120);
            deltaRoot.postDelayed(applyDeltaLayout, 400);
        }

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (isTimeoutEnabled) {
            startTouchscreenTimeout();
        }

        if (container != null) {
            String hudModeExtra = container.getExtra("hudMode");
            int hudMode = !hudModeExtra.isEmpty()
                    ? Integer.parseInt(hudModeExtra)
                    : (container.isShowFPS() ? 1 : 0);

            if (hudMode == 1) {

                classicHud = new FrameRating(this, graphicsDriverConfig);
                classicHud.setVisibility(View.GONE);
                rootView.addView(classicHud);
                renderer.setFrameRating(classicHud);
            } else if (hudMode == 2) {

                modernHud = new WinlatorHUD(this);
                modernHud.setVisibility(View.GONE);
                rootView.addView(modernHud);
                modernHud.enableByUser();
                renderer.setFrameRating(modernHud);
            }
        }

        String shortcutFullscreenStretched = shortcut != null ? shortcut.getExtra("fullscreenStretched") : null;

        boolean shouldStretch = false;

        if (shortcut != null && shortcutFullscreenStretched != null) {

            shouldStretch = shortcutFullscreenStretched.equals("1");
        } else if (container != null && container.isFullscreenStretched()) {

            shouldStretch = true;
        }

        // Delta portrait shell needs aspect-correct letterbox (e.g. 800x600).
        // Skip stretch-to-fill so the game is not vertically distorted.
        boolean deltaShell = findViewById(R.id.DeltaChassis) != null;
        if (shouldStretch && !deltaShell) {
            renderer.toggleFullscreen();
            touchpadView.toggleFullscreen();
        } else if (deltaShell) {
            try { renderer.setStretchMode(0); } catch (Throwable ignored) {}
        }

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null)
                    showInputControls(profile);
            }

            String simTouchScreen = shortcut.getExtra("simTouchScreen");
            touchpadView.setSimTouchScreen(simTouchScreen.equals("1"));
            if (simulateTouchScreen) {
                renderer.setCursorVisible(false);
            }
        }

        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);

        loadSessionGraphicsState();
        applySessionGraphicsToRenderer();
        refreshSessionMenu();
    }

    /**
     * Keep DXVK's built-in HUD off by default. Only leave DXVK_HUD when the user
     * enabled FPS/HUD, or set a non-legacy custom DXVK_HUD value.
     */
    private void applyDxvkHudPolicy() {
        boolean hudOn = container != null && container.isHudOverlayEnabled();
        if (hudOn) return;
        if (!envVars.has("DXVK_HUD")) return;
        String value = envVars.get("DXVK_HUD");
        if (value == null) value = "";
        boolean legacyDefault =
                value.equals(Container.LEGACY_DEFAULT_DXVK_HUD)
                        || value.equals("fps")
                        || value.equals("devinfo,fps")
                        || value.equals("fps,devinfo,version,gpuload")
                        || value.isEmpty();
        // First-run / unconfigured: always clear. Custom values stay only if user
        // also turned on HUD — otherwise strip everything so the game starts clean.
        envVars.remove("DXVK_HUD");
        if (!legacyDefault) {
            // User had a custom HUD string but HUD toggle is off — still hide until configured on.
            Log.i("XServerDisplayActivity", "DXVK_HUD suppressed until HUD/FPS is enabled");
        }
    }

    private ActivityResultLauncher<Intent> controlsEditorActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (editInputControlsCallback != null) {
                    editInputControlsCallback.run();
                    editInputControlsCallback = null;
                }
            });

    private String parseShortcutNameFromDesktopFile(File desktopFile) {
        String shortcutName = "";
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Name=")) {
                        shortcutName = line.split("=")[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e("XServerDisplayActivity", "Error reading shortcut name from .desktop file", e);
            }
        }
        return shortcutName;
    }

    private void setTextColorForDialog(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {

                setTextColorForDialog((ViewGroup) child, color);
            } else if (child instanceof TextView) {

                ((TextView) child).setTextColor(color);
            }
        }
    }

    private void bindSessionMenu() {
        androidx.compose.ui.platform.ComposeView composeView = findViewById(R.id.SessionMenuCompose);
        if (composeView == null) return;

        sessionMenuController = new SessionMenuController(composeView, this, new SessionMenuActionsAdapter() {
            @Override public void closeMenu() {
                runOnUiThread(() -> drawerLayout.closeDrawers());
            }

            @Override public void togglePause() {
                runOnUiThread(() -> {
                    if (isPaused) {
                        ProcessHelper.resumeAllWineProcesses();
                    } else {
                        ProcessHelper.pauseAllWineProcesses();
                    }
                    isPaused = !isPaused;
                    drawerLayout.closeDrawers();
                    refreshSessionMenu();
                });
            }

            @Override public void showKeyboard() {
                runOnUiThread(() -> {
                    AppUtils.showKeyboard(XServerDisplayActivity.this);
                    drawerLayout.closeDrawers();
                });
            }

            @Override public void enterPip() {
                runOnUiThread(() -> {
                    enterPipMode();
                    drawerLayout.closeDrawers();
                });
            }

            @Override public void toggleFullscreen() {
                runOnUiThread(() -> {
                    if (xServerView != null) {
                        xServerView.getRenderer().toggleFullscreen();
                        if (touchpadView != null) touchpadView.toggleFullscreen();
                    }
                    drawerLayout.closeDrawers();
                });
            }

            @Override public void openMagnifier() {
                runOnUiThread(() -> {
                    if (xServerView != null) {
                        final VulkanRenderer renderer = xServerView.getRenderer();
                        if (magnifierView == null) {
                            FrameLayout flContainer = findViewById(R.id.FLXServerDisplay);
                            magnifierView = new MagnifierView(XServerDisplayActivity.this);
                            magnifierView.setZoomButtonCallback(value -> {
                                renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                                magnifierView.setZoomValue(renderer.getMagnifierZoom());
                            });
                            magnifierView.setZoomValue(renderer.getMagnifierZoom());
                            magnifierView.setHideButtonCallback(() -> {
                                flContainer.removeView(magnifierView);
                                magnifierView = null;
                            });
                            flContainer.addView(magnifierView);
                        }
                    }
                    drawerLayout.closeDrawers();
                });
            }

            @Override public void toggleSoftStretch() {
                runOnUiThread(() -> {
                    if (xServerView != null) {
                        softStretchEnabled = !softStretchEnabled;
                        VulkanRenderer rendererRef = xServerView.getRenderer();
                        if (softStretchEnabled && !rendererRef.isFullscreen()) {
                            rendererRef.toggleFullscreen();
                            if (touchpadView != null) touchpadView.toggleFullscreen();
                        }
                        rendererRef.setStretchMode(softStretchEnabled ? 1 : 0);
                    }
                    refreshSessionMenu();
                });
            }

            @Override public void setRelativeMouse(boolean enabled) {
                runOnUiThread(() -> {
                    isRelativeMouseMovement = enabled;
                    if (xServer != null) xServer.setRelativeMouseMovement(isRelativeMouseMovement);
                    refreshSessionMenu();
                });
            }

            @Override public void setMouseDisabled(boolean disabled) {
                runOnUiThread(() -> {
                    isMouseDisabled = disabled;
                    if (touchpadView != null) touchpadView.setMouseEnabled(!isMouseDisabled);
                    refreshSessionMenu();
                });
            }

            @Override public void showVibration() {
                // Nested Console panel handled by SessionMenuController.
            }

            @Override public void openControlPanelEdit() {
                runOnUiThread(() -> {
                    ChassisEditState.INSTANCE.openEdit();
                    drawerLayout.closeDrawers();
                });
            }

            @Override public void setShowTouchControls(boolean enabled) {
                runOnUiThread(() -> {
                    if (inputControlsView != null) {
                        inputControlsView.setShowTouchscreenControls(enabled);
                        preferences.edit().putBoolean("show_touchscreen_controls_enabled", enabled).apply();
                        applySessionInputProfileSelection(resolveSessionProfileIndex());
                    }
                    refreshSessionMenu();
                });
            }

            @Override public void setTouchTimeout(boolean enabled) {
                runOnUiThread(() -> {
                    preferences.edit().putBoolean("touchscreen_timeout_enabled", enabled).apply();
                    if (enabled && inputControlsView != null && inputControlsView.getVisibility() == View.VISIBLE) {
                        startTouchscreenTimeout();
                    } else if (touchpadView != null) {
                        touchpadView.setOnTouchListener(null);
                    }
                    refreshSessionMenu();
                });
            }

            @Override public void setTouchHaptics(boolean enabled) {
                runOnUiThread(() -> {
                    preferences.edit().putBoolean("touchscreen_haptics_enabled", enabled).apply();
                    refreshSessionMenu();
                });
            }

            @Override public void setControlsOpacity(float opacity) {
                runOnUiThread(() -> {
                    preferences.edit().putFloat("overlay_opacity", opacity).apply();
                    if (inputControlsView != null) {
                        inputControlsView.setOverlayOpacity(opacity);
                        inputControlsView.invalidate();
                    }
                    refreshSessionMenu();
                });
            }

            @Override public void setInputProfileIndex(int index) {
                runOnUiThread(() -> {
                    preferences.edit().putInt("selected_profile_index", index - 1).apply();
                    applySessionInputProfileSelection(index);
                    refreshSessionMenu();
                });
            }

            @Override public void editInputProfiles() {
                runOnUiThread(() -> {
                    int position = resolveSessionProfileIndex();
                    Intent intent = new Intent(XServerDisplayActivity.this, MainActivity.class);
                    intent.putExtra("edit_input_controls", true);
                    intent.putExtra("selected_profile_id",
                            position > 0 && inputControlsManager != null
                                    ? inputControlsManager.getProfiles().get(position - 1).id : 0);
                    editInputControlsCallback = () -> {
                        hideInputControls();
                        if (inputControlsManager != null) inputControlsManager.loadProfiles(true);
                        applySessionInputProfileSelection(resolveSessionProfileIndex());
                        refreshSessionMenu();
                    };
                    controlsEditorActivityResultLauncher.launch(intent);
                    drawerLayout.closeDrawers();
                });
            }

            @Override public void setHudMode(int mode) {
                runOnUiThread(() -> {
                    if (mode == 0) {
                        if (classicHud != null) classicHud.disableByUser();
                        if (modernHud != null) modernHud.disableByUser();
                    } else {
                        if (classicHud != null) classicHud.disableByUser(false);
                        if (modernHud != null) modernHud.disableByUser(false);
                        enableHudLazily(mode);
                    }
                    saveHudModeToContainer(mode);
                    refreshSessionMenu();
                });
            }

            @Override public void setFpsLimitIndex(int index) {
                runOnUiThread(() -> {
                    sessionFpsLimitIndex = Math.max(0, Math.min(index, sessionFpsValues.length - 1));
                    if (xServerView != null) {
                        xServerView.getRenderer().setFpsLimit(sessionFpsValues[sessionFpsLimitIndex]);
                    }
                    refreshSessionMenu();
                });
            }

            @Override public void setFsrEnabled(boolean enabled) {
                runOnUiThread(() -> {
                    sessionFsrEnabled = enabled;
                    applySessionGraphicsToRenderer();
                    refreshSessionMenu();
                });
            }

            @Override public void setPostFxIndex(int index) {
                runOnUiThread(() -> {
                    sessionPostFxIndex = Math.max(0, Math.min(index, 4));
                    applySessionGraphicsToRenderer();
                    refreshSessionMenu();
                });
            }

            @Override public void setSharpness(float value) {
                runOnUiThread(() -> {
                    sessionSharpness = value;
                    if (xServerView != null) {
                        xServerView.getRenderer().setSharpness(value / 100f);
                    }
                    refreshSessionMenu();
                });
            }

            @Override public void saveGraphicsPreset() {
                runOnUiThread(() -> {
                    if (container == null) return;
                    container.putExtra("graphicsFpsPreset", String.valueOf(sessionFpsLimitIndex));
                    container.putExtra("graphicsFilterMode", String.valueOf(sessionFsrEnabled ? 2 : 0));
                    container.putExtra("graphicsSharpness", String.valueOf(sessionSharpness));
                    container.putExtra("graphicsPostFXMode", String.valueOf(sessionPostFxIndex));
                    container.putExtra("graphicsColorMode", "0");
                    container.saveData();
                    Toast.makeText(XServerDisplayActivity.this, "Preset saved", Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void openLogs() {
                // Nested Console panel handled by SessionMenuController.
            }

            @Override public void openTaskManager() {
                // Nested Console panel handled by SessionMenuController.
            }

            @Override public void exitSession() {
                runOnUiThread(() -> {
                    drawerLayout.closeDrawers();
                    userRequestedExit = true;
                    exit();
                });
            }
        });
        refreshSessionMenu();
    }

    private void refreshSessionMenu() {
        if (sessionMenuController == null) return;
        java.util.ArrayList<String> profiles = new java.util.ArrayList<>();
        profiles.add("-- Disabled --");
        int profileIndex = 0;
        if (inputControlsManager != null) {
            java.util.ArrayList<ControlsProfile> list = inputControlsManager.getProfiles(true);
            for (int i = 0; i < list.size(); i++) {
                ControlsProfile profile = list.get(i);
                if (inputControlsView != null && inputControlsView.getProfile() != null
                        && profile.id == inputControlsView.getProfile().id) {
                    profileIndex = i + 1;
                }
                profiles.add(profile.getName());
            }
        }
        int hudMode = 0;
        if (modernHud != null && modernHud.isUserEnabled()) hudMode = 2;
        else if (classicHud != null && classicHud.getVisibility() == View.VISIBLE) hudMode = 1;
        else if (container != null) {
            String extra = container.getExtra("hudMode");
            if (!extra.isEmpty()) {
                try { hudMode = Integer.parseInt(extra); } catch (Exception ignored) {}
            } else if (container.isShowFPS()) {
                hudMode = 1;
            }
        }

        String title = shortcutName != null && !shortcutName.isEmpty()
                ? shortcutName
                : (container != null ? container.getName() : "Session");

        sessionMenuController.update(SessionMenuModel.base(
                title,
                isPaused,
                isRelativeMouseMovement,
                isMouseDisabled,
                softStretchEnabled,
                inputControlsView != null && inputControlsView.isShowTouchscreenControls(),
                preferences.getBoolean("touchscreen_timeout_enabled", false),
                preferences.getBoolean("touchscreen_haptics_enabled", false),
                preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY),
                hudMode,
                sessionFpsLimitIndex,
                sessionFsrEnabled,
                sessionPostFxIndex,
                sessionSharpness,
                sessionLogsEnabled,
                XrActivity.isEnabled(this),
                profiles,
                profileIndex
        ));
    }

    private void loadSessionGraphicsState() {
        if (container == null) return;
        try {
            String savedFps = container.getExtra("graphicsFpsPreset");
            sessionFpsLimitIndex = savedFps.isEmpty() ? 0 : Integer.parseInt(savedFps);
        } catch (Exception e) {
            sessionFpsLimitIndex = 0;
        }
        if (sessionFpsLimitIndex < 0 || sessionFpsLimitIndex >= sessionFpsValues.length) sessionFpsLimitIndex = 0;

        try {
            String savedFilter = container.getExtra("graphicsFilterMode");
            sessionFsrEnabled = !savedFilter.isEmpty() && Integer.parseInt(savedFilter) > 0;
        } catch (Exception e) {
            sessionFsrEnabled = false;
        }

        try {
            String savedSharp = container.getExtra("graphicsSharpness");
            sessionSharpness = savedSharp.isEmpty() ? 50f : Float.parseFloat(savedSharp);
        } catch (Exception e) {
            sessionSharpness = 50f;
        }

        try {
            String savedPFX = container.getExtra("graphicsPostFXMode");
            sessionPostFxIndex = savedPFX.isEmpty() ? 0 : Integer.parseInt(savedPFX);
        } catch (Exception e) {
            sessionPostFxIndex = 0;
        }
        if (sessionPostFxIndex < 0 || sessionPostFxIndex > 4) sessionPostFxIndex = 0;
    }

    private void applySessionGraphicsToRenderer() {
        if (xServerView == null) return;
        VulkanRenderer renderer = xServerView.getRenderer();
        renderer.setFpsLimit(sessionFpsValues[sessionFpsLimitIndex]);
        renderer.setFilterMode(sessionFsrEnabled ? 2 : (container != null ? container.getRendererFilterMode() : 0));
        renderer.setPostFXMode(sessionPostFxIndex);
        renderer.setSharpness(sessionSharpness / 100f);
    }

    private void applySessionInputDefaults() {
        if (inputControlsView == null) return;
        boolean show = preferences.getBoolean("show_touchscreen_controls_enabled",
                inputControlsView.isShowTouchscreenControls());
        inputControlsView.setShowTouchscreenControls(show);
        applySessionInputProfileSelection(resolveSessionProfileIndex());
        refreshSessionMenu();
    }

    private int resolveSessionProfileIndex() {
        int idx = preferences.getInt("selected_profile_index", -1) + 1;
        if (inputControlsManager == null) return 0;
        int max = inputControlsManager.getProfiles().size();
        if (idx < 0) idx = 0;
        if (idx > max) idx = 0;
        return idx;
    }

    private void applySessionInputProfileSelection(int position) {
        if (inputControlsManager == null || inputControlsView == null) return;
        java.util.ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles();
        if (position > 0 && position - 1 < profiles.size()) {
            showInputControls(profiles.get(position - 1));
        } else {
            hideInputControls();
        }
        boolean timeout = preferences.getBoolean("touchscreen_timeout_enabled", false);
        if (timeout && inputControlsView.getVisibility() == View.VISIBLE) {
            startTouchscreenTimeout();
        } else if (touchpadView != null) {
            touchpadView.setOnTouchListener(null);
        }
    }

    private void enableHudLazily(int style) {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        if (rootView == null || xServerView == null) return;
        final VulkanRenderer renderer = xServerView.getRenderer();
        boolean rendererAlreadyActive = (activeRendererWindowId != -1);

        if (style == 2) {
            if (modernHud == null) {
                modernHud = new WinlatorHUD(this);
                modernHud.setVisibility(View.GONE);
                rootView.addView(modernHud);
                if (renderer != null) renderer.setFrameRating(modernHud);
                if (rendererAlreadyActive) {
                    frameRatingWindowId = activeRendererWindowId;
                    if (renderer != null) renderer.setFpsWindowId(frameRatingWindowId);
                    final String name = lastRendererName;
                    modernHud.onRendererDetected(name);
                }
            }
            modernHud.enableByUser();
        } else {
            if (classicHud == null) {
                classicHud = new FrameRating(this, graphicsDriverConfig);
                classicHud.setVisibility(View.GONE);
                rootView.addView(classicHud);
                if (renderer != null) renderer.setFrameRating(classicHud);
                if (rendererAlreadyActive) {
                    frameRatingWindowId = activeRendererWindowId;
                    if (renderer != null) renderer.setFpsWindowId(frameRatingWindowId);
                    runOnUiThread(() -> classicHud.update());
                }
            }
            classicHud.enableByUser();
        }
    }

    private void saveHudModeToContainer(int mode) {
        if (container == null) return;
        container.putExtra("hudMode", String.valueOf(mode));
        container.setShowFPS(mode != 0);
        container.saveData();
    }

    private void showInputControlsDialog() {
        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);
        dialog.setTitle(R.string.input_controls);
        dialog.setIcon(R.drawable.icon_input_controls);

        final Spinner sProfile = dialog.findViewById(R.id.SProfile);

        dialog.getWindow().setBackgroundDrawableResource(
                isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sProfile.setPopupBackgroundResource(
                isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        int textColor = ContextCompat.getColor(this, isDarkMode ? R.color.white : R.color.black);
        ViewGroup dialogViewGroup = (ViewGroup) dialog.getWindow().getDecorView().findViewById(android.R.id.content);
        setTextColorForDialog(dialogViewGroup, textColor);

        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- " + getString(R.string.disabled) + " --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                    selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));
            sProfile.setSelection(selectedPosition);
        };
        loadProfileSpinner.run();

        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);
        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());

        final CheckBox cbEnableTimeout = dialog.findViewById(R.id.CBEnableTimeout);
        cbEnableTimeout.setChecked(preferences.getBoolean("touchscreen_timeout_enabled", false));

        final CheckBox cbEnableHaptics = dialog.findViewById(R.id.CBEnableHaptics);
        cbEnableHaptics.setChecked(preferences.getBoolean("touchscreen_haptics_enabled", false));

        final Runnable updateProfile = () -> {
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            } else
                hideInputControls();
        };

        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {
            int position = sProfile.getSelectedItemPosition();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("edit_input_controls", true);
            intent.putExtra("selected_profile_id",
                    position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);
            editInputControlsCallback = () -> {
                hideInputControls();
                inputControlsManager.loadProfiles(true);
                loadProfileSpinner.run();
                updateProfile.run();
            };
            controlsEditorActivityResultLauncher.launch(intent);
        });

        dialog.setOnConfirmCallback(() -> {
            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());
            boolean isTimeoutEnabled = cbEnableTimeout.isChecked();
            boolean isHapticsEnabled = cbEnableHaptics.isChecked();
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
            editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
            editor.apply();

            if (isTimeoutEnabled) {
                startTouchscreenTimeout(); 
            } else {
                touchpadView.setOnTouchListener(null); 
            }
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            } else
                hideInputControls();
            updateProfile.run();
        });

        dialog.setOnCancelCallback(updateProfile::run);

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void simulateConfirmInputControlsDialog() {

        boolean isShowTouchscreenControls = preferences.getBoolean("show_touchscreen_controls_enabled", false); 

        inputControlsView.setShowTouchscreenControls(isShowTouchscreenControls);

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);
        boolean isHapticsEnabled = preferences.getBoolean("touchscreen_haptics_enabled", false);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("touchscreen_timeout_enabled", isTimeoutEnabled);
        editor.putBoolean("touchscreen_haptics_enabled", isHapticsEnabled);
        editor.apply();

        int selectedProfileIndex = preferences.getInt("selected_profile_index", -1); 

        if (selectedProfileIndex >= 0 && selectedProfileIndex < inputControlsManager.getProfiles().size()) {

            ControlsProfile profile = inputControlsManager.getProfiles().get(selectedProfileIndex);
            showInputControls(profile);
        } else {

            hideInputControls();
        }

        if (isTimeoutEnabled && inputControlsView.getVisibility() == View.VISIBLE) {
            startTouchscreenTimeout(); 
        } else {
            touchpadView.setOnTouchListener(null); 
        }

        Log.d("XServerDisplayActivity", "Input controls simulated confirmation executed.");
    }

    private void startTouchscreenTimeout() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }

        if (DISABLE_TOUCHSCREEN_AUTO_HIDE) {
            Log.d("XServerDisplayActivity", "Touchscreen auto-hide disabled; controls remain visible.");
            if (touchpadView != null) {
                touchpadView.setOnTouchListener(null);
            }
            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.VISIBLE);
            }
            return;
        }

        boolean isTimeoutEnabled = preferences.getBoolean("touchscreen_timeout_enabled", false);

        if (isTimeoutEnabled) {
            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.VISIBLE);
            }
            Log.d("XServerDisplayActivity", "Timeout is enabled, setting up timeout logic.");

            touchpadView.setOnTouchListener((v, event) -> {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    if (inputControlsView != null && inputControlsView.getProfile() != null) {
                        inputControlsView.setVisibility(View.VISIBLE);
                    }

                    timeoutHandler.removeCallbacks(hideControlsRunnable);
                    timeoutHandler.postDelayed(hideControlsRunnable, 5000);
                }

                return false;
            });

            timeoutHandler.postDelayed(hideControlsRunnable, 5000);
        } else {
            Log.d("XServerDisplayActivity", "Timeout is disabled, controls will stay visible.");

            if (inputControlsView != null && inputControlsView.getProfile() != null) {
                inputControlsView.setVisibility(View.VISIBLE);
            }
            timeoutHandler.removeCallbacks(hideControlsRunnable);
            touchpadView.setOnTouchListener(null);
        }
    }

    private void showInputControls(ControlsProfile profile) {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }

        inputControlsView.setProfile(profile);
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);

        inputControlsView.invalidate();
        winHandler.sendGamepadState();
    }

    private void hideInputControls() {
        if (timeoutHandler != null && hideControlsRunnable != null) {
            timeoutHandler.removeCallbacks(hideControlsRunnable);
        }

        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
        winHandler.sendGamepadState();
    }

    private void extractGraphicsDriverFiles() {
        String adrenoToolsDriverId = graphicsDriverConfig.get("version");

        Log.d("GraphicsDriverExtraction", "Adrenotools DriverID: " + adrenoToolsDriverId);

        File rootDir = imageFs.getRootDir();

        if (dxwrapper.contains("dxvk")) {
            DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
            String version = dxwrapperConfig.get("version");
            if (version.equals("1.11.1-sarek")) {
                Log.d("GraphicsDriverExtraction", "Disabling Wrapper PATCH_OPCONSTCOMP SPIR-V pass");
                envVars.put("WRAPPER_NO_PATCH_OPCONSTCOMP", "1");
            }
        } else {
            WineD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
        }

        boolean useDRI3 = preferences.getBoolean("use_dri3", true);
        if (!useDRI3) {
            envVars.put("MESA_VK_WSI_DEBUG", "sw");
        }

        envVars.put("VK_ICD_FILENAMES", imageFs.getShareDir() + "/vulkan/icd.d/wrapper_icd.aarch64.json");
        envVars.put("GALLIUM_DRIVER", "zink");

        if (firstTimeBoot) {
            Log.d("XServerDisplayActivity", "First time container boot, re-extracting libs");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/wrapper" + ".tzst",
                    rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "layers" + ".tzst", rootDir);
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/extra_libs" + ".tzst",
                    rootDir);
        }

        if (!"System".equals(adrenoToolsDriverId)) {
            AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(this);
            adrenotoolsManager.setDriverById(envVars, imageFs, adrenoToolsDriverId);
        }

        String vulkanVersion = graphicsDriverConfig.get("vulkanVersion");
        String vulkanVersionPatch = GPUInformation.getVulkanVersion(adrenoToolsDriverId, this).split("\\.")[2];
        vulkanVersion = vulkanVersion + "." + vulkanVersionPatch;
        envVars.put("WRAPPER_VK_VERSION", vulkanVersion);

        String blacklistedExtensions = graphicsDriverConfig.get("blacklistedExtensions");
        envVars.put("WRAPPER_EXTENSION_BLACKLIST", blacklistedExtensions);

        String gpuName = graphicsDriverConfig.get("gpuName");
        String dxvkVersion = dxwrapperConfig.get("version");
        if (!gpuName.equals("Device") && !dxvkVersion.equals("1.11.1-sarek")) {
            envVars.put("WRAPPER_DEVICE_NAME", gpuName);
            envVars.put("WRAPPER_DEVICE_ID", WineD3DConfigDialog.getDeviceIdFromGPUName(this, gpuName));
            envVars.put("WRAPPER_VENDOR_ID", WineD3DConfigDialog.getVendorIdFromGPUName(this, gpuName));
        }

        String maxDeviceMemory = graphicsDriverConfig.get("maxDeviceMemory");
        if (maxDeviceMemory != null && Integer.parseInt(maxDeviceMemory) > 0)
            envVars.put("WRAPPER_VMEM_MAX_SIZE", maxDeviceMemory);

        String presentMode = graphicsDriverConfig.get("presentMode");
        if (presentMode.contains("immediate")) {
            envVars.put("WRAPPER_MAX_IMAGE_COUNT", "1");
        }
        envVars.put("MESA_VK_WSI_PRESENT_MODE", presentMode);

        String resourceType = graphicsDriverConfig.get("resourceType");
        envVars.put("WRAPPER_RESOURCE_TYPE", resourceType);

        String syncFrame = graphicsDriverConfig.get("syncFrame");
        if (syncFrame.equals("1"))
            envVars.put("MESA_VK_WSI_DEBUG", "forcesync");

        String disablePresentWait = graphicsDriverConfig.get("disablePresentWait");
        envVars.put("WRAPPER_DISABLE_PRESENT_WAIT", disablePresentWait);

        String bcnEmulation = graphicsDriverConfig.get("bcnEmulation");
        String bcnEmulationType = graphicsDriverConfig.get("bcnEmulationType");

        switch (bcnEmulation) {
            case "auto" -> {
                if (bcnEmulationType.equals("compute") && GPUInformation.getVendorID(null, null) != 0x5143) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "1");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "3");
            }
            case "full" -> {
                if (bcnEmulationType.equals("compute") && GPUInformation.getVendorID(null, null) != 0x5143) {
                    envVars.put("ENABLE_BCN_COMPUTE", "1");
                    envVars.put("BCN_COMPUTE_AUTO", "0");
                }
                envVars.put("WRAPPER_EMULATE_BCN", "2");
            }
            case "none" -> envVars.put("WRAPPER_EMULATE_BCN", "0");
            default -> envVars.put("WRAPPER_EMULATE_BCN", "1");
        }

        String bcnEmulationCache = graphicsDriverConfig.get("bcnEmulationCache");
        envVars.put("WRAPPER_USE_BCN_CACHE", bcnEmulationCache);

        if (!vkbasaltConfig.isEmpty()) {
            envVars.put("ENABLE_VKBASALT", "1");
            envVars.put("VKBASALT_CONFIG", vkbasaltConfig);
        }
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        boolean handledByWinHandler = false;
        boolean handledByTouchpadView = false;

        if (winHandler != null) {
            handledByWinHandler = winHandler.onGenericMotionEvent(event);
            if (handledByWinHandler) {

            }
        }

        if (touchpadView != null) {
            handledByTouchpadView = touchpadView.onExternalMouseEvent(event);
            if (handledByTouchpadView) {

            }
        }

        boolean handledBySuper = super.dispatchGenericMotionEvent(event);
        if (!handledBySuper) {

        }

        return handledByWinHandler || handledByTouchpadView || handledBySuper;
    }

    private static final int RECAPTURE_DELAY_MS = 10000; 

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE || event.getKeyCode() == KeyEvent.KEYCODE_HOME
                    || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT) {
                boolean handled = inputControlsView.onKeyEvent(event)
                        || (winHandler != null && winHandler.onKeyEvent(event))
                                && (xServer != null && xServer.keyboard.onKeyEvent(event));
                return true;
            }
        }

        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event)
                && xServer.keyboard.onKeyEvent(event)) ||
                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }

    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    private static final String TAG = "DXWrapperExtraction";

    private void extractDXWrapperFiles(String dxwrapper) {
        final String[] dlls = { "d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll",
                "d3d8.dll", "d3d9.dll", "dxgi.dll", "ddraw.dll", "d3dimm.dll" };

        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");

        if (dxwrapper.contains("dxvk")) {
            Log.d(TAG, "Extracting DXVK wrapper files, version: " + dxwrapper);

            String dxvkWrapper = dxwrapper.split(";")[0];
            String vkd3dWrapper = dxwrapper.split(";")[1];
            String ddrawrapper = dxwrapper.split(";")[2];

            ContentProfile dxvkProfile = contentsManager.getProfileByEntryName(dxvkWrapper);
            if (dxvkProfile != null) {
                Log.d(TAG, "Applying user-defined DXVK content profile: " + dxvkWrapper);
                contentsManager.applyContent(dxvkProfile);
            } else {
                Log.d(TAG, "Extracting fallback DXVK .tzst archive: " + dxvkWrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxvkWrapper + ".tzst",
                        windowsDir, onExtractFileListener);

                if (compareVersion(dxvkWrapper, "2.4") < 0) {
                    Log.d(TAG, "Extracting d8vk as part of DXVK version " + dxvkWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "dxwrapper/d8vk-" + DefaultVersion.D8VK + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            if (vkd3dWrapper.contains("None")) {
                Log.d(TAG, "No VKD3D has been selected, restoring original d3d12");
                restoreOriginalDllFiles(new String[] { "d3d12.dll", "d3d12core.dll" });
            } else {
                ContentProfile vkd3dProfile = contentsManager.getProfileByEntryName(vkd3dWrapper);
                if (vkd3dProfile != null) {
                    Log.d(TAG, "Applying user-defined VKD3D content profile: " + vkd3dWrapper);
                    contentsManager.applyContent(vkd3dProfile);
                } else {
                    Log.d(TAG, "Extracting fallback VKD3D .tzst archive: " + vkd3dWrapper);
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "dxwrapper/" + vkd3dWrapper + ".tzst", windowsDir, onExtractFileListener);
                }
            }

            Log.d(TAG, "Extracting nglide wrapper");
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/nglide.tzst", windowsDir,
                    onExtractFileListener);

            if (ddrawrapper.contains("None")) {
                Log.d(TAG, "No DDRaw wrapper has been selected, restoring original ddraw files");
                restoreOriginalDllFiles(new String[] { "ddraw.dll", "d3dimm.dll" });
            } else {
                if (ddrawrapper.equals("cnc-ddraw"))
                    envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\windows\\syswow64\\ddraw.ini");

                Log.d(TAG, "Extracting ddrawrapper " + ddrawrapper);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "ddrawrapper/" + ddrawrapper + ".tzst",
                        windowsDir, onExtractFileListener);
            }

            Log.d(TAG, "Finished extraction of DXVK wrapper files, version: " + dxwrapper);
        } else if (dxwrapper.contains("wined3d")) {
            Log.d(TAG, "Restoring original DLL files for wined3d.");
            restoreOriginalDllFiles(dlls);
        }
    }

    private static int compareVersion(String varA, String varB) {
        int[] a = parseSemverLoose(varA);
        int[] b = parseSemverLoose(varB);

        if (a[0] != b[0])
            return a[0] - b[0];
        if (a[1] != b[1])
            return a[1] - b[1];
        return a[2] - b[2];
    }

    private static final Pattern SEMVER_LOOSE = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static int[] parseSemverLoose(String s) {
        if (s == null)
            return new int[] { 0, 0, 0 };

        Matcher m = SEMVER_LOOSE.matcher(s);

        String g1 = null, g2 = null, g3 = null;
        while (m.find()) {
            g1 = m.group(1);
            g2 = m.group(2);
            g3 = m.group(3);
        }

        if (g1 == null || g2 == null) {
            return new int[] { 0, 0, 0 };
        }

        int major = safeParseInt(g1);
        int minor = safeParseInt(g2);
        int patch = safeParseInt(g3);
        return new int[] { major, minor, patch };
    }

    private static int safeParseInt(String s) {
        if (s == null || s.isEmpty())
            return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void extractWinComponentFiles() {
        Log.d("XServerDisplayActivity", "Extracting WinComponents");
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(
                    FileUtils.readString(this, "wincomponents/wincomponents.json"));
            ArrayList<String> dlls = new ArrayList<>();
            String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents())
                    : container.getWinComponents();

            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(
                    container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                if (wincomponent[1].equals(oldWinComponentsIter.next()[1]) && !firstTimeBoot)
                    continue;
                String identifier = wincomponent[0];
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this,
                            "wincomponents/" + identifier + ".tzst", windowsDir, onExtractFileListener);
                } else {
                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
                    for (int i = 0; i < dlnames.length(); i++) {
                        String dlname = dlnames.getString(i);
                        dlls.add(!dlname.endsWith(".exe") ? dlname + ".dll" : dlname);
                    }
                }
                Log.d("XServerDisplayActivity",
                        "Setting wincomponent " + identifier + " to " + String.valueOf(useNative));
                WineUtils.overrideWinComponentDlls(this, container, identifier, useNative);
                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative, this);
            }

            if (!dlls.isEmpty())
                restoreOriginalDllFiles(dlls.toArray(new String[0]));
        } catch (JSONException e) {
        }
    }

    private void restoreOriginalDllFiles(final String... dlls) {
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
        File system32dlls = null;
        File syswow64dlls = null;

        if (wineInfo.isArm64EC())
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/aarch64-windows");
        else
            system32dlls = new File(imageFs.getWinePath() + "/lib/wine/x86_64-windows");

        syswow64dlls = new File(imageFs.getWinePath() + "/lib/wine/i386-windows");

        for (String dll : dlls) {
            File srcFile = new File(system32dlls, dll);
            File dstFile = new File(windowsDir, "system32/" + dll);
            FileUtils.copy(srcFile, dstFile);
            srcFile = new File(syswow64dlls, dll);
            dstFile = new File(windowsDir, "syswow64/" + dll);
            FileUtils.copy(srcFile, dstFile);
        }
    }

    private String getWineStartCommand() {
        // Initialize overrideEnvVars if not already done
        EnvVars envVars = getOverrideEnvVars();

        // Define default arguments
        String args = "";

        if (shortcut != null) {
            String execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " " + execArgs : "";

            if (shortcut.path == null || shortcut.path.trim().isEmpty()) {
                Log.e("XServerDisplayActivity", "Shortcut has empty exec path: " + shortcut.file);
                args += "\"wfm.exe\"";
            } else if (shortcut.path.endsWith(".lnk")) {
                args += "\"" + shortcut.path + "\"" + execArgs;
            } else {
                String fullPath = shortcut.path.replace("\"", "").trim();
                // Map /storage/... unix paths onto container drive letters (e.g. F:\...)
                // so winhandler /dir works inside Wine.
                String winePath = toWineDosPath(fullPath);
                String exeDir;
                String filename;

                if (winePath.contains("\\")) {
                    int lastSlash = winePath.lastIndexOf("\\");
                    if (lastSlash != -1) {
                        exeDir = winePath.substring(0, lastSlash);
                        filename = winePath.substring(lastSlash + 1);
                    } else {
                        exeDir = "D:\\";
                        filename = winePath;
                    }
                } else {
                    exeDir = FileUtils.getDirname(winePath);
                    filename = FileUtils.getName(winePath);
                    if (exeDir.isEmpty() || ".".equals(exeDir)) {
                        exeDir = "D:\\";
                        if (filename.isEmpty()) filename = winePath;
                    }
                }

                int dotIndex = filename.lastIndexOf(".");
                int spaceIndex = (dotIndex != -1) ? filename.indexOf(" ", dotIndex) : -1;

                if (spaceIndex != -1) {
                    execArgs = filename.substring(spaceIndex + 1) + execArgs;
                    filename = filename.substring(0, spaceIndex);
                }

                Log.d("XServerDisplayActivity", "Wine start /dir=" + exeDir + " exe=" + filename);
                args += "/dir " + StringUtils.escapeDOSPath(exeDir) + " \"" + filename + "\"" + execArgs;
            }
        } else {
            // Append EXTRA_EXEC_ARGS from overrideEnvVars if it exists
            if (envVars.has("EXTRA_EXEC_ARGS")) {
                args += " " + envVars.get("EXTRA_EXEC_ARGS");
                envVars.remove("EXTRA_EXEC_ARGS"); // Remove the key after use
            } else {
                args += "\"wfm.exe\"";
            }
        }
        // Construct the final command
        String command = "winhandler.exe " + args;

        return command;
    }

    /**
     * Convert an absolute Android/unix path into a Wine DOS path using the
     * container's drive map (longest matching mount wins). Falls back to the
     * original path when nothing matches.
     */
    private String toWineDosPath(String unixPath) {
        if (unixPath == null || unixPath.isEmpty()) return unixPath;
        if (unixPath.contains("\\") && unixPath.length() >= 2 && unixPath.charAt(1) == ':') {
            return unixPath;
        }
        String normalized = unixPath.replace('\\', '/');
        String bestLetter = null;
        String bestMount = "";
        if (container != null) {
            for (String[] drive : container.drivesIterator()) {
                String letter = drive[0];
                String mount = drive[1] != null ? drive[1].replace('\\', '/') : "";
                while (mount.endsWith("/")) mount = mount.substring(0, mount.length() - 1);
                if (mount.isEmpty()) continue;
                if (normalized.equals(mount) || normalized.startsWith(mount + "/")) {
                    if (mount.length() > bestMount.length()) {
                        bestMount = mount;
                        bestLetter = letter;
                    }
                }
            }
        }
        if (bestLetter == null) return unixPath;
        String relative = normalized.equals(bestMount) ? "" : normalized.substring(bestMount.length());
        while (relative.startsWith("/")) relative = relative.substring(1);
        String dos = bestLetter.toUpperCase(java.util.Locale.US) + ":\\" + relative.replace('/', '\\');
        return dos;
    }

    private String getExecutable() {
        String filename = "wfm.exe";
        if (shortcut != null && shortcut.path != null) {
            String cleanPath = shortcut.path.replace("\"", "");
            int lastSlash = cleanPath.lastIndexOf('/');
            int lastBackslash = cleanPath.lastIndexOf('\\');
            int lastSeparator = Math.max(lastSlash, lastBackslash);
            if (lastSeparator != -1) {
                filename = cleanPath.substring(lastSeparator + 1);
            } else {
                filename = cleanPath;
            }
        }
        return filename;
    }

    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public EnvVars getOverrideEnvVars() {
        if (overrideEnvVars == null) {
            overrideEnvVars = new EnvVars();
        }
        return overrideEnvVars;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = imageFs.getRootDir();
            File userRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals("alsa")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                } else if (audioDriver.equals("pulseaudio")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = imageFs.getRootDir();
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "container_pattern_common.tzst", rootDir);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst",
                new File(getFilesDir(), "pulseaudio"));
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("graphicsDriver", null);
        container.putExtra("desktopTheme", null);
    }

    private void assignTaskAffinity(Window window) {
        if (taskAffinityMask == 0 || taskAffinityMaskWoW64 == 0)
            return;
        int processId = window.getProcessId();
        String className = window.getClassName();
        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

        if (processId > 0) {
            winHandler.setProcessAffinity(processId, processAffinity);
        } else if (!className.isEmpty()) {
            winHandler.setProcessAffinity(window.getClassName(), processAffinity);
        }
    }

    private void changeFrameRatingVisibility(Window window, Property property) {

        String propName = (property != null) ? property.nameAsString() : null;

        if (property != null) {
            if (activeRendererWindowId == -1 && propName.contains("_MESA_DRV")) {
                activeRendererWindowId = window.id;
            }

            if (propName.contains("_MESA_DRV_ENGINE_NAME")
                    && (activeRendererWindowId == -1 || window.id == activeRendererWindowId)) {
                lastRendererName = property.toString();
            }
        } else if (activeRendererWindowId != -1 && window.id == activeRendererWindowId) {

            activeRendererWindowId = -1;
            lastRendererName = null;
        }

        if (classicHud == null && modernHud == null) return;

        if (property != null) {
            if (frameRatingWindowId == -1 && propName.contains("_MESA_DRV")) {
                frameRatingWindowId = window.id;
                if (xServerView != null) xServerView.getRenderer().setFpsWindowId(window.id);
                Log.d("XServerDisplayActivity", "Showing hud for Window " + window.getName());
                if (classicHud != null) classicHud.update();

                if (modernHud != null) {
                    final String nameToPass = lastRendererName;
                    runOnUiThread(() -> modernHud.onRendererDetected(nameToPass));
                }
            }

            if (propName.contains("_MESA_DRV_ENGINE_NAME") && window.id == frameRatingWindowId) {
                String rendererName = property.toString();
                if (classicHud != null) runOnUiThread(() -> classicHud.setRenderer(rendererName));
                if (modernHud != null) runOnUiThread(() -> modernHud.setRenderer(rendererName));
            }
            if (propName.contains("_MESA_DRV_GPU_NAME") && window.id == frameRatingWindowId) {
                String gpuName = property.toString();
                if (classicHud != null) runOnUiThread(() -> classicHud.setGpuName(gpuName));
                if (modernHud != null) runOnUiThread(() -> modernHud.setGpuName(gpuName));
            }
        } else if (frameRatingWindowId != -1 && window.id == frameRatingWindowId) {

            frameRatingWindowId = -1;
            if (xServerView != null) xServerView.getRenderer().setFpsWindowId(-1);
            Log.d("XServerDisplayActivity", "Hiding hud for Window " + window.getName());
            if (classicHud != null) runOnUiThread(() -> {
                classicHud.setVisibility(View.GONE);
                classicHud.reset();
            });
            if (modernHud != null) runOnUiThread(() -> modernHud.onRendererGone());
        }
    }

    public String getScreenEffectProfile() {
        return screenEffectProfile;
    }

    public void setScreenEffectProfile(String screenEffectProfile) {
        this.screenEffectProfile = screenEffectProfile;
    }

}
