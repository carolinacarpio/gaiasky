/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;
import gaiasky.GaiaSky;
import gaiasky.data.group.DatasetOptions;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.scene.Scene;
import gaiasky.scene.entity.EntityUtils;
import gaiasky.scene.view.FocusView;
import gaiasky.scene.camera.CameraManager;
import gaiasky.util.*;
import gaiasky.util.CatalogInfo.CatalogInfoSource;
import gaiasky.util.i18n.I18n;
import gaiasky.util.parse.Parser;
import gaiasky.util.scene2d.FileChooser;
import gaiasky.util.scene2d.OwnLabel;
import gaiasky.util.scene2d.OwnTextButton;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Manages the Graphical User Interfaces of Gaia Sky
 */
public class GuiRegistry implements IObserver {
    private static final Logger.Log logger = Logger.getLogger(GuiRegistry.class);

    private Skin skin;

    private PreferencesWindow preferencesWindow;
    private AboutWindow aboutWindow;
    private SearchDialog searchDialog;

    /**
     * Keyframes window
     **/
    private KeyframesWindow keyframesWindow;

    /**
     * Individual visibility
     */
    private IndividualVisibilityWindow indVisWindow;

    /**
     * Mode change info popup
     */
    public Table modeChangeInfoPopup;

    /** Task to remove the information pop-up. **/
    private Task removePopup;

    /**
     * Last open location
     */
    private Path lastOpenLocation;

    /* Slave config window */
    private SlaveConfigWindow slaveConfigWindow;

    /** Scene reference. **/
    protected final Scene scene;

    /**
     * Registered GUI array
     **/
    private final Array<IGui> guis;

    /**
     * Render lock object
     */
    private final Object renderLock = new Object();

    /**
     * Current GUI object
     **/
    public IGui current;
    /**
     * Previous GUI object, if any
     **/
    public IGui previous;

    /**
     * Global input multiplexer
     **/
    private InputMultiplexer inputMultiplexer = null;

    /**
     * The catalog manager
     */
    private final CatalogManager catalogManager;

    private final FocusView view;

    /**
     * Create new GUI registry object.
     */
    public GuiRegistry(final Skin skin, final Scene scene, final CatalogManager catalogManager) {
        super();
        this.skin = skin;
        this.scene = scene;
        this.guis = new Array<>(true, 2);
        this.catalogManager = catalogManager;
        this.view = new FocusView();
        // Windows which are visible from any GUI
        EventManager.instance.subscribe(this, Event.SHOW_SEARCH_ACTION, Event.SHOW_QUIT_ACTION, Event.SHOW_ABOUT_ACTION, Event.SHOW_LOAD_CATALOG_ACTION, Event.SHOW_PREFERENCES_ACTION, Event.SHOW_KEYFRAMES_WINDOW_ACTION, Event.SHOW_SLAVE_CONFIG_ACTION, Event.UI_THEME_RELOAD_INFO, Event.MODE_POPUP_CMD, Event.DISPLAY_GUI_CMD, Event.CAMERA_MODE_CMD, Event.UI_RELOAD_CMD, Event.SHOW_PER_OBJECT_VISIBILITY_ACTION, Event.SHOW_RESTART_ACTION);
    }

    public void setInputMultiplexer(InputMultiplexer inputMultiplexer) {
        this.inputMultiplexer = inputMultiplexer;
    }

    public InputMultiplexer getInputMultiplexer() {
        return this.inputMultiplexer;
    }

    /**
     * Switches the current GUI with the given one, updating the processors.
     * It also sets the previous GUI to the given value.
     *
     * @param gui      The new GUI
     * @param previous The new previous GUI
     */
    public void change(IGui gui, IGui previous) {
        if (current != gui) {
            unset(previous);
            set(gui);
        }
    }

    /**
     * Switches the current GUI with the given one, updating the processors
     *
     * @param gui The new gui
     */
    public void change(IGui gui) {
        if (current != gui) {
            unset();
            set(gui);
        }
    }

    /**
     * Unsets the current GUI and sets it as previous
     */
    public void unset() {
        unset(current);
    }

    /**
     * Unsets the given GUI and sets it as previous
     *
     * @param gui The GUI
     */
    public void unset(IGui gui) {
        if (gui != null) {
            unregisterGui(gui);
            inputMultiplexer.removeProcessor(gui.getGuiStage());
        }
        previous = gui;
    }

    /**
     * Sets the given GUI as current
     *
     * @param gui The new GUI
     */
    public void set(IGui gui) {
        if (gui != null) {
            registerGui(gui);
            inputMultiplexer.addProcessor(0, gui.getGuiStage());
        }
        current = gui;
    }

    /**
     * Sets the given GUI as previous
     *
     * @param gui The new previous GUI
     */
    public void setPrevious(IGui gui) {
        previous = gui;
    }

    /**
     * Registers a new GUI
     *
     * @param gui The GUI to register
     */
    public void registerGui(IGui gui) {
        if (!guis.contains(gui, true)) {
            guis.add(gui);
        }
    }

    /**
     * Unregisters a GUI
     *
     * @param gui The GUI to unregister
     *
     * @return True if the GUI was unregistered
     */
    public boolean unregisterGui(IGui gui) {
        return guis.removeValue(gui, true);
    }

    /**
     * Unregisters all GUIs
     *
     * @return True if operation succeeded
     */
    public boolean unregisterAll() {
        guis.clear();
        return true;
    }

    /**
     * Renders the registered GUIs
     *
     * @param rw The render width
     * @param rh The render height
     */
    public void render(int rw, int rh) {
        if (Settings.settings.runtime.displayGui) {
            synchronized (renderLock) {
                for (int i = 0; i < guis.size; i++) {
                    guis.get(i).getGuiStage().getViewport().apply();
                    try {
                        guis.get(i).render(rw, rh);
                    } catch (Exception ignored) {

                    }
                }
            }
        }
    }

    /**
     * Adds the stage of the given GUI to the processors in
     * the input multiplexer
     *
     * @param gui The gui
     */
    public void addProcessor(IGui gui) {
        if (inputMultiplexer != null && gui != null)
            inputMultiplexer.addProcessor(gui.getGuiStage());
    }

    public void removeProcessor(IGui gui) {
        if (inputMultiplexer != null && gui != null)
            inputMultiplexer.removeProcessor(gui.getGuiStage());
    }

    /**
     * Updates the registered GUIs
     *
     * @param dt The delta time in seconds
     */
    public void update(double dt) {
        for (IGui gui : guis)
            gui.update(dt);
    }

    public void publishReleaseNotes() {
        // Check release notes if needed
        Path releaseNotesRev = SysUtils.getReleaseNotesRevisionFile();
        int releaseNotesVersion = 0;
        if (Files.exists(releaseNotesRev) && Files.isRegularFile(releaseNotesRev)) {
            try {
                String contents = Files.readString(releaseNotesRev).trim();
                releaseNotesVersion = Parser.parseInt(contents);
            } catch (Exception e) {
                logger.warn(I18n.msg("error.file.read", releaseNotesRev.toString()));
            }
        }

        if (releaseNotesVersion < Settings.settings.version.versionNumber) {
            Path releaseNotesFile = SysUtils.getReleaseNotesFile();
            if (Files.exists(releaseNotesFile)) {
                final Task releaseNotesTask = new Task() {
                    @Override
                    public void run() {
                        Stage ui = current.getGuiStage();
                        ReleaseNotesWindow releaseNotesWindow = new ReleaseNotesWindow(ui, skin, releaseNotesFile);
                        releaseNotesWindow.show(ui);
                    }
                };
                Timer.schedule(releaseNotesTask, 3);
            }
        }
    }

    public void dispose() {
        EventManager.instance.removeAllSubscriptions(this);
        if (searchDialog != null)
            searchDialog.dispose();
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        if (current != null) {
            Stage ui = current.getGuiStage();
            // Treats windows that can appear in any GUI
            switch (event) {
            case SHOW_SEARCH_ACTION:
                if (searchDialog == null) {
                    searchDialog = new SearchDialog(skin, ui, scene, true);
                } else {
                    searchDialog.clearText();
                }
                if (!searchDialog.isVisible() | !searchDialog.hasParent())
                    searchDialog.show(ui);
                break;
            case SHOW_QUIT_ACTION:
                if (!removeModeChangePopup() && !removeControllerGui()) {
                    if (GLFW.glfwGetInputMode(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle(), GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED) {
                        // Release mouse if captured
                        GLFW.glfwSetInputMode(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                    } else {
                        Runnable quitRunnable = data.length > 0 ? (Runnable) data[0] : null;
                        if (Settings.settings.program.exitConfirmation) {
                            QuitWindow quit = new QuitWindow(ui, skin);
                            if (data.length > 0) {
                                quit.setAcceptRunnable(quitRunnable);
                            }
                            quit.show(ui);
                        } else {
                            if (quitRunnable != null)
                                quitRunnable.run();
                            GaiaSky.postRunnable(() -> Gdx.app.exit());
                        }
                    }
                }
                break;
            case CAMERA_MODE_CMD:
                removeModeChangePopup();
                break;
            case SHOW_ABOUT_ACTION:
                if (aboutWindow == null) {
                    aboutWindow = new AboutWindow(ui, skin);
                }
                if (!aboutWindow.isVisible() || !aboutWindow.hasParent()) {
                    aboutWindow.show(ui);
                }
                break;
            case SHOW_PREFERENCES_ACTION:
                Array<Actor> prefs = getElementsOfType(PreferencesWindow.class);
                if (prefs.isEmpty()) {
                    if (preferencesWindow == null) {
                        preferencesWindow = new PreferencesWindow(ui, skin, GaiaSky.instance.getGlobalResources());
                    }
                    if (!preferencesWindow.isVisible() || !preferencesWindow.hasParent()) {
                        preferencesWindow.show(ui);
                    }
                } else {
                    // Close current windows
                    for (Actor pref : prefs) {
                        if (pref instanceof PreferencesWindow) {
                            ((PreferencesWindow) pref).cancel();
                            ((PreferencesWindow) pref).hide();
                        }
                    }
                }
                break;
            case SHOW_PER_OBJECT_VISIBILITY_ACTION:
                if (indVisWindow == null) {
                    indVisWindow = new IndividualVisibilityWindow(scene, ui, skin);
                }
                if (!indVisWindow.isVisible() || !indVisWindow.hasParent())
                    indVisWindow.show(ui);
                break;
            case SHOW_SLAVE_CONFIG_ACTION:
                if (MasterManager.hasSlaves()) {
                    if (slaveConfigWindow == null) {
                        slaveConfigWindow = new SlaveConfigWindow(ui, skin);
                    }
                    if (!slaveConfigWindow.isVisible() || !slaveConfigWindow.hasParent()) {
                        slaveConfigWindow.show(ui);
                    }
                }
                break;
            case SHOW_LOAD_CATALOG_ACTION:
                if (lastOpenLocation == null && Settings.settings.program.fileChooser.lastLocation != null && !Settings.settings.program.fileChooser.lastLocation.isEmpty()) {
                    try {
                        lastOpenLocation = Paths.get(Settings.settings.program.fileChooser.lastLocation);
                    } catch (Exception e) {
                        lastOpenLocation = null;
                    }
                }
                if (lastOpenLocation == null) {
                    lastOpenLocation = SysUtils.getUserHome();
                } else if (!Files.exists(lastOpenLocation) || !Files.isDirectory(lastOpenLocation)) {
                    lastOpenLocation = SysUtils.getHomeDir();
                }

                FileChooser fc = new FileChooser(I18n.msg("gui.loadcatalog"), skin, ui, lastOpenLocation, FileChooser.FileChooserTarget.FILES);
                fc.setShowHidden(Settings.settings.program.fileChooser.showHidden);
                fc.setShowHiddenConsumer((showHidden) -> Settings.settings.program.fileChooser.showHidden = showHidden);
                fc.setAcceptText(I18n.msg("gui.loadcatalog"));
                fc.setFileFilter(pathname -> pathname.getFileName().toString().endsWith(".vot") || pathname.getFileName().toString().endsWith(".csv") || pathname.getFileName().toString().endsWith(".fits") || pathname.getFileName().toString().matches("^catalog-[\\w-]+.json$"));
                fc.setAcceptedFiles("*.vot, *.csv, *.fits, catalog-*.json");
                fc.setResultListener((success, result) -> {
                    if (success) {
                        if (Files.exists(result) && Files.exists(result)) {
                            // Load selected file
                            try {
                                String fileName = result.getFileName().toString();
                                if (fileName.endsWith(".json")) {
                                    // Load internal JSON catalog file
                                    GaiaSky.instance.getExecutorService().execute(() -> {
                                        GaiaSky.instance.scripting().loadJsonCatalog(fileName, result.toAbsolutePath().toString());
                                    });
                                } else {
                                    final DatasetLoadDialog dld = new DatasetLoadDialog(I18n.msg("gui.dsload.title") + ": " + fileName, fileName, skin, ui);
                                    Runnable doLoad = () -> {
                                        GaiaSky.instance.getExecutorService().execute(() -> {
                                            DatasetOptions datasetOptions = dld.generateDatasetOptions();
                                            // Load dataset
                                            GaiaSky.instance.scripting().loadDataset(datasetOptions.catalogName, result.toAbsolutePath().toString(), CatalogInfoSource.UI, datasetOptions, true);
                                            // Select first
                                            CatalogInfo ci = this.catalogManager.get(datasetOptions.catalogName);
                                            if (datasetOptions.type.isSelectable() && ci != null && ci.entity != null) {
                                                view.setEntity(ci.entity);
                                                if (view.isSet()) {
                                                    var set = view.getSet();
                                                    if (set.data() != null && !set.data().isEmpty() && EntityUtils.isVisibilityOn(ci.entity)) {
                                                        EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraManager.CameraMode.FOCUS_MODE);
                                                        EventManager.publish(Event.FOCUS_CHANGE_CMD, this, set.getRandomParticleName());
                                                    }
                                                } else if (view.getGraph().children != null && !view.getGraph().children.isEmpty() && EntityUtils.isVisibilityOn(view.getGraph().children.get(0))) {
                                                    EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraManager.CameraMode.FOCUS_MODE);
                                                    EventManager.publish(Event.FOCUS_CHANGE_CMD, this, EntityUtils.isVisibilityOn(view.getGraph().children.get(0)));
                                                }
                                                // Open UI datasets
                                                GaiaSky.instance.scripting().maximizeInterfaceWindow();
                                                GaiaSky.instance.scripting().expandGuiComponent("DatasetsComponent");
                                            } else {
                                                logger.info("No data loaded (did the load crash?)");
                                            }
                                        });
                                    };
                                    dld.setAcceptRunnable(doLoad);
                                    dld.show(ui);
                                }

                                lastOpenLocation = result.getParent();
                                Settings.settings.program.fileChooser.lastLocation = lastOpenLocation.toAbsolutePath().toString();
                                return true;
                            } catch (Exception e) {
                                logger.error(I18n.msg("notif.error", result.getFileName()), e);
                                return false;
                            }

                        } else {
                            logger.error("Selection must be a file: " + result.toAbsolutePath());
                            return false;
                        }
                    } else {
                        // Still, update last location
                        if (!Files.isDirectory(result)) {
                            lastOpenLocation = result.getParent();
                        } else {
                            lastOpenLocation = result;
                        }
                        Settings.settings.program.fileChooser.lastLocation = lastOpenLocation.toAbsolutePath().toString();
                    }
                    return false;
                });
                fc.show(ui);
                break;
            case SHOW_KEYFRAMES_WINDOW_ACTION:
                if (keyframesWindow == null) {
                    keyframesWindow = new KeyframesWindow(scene, ui, skin);
                }
                if (!keyframesWindow.isVisible() || !keyframesWindow.hasParent())
                    keyframesWindow.show(ui, 0, 0);
                if (!GaiaSky.instance.isOn(ComponentType.Others)) {
                    // Notify that the user needs to enable 'others'
                    EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, I18n.msg("notif.keyframe.ct"), 10f);
                }
                break;
            case UI_THEME_RELOAD_INFO:
                if (keyframesWindow != null) {
                    keyframesWindow.dispose();
                    keyframesWindow = null;
                }
                this.skin = (Skin) data[0];
                break;
            case MODE_POPUP_CMD:
                if (Settings.settings.runtime.displayGui && Settings.settings.program.ui.modeChangeInfo) {
                    ModePopupInfo mpi = (ModePopupInfo) data[0];
                    String name = (String) data[1];

                    if (mpi != null) {
                        // Add
                        Float seconds = (Float) data[2];
                        float pad10 = 16f;
                        float pad5 = 8f;
                        float pad3 = 4.8f;
                        if (modeChangeInfoPopup != null) {
                            modeChangeInfoPopup.remove();
                        }
                        modeChangeInfoPopup = new Table(skin);
                        modeChangeInfoPopup.setName("mct-" + name);
                        modeChangeInfoPopup.setBackground("table-bg");
                        modeChangeInfoPopup.pad(pad10);

                        // Fill up table
                        OwnLabel ttl = new OwnLabel(mpi.title, skin, "hud-header");
                        modeChangeInfoPopup.add(ttl).left().padBottom(pad10).row();

                        OwnLabel dsc = new OwnLabel(mpi.header, skin);
                        modeChangeInfoPopup.add(dsc).left().padBottom(pad5 * 3f).row();

                        Table keysTable = new Table(skin);
                        for (Pair<String[], String> m : mpi.mappings) {
                            HorizontalGroup keysGroup = new HorizontalGroup();
                            keysGroup.space(pad3);
                            String[] keys = m.getFirst();
                            String action = m.getSecond();
                            if (keys != null && keys.length > 0 && action != null && !action.isEmpty()) {
                                for (int i = 0; i < keys.length; i++) {
                                    TextButton key = new TextButton(keys[i], skin, "key");
                                    key.pad(0, pad3, 0, pad3);
                                    key.pad(pad5);
                                    keysGroup.addActor(key);
                                    if (i < keys.length - 1) {
                                        keysGroup.addActor(new OwnLabel("+", skin));
                                    }
                                }
                                keysTable.add(keysGroup).right().padBottom(pad5).padRight(pad10 * 2f);
                                keysTable.add(new OwnLabel(action, skin)).left().padBottom(pad5).row();
                            }
                        }
                        modeChangeInfoPopup.add(keysTable).center().row();
                        if (mpi.warn != null && !mpi.warn.isEmpty()) {
                            modeChangeInfoPopup.add(new OwnLabel(mpi.warn, skin, "mono-pink")).left().padTop(pad10).padBottom(pad5).row();
                        }
                        OwnTextButton closeButton = new OwnTextButton(I18n.msg("gui.ok") + " [esc]", skin);
                        closeButton.pad(pad3, pad10, pad3, pad10);
                        closeButton.addListener(event1 -> {
                            if (event1 instanceof ChangeEvent) {
                                removeModeChangePopup();
                                return true;
                            }
                            return false;
                        });
                        modeChangeInfoPopup.add(closeButton).right().padTop(pad10 * 2f);

                        modeChangeInfoPopup.pack();

                        // Add table to UI
                        Container<Table> mct = new Container<>(modeChangeInfoPopup);
                        mct.setFillParent(true);
                        mct.top();
                        mct.pad(pad10 * 2, 0, 0, 0);
                        ui.addActor(mct);

                        // Cancel and schedule task
                        cancelRemovePopupTask();
                        removePopup = new Task() {
                            @Override
                            public void run() {
                                if (modeChangeInfoPopup != null && modeChangeInfoPopup.hasParent()) {
                                    modeChangeInfoPopup.remove();
                                }
                            }
                        };
                        Timer.schedule(removePopup, seconds);

                    } else {
                        // Remove
                        if (modeChangeInfoPopup != null && modeChangeInfoPopup.hasParent() && modeChangeInfoPopup.getName().equals("mct-" + name)) {
                            modeChangeInfoPopup.remove();
                            modeChangeInfoPopup = null;
                        }
                    }
                }
                break;
            case DISPLAY_GUI_CMD:
                boolean displayGui = (Boolean) data[0];
                if (!displayGui) {
                    // Remove processor
                    inputMultiplexer.removeProcessor(current.getGuiStage());
                } else {
                    // Add processor
                    inputMultiplexer.addProcessor(0, current.getGuiStage());
                }
                break;
            case SHOW_RESTART_ACTION:
                String text;
                if (data.length > 0) {
                    text = (String) data[0];
                } else {
                    text = I18n.msg("gui.restart.default");
                }
                GenericDialog restart = new GenericDialog(I18n.msg("gui.restart.title"), skin, ui) {

                    @Override
                    protected void build() {
                        content.clear();
                        content.add(new OwnLabel(text, skin)).left().padBottom(pad10 * 2f).row();
                    }

                    @Override
                    protected void accept() {
                        // Shut down
                        GaiaSky.postRunnable(() -> {
                            Gdx.app.exit();
                            // Attempt restart
                            Path workingDir = Path.of(System.getProperty("user.dir"));
                            Path[] scripts;
                            if (SysUtils.isWindows()) {
                                scripts = new Path[] { workingDir.resolve("gaiasky.exe"), workingDir.resolve("gaiasky.bat"), workingDir.resolve("gradlew.bat") };
                            } else {
                                scripts = new Path[] { workingDir.resolve("gaiasky"), workingDir.resolve("gradlew") };
                            }
                            for (Path file : scripts) {
                                if (Files.exists(file) && Files.isRegularFile(file) && Files.isExecutable(file)) {
                                    try {
                                        if (file.getFileName().toString().contains("gaiasky")) {
                                            // Just use the script
                                            final ArrayList<String> command = new ArrayList<>();
                                            command.add(file.toString());
                                            final ProcessBuilder builder = new ProcessBuilder(command);
                                            builder.start();
                                        } else if (file.getFileName().toString().contains("gradlew")) {
                                            // Gradle script
                                            final ArrayList<String> command = new ArrayList<>();
                                            command.add(file.toString());
                                            command.add("core:run");
                                            final ProcessBuilder builder = new ProcessBuilder(command);
                                            builder.start();
                                        }
                                        break;
                                    } catch (IOException e) {
                                        logger.error(e, "Error running Gaia Sky");
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    protected void cancel() {
                        // Nothing
                    }

                    @Override
                    public void dispose() {
                        // Nothing
                    }
                };
                restart.setAcceptText(I18n.msg("gui.yes"));
                restart.setCancelText(I18n.msg("gui.no"));
                restart.buildSuper();
                restart.show(ui);
                break;
            case UI_RELOAD_CMD:
                reloadUI((GlobalResources) data[0]);
                break;
            default:
                break;
            }
        }

    }

    private Array<Actor> getElementsOfType(Class<? extends Actor> clazz) {
        Array<Actor> result = new Array<>();
        if (current != null) {
            Stage ui = current.getGuiStage();
            Array<Actor> actors = ui.getActors();
            for (Actor actor : actors) {
                if (clazz.isAssignableFrom(actor.getClass())) {
                    result.add(actor);
                }
            }
        }
        return result;
    }

    public boolean removeControllerGui() {
        for (int i = 0; i < guis.size; i++) {
            IGui gui = guis.get(i);
            if (gui instanceof ControllerGui) {
                ControllerGui cgui = (ControllerGui) gui;
                return cgui.removeControllerGui(GaiaSky.instance.cameraManager.naturalCamera);
            }
        }
        return false;
    }

    /**
     * Cancels the task that removes the information pop-up if it is
     * scheduled.
     *
     * @return True if the task was canceled, false if it was not scheduled.
     */
    private boolean cancelRemovePopupTask() {
        if (removePopup != null && removePopup.isScheduled()) {
            removePopup.cancel();
            return true;
        }
        return false;
    }

    public boolean removeModeChangePopup() {
        boolean removed = false;
        if (modeChangeInfoPopup != null) {
            removed = modeChangeInfoPopup.remove();
            cancelRemovePopupTask();
        }

        return removed;
    }

    /**
     * This method updates the default skin and reloads the full UI.
     *
     * @param globalResources The global resources object to update.
     */
    private void reloadUI(GlobalResources globalResources) {
        // Reinitialise user interface
        GaiaSky.postRunnable(() -> {
            // Reinitialise GUI system
            globalResources.updateSkin();
            GenericDialog.updatePads();
            GaiaSky.instance.reinitialiseGUI1();
            EventManager.publish(Event.SPACECRAFT_LOADED, this, scene.getEntity("Spacecraft"));
            GaiaSky.instance.reinitialiseGUI2();
            // Time init
            EventManager.publish(Event.TIME_CHANGE_INFO, this, GaiaSky.instance.time.getTime());
            if (GaiaSky.instance.cameraManager.mode == CameraManager.CameraMode.FOCUS_MODE) {
                // Refocus
                FocusView focus = (FocusView) GaiaSky.instance.cameraManager.getFocus();
                EventManager.publish(Event.FOCUS_CHANGE_CMD, this, focus.getEntity());
            }
            // UI theme reload broadcast
            EventManager.publish(Event.UI_THEME_RELOAD_INFO, this, globalResources.getSkin());
            EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, I18n.msg("notif.ui.reload"));
        });
    }

}
