/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interafce;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.interafce.components.*;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.scenegraph.ISceneGraph;
import gaiasky.util.CatalogManager;
import gaiasky.util.I18n;
import gaiasky.util.MusicManager;
import gaiasky.util.Settings;
import gaiasky.util.math.MathUtilsd;
import gaiasky.util.scene2d.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControlsWindow extends CollapsibleWindow implements IObserver {

    /**
     * Content width. To be used in all components.
     *
     * @return The width of the content.
     */
    public static float getContentWidth() {
        return 352f;
    }

    /**
     * The user interface stage
     */
    protected Stage ui;
    protected Skin skin;
    protected VerticalGroup mainVertical;
    protected OwnScrollPane windowScroll;
    protected Table guiLayout;
    protected OwnImageButton recCamera = null, recKeyframeCamera = null, playCamera = null, playStop = null;
    protected OwnTextIconButton map = null;
    protected TiledDrawable separator;
    /**
     * The scene graph
     */
    private ISceneGraph sg;

    private final CatalogManager catalogManager;

    /**
     * Entities that will go in the visibility check boxes
     */
    private ComponentType[] visibilityEntities;
    private boolean[] visible;

    /**
     * Access panes
     **/
    private Map<String, CollapsiblePane> panes;

    public ControlsWindow(final String title, final Skin skin, final Stage ui, final CatalogManager catalogManager) {
        super(title, skin);
        this.setName(title);
        this.skin = skin;
        this.ui = ui;
        this.catalogManager = catalogManager;

        // Global resources
        TextureRegion separatorTextureRegion = ((TextureRegionDrawable) skin.newDrawable("separator")).getRegion();
        separatorTextureRegion.getTexture().setWrap(TextureWrap.Repeat, TextureWrap.ClampToEdge);
        this.separator = new TiledDrawable(separatorTextureRegion);

        EventManager.instance.subscribe(this, Events.TIME_STATE_CMD, Events.GUI_SCROLL_POSITION_CMD, Events.GUI_FOLD_CMD, Events.GUI_MOVE_CMD, Events.RECALCULATE_OPTIONS_SIZE, Events.EXPAND_PANE_CMD, Events.COLLAPSE_PANE_CMD, Events.TOGGLE_EXPANDCOLLAPSE_PANE_CMD, Events.SHOW_MINIMAP_ACTION, Events.TOGGLE_MINIMAP, Events.RECORD_CAMERA_CMD);
    }

    public void initialize() {

        /* Global layout */
        guiLayout = new Table();
        guiLayout.pad(0);
        guiLayout.align(Align.left);

        List<Actor> mainActors = new ArrayList<>();
        panes = new HashMap<>();

        /* ----TIME GROUP---- */
        playStop = new OwnImageButton(skin, "playstop");
        playStop.setName("play stop");
        playStop.setChecked(Settings.settings.runtime.timeOn);
        playStop.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.TIME_STATE_CMD, playStop.isChecked(), true);
                return true;
            }
            return false;
        });
        String timeHotkey = KeyBindings.instance.getStringKeys("action.pauseresume");
        playStop.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.tooltip.playstop"), timeHotkey, skin));

        TimeComponent timeComponent = new TimeComponent(skin, ui);
        timeComponent.initialize();

        String shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.time");

        CollapsiblePane time = new CollapsiblePane(ui, I18n.txt("gui.time"), timeComponent.getActor(), getContentWidth(), skin, true, shortcut, playStop);
        time.align(Align.left);
        mainActors.add(time);
        panes.put(timeComponent.getClass().getSimpleName(), time);

        /* ----CAMERA---- */
        // Record camera button
        recCamera = new OwnImageButton(skin, "rec");
        recCamera.setName("recCam");
        recCamera.setChecked(Settings.settings.runtime.recordCamera);
        recCamera.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.RECORD_CAMERA_CMD, recCamera.isChecked(), null, true);
                return true;
            }
            return false;
        });
        recCamera.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.reccamera"), skin));

        // Record camera (keyframes)
        recKeyframeCamera = new OwnImageButton(skin, "rec-key");
        recKeyframeCamera.setName("recKeyframeCamera");
        recKeyframeCamera.setChecked(Settings.settings.runtime.recordKeyframeCamera);
        recKeyframeCamera.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_KEYFRAMES_WINDOW_ACTION);
                return true;
            }
            return false;
        });
        recKeyframeCamera.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.reccamerakeyframe"), skin));

        // Play camera button
        playCamera = new OwnImageButton(skin, "play");
        playCamera.setName("playCam");
        playCamera.setChecked(false);
        playCamera.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_PLAYCAMERA_ACTION);
                return true;
            }
            return false;
        });

        playCamera.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.playcamera"), skin));

        CameraComponent cameraComponent = new CameraComponent(skin, ui);
        cameraComponent.initialize();

        shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.camera");

        CollapsiblePane camera = new CollapsiblePane(ui, I18n.txt("gui.camera"), cameraComponent.getActor(), getContentWidth(), skin, false, shortcut, recCamera, recKeyframeCamera, playCamera);
        camera.align(Align.left);
        mainActors.add(camera);
        panes.put(cameraComponent.getClass().getSimpleName(), camera);

        /* ----OBJECT TOGGLES GROUP---- */
        VisibilityComponent visibilityComponent = new VisibilityComponent(skin, ui);
        visibilityComponent.setVisibilityEntitites(visibilityEntities, visible);
        visibilityComponent.initialize();

        shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.visibility");

        CollapsiblePane visibility = new CollapsiblePane(ui, I18n.txt("gui.visibility"), visibilityComponent.getActor(), getContentWidth(), skin, false, shortcut);
        visibility.align(Align.left);
        mainActors.add(visibility);
        panes.put(visibilityComponent.getClass().getSimpleName(), visibility);

        /* ----LIGHTING GROUP---- */
        VisualEffectsComponent visualEffectsComponent = new VisualEffectsComponent(skin, ui);
        visualEffectsComponent.initialize();

        shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.lighting");

        CollapsiblePane visualEffects = new CollapsiblePane(ui, I18n.txt("gui.lighting"), visualEffectsComponent.getActor(), getContentWidth(), skin, false, shortcut);
        visualEffects.align(Align.left);
        mainActors.add(visualEffects);
        panes.put(visualEffectsComponent.getClass().getSimpleName(), visualEffects);

        /* ----DATASETS---- */
        DatasetsComponent datasetsComponent = new DatasetsComponent(skin, ui, catalogManager);
        datasetsComponent.initialize();

        shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.dataset.title");

        CollapsiblePane datasets = new CollapsiblePane(ui, I18n.txt("gui.dataset.title"), datasetsComponent.getActor(), getContentWidth(), skin, false, shortcut);
        datasets.align(Align.left);
        mainActors.add(datasets);
        panes.put(datasetsComponent.getClass().getSimpleName(), datasets);

        /* ----LOCATION LOG---- */
        LocationLogComponent locationLogComponent = new LocationLogComponent(skin, ui);
        locationLogComponent.initialize();

        CollapsiblePane locationLog = new CollapsiblePane(ui, I18n.txt("gui.locationlog"), locationLogComponent.getActor(), getContentWidth(), skin, false, null);
        locationLog.align(Align.left);
        mainActors.add(locationLog);
        panes.put(locationLogComponent.getClass().getSimpleName(), locationLog);

        /* ----BOOKMARKS---- */
        BookmarksComponent bookmarksComponent = new BookmarksComponent(skin, ui);
        bookmarksComponent.setSceneGraph(sg);
        bookmarksComponent.initialize();

        shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.objects");

        CollapsiblePane bookmarks = new CollapsiblePane(ui, I18n.txt("gui.bookmarks"), bookmarksComponent.getActor(), getContentWidth(), skin, false, shortcut);
        bookmarks.align(Align.left);
        mainActors.add(bookmarks);
        panes.put(bookmarksComponent.getClass().getSimpleName(), bookmarks);

        /* ----GAIA SCAN GROUP---- */
        //	GaiaComponent gaiaComponent = new GaiaComponent(skin, ui);
        //	gaiaComponent.initialize();
        //
        //	CollapsiblePane gaia = new CollapsiblePane(ui, I18n.txt("gui.gaiascan"), gaiaComponent.getActor(), skin, false);
        //	gaia.align(Align.left);
        //	mainActors.add(gaia);
        //	panes.put(gaiaComponent.getClass().getSimpleName(), gaia);

        /* ----MUSIC GROUP---- */
        if(MusicManager.initialized()) {
            MusicComponent musicComponent = new MusicComponent(skin, ui);
            musicComponent.initialize();

            Actor[] musicActors = MusicActorsManager.getMusicActors() != null ? MusicActorsManager.getMusicActors().getActors(skin) : null;

            shortcut = KeyBindings.instance.getStringKeys("action.expandcollapse.pane/gui.music");

            CollapsiblePane music = new CollapsiblePane(ui, I18n.txt("gui.music"), musicComponent.getActor(), getContentWidth(), skin, false, shortcut, musicActors);
            music.align(Align.left);
            mainActors.add(music);
            panes.put(musicComponent.getClass().getSimpleName(), music);
        }

        Table buttonsTable;
        /* BUTTONS */
        float bw = 48f, bh = 48f;
        KeyBindings kb = KeyBindings.instance;
        Image icon = new Image(skin.getDrawable("map-icon"));
        map = new OwnTextIconButton("", icon, skin, "toggle");
        map.setSize(bw, bh);
        map.setName("map");
        map.setChecked(Settings.settings.program.minimap.active);
        String minimapHotkey = kb.getStringKeys("action.toggle/gui.minimap.title");
        map.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.map"), minimapHotkey, skin));
        map.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_MINIMAP_ACTION, map.isChecked(), true);
            }
            return false;
        });
        Button load = new OwnTextIconButton("", skin, "load");
        load.setSize(bw, bh);
        load.setName("loadcatalog");
        load.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.loadcatalog"), kb.getStringKeys("action.loadcatalog"), skin));
        load.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_LOAD_CATALOG_ACTION);
            }
            return false;
        });
        Button preferences = new OwnTextIconButton("", skin, "preferences");
        preferences.setSize(bw, bh);
        preferences.setName("preferences");
        String prefsHotkey = kb.getStringKeys("action.preferences");
        preferences.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.preferences"), prefsHotkey, skin));
        preferences.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_PREFERENCES_ACTION);
            }
            return false;
        });
        Button showLog = new OwnTextIconButton("", skin, "log");
        showLog.setSize(bw, bh);
        showLog.setName("show log");
        showLog.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.tooltip.log"), kb.getStringKeys("action.log"), skin));
        showLog.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_LOG_ACTION);
            }
            return false;
        });
        Button about = new OwnTextIconButton("", skin, "help");
        about.setSize(bw, bh);
        about.setName("about");
        String helpHotkey = kb.getStringKeys("action.help");
        about.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.help"), helpHotkey, skin));
        about.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_ABOUT_ACTION);
            }
            return false;
        });
        Button quit = new OwnTextIconButton("", skin, "quit");
        quit.setSize(bw, bh);
        quit.setName("quit");
        quit.addListener(new OwnTextHotkeyTooltip(I18n.txt("gui.quit.title"), kb.getStringKeys("action.exit"), skin));
        quit.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_QUIT_ACTION);
            }
            return false;
        });

        buttonsTable = new Table(skin);
        buttonsTable.add(map).pad(1).top().left();
        buttonsTable.add(load).pad(1).top().left();
        buttonsTable.add(preferences).pad(1).top().left();
        buttonsTable.add(showLog).pad(1).top().left();
        buttonsTable.add(about).pad(1).top().left();
        buttonsTable.add(quit).pad(1).top().left();

        buttonsTable.pack();

        /* ADD GROUPS TO VERTICAL LAYOUT */

        int padBottom = Math.round(16f);
        int padSides = Math.round(8f);
        int padSeparator = Math.round(3.2f);

        guiLayout.padTop(padSides);

        int size = mainActors.size();
        for (int i = 0; i < size; i++) {
            Actor actor = mainActors.get(i);
            guiLayout.add(actor).prefWidth(188f).left().padBottom(padBottom).padLeft(padSides);
            if (i < size - 1) {
                // Not last
                guiLayout.row();
                guiLayout.add(new Image(separator)).left().fill(true, false).padBottom(padSeparator).padLeft(padSides);
                guiLayout.row();
            }
        }
        guiLayout.align(Align.top | Align.left);

        windowScroll = new OwnScrollPane(guiLayout, skin, "minimalist-nobg");
        windowScroll.setFadeScrollBars(true);
        windowScroll.setScrollingDisabled(true, false);
        windowScroll.setOverscroll(false, false);
        windowScroll.setSmoothScrolling(true);
        windowScroll.pack();
        windowScroll.setWidth(guiLayout.getWidth() + windowScroll.getStyle().vScroll.getMinWidth());

        mainVertical = new VerticalGroup();
        mainVertical.space(padSides);
        mainVertical.align(Align.right).align(Align.top);
        mainVertical.addActor(windowScroll);
        // Add buttons only in desktop version
        mainVertical.addActor(buttonsTable);
        mainVertical.pack();

        /* ADD TO MAIN WINDOW */
        add(mainVertical).top().left().expand();
        setPosition(0, Math.round(Gdx.graphics.getHeight() - getHeight()));

        setWidth(mainVertical.getWidth());

        pack();
        recalculateSize();
    }

    public void recalculateSize() {
        // Save position
        float topy = getY() + getHeight();

        // Calculate new size
        guiLayout.pack();
        if (windowScroll != null) {
            float unitsPerPixel = ((ScreenViewport)ui.getViewport()).getUnitsPerPixel();
            windowScroll.setHeight(Math.min(guiLayout.getHeight(), ui.getHeight() - 120 * unitsPerPixel));
            windowScroll.pack();

            mainVertical.setHeight(windowScroll.getHeight() + 30 * unitsPerPixel);
            mainVertical.pack();

            setHeight(windowScroll.getHeight() + 40 * unitsPerPixel);
        }
        pack();
        validate();

        // Restore position
        setY(topy - getHeight());
    }

    public void setSceneGraph(ISceneGraph sg) {
        this.sg = sg;
    }

    public void setVisibilityToggles(ComponentType[] entities, boolean[] visible) {
        this.visibilityEntities = entities;
        this.visible = visible;
    }

    @Override
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case TIME_STATE_CMD:
            // Pause has been toggled, update playstop button only if this does
            // not come from this interface
            if (!(Boolean) data[1]) {
                playStop.setCheckedNoFire((Boolean) data[0]);
            }
            break;
        case GUI_SCROLL_POSITION_CMD:
            this.windowScroll.setScrollY((float) data[0]);
            break;
        case GUI_FOLD_CMD:
            boolean collapse;
            if (data.length >= 1) {
                collapse = (boolean) data[0];
            } else {
                // Toggle
                collapse = !isCollapsed();
            }
            if (collapse) {
                collapse();
            } else {
                expand();
            }
            break;
        case GUI_MOVE_CMD:
            float x = (float) data[0];
            float y = (float) data[1];
            float width = Gdx.graphics.getWidth();
            float height = Gdx.graphics.getHeight();
            float windowWidth = getWidth();
            float windowHeight = getHeight();

            x = MathUtilsd.clamp(x * width, 0, width - windowWidth);
            y = MathUtilsd.clamp(y * height - windowHeight, 0, height - windowHeight);

            setPosition(Math.round(x), Math.round(y));

            break;
        case RECALCULATE_OPTIONS_SIZE:
            recalculateSize();
            break;
        case EXPAND_PANE_CMD:
            String paneName = (String) data[0];
            CollapsiblePane pane = panes.get(paneName);
            pane.expandPane();
            break;
        case COLLAPSE_PANE_CMD:
            paneName = (String) data[0];
            pane = panes.get(paneName);
            pane.collapsePane();
            break;
        case TOGGLE_EXPANDCOLLAPSE_PANE_CMD:
            paneName = (String) data[0];
            pane = panes.get(paneName);
            pane.togglePane();
            break;
        case SHOW_MINIMAP_ACTION:
            boolean show = (Boolean) data[0];
            boolean ui = (Boolean) data[1];
            if (!ui) {
                map.setProgrammaticChangeEvents(false);
                map.setChecked(show);
                map.setProgrammaticChangeEvents(true);
            }
            break;
        case TOGGLE_MINIMAP:
            map.setProgrammaticChangeEvents(false);
            map.setChecked(!map.isChecked());
            map.setProgrammaticChangeEvents(true);
            break;
        case RECORD_CAMERA_CMD:
            boolean state = (Boolean) data[0];
            ui = (Boolean) data[2];
            if (!ui) {
                recCamera.setCheckedNoFire(state);
            }
            break;
        default:
            break;
        }

    }

    public CollapsiblePane getCollapsiblePane(String name) {
        if (panes.containsKey(name)) {
            return panes.get(name);
        } else {
            return null;
        }
    }

}
