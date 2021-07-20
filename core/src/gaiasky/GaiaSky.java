/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky;

import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.Timer.Task;
import gaiasky.assets.*;
import gaiasky.assets.GaiaAttitudeLoader.GaiaAttitudeLoaderParameter;
import gaiasky.assets.SGLoader.SGLoaderParameter;
import gaiasky.data.AssetBean;
import gaiasky.data.StreamingOctreeLoader;
import gaiasky.data.util.PointCloudData;
import gaiasky.desktop.util.CrashReporter;
import gaiasky.desktop.util.SysUtils;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.interafce.*;
import gaiasky.render.*;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.IPostProcessor.PostProcessBean;
import gaiasky.render.IPostProcessor.RenderType;
import gaiasky.scenegraph.*;
import gaiasky.scenegraph.camera.CameraManager;
import gaiasky.scenegraph.camera.CameraManager.CameraMode;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.component.ModelComponent;
import gaiasky.screenshot.ScreenshotsManager;
import gaiasky.script.HiddenHelperUser;
import gaiasky.script.ScriptingServer;
import gaiasky.util.Logger;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.ds.DatasetUpdater;
import gaiasky.util.gaia.GaiaAttitudeServer;
import gaiasky.util.gdx.contrib.postprocess.utils.PingPongBuffer;
import gaiasky.util.gdx.g2d.BitmapFont;
import gaiasky.util.gdx.loader.*;
import gaiasky.util.gdx.loader.is.GzipInputStreamProvider;
import gaiasky.util.gdx.loader.is.RegularInputStreamProvider;
import gaiasky.util.gdx.model.IntModel;
import gaiasky.util.gdx.shader.*;
import gaiasky.util.gravwaves.RelativisticEffectsManager;
import gaiasky.util.samp.SAMPClient;
import gaiasky.util.time.GlobalClock;
import gaiasky.util.time.ITimeFrameProvider;
import gaiasky.util.time.RealTimeClock;
import gaiasky.util.tree.OctreeNode;
import gaiasky.vr.openvr.VRContext;
import gaiasky.vr.openvr.VRContext.VRDevice;
import gaiasky.vr.openvr.VRContext.VRDeviceType;
import gaiasky.vr.openvr.VRStatus;
import org.lwjgl.opengl.GL30;
import org.lwjgl.openvr.Texture;
import org.lwjgl.openvr.VR;
import org.lwjgl.openvr.VRCompositor;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * The main class. Holds all the entities manages the update/draw cycle and all
 * other top-level functions of Gaia Sky.
 */
public class GaiaSky implements ApplicationListener, IObserver, IMainRenderer {
    private static final Log logger = Logger.getLogger(GaiaSky.class);

    /**
     * Current render process.
     * One of {@link #runnableInitialGui}, {@link #runnableLoadingGui} or {@link #runnableRender}.
     **/
    private Runnable renderProcess;

    /**
     * Singleton instance
     **/
    public static GaiaSky instance;

    /**
     * Window
     **/
    public Lwjgl3Window window;
    /**
     * Graphics
     **/
    public Lwjgl3Graphics graphics;

    /**
     * The {@link VRContext} setup in {@link #createVR()}, may be null if no HMD is
     * present or SteamVR is not installed
     */
    public VRContext vrContext;

    /**
     * Loading frame buffers
     **/
    public FrameBuffer vrLoadingLeftFb, vrLoadingRightFb;
    /**
     * Loading texture
     **/
    public Texture vrLoadingLeftTex, vrLoadingRightTex;

    /**
     * Maps the VR devices to model objects
     */
    private HashMap<VRDevice, StubModel> vrDeviceToModel;

    // Asset manager
    public AssetManager manager;

    // Camera
    public CameraManager cam;

    // Data load string
    private String dataLoadString;

    // Reference to the scene graph
    public ISceneGraph sg;
    // Scene graph renderer
    public SceneGraphRenderer sgr;
    // Main post processor
    private IPostProcessor pp;

    // Start time
    private long startTime;

    // Time since the start in seconds
    private double t;

    // The frame number
    public long frames;

    // Frame buffer map
    private Map<String, FrameBuffer> frameBufferMap;

    // Registry
    private GuiRegistry guiRegistry;

    // Dynamic resolution state
    private boolean lowResolution = false;
    private long lastResolutionChange = 0;

    /**
     * Provisional console logger
     */
    private ConsoleLogger consoleLogger;

    public InputMultiplexer inputMultiplexer;

    /**
     * The user interfaces
     */
    public IGui welcomeGui, welcomeGuiVR, loadingGui, loadingGuiVR, mainGui, spacecraftGui, stereoGui, debugGui, crashGui, controllerGui;

    /**
     * List of GUIs
     */
    private List<IGui> guis;

    /**
     * Time
     */
    public ITimeFrameProvider time;

    // The sprite batch to render the back buffer to screen
    private SpriteBatch renderBatch;

    /**
     * Camera recording or not?
     */
    private boolean camRecording = false;

    // Gaia Sky has finished initialization
    private boolean initialized = false;
    // Window has been created successfully
    public boolean windowCreated = false;

    /**
     * Used to wait for new frames
     */
    public final Object frameMonitor = new Object();

    /**
     * Set log level to debug
     */
    private final boolean debugMode;

    /**
     * Whether to attempt a connection to the VR HMD
     */
    private final boolean vr;

    /**
     * Skip welcome screen if possible
     */
    private final boolean skipWelcome;

    /**
     * Forbids the creation of the scripting server
     */
    private final boolean noScripting;

    /**
     * Save state on exit
     */
    public boolean saveState = true;

    /**
     * External view with final rendered scene and no UI
     */
    public boolean externalView;

    /**
     * External UI window
     */
    public GaiaSkyView gaiaSkyView = null;

    /**
     * Global resources holder
     */
    private GlobalResources globalResources;

    /**
     * Runnables
     */
    private final Array<Runnable> parkedRunnables;
    private final Map<String, Runnable> parkedRunnablesMap;

    /**
     * Creates an instance of Gaia Sky.
     */
    public GaiaSky() {
        this(false, false, false, false, false);
    }

    /**
     * Creates an instance of Gaia Sky.
     *
     * @param skipWelcome  Skips welcome screen if possible
     * @param vr           Launch in VR mode
     * @param externalView Open a new window with a view of the rendered scene
     * @param debugMode    Output debug information
     */
    public GaiaSky(boolean skipWelcome, boolean vr, boolean externalView, boolean noScriptingServer, boolean debugMode) {
        super();
        instance = this;
        this.skipWelcome = skipWelcome;
        this.debugMode = debugMode;
        this.parkedRunnablesMap = new HashMap<>();
        this.parkedRunnables = new Array<>();
        this.vr = vr;
        this.externalView = externalView;
        this.renderProcess = runnableInitialGui;
        this.noScripting = noScriptingServer;
    }

    @Override
    public void create() {
        startTime = TimeUtils.millis();
        // Log level
        Gdx.app.setLogLevel(debugMode ? Application.LOG_DEBUG : Application.LOG_INFO);
        Logger.level = debugMode ? Logger.LoggerLevel.DEBUG : Logger.LoggerLevel.INFO;

        consoleLogger = new ConsoleLogger();

        if (debugMode)
            logger.debug("Logging level set to DEBUG");

        // Init graphics and window
        graphics = (Lwjgl3Graphics) Gdx.graphics;
        window = graphics.getWindow();

        // Basic info
        logger.info(GlobalConf.version.version, I18n.txt("gui.build", GlobalConf.version.build));
        logger.info("Display mode", graphics.getWidth() + "x" + graphics.getHeight(), "Fullscreen: " + Gdx.graphics.isFullscreen());
        logger.info("Device", GL30.glGetString(GL30.GL_RENDERER));
        logger.info(I18n.txt("notif.glversion", GL30.glGetString(GL30.GL_VERSION)));
        logger.info(I18n.txt("notif.glslversion", GL30.glGetString(GL30.GL_SHADING_LANGUAGE_VERSION)));
        logger.info(I18n.txt("notif.javaversion", System.getProperty("java.version"), System.getProperty("java.vendor")));

        // Frame buffer map
        frameBufferMap = new HashMap<>();

        // Disable all kinds of input
        EventManager.instance.post(Events.INPUT_ENABLED_CMD, false);

        if (!GlobalConf.initialized()) {
            logger.error(new RuntimeException("FATAL: Global configuration not initlaized"));
            return;
        }

        // Initialise times
        ITimeFrameProvider clock = new GlobalClock(1, Instant.now());
        ITimeFrameProvider real = new RealTimeClock();
        time = GlobalConf.runtime.REAL_TIME ? real : clock;
        t = 0;

        // Initialise i18n
        I18n.initialize();

        // Tooltips
        TooltipManager.getInstance().initialTime = 1f;
        TooltipManager.getInstance().hideAll();

        // Initialise asset manager
        FileHandleResolver internalResolver = new InternalFileHandleResolver();
        FileHandleResolver dataResolver = fileName -> GlobalConf.data.dataFileHandle(fileName);
        manager = new AssetManager(internalResolver);
        manager.setLoader(com.badlogic.gdx.graphics.Texture.class, ".pfm", new PFMTextureLoader(dataResolver));
        manager.setLoader(PFMData.class, new PFMDataLoader(dataResolver));
        manager.setLoader(ISceneGraph.class, new SGLoader(dataResolver));
        manager.setLoader(PointCloudData.class, new OrbitDataLoader(dataResolver));
        manager.setLoader(GaiaAttitudeServer.class, new GaiaAttitudeLoader(dataResolver));
        manager.setLoader(ExtShaderProgram.class, new ShaderProgramProvider(internalResolver, ".vertex.glsl", ".fragment.glsl"));
        manager.setLoader(BitmapFont.class, new BitmapFontLoader(internalResolver));
        //manager.setLoader(DefaultIntShaderProvider.class, new DefaultShaderProviderLoader<>(resolver));
        manager.setLoader(AtmosphereShaderProvider.class, new AtmosphereShaderProviderLoader<>(internalResolver));
        manager.setLoader(GroundShaderProvider.class, new GroundShaderProviderLoader<>(internalResolver));
        manager.setLoader(TessellationShaderProvider.class, new TessellationShaderProviderLoader<>(internalResolver));
        manager.setLoader(RelativisticShaderProvider.class, new RelativisticShaderProviderLoader<>(internalResolver));
        manager.setLoader(IntModel.class, ".obj", new ObjLoader(new RegularInputStreamProvider(), internalResolver));
        manager.setLoader(IntModel.class, ".obj.gz", new ObjLoader(new GzipInputStreamProvider(), internalResolver));
        manager.setLoader(IntModel.class, ".g3dj", new G3dModelLoader(new JsonReader(), internalResolver));
        manager.setLoader(IntModel.class, ".g3db", new G3dModelLoader(new UBJsonReader(), internalResolver));
        // Remove Model loaders

        // Init global resources
        globalResources = new GlobalResources(manager);


        // Initialize screenshots manager
        ScreenshotsManager.initialize(globalResources);

        // Catalog manager
        CatalogManager.initialize();

        // Initialise master manager
        MasterManager.initialize();

        // Initialise dataset updater
        DatasetUpdater.initialize();

        // Bookmarks
        BookmarksManager.initialize();

        // Location log
        LocationLogManager.initialize();

        // Load slave assets
        SlaveManager.load(manager);

        // Init timer thread
        Timer.instance();

        // Initialise Cameras
        cam = new CameraManager(manager, CameraMode.FOCUS_MODE, vr, globalResources);

        // Set asset manager to asset bean
        AssetBean.setAssetManager(manager);

        // Create vr context if possible
        VRStatus vrStatus = createVR();
        cam.updateFrustumPlanes();

        // Tooltip to 1s
        TooltipManager.getInstance().initialTime = 1f;

        // Initialise Gaia attitudes
        manager.load(Constants.ATTITUDE_FOLDER, GaiaAttitudeServer.class, new GaiaAttitudeLoaderParameter());

        // Initialise hidden helper user
        HiddenHelperUser.initialize();

        // Initialise gravitational waves helper
        RelativisticEffectsManager.initialize(time);

        // GUI
        guis = new ArrayList<>(3);

        // Post-processor
        pp = PostProcessorFactory.instance.getPostProcessor();

        // Scene graph renderer
        sgr = new SceneGraphRenderer(vrContext, globalResources);
        sgr.initialize(manager);

        // Initialise scripting gateway server
        if (!noScripting)
            ScriptingServer.initialize();

        // Tell the asset manager to load all the assets
        Set<AssetBean> assets = AssetBean.getAssets();
        for (AssetBean ab : assets) {
            ab.load(manager);
        }

        renderBatch = globalResources.getSpriteBatch();

        EventManager.instance.subscribe(this, Events.LOAD_DATA_CMD);

        welcomeGui = new WelcomeGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE, skipWelcome, vrStatus);
        welcomeGui.initialize(manager, globalResources.getSpriteBatch());
        Gdx.input.setInputProcessor(welcomeGui.getGuiStage());

        if (GlobalConf.runtime.OPENVR) {
            welcomeGuiVR = new VRGui<>(WelcomeGuiVR.class, (int) (GlobalConf.screen.BACKBUFFER_WIDTH / 4f), graphics, 1f / GlobalConf.program.UI_SCALE);
            welcomeGuiVR.initialize(manager, globalResources.getSpriteBatch());
        }

        // GL clear state
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClearDepthf(1f);
    }

    /**
     * Attempt to create a VR context. This operation will only succeed if an HMD is connected
     * and detected via OpenVR
     **/
    private VRStatus createVR() {
        if (vr) {
            // Initializing the VRContext may fail if no HMD is connected or SteamVR
            // is not installed.
            try {
                //OpenVRQuery.queryOpenVr();
                GlobalConf.runtime.OPENVR = true;
                Constants.initialize(GlobalConf.scene.DIST_SCALE_VR);

                vrContext = new VRContext();
                vrContext.pollEvents();

                VRDevice hmd = vrContext.getDeviceByType(VRDeviceType.HeadMountedDisplay);
                logger.info("Initialization of VR successful");
                if (hmd == null) {
                    logger.info("HMD device is null!");
                } else {
                    logger.info("HMD device is not null: " + vrContext.getDeviceByType(VRDeviceType.HeadMountedDisplay).toString());
                }

                vrDeviceToModel = new HashMap<>();

                GlobalConf.runtime.OPENVR = true;
                if (GlobalConf.screen.SCREEN_WIDTH != vrContext.getWidth()) {
                    logger.info("Warning, resizing according to VRSystem values:  [" + GlobalConf.screen.SCREEN_WIDTH + "x" + GlobalConf.screen.SCREEN_HEIGHT + "] -> [" + vrContext.getWidth() + "x" + vrContext.getHeight() + "]");
                    // Do not resize the screen!
                    GlobalConf.screen.BACKBUFFER_HEIGHT = vrContext.getHeight();
                    GlobalConf.screen.BACKBUFFER_WIDTH = vrContext.getWidth();
                    //this.resizeImmediate(vrContext.getWidth(), vrContext.getHeight(), true, true, true);
                }
                GlobalConf.screen.VSYNC = false;

                graphics.setWindowedMode(GlobalConf.screen.SCREEN_WIDTH, GlobalConf.screen.SCREEN_HEIGHT);
                graphics.setVSync(GlobalConf.screen.VSYNC);

                vrLoadingLeftFb = new FrameBuffer(Format.RGBA8888, vrContext.getWidth(), vrContext.getHeight(), true);
                vrLoadingLeftTex = org.lwjgl.openvr.Texture.create();
                vrLoadingLeftTex.set(vrLoadingLeftFb.getColorBufferTexture().getTextureObjectHandle(), VR.ETextureType_TextureType_OpenGL, VR.EColorSpace_ColorSpace_Gamma);

                vrLoadingRightFb = new FrameBuffer(Format.RGBA8888, vrContext.getWidth(), vrContext.getHeight(), true);
                vrLoadingRightTex = org.lwjgl.openvr.Texture.create();
                vrLoadingRightTex.set(vrLoadingRightFb.getColorBufferTexture().getTextureObjectHandle(), VR.ETextureType_TextureType_OpenGL, VR.EColorSpace_ColorSpace_Gamma);

                // Sprite batch for VR - uses backbuffer resolution
                globalResources.setSpriteBatchVR(new SpriteBatch(500, globalResources.getSpriteShader()));
                globalResources.getSpriteBatchVR().getProjectionMatrix().setToOrtho2D(0, 0, GlobalConf.screen.BACKBUFFER_WIDTH, GlobalConf.screen.BACKBUFFER_HEIGHT);

                // Enable visibility of 'Others' if off (for VR controllers)
                if (!GlobalConf.scene.VISIBILITY[ComponentType.Others.ordinal()]) {
                    EventManager.instance.post(Events.TOGGLE_VISIBILITY_CMD, "element.others", false, true);
                }
                return VRStatus.OK;
            } catch (Exception e) {
                // If initializing the VRContext failed
                GlobalConf.runtime.OPENVR = false;
                logger.error(e);
                logger.error("Initialisation of VR context failed");
                return VRStatus.ERROR_NO_CONTEXT;
            }
        } else {
            // Desktop mode
            GlobalConf.runtime.OPENVR = false;
            Constants.initialize(GlobalConf.scene.DIST_SCALE_DESKTOP);
        }
        return VRStatus.NO_VR;
    }

    /**
     * Execute this when the models have finished loading. This sets the models
     * to their classes and removes the Loading message
     */
    private void doneLoading() {
        windowCreated = true;
        // Dispose of initial and loading GUIs
        welcomeGui.dispose();
        welcomeGui = null;

        loadingGui.dispose();
        loadingGui = null;

        // Dispose vr loading GUI
        if (GlobalConf.runtime.OPENVR) {
            welcomeGuiVR.dispose();
            welcomeGuiVR = null;

            loadingGuiVR.dispose();
            loadingGuiVR = null;

            vrLoadingLeftTex.clear();
            vrLoadingLeftFb.dispose();
            vrLoadingLeftTex = null;
            vrLoadingLeftFb = null;

            vrLoadingRightTex.clear();
            vrLoadingRightFb.dispose();
            vrLoadingRightTex = null;
            vrLoadingRightFb = null;
        }

        // Get attitude
        if (manager.isLoaded(Constants.ATTITUDE_FOLDER)) {
            GaiaAttitudeServer.instance = manager.get(Constants.ATTITUDE_FOLDER);
        }

        /*
         * SAMP client
         */
        SAMPClient.getInstance().initialize(globalResources.getSkin());

        /*
         * POST-PROCESSOR
         */
        pp.doneLoading(manager);

        /*
         * GET SCENE GRAPH
         */
        if (manager.isLoaded(dataLoadString)) {
            sg = manager.get(dataLoadString);
        } else {
            throw new RuntimeException("Error loading scene graph from data load string: " + dataLoadString + ", and files: " + TextUtils.concatenate(File.pathSeparator, GlobalConf.data.CATALOG_JSON_FILES));
        }

        /*
         * SCENE GRAPH RENDERER
         */
        AbstractRenderer.initialize(sg);
        sgr.doneLoading(manager);
        sgr.resize(graphics.getWidth(), graphics.getHeight(), (int) Math.round(graphics.getWidth() * GlobalConf.screen.BACKBUFFER_SCALE), (int) Math.round(graphics.getHeight() * GlobalConf.screen.BACKBUFFER_SCALE));

        // First time, set assets
        Array<SceneGraphNode> nodes = sg.getNodes();
        for (SceneGraphNode sgn : nodes) {
            sgn.doneLoading(manager);
        }

        // Initialise input multiplexer to handle various input processors
        // The input multiplexer
        guiRegistry = new GuiRegistry(globalResources.getSkin(), sg);
        inputMultiplexer = new InputMultiplexer();
        guiRegistry.setInputMultiplexer(inputMultiplexer);
        Gdx.input.setInputProcessor(inputMultiplexer);

        // Stop updating log list
        consoleLogger.setUseHistorical(false);

        // Init GUIs, step 2
        reinitialiseGUI2();

        // Publish visibility
        EventManager.instance.post(Events.VISIBILITY_OF_COMPONENTS, SceneGraphRenderer.visible);

        // Key bindings
        inputMultiplexer.addProcessor(new KeyboardInputController(Gdx.input));

        EventManager.instance.post(Events.SCENE_GRAPH_LOADED, sg);

        // Update whole tree to initialize positions
        OctreeNode.LOAD_ACTIVE = false;
        time.update(0.000000001f);
        // Update whole scene graph
        sg.update(time, cam);
        sgr.clearLists();
        time.update(0);
        OctreeNode.LOAD_ACTIVE = true;

        // Initialise time in GUI
        EventManager.instance.post(Events.TIME_CHANGE_INFO, time.getTime());

        // Subscribe to events
        EventManager.instance.subscribe(this, Events.TOGGLE_AMBIENT_LIGHT, Events.AMBIENT_LIGHT_CMD, Events.RECORD_CAMERA_CMD, Events.CAMERA_MODE_CMD, Events.STEREOSCOPIC_CMD, Events.FRAME_SIZE_UPDATE, Events.SCREENSHOT_SIZE_UPDATE, Events.PARK_RUNNABLE, Events.UNPARK_RUNNABLE, Events.SCENE_GRAPH_ADD_OBJECT_CMD, Events.SCENE_GRAPH_ADD_OBJECT_NO_POST_CMD, Events.SCENE_GRAPH_REMOVE_OBJECT_CMD, Events.HOME_CMD, Events.UI_SCALE_CMD, Events.PER_OBJECT_VISIBILITY_CMD);

        // Re-enable input
        EventManager.instance.post(Events.INPUT_ENABLED_CMD, true);

        // Set current date
        EventManager.instance.post(Events.TIME_CHANGE_CMD, Instant.now());

        // Resize GUIs to current size
        for (IGui gui : guis)
            gui.resize(graphics.getWidth(), graphics.getHeight());

        if (GlobalConf.runtime.OPENVR) {
            // Resize post-processors and render systems
            postRunnable(() -> resizeImmediate(vrContext.getWidth(), vrContext.getHeight(), true, false, false, false));
        }

        // Initialise frames
        frames = 0;

        // Debug info scheduler
        Task debugTask1 = new Task() {
            @Override
            public void run() {
                // FPS
                EventManager.instance.post(Events.FPS_INFO, 1f / graphics.getDeltaTime());
                // Current session time
                EventManager.instance.post(Events.DEBUG_TIME, TimeUtils.timeSinceMillis(startTime) / 1000d);
                // Memory
                EventManager.instance.post(Events.DEBUG_RAM, MemInfo.getUsedMemory(), MemInfo.getFreeMemory(), MemInfo.getTotalMemory(), MemInfo.getMaxMemory());
                // Observed objects
                EventManager.instance.post(Events.DEBUG_OBJECTS, OctreeNode.nObjectsObserved, StreamingOctreeLoader.getNLoadedStars());
                // Observed octants
                EventManager.instance.post(Events.DEBUG_QUEUE, OctreeNode.nOctantsObserved, StreamingOctreeLoader.getLoadQueueSize());
                // VRAM
                EventManager.instance.post(Events.DEBUG_VRAM, VMemInfo.getUsedMemory(), VMemInfo.getTotalMemory());
            }
        };

        Task debugTask10 = new Task() {
            @Override
            public void run() {
                EventManager.instance.post(Events.SAMP_INFO, SAMPClient.getInstance().getStatus());
            }
        };

        // Every second
        Timer.schedule(debugTask1, 2, 1);
        // Every 10 seconds
        Timer.schedule(debugTask10, 2, 10);

        // Start capturing locations
        Task startCapturing = new Task() {
            @Override
            public void run() {
                LocationLogManager.instance().startCapturing();
            }
        };
        Timer.schedule(startCapturing, 1f);

        // Go home
        goHome();

        EventManager.instance.post(Events.INITIALIZED_INFO);
        initialized = true;
    }

    /**
     * Moves the camera home. That is either the Earth, if it exists, or somewhere close to the Sun
     */
    private void goHome() {
        IFocus homeObject = sg.findFocus(GlobalConf.scene.STARTUP_OBJECT);
        boolean isOn = true;
        if (homeObject != null && (isOn = GaiaSky.instance.isOn(homeObject.getCt())) && !GlobalConf.program.NET_SLAVE) {
            // Set focus to Earth
            EventManager.instance.post(Events.CAMERA_MODE_CMD, CameraMode.FOCUS_MODE);
            EventManager.instance.post(Events.FOCUS_CHANGE_CMD, homeObject, true);
            EventManager.instance.post(Events.GO_TO_OBJECT_CMD);
            if (GlobalConf.runtime.OPENVR) {
                // Free mode by default in VR
                EventManager.instance.postDelayed(Events.CAMERA_MODE_CMD, 1000L, CameraMode.FREE_MODE);
            }
        } else {
            // At 5 AU in Y looking towards origin (top-down look)
            EventManager.instance.post(Events.CAMERA_MODE_CMD, CameraMode.FREE_MODE);
            EventManager.instance.post(Events.CAMERA_POS_CMD, (Object) new double[] { 0d, 5d * Constants.AU_TO_U, 0d });
            EventManager.instance.post(Events.CAMERA_DIR_CMD, (Object) new double[] { 0d, -1d, 0d });
            EventManager.instance.post(Events.CAMERA_UP_CMD, (Object) new double[] { 0d, 0d, 1d });
        }

        if (!isOn) {
            Task t = new Task(){

                @Override
                public void run() {
                    logger.info("The home object '" + GlobalConf.scene.STARTUP_OBJECT + "' is invisible due to its type(s): " + homeObject.getCt());
                }
            };
            Timer.schedule(t, 1);
        }
    }

    /**
     * Re-initialises all the GUI (step 1)
     */
    public void reinitialiseGUI1() {
        if (guis != null && !guis.isEmpty()) {
            for (IGui gui : guis)
                gui.dispose();
            guis.clear();
        }

        mainGui = new FullGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE, globalResources);
        mainGui.initialize(manager, globalResources.getSpriteBatch());

        debugGui = new DebugGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE);
        debugGui.initialize(manager, globalResources.getSpriteBatch());

        spacecraftGui = new SpacecraftGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE);
        spacecraftGui.initialize(manager, globalResources.getSpriteBatch());

        stereoGui = new StereoGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE);
        stereoGui.initialize(manager, globalResources.getSpriteBatch());

        controllerGui = new ControllerGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE);
        controllerGui.initialize(manager, globalResources.getSpriteBatch());

        if (guis != null) {
            guis.add(mainGui);
            guis.add(debugGui);
            guis.add(spacecraftGui);
            guis.add(stereoGui);
            guis.add(controllerGui);
        }
    }

    /**
     * Second step in GUI initialisation.
     */
    public void reinitialiseGUI2() {
        // Reinitialise registry to listen to relevant events
        if (guiRegistry != null)
            guiRegistry.dispose();
        guiRegistry = new GuiRegistry(globalResources.getSkin(), sg);
        guiRegistry.setInputMultiplexer(inputMultiplexer);

        // Unregister all current GUIs
        guiRegistry.unregisterAll();

        // Only for the Full GUI
        ((FullGui) mainGui).setSceneGraph(sg);
        mainGui.setVisibilityToggles(ComponentType.values(), SceneGraphRenderer.visible);

        for (IGui gui : guis)
            gui.doneLoading(manager);

        if (GlobalConf.program.STEREOSCOPIC_MODE) {
            guiRegistry.set(stereoGui);
            guiRegistry.setPrevious(mainGui);
        } else {
            guiRegistry.set(mainGui);
            guiRegistry.setPrevious(null);
        }
        guiRegistry.registerGui(debugGui);
        guiRegistry.addProcessor(debugGui);

        guiRegistry.registerGui(controllerGui);
        guiRegistry.addProcessor(controllerGui);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (saveState && !crashed) {
            if (ConfInit.instance != null)
                ConfInit.instance.persistGlobalConf(new File(System.getProperty("properties.file")));
            if (BookmarksManager.instance != null)
                BookmarksManager.instance.persistBookmarks();
        }

        if (vrContext != null)
            vrContext.dispose();

        // Flush frames
        EventManager.instance.post(Events.FLUSH_FRAMES);

        // Dispose all
        if (guis != null)
            for (IGui gui : guis)
                gui.dispose();

        EventManager.instance.post(Events.DISPOSE);
        if (sg != null) {
            sg.dispose();
        }
        ModelCache.cache.dispose();

        // Shutdown dataset updater thread pool
        DatasetUpdater.shutDownThreadPool();

        // Scripting
        ScriptingServer.dispose();

        // Renderer
        if (sgr != null)
            sgr.dispose();

        // Post processor
        if (pp != null)
            pp.dispose();

        // Dispose music manager
        MusicManager.dispose();

        // Clear temp
        try {
            Path tmp = SysUtils.getTempDir(GlobalConf.data.DATA_LOCATION);
            if (java.nio.file.Files.exists(tmp) && java.nio.file.Files.isDirectory(tmp))
                GlobalResources.deleteRecursively(tmp);
        } catch (Exception e) {
            logger.error(e, "Error deleting tmp directory");
        }
    }

    /**
     * Renders the scene
     **/
    private final Runnable runnableRender = () -> {

        // Asynchronous load of textures and resources
        manager.update();

        if (!GlobalConf.runtime.UPDATE_PAUSE) {
            synchronized (frameMonitor) {
                frameMonitor.notify();
            }
            /*
             * UPDATE
             */
            update(graphics.getDeltaTime());

            /*
             * FRAME OUTPUT
             */
            EventManager.instance.post(Events.RENDER_FRAME, this);

            /*
             * SCREENSHOT OUTPUT - simple|redraw mode
             */
            EventManager.instance.post(Events.RENDER_SCREENSHOT, this);

            /*
             * SCREEN OUTPUT
             */
            if (GlobalConf.screen.SCREEN_OUTPUT) {
                int tw = graphics.getWidth();
                int th = graphics.getHeight();
                if (tw == 0 || th == 0) {
                    // Hack - on Windows the reported width and height is 0 when the window is minimized
                    tw = GlobalConf.screen.SCREEN_WIDTH;
                    th = GlobalConf.screen.SCREEN_HEIGHT;
                }
                int w = (int) (tw * GlobalConf.screen.BACKBUFFER_SCALE);
                int h = (int) (th * GlobalConf.screen.BACKBUFFER_SCALE);
                /* RENDER THE SCENE */
                preRenderScene();
                if (GlobalConf.runtime.OPENVR) {
                    renderSgr(cam, t, GlobalConf.screen.BACKBUFFER_WIDTH, GlobalConf.screen.BACKBUFFER_HEIGHT, tw, th, null, pp.getPostProcessBean(RenderType.screen));
                } else {
                    PostProcessBean ppb = pp.getPostProcessBean(RenderType.screen);
                    if (ppb != null)
                        renderSgr(cam, t, w, h, tw, th, null, ppb);
                }

                // Render the GUI, setting the viewport
                if (GlobalConf.runtime.OPENVR) {
                    guiRegistry.render(GlobalConf.screen.BACKBUFFER_WIDTH, GlobalConf.screen.BACKBUFFER_HEIGHT);
                } else {
                    guiRegistry.render(tw, th);
                }
            }
        }
        // Clean lists
        sgr.clearLists();
        // Number of frames
        frames++;

        if (GlobalConf.screen.LIMIT_FPS > 0.0) {
            sleep(GlobalConf.screen.LIMIT_FPS);
        } else if (GlobalConf.screen.DYNAMIC_RESOLUTION && TimeUtils.millis() - startTime > 10000 && TimeUtils.millis() - lastResolutionChange > 2000 && !GlobalConf.runtime.OPENVR) {
            // Dynamic resolution
            float fps = 1f / graphics.getDeltaTime();
            if (!lowResolution && fps < 20) {
                // Downscale
                GlobalConf.screen.BACKBUFFER_SCALE = 0.6f;
                resize(graphics.getWidth(), graphics.getHeight());
                postRunnable(() -> resizeImmediate(graphics.getWidth(), graphics.getHeight(), true, true, true, true));
                lowResolution = true;
                lastResolutionChange = TimeUtils.millis();
            } else if (lowResolution && fps > 60) {
                // Set to regular resolution
                GlobalConf.screen.BACKBUFFER_SCALE = 1f;
                postRunnable(() -> resizeImmediate(graphics.getWidth(), graphics.getHeight(), true, true, true, true));
                lowResolution = false;
                lastResolutionChange = TimeUtils.millis();
            }
        }
    };

    public FrameBuffer getBackRenderBuffer() {
        return sgr.getCurrentSGR().getResultBuffer();
    }

    /**
     * Displays the initial GUI
     **/
    private final Runnable runnableInitialGui = () -> {
        renderGui(welcomeGui);
        if (GlobalConf.runtime.OPENVR) {
            try {
                vrContext.pollEvents();
            } catch (Exception e) {
                logger.error(e);
            }

            renderVRGui((VRGui) welcomeGuiVR);
        }
    };

    /**
     * Displays the loading GUI
     **/
    private final Runnable runnableLoadingGui = () -> {
        boolean finished = false;
        try {
            finished = manager.update();
        } catch (GdxRuntimeException e) {
            // Resource failed to load
            logger.warn(e.getLocalizedMessage());
        }
        if (finished) {
            doneLoading();
            renderProcess = runnableRender;
        } else {
            // Display loading screen
            if (GlobalConf.runtime.OPENVR) {
                renderGui(loadingGui);

                try {
                    vrContext.pollEvents();
                } catch (Exception e) {
                    logger.error(e);
                }
                renderVRGui((VRGui) loadingGuiVR);
            } else {
                renderGui(loadingGui);
            }
        }
    };

    private void renderVRGui(VRGui vrGui) {
        vrLoadingLeftFb.begin();
        renderGui((vrGui).left());
        vrLoadingLeftFb.end();

        vrLoadingRightFb.begin();
        renderGui((vrGui).right());
        vrLoadingRightFb.end();

        /* SUBMIT TO VR COMPOSITOR */
        VRCompositor.VRCompositor_Submit(VR.EVREye_Eye_Left, vrLoadingLeftTex, null, VR.EVRSubmitFlags_Submit_Default);
        VRCompositor.VRCompositor_Submit(VR.EVREye_Eye_Right, vrLoadingRightTex, null, VR.EVRSubmitFlags_Submit_Default);
    }

    // Has the application crashed?
    private boolean crashed = false;

    public void setCrashed(boolean crashed) {
        this.crashed = crashed;
    }

    public boolean isCrashed() {
        return crashed;
    }

    @Override
    public void render() {
        try {
            if (!crashed) {
                // Run the render process
                renderProcess.run();

                // Run parked runnables
                synchronized (parkedRunnables) {
                    if (parkedRunnables.size > 0) {
                        Iterator<Runnable> it = parkedRunnables.iterator();
                        while (it.hasNext()) {
                            Runnable r = it.next();
                            try {
                                r.run();
                            } catch (Exception e) {
                                logger.error(e);
                                // If it crashed, remove it
                                it.remove();
                            }
                        }
                    }
                }
            } else if (crashGui != null) {
                // Crash information
                renderGui(crashGui);
            }
        } catch (Throwable t) {
            // Report the crash
            CrashReporter.reportCrash(t, logger);
            // Set up crash window
            crashGui = new CrashGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE, t);
            crashGui.initialize(manager, globalResources.getSpriteBatch());
            Gdx.input.setInputProcessor(crashGui.getGuiStage());
            // Flag up
            crashed = true;
        }

        // Create UI window if needed
        if (externalView && gaiaSkyView == null) {
            postRunnable(() -> {
                // Create window
                Lwjgl3Application app = (Lwjgl3Application) Gdx.app;
                Lwjgl3WindowConfiguration config = new Lwjgl3WindowConfiguration();
                config.setWindowPosition(0, 0);
                config.setWindowedMode(graphics.getWidth(), graphics.getHeight());
                config.setTitle(GlobalConf.APPLICATION_NAME + " - External view");
                config.useVsync(false);
                config.setWindowIcon(Files.FileType.Internal, "icon/gs_icon.png");
                gaiaSkyView = new GaiaSkyView(globalResources.getSkin(), globalResources.getSpriteShader());
                Lwjgl3Window newWindow = app.newWindow(gaiaSkyView, config);
                gaiaSkyView.setWindow(newWindow);
            });
        }
    }

    private long start = System.currentTimeMillis();

    private void sleep(double fps) {
        if (fps > 0) {
            long diff = System.currentTimeMillis() - start;
            long targetDelay = Math.round((1000.0 / fps));
            if (diff < targetDelay) {
                try {
                    Thread.sleep(targetDelay - diff);
                } catch (InterruptedException ignored) {
                }
            }
            start = System.currentTimeMillis();
        }
    }

    /**
     * Update method.
     *
     * @param dt Delta time in seconds.
     */
    public void update(double dt) {
        // Resize if needed
        updateResize();

        Timer.instance();
        // The actual frame time difference in seconds
        double dtGs;
        if (GlobalConf.frame.RENDER_OUTPUT) {
            // If RENDER_OUTPUT is active, we need to set our dt according to
            // the fps
            dtGs = 1.0 / GlobalConf.frame.RENDER_TARGET_FPS;
        } else if (camRecording) {
            // If Camera is recording, we need to set our dt according to
            // the fps
            dtGs = 1.0 / GlobalConf.frame.CAMERA_REC_TARGET_FPS;
        } else {
            // Max time step is 0.05 seconds. Not in RENDER_OUTPUT MODE.
            dtGs = Math.min(dt, 0.05);
        }

        this.t += dtGs;

        // Update GUI 
        guiRegistry.update(dtGs);
        EventManager.instance.post(Events.UPDATE_GUI, dtGs);

        // Update clock
        time.update(dtGs);

        // Update events
        EventManager.instance.dispatchDelayedMessages();

        // Update cameras
        cam.update(dtGs, time);

        // Update GravWaves params
        RelativisticEffectsManager.getInstance().update(time, cam.current);

        // Update scene graph
        sg.update(time, cam);

        // Swap proximity buffers
        cam.swapBuffers();

    }

    public void preRenderScene() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
    }

    public void renderSgr(ICamera camera, double t, int width, int height, int tw, int th, FrameBuffer frameBuffer, PostProcessBean ppb) {
        sgr.render(camera, t, width, height, tw, th, frameBuffer, ppb);
    }

    private long lastResizeTime = Long.MAX_VALUE;
    private int resizeWidth, resizeHeight;

    @Override
    public void resize(final int width, final int height) {
        if (width != 0 && height != 0) {
            if (!initialized) {
                resizeImmediate(width, height, true, true, true, true);
            }

            resizeWidth = width;
            resizeHeight = height;
            lastResizeTime = System.currentTimeMillis();

            if (renderBatch != null)
                renderBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        }
    }

    private void updateResize() {
        long currResizeTime = System.currentTimeMillis();
        if (currResizeTime - lastResizeTime > 100L) {
            resizeImmediate(resizeWidth, resizeHeight, !GlobalConf.runtime.OPENVR, !GlobalConf.runtime.OPENVR, true, true);
            lastResizeTime = Long.MAX_VALUE;
        }
    }

    public void resizeImmediate(final int width, final int height, boolean resizePostProcessors, boolean resizeRenderSys, boolean resizeGuis, boolean resizeScreenConf) {
        try {
            int renderWidth = (int) Math.round(width * GlobalConf.screen.BACKBUFFER_SCALE);
            int renderHeight = (int) Math.round(height * GlobalConf.screen.BACKBUFFER_SCALE);

            // Resize global UI sprite batch
            globalResources.getSpriteBatch().getProjectionMatrix().setToOrtho2D(0, 0, renderWidth, renderHeight);

            if (!initialized) {
                if (welcomeGui != null)
                    welcomeGui.resize(width, height);
                if (loadingGui != null)
                    loadingGui.resizeImmediate(width, height);
            } else {
                if (resizePostProcessors)
                    pp.resizeImmediate(renderWidth, renderHeight);

                if (resizeGuis)
                    for (IGui gui : guis)
                        gui.resizeImmediate(width, height);

                sgr.resize(width, height, renderWidth, renderHeight, resizeRenderSys);

                if (resizeScreenConf)
                    GlobalConf.screen.resize(width, height);
            }

            cam.updateAngleEdge(renderWidth, renderHeight);
            cam.resize(width, height);
        } catch (Exception e) {
            // TODO This try-catch block is a provisional fix for Windows, as GLFW crashes when minimizing with lwjgl 3.2.3 and libgdx 1.9.10
        }
    }

    /**
     * Renders a particular GUI
     *
     * @param gui The GUI to render
     */
    private void renderGui(IGui gui) {
        gui.update(graphics.getDeltaTime());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        gui.render(graphics.getWidth(), graphics.getHeight());
    }

    public Array<IFocus> getFocusableEntities() {
        return sg.getFocusableObjects();
    }

    public FrameBuffer getFrameBuffer(int w, int h) {
        String key = getKey(w, h);
        if (!frameBufferMap.containsKey(key)) {
            FrameBuffer fb = PingPongBuffer.createMainFrameBuffer(w, h, true, true, Format.RGB888, true);
            frameBufferMap.put(key, fb);
        }
        return frameBufferMap.get(key);
    }

    private String getKey(int w, int h) {
        return w + "x" + h;
    }

    public HashMap<VRDevice, StubModel> getVRDeviceToModel() {
        return vrDeviceToModel;
    }

    public GuiRegistry getGuiRegistry() {
        return this.guiRegistry;
    }

    public ICamera getICamera() {
        return cam.current;
    }

    public double getT() {
        return t;
    }

    public CameraManager getCameraManager() {
        return cam;
    }

    public IPostProcessor getPostProcessor() {
        return pp;
    }

    public boolean isOn(int ordinal) {
        return sgr.isOn(ordinal);
    }

    public boolean isOn(ComponentType comp) {
        return sgr.isOn(comp);
    }

    public boolean isOn(ComponentTypes cts) {
        return sgr.allOn(cts);
    }

    @Override
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case LOAD_DATA_CMD:
            // Init components that need assets in data folder
            reinitialiseGUI1();
            pp.initialize(manager);

            // Initialise loading screen
            loadingGui = new LoadingGui(globalResources.getSkin(), graphics, 1f / GlobalConf.program.UI_SCALE, false);
            loadingGui.initialize(manager, globalResources.getSpriteBatch());

            Gdx.input.setInputProcessor(loadingGui.getGuiStage());

            // Also VR
            if (GlobalConf.runtime.OPENVR) {
                loadingGuiVR = new VRGui<>(LoadingGui.class, (int) (GlobalConf.screen.BACKBUFFER_WIDTH / 4f), graphics, 1f / GlobalConf.program.UI_SCALE);
                loadingGuiVR.initialize(manager, globalResources.getSpriteBatch());
            }

            this.renderProcess = runnableLoadingGui;

            /* LOAD SCENE GRAPH */
            if (sg == null) {
                dataLoadString = "SceneGraphData";
                String[] dataFilesToLoad = new String[GlobalConf.data.CATALOG_JSON_FILES.size + 1];
                // Prepare files to load
                int i = 0;
                for (String dataFile : GlobalConf.data.CATALOG_JSON_FILES) {
                    dataFilesToLoad[i] = dataFile;
                    i++;
                }
                dataFilesToLoad[i] = GlobalConf.data.OBJECTS_JSON_FILES;
                manager.load(dataLoadString, ISceneGraph.class, new SGLoaderParameter(dataFilesToLoad, time, GlobalConf.performance.MULTITHREADING, GlobalConf.performance.NUMBER_THREADS()));
            }
            break;
        case TOGGLE_AMBIENT_LIGHT:
            // TODO No better place to put this??
            ModelComponent.toggleAmbientLight((Boolean) data[1]);
            break;
        case AMBIENT_LIGHT_CMD:
            ModelComponent.setAmbientLight((float) data[0]);
            break;
        case RECORD_CAMERA_CMD:
            if (data != null && data.length > 0) {
                camRecording = (Boolean) data[0];
            } else {
                camRecording = !camRecording;
            }
            break;
        case CAMERA_MODE_CMD:
            // Register/unregister GUI
            CameraMode mode = (CameraMode) data[0];
            if (GlobalConf.program.isStereoHalfViewport()) {
                guiRegistry.change(stereoGui);
            } else if (mode == CameraMode.SPACECRAFT_MODE) {
                guiRegistry.change(spacecraftGui);
            } else {
                guiRegistry.change(mainGui);
            }
            break;
        case STEREOSCOPIC_CMD:
            boolean stereoMode = (Boolean) data[0];
            if (stereoMode && guiRegistry.current != stereoGui) {
                guiRegistry.change(stereoGui);
            } else if (!stereoMode && guiRegistry.previous != stereoGui) {
                IGui prev = guiRegistry.current != null ? guiRegistry.current : mainGui;
                guiRegistry.change(guiRegistry.previous, prev);
            }

            // Post a message to the screen
            if (stereoMode) {
                ModePopupInfo mpi = new ModePopupInfo();
                mpi.title = "Stereoscopic mode";
                mpi.header = "You have entered Stereoscopic mode!";
                mpi.addMapping("Back to normal mode", "CTRL", "S");
                mpi.addMapping("Switch stereo profile", "CTRL", "SHIFT", "S");

                EventManager.instance.post(Events.MODE_POPUP_CMD, mpi, "stereo", 120f);
            } else {
                EventManager.instance.post(Events.MODE_POPUP_CMD, null, "stereo");
            }

            break;
        case SCREENSHOT_SIZE_UPDATE:
        case FRAME_SIZE_UPDATE:
            //GaiaSky.postRunnable(() -> {
            //clearFrameBufferMap();
            //});
            break;
        case SCENE_GRAPH_ADD_OBJECT_CMD:
            final SceneGraphNode nodeToAdd = (SceneGraphNode) data[0];
            final boolean addToIndex = data.length == 1 || (Boolean) data[1];
            if (sg != null) {
                postRunnable(() -> {
                    try {
                        sg.insert(nodeToAdd, addToIndex);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                });
            }
            break;
        case SCENE_GRAPH_ADD_OBJECT_NO_POST_CMD:
            final SceneGraphNode nodeToAddp = (SceneGraphNode) data[0];
            final boolean addToIndexp = data.length == 1 || (Boolean) data[1];
            if (sg != null) {
                try {
                    sg.insert(nodeToAddp, addToIndexp);
                } catch (Exception e) {
                    logger.error(e);
                }
            }
            break;
        case SCENE_GRAPH_REMOVE_OBJECT_CMD:
            SceneGraphNode aux;
            if (data[0] instanceof String) {
                aux = sg.getNode((String) data[0]);
                if (aux == null)
                    return;
            } else {
                aux = (SceneGraphNode) data[0];
            }
            final SceneGraphNode nodeToRemove = aux;
            final boolean removeFromIndex = data.length == 1 || (Boolean) data[1];
            if (sg != null) {
                postRunnable(() -> sg.remove(nodeToRemove, removeFromIndex));
            }
            break;
        case UI_SCALE_CMD:
            if (guis != null) {
                float uiScale = (Float) data[0];
                for (IGui gui : guis) {
                    gui.updateUnitsPerPixel(1f / uiScale);
                }
            }
            break;
        case HOME_CMD:
            goHome();
            break;
        case PER_OBJECT_VISIBILITY_CMD:
            IVisibilitySwitch vs = (IVisibilitySwitch) data[0];
            String name = (String) data[1];
            boolean state = (boolean) data[2];
            vs.setVisible(state, name);
            logger.info(I18n.txt("notif.visibility.object.set", vs.getName(), state));
            break;
        case PARK_RUNNABLE:
            synchronized (parkedRunnables) {
                String key = (String) data[0];
                Runnable runnable = (Runnable) data[1];
                parkRunnable(key, runnable);
            }
            break;
        case UNPARK_RUNNABLE:
            synchronized (parkedRunnables) {
                String key = (String) data[0];
                unparkRunnable(key);
            }
            break;
        default:
            break;
        }

    }

    public boolean isInitialised() {
        return initialized;
    }

    public GlobalResources getGlobalResources() {
        return this.globalResources;
    }

    /**
     * Parks a runnable that will run every frame right the update() method (before render)
     * until it is unparked.
     *
     * @param key      The key to identify the runnable.
     * @param runnable The runnable.
     */
    public void parkRunnable(String key, Runnable runnable) {
        parkedRunnablesMap.put(key, runnable);
        parkedRunnables.add(runnable);
    }

    /**
     * Unparks a previously parked runnable.
     *
     * @param key The key of the runnable to unpark.
     */
    public void unparkRunnable(String key) {
        Runnable r = parkedRunnablesMap.get(key);
        if (r != null) {
            parkedRunnables.removeValue(r, true);
            parkedRunnablesMap.remove(key);
        }
    }

    /**
     * Posts a runnable that will run once after the current frame.
     *
     * @param r The runnable to post.
     */
    public static void postRunnable(Runnable r) {
        if (instance != null && instance.window != null)
            instance.window.postRunnable(r);
        else
            Gdx.app.postRunnable(r);
    }

}
