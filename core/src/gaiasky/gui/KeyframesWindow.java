/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.api.IFocus;
import gaiasky.scene.camera.CameraManager;
import gaiasky.scene.component.Keyframes;
import gaiasky.scene.view.FocusView;
import gaiasky.scene.view.KeyframesView;
import gaiasky.scene.view.VertsView;
import gaiasky.util.Logger;
import gaiasky.util.Settings;
import gaiasky.util.SysUtils;
import gaiasky.util.TextUtils;
import gaiasky.util.camera.rec.Keyframe;
import gaiasky.util.camera.rec.KeyframesManager;
import gaiasky.util.color.ColorUtils;
import gaiasky.util.i18n.I18n;
import gaiasky.util.math.InterpolationDouble;
import gaiasky.util.math.Vector3d;
import gaiasky.util.scene2d.*;
import gaiasky.util.validator.FloatValidator;
import gaiasky.util.validator.LengthValidator;
import gaiasky.util.validator.RegexpValidator;

import java.nio.file.Files;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class KeyframesWindow extends GenericDialog implements IObserver {
    private static final Logger.Log logger = Logger.getLogger(KeyframesWindow.class);

    private final DecimalFormat secondsFormatter;
    private final DateTimeFormatter dateFormat;

    private final KeyframesManager manager;
    /**
     * Seconds cells
     **/
    private final Map<Keyframe, Cell<?>> secondsCells;
    /**
     * Names cells
     **/
    private final Map<Keyframe, Cell<?>> namesCells;
    /**
     * Keyframe cells
     */
    private final Map<Keyframe, OwnLabel> keyframeNames;
    /**
     * Lock object for this window instance.
     **/
    private final Object windowLock = new Object();
    /**
     * Date format.
     **/
    private final DateFormat df;
    private final VertsView vertsView;
    private final KeyframesView view;
    private final float buttonSize;
    private final float buttonSizeL;
    private final Editing editing;
    /**
     * Seconds
     **/
    private OwnTextField secondsInput;
    /**
     * Name
     **/
    private OwnTextField nameInput;
    /**
     * Keyframes table
     **/
    private Table keyframesTable;
    /**
     * Media controls table.
     **/
    private Table mediaTable;
    /**
     * Notice cell
     **/
    private Cell<?> notice;
    /**
     * Keyframes visibility.
     */
    private Button visibility;
    /**
     * Scroll for keyframes
     **/
    private OwnScrollPane rightScroll;
    /**
     * Last loaded keyframe file name
     **/
    private String lastKeyframeFileName = null;
    /**
     * Model object to represent the path
     **/
    private final Entity keyframesPathEntity;
    private final Keyframes keyframesComponent;
    private long lastMs = 0L;
    private Color colorBak;

    /**
     * Playback buttons.
     **/
    private Button skipBack, stepBack, playPause, stepForward, skipForward;
    /**
     * Cell that contains the timeline slider.
     */
    private Cell<OwnSlider> timelineCell;
    /**
     * Timeline slider widget.
     */
    private OwnSlider timelineSlider;

    /**
     * Create a keyframes window with a given scene, stage and skin.
     *
     * @param scene The scene
     * @param stage The UI stage.
     * @param skin  The skin.
     */
    public KeyframesWindow(Scene scene,
                           Stage stage,
                           Skin skin) {
        super(I18n.msg("gui.keyframes.title"), skin, stage);

        this.manager = KeyframesManager.instance;
        buttonSize = 26f;
        buttonSizeL = 28f;

        this.view = new KeyframesView(scene);
        this.vertsView = new VertsView();
        this.enterExit = false;
        this.editing = new Editing();
        this.secondsCells = new HashMap<>();
        this.namesCells = new HashMap<>();
        this.keyframeNames = new HashMap<>();
        this.secondsFormatter = new DecimalFormat("000.00");
        this.df = new SimpleDateFormat("yyyyMMdd_HH-mm-ss-SSS");
        this.dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM).withLocale(I18n.locale).withZone(ZoneOffset.UTC);
        setModal(false);

        setCancelText(I18n.msg("gui.close"));

        // Build UI
        buildSuper();

        // Add path object to model
        var entity = scene.archetypes().get("KeyframesPathObject").createEntity();

        var base = Mapper.base.get(entity);
        base.setName("keyframed.camera.path");
        base.setComponentType(ComponentType.Keyframes);

        var graph = Mapper.graph.get(entity);
        graph.setParent(Scene.ROOT_NAME);

        scene.initializeEntity(entity);
        scene.setUpEntity(entity);

        keyframesComponent = Mapper.keyframes.get(entity);
        keyframesComponent.keyframes = manager.keyframes;
        keyframesPathEntity = entity;

        EventManager.publish(Event.SCENE_ADD_OBJECT_CMD, this, keyframesPathEntity, false);

        // Set entity to view.
        view.setEntity(keyframesPathEntity);
    }

    @Override
    protected void build() {
        /*
         * Right and left tables
         */
        Table left = new Table(skin);
        left.align(Align.top | Align.left);
        left.setBackground("textfield-disabled");
        Table right = new Table(skin);
        right.align(Align.top | Align.left);

        /* LEFT - CONTROLS */

        // ADD
        OwnTextIconButton addKeyframe = new OwnTextIconButton(I18n.msg("gui.keyframes.add.end"), skin, "add");
        addKeyframe.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.add.end"), skin));
        addKeyframe.pad(pad10);
        left.add(addKeyframe).left().pad(pad10, pad10, 0, pad10).colspan(2).padBottom(pad34).row();
        addKeyframe.addListener(event -> {
            if (event instanceof ChangeEvent) {
                // Add at end.
                return addKeyframe(-1);
            }
            return false;
        });

        // SECONDS
        FloatValidator secondsValidator = new FloatValidator(0.0001f, 9999.0f);
        secondsValidator.setIsValidCallback(() -> {
            // Enable add button
            addKeyframe.setDisabled(false);
        });
        secondsValidator.setIsInvalidCallback(() -> {
            // Disable add button
            addKeyframe.setDisabled(true);
        });
        secondsInput = new OwnTextField("1.0", skin, secondsValidator);
        secondsInput.setWidth(150f);
        OwnLabel secondsLabel = new OwnLabel(I18n.msg("gui.keyframes.secsafter") + ":", skin);
        left.add(secondsLabel).center().left().padRight(pad18).padBottom(pad18).padLeft(pad10);
        left.add(secondsInput).center().left().padBottom(pad18).padRight(pad10).row();

        // NAME
        LengthValidator lengthValidator = new LengthValidator(0, 15);
        RegexpValidator nameValidator = new RegexpValidator(lengthValidator, "^[^*&%\\s\\+\\=\\\\\\/@#\\$&\\*()~]*$");
        nameInput = new OwnTextField("", skin, nameValidator);
        nameInput.setWidth(150f);
        OwnLabel nameLabel = new OwnLabel(I18n.msg("gui.keyframes.name") + ":", skin);
        left.add(nameLabel).center().left().padRight(pad18).padBottom(pad18).padLeft(pad10);
        left.add(nameInput).center().left().padBottom(pad18).padRight(pad10).row();

        // KEYFRAMES VISIBILITY
        var ct = ComponentType.Keyframes;
        Image icon = new Image(skin.getDrawable(ct.style));
        visibility = new OwnTextIconButton(TextUtils.capitalise(I18n.msg("gui.keyframes.visibility")), icon, skin, "toggle");
        // Tooltip (with or without hotkey)
        String hk = KeyBindings.instance.getStringKeys("action.toggle/" + ct.key);
        if (hk != null) {
            visibility.addListener(new OwnTextHotkeyTooltip(TextUtils.capitalise(Objects.requireNonNull(ct.getName())), hk, skin));
        } else {
            visibility.addListener(new OwnTextTooltip(TextUtils.capitalise(Objects.requireNonNull(ct.getName())), skin));
        }
        visibility.setChecked(GaiaSky.instance.sceneRenderer.isOn(ct));
        visibility.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.publish(Event.TOGGLE_VISIBILITY_CMD, visibility, ct.key, visibility.isChecked());
                return true;
            }
            return false;
        });
        left.add(visibility).colspan(2).center().pad(pad18).row();

        left.pack();

        /* RIGHT - KEYFRAMES */
        OwnLabel keyframesTitle = new OwnLabel(I18n.msg("gui.keyframes.list"), skin, "hud-header");

        // MEDIA CONTROLS TABLE
        mediaTable = new Table(skin);
        mediaTable.setBackground("sc-engine-power-bg");

        // KEYFRAMES TABLE
        keyframesTable = buildKeyframesTable();
        checkKeyframesTable();

        // ADD SCROLL
        rightScroll = new OwnScrollPane(keyframesTable, skin, "minimalist-nobg");
        rightScroll.setExpand(true);
        rightScroll.setScrollingDisabled(true, false);
        rightScroll.setHeight(250f);
        rightScroll.setWidth(820f);
        rightScroll.setFadeScrollBars(false);

        right.add(keyframesTitle).top().left().padBottom(pad18).row();
        right.add(rightScroll).width(820f).height(250f).center().left().row();
        right.add(mediaTable).center().padTop(pad10);

        right.pack();

        /* RE-NORMALIZE TIME */
        OwnTextButton normalizeTime = new OwnTextButton(I18n.msg("gui.keyframes.normalize"), skin);
        normalizeTime.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.normalize"), skin));
        normalizeTime.pad(pad18);
        normalizeTime.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                if (manager.keyframes != null && manager.keyframes.size > 2) {
                    Vector3d aux = new Vector3d();
                    int n = manager.keyframes.size;
                    double totalTime = 0;
                    double totalDist = 0;
                    for (int i = 1; i < n; i++) {
                        Keyframe kf0 = manager.keyframes.get(i - 1);
                        Keyframe kf1 = manager.keyframes.get(i);
                        totalTime += kf1.seconds;
                        totalDist += aux.set(kf1.pos).sub(kf0.pos).len();
                    }
                    // Loop over keyframes and assign new times.
                    for (int i = 1; i < n; i++) {
                        Keyframe kf0 = manager.keyframes.get(i - 1);
                        Keyframe kf1 = manager.keyframes.get(i);
                        double dist = aux.set(kf1.pos).sub(kf0.pos).len();
                        kf1.seconds = totalTime * dist / totalDist;
                    }
                    // Reload window contents.
                    reinitialiseKeyframes(manager.keyframes, null);

                    synchronized (view) {
                        view.setEntity(keyframesPathEntity);
                        view.unselect();
                    }
                    logger.info(I18n.msg("gui.keyframes.normalize.done"));

                    // Check timings.
                    checkKeyframeTimings();
                }
                return true;
            }
            return false;
        });


        /* ACTION BUTTONS */
        HorizontalGroup buttons = new HorizontalGroup();
        buttons.space(pad18);

        // Open keyframes.
        OwnTextIconButton open = new OwnTextIconButton(I18n.msg("gui.keyframes.load"), skin, "open");
        open.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.load"), skin));
        open.pad(pad10);
        open.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                FileChooser fc = new FileChooser(I18n.msg("gui.download.pickloc"), skin, stage, SysUtils.getDefaultCameraDir(), FileChooser.FileChooserTarget.FILES);
                fc.setShowHidden(Settings.settings.program.fileChooser.showHidden);
                fc.setShowHiddenConsumer((showHidden) -> Settings.settings.program.fileChooser.showHidden = showHidden);
                fc.setFileFilter(pathname -> pathname.getFileName().toString().endsWith(".gkf"));
                fc.setAcceptedFiles("*.gkf");
                fc.setResultListener((success, result) -> {
                    if (success) {
                        if (Files.exists(result) && Files.isRegularFile(result)) {
                            // Load selected file.
                            try {
                                Array<Keyframe> kfs = manager.loadKeyframesFile(result);
                                // Update current instance.
                                reinitialiseKeyframes(kfs, null);
                                synchronized (view) {
                                    view.setEntity(keyframesPathEntity);
                                    view.unselect();
                                }
                                lastKeyframeFileName = result.getFileName().toString();
                                logger.info(I18n.msg("gui.keyframes.load.success", manager.keyframes.size, result.getFileName()));
                            } catch (RuntimeException e) {
                                logger.error(I18n.msg("gui.keyframes.load.error", result.getFileName()), e);
                                Label warn = new OwnLabel(I18n.msg("error.loading.format", result.getFileName()), skin);
                                warn.setColor(1f, .4f, .4f, 1f);
                                notice.setActor(warn);
                                return false;
                            }

                        } else {
                            logger.error(I18n.msg("error.loading.notexistent", result.getFileName()));
                            Label warn = new OwnLabel(I18n.msg("error.loading.notexistent", result.getFileName()), skin);
                            warn.setColor(1f, .4f, .4f, 1f);
                            notice.setActor(warn);
                            return false;
                        }
                    }
                    notice.clearActor();
                    pack();
                    return true;
                });

                fc.show(stage);
                return true;
            }
            return false;
        });

        // Save keyframes.
        OwnTextIconButton save = new OwnTextIconButton(I18n.msg("gui.keyframes.save"), skin, "save");
        save.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.save"), skin));
        save.pad(pad10);
        save.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                String suggestedName = lastKeyframeFileName == null ? df.format(new Date()) + "_keyframes.gkf" : lastKeyframeFileName;
                FileNameWindow fnw = new FileNameWindow(suggestedName, stage, skin);
                OwnTextField textField = fnw.getFileNameField();
                fnw.setAcceptRunnable(() -> {
                    if (textField.isValid()) {
                        EventManager.publish(Event.KEYFRAMES_FILE_SAVE, fnw, manager.keyframes, textField.getText());
                        lastKeyframeFileName = textField.getText();
                        notice.clearActor();
                    } else {
                        Label warn = new OwnLabel(I18n.msg("error.file.name.notvalid", textField.getText()), skin);
                        warn.setColor(1f, .4f, .4f, 1f);
                        notice.setActor(warn);
                    }
                    pack();
                });
                fnw.show(stage);
                return true;
            }
            return false;
        });

        // Export to camera path.
        OwnTextIconButton export = new OwnTextIconButton(I18n.msg("gui.keyframes.export"), skin, "export");
        export.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.export"), skin));
        export.pad(pad10);
        export.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                String suggestedName = df.format(new Date()) + ".gsc";
                FileNameWindow fnw = new FileNameWindow(suggestedName, stage, skin);
                OwnTextField textField = fnw.getFileNameField();
                fnw.setAcceptRunnable(() -> {
                    if (textField.isValid()) {
                        EventManager.publish(Event.KEYFRAMES_EXPORT, fnw, manager.keyframes, textField.getText());
                        notice.clearActor();
                    } else {
                        Label warn = new OwnLabel(I18n.msg("error.file.name.notvalid", textField.getText()), skin);
                        warn.setColor(1f, .4f, .4f, 1f);
                        notice.setActor(warn);
                    }
                });
                fnw.show(stage);
                return true;
            }
            return false;
        });

        // Keyframe preferences.
        Button preferences = new OwnTextIconButton(I18n.msg("gui.preferences"), skin, "preferences");
        preferences.setName("keyframe preferences");
        preferences.pad(pad10);
        preferences.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.editprefs"), skin));
        preferences.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                KeyframePreferencesWindow kpw = new KeyframePreferencesWindow(stage, skin);
                kpw.setAcceptRunnable(() -> {
                    // Resample
                    GaiaSky.postRunnable(() -> {
                        synchronized (view) {
                            view.setEntity(keyframesPathEntity);
                            view.resamplePath();
                            reinitialiseKeyframes(manager.keyframes, null);
                        }
                    });

                });
                kpw.setCancelRunnable(() -> {

                });
                kpw.show(stage, me.getWidth(), 0);
                return true;
            }
            return false;
        });

        buttons.addActor(open);
        buttons.addActor(save);
        buttons.addActor(export);
        buttons.addActor(preferences);

        /* FINAL LAYOUT */
        content.add(left).top().left().padRight(pad18 * 2f).padBottom(pad18 * 3f);
        content.add(right).width(830f).top().left().padBottom(pad18).row();
        notice = content.add();
        notice.padBottom(pad18 * 2f).expandY().center().colspan(2).row();
        content.add(normalizeTime).colspan(2).bottom().center().padBottom(pad18).row();
        content.add(buttons).colspan(2).bottom().right().row();

        // CLEAR
        OwnTextButton clear = new OwnTextButton(I18n.msg("gui.clear"), skin);
        clear.setName("clear");
        clear.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                GaiaSky.postRunnable(this::clean);
                return true;
            }
            return false;
        });
        buttonGroup.addActorAt(0, clear);

        // HIDE
        OwnTextButton hide = new OwnTextButton(I18n.msg("gui.hide"), skin);
        hide.setName("hide");
        hide.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                hide();
                return true;
            }
            return false;
        });
        buttonGroup.addActorAt(1, hide);

        recalculateButtonSize();

    }

    /**
     * Adds a new keyframe at the given index position using the given camera position, orientation and time
     *
     * @param index The position of the keyframe, negative to add at the end
     * @param cPos  The position
     * @param cDir  The direction
     * @param cUp   The up
     * @param cTime The time
     * @return True if the keyframe was added, false otherwise
     */
    private boolean addKeyframe(int index,
                                Vector3d cPos,
                                Vector3d cDir,
                                Vector3d cUp,
                                long cTime) {

        try {
            boolean secOk = secondsInput.isValid();
            boolean nameOk = nameInput.isValid();
            if (secOk && nameOk) {
                // Seconds after - first keyframe at zero
                double secsAfter = manager.keyframes.size == 0 ? 0 : Double.parseDouble(secondsInput.getText());

                // Name
                String name;
                String ni = nameInput.getText();
                if (ni != null && !ni.isEmpty()) {
                    name = ni;
                } else {
                    name = "Keyframe " + (manager.keyframes.size + 1);
                }

                Keyframe kf = new Keyframe(name, cPos, cDir, cUp, cTime, secsAfter, false);
                final boolean insert = index >= 0 && index != manager.keyframes.size;
                if (!insert) {
                    synchronized (manager.keyframes) {
                        manager.keyframes.add(kf);
                    }
                    double prevT = 0;
                    for (Keyframe kfr : manager.keyframes) {
                        if (kfr == kf)
                            break;
                        prevT += kfr.seconds;
                    }

                    addKeyframeToTable(kf, prevT, manager.keyframes.size - 1, keyframesTable, true);
                } else {
                    synchronized (manager.keyframes) {
                        manager.keyframes.insert(index, kf);
                    }
                }
                GaiaSky.postRunnable(() -> {
                    if (insert) {
                        reinitialiseKeyframes(manager.keyframes, kf);
                    } else {
                        checkKeyframesTable();
                    }
                    scrollToKeyframe(kf);
                });

            } else {
                logger.info(I18n.msg("gui.keyframes.notadded") + "-" + I18n.msg("gui.keyframes.error.values", secOk ? I18n.msg("gui.ok") : I18n.msg("gui.wrong"),
                        nameOk ? I18n.msg("gui.ok") : I18n.msg("gui.wrong")));
            }
        } catch (Exception e) {
            logger.error(I18n.msg("gui.keyframes.notadded") + " - " + I18n.msg("gui.keyframes.error.input"), e);
            return false;
        }
        return true;
    }

    /**
     * Adds a new keyframe at the given index position using the current camera state and time.
     *
     * @param index The position of the keyframe, negative to add at the end.
     * @return True if the keyframe was added, false otherwise.
     */
    private boolean addKeyframe(int index) {
        checkKeyframesTableBeforeAdd();
        // Create new instances for the current keyframe
        Vector3d cPos = new Vector3d();
        Vector3d cDir = new Vector3d();
        Vector3d cUp = new Vector3d();
        long cTime;
        // Freeze the camera info
        synchronized (windowLock) {
            cTime = manager.t.getTime().toEpochMilli();
            cPos.set(manager.pos);
            cDir.set(manager.dir);
            cUp.set(manager.up);
        }
        boolean result = addKeyframe(index, cPos, cDir, cUp, cTime);
        updateCurrentPathAndTimeline();
        checkKeyframeTimings();
        return result;
    }

    private Table buildKeyframesTable() {
        Table table = new Table(skin);
        table.align(Align.top | Align.left);
        table.setWidth(700f);

        addKeyframesToTable(manager.keyframes, table);

        table.pack();

        return table;
    }

    private void addMediaControlsToTable(Table table) {
        table.setWidth(800f);

        // Skip back.
        skipBack = new OwnImageButton(skin, "media-skip-backward");
        skipBack.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // To first.
                EventManager.publish(Event.KEYFRAME_PLAY_FRAME, skipBack, 0L);
                return true;
            }
            return false;
        });
        skipBack.addListener(new OwnTextTooltip(I18n.msg("gui.keyframes.start"), skin));
        // Step back.
        stepBack = new OwnImageButton(skin, "media-step-backward");
        stepBack.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // To previous.
                EventManager.publish(Event.KEYFRAME_PLAY_FRAME, stepBack, Math.max(0L, manager.currentPath.i - 1L));
                return true;
            }
            return false;
        });
        stepBack.addListener(new OwnTextTooltip(I18n.msg("gui.keyframes.skip.backward"), skin));
        // Play/pause.
        playPause = new OwnImageButton(skin, "media-play-pause");
        playPause.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                if (playPause.isChecked()) {
                    // Play.
                    if (manager.currentPath != null) {
                        manager.play();
                    }
                } else {
                    // Pause.
                    if (manager.currentPath != null) {
                        manager.pause();
                    }
                }
                return true;
            }
            return false;
        });
        playPause.addListener(new OwnTextTooltip(I18n.msg("gui.keyframes.play.pause"), skin));
        // Step forward.
        stepForward = new OwnImageButton(skin, "media-step-forward");
        stepForward.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // To previous.
                EventManager.publish(Event.KEYFRAME_PLAY_FRAME, stepForward, Math.min(manager.currentPath.n - 1L, manager.currentPath.i + 1L));
                return true;
            }
            return false;
        });
        stepForward.addListener(new OwnTextTooltip(I18n.msg("gui.keyframes.skip.forward"), skin));
        // Skip forward.
        skipForward = new OwnImageButton(skin, "media-skip-forward");
        skipForward.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // To previous.
                EventManager.publish(Event.KEYFRAME_PLAY_FRAME, skipForward, manager.currentPath.n - 1L);
                return true;
            }
            return false;
        });
        skipForward.addListener(new OwnTextTooltip(I18n.msg("gui.keyframes.end"), skin));

        // Add to buttons.
        HorizontalGroup mediaButtons = new HorizontalGroup();
        mediaButtons.center();
        mediaButtons.space(pad18);
        mediaButtons.addActor(skipBack);
        mediaButtons.addActor(stepBack);
        mediaButtons.addActor(playPause);
        mediaButtons.addActor(stepForward);
        mediaButtons.addActor(skipForward);

        // Add to table.
        table.add(mediaButtons).center().pad(pad10).row();
        timelineCell = table.add().center().pad(pad10);

        // Update timeline.
        updateCurrentPathAndTimeline();
    }

    private void updateCurrentPathAndTimeline() {
        if (timelineCell != null && manager.keyframes != null && manager.keyframes.size > 1) {

            // Generate new current camera path.
            manager.regenerateCameraPath();

            // Update timeline slider.
            timelineSlider = new OwnSlider(0f, manager.currentPath.n - 1, 1f, skin);
            timelineSlider.setWidth(800f);
            timelineSlider.setValuePrefix("frame ");
            timelineSlider.setValueFormatter(new DecimalFormat("######0"));
            timelineSlider.addListener((event) -> {
                if (event instanceof ChangeEvent) {
                    long frame = (long) (timelineSlider.getValue());
                    EventManager.publish(Event.KEYFRAME_PLAY_FRAME, timelineSlider, frame);
                    return true;
                }
                return false;
            });

            timelineCell.clearActor();
            timelineCell.setActor(timelineSlider);
            mediaTable.pack();
        }
    }

    private void checkKeyframesTableBeforeAdd() {
        if (manager.keyframes.size == 0) {
            keyframesTable.clear();
        }
    }

    private void checkKeyframesTable() {
        if (manager.keyframes.size == 0) {
            if (mediaTable != null) {
                mediaTable.setVisible(false);
            }
            if (keyframesTable != null) {
                OwnLabel emptyList = new OwnLabel(TextUtils.breakCharacters(I18n.msg("gui.keyframes.empty"), 65), skin, "default-pink");
                keyframesTable.add(emptyList).center();
            }
        } else if (manager.keyframes.size == 1) {
            if (mediaTable != null) {
                mediaTable.setVisible(false);
            }
        } else {
            if (mediaTable != null) {
                mediaTable.setVisible(true);
            }
        }

        // Check timings if necessary.
        checkKeyframeTimings();
    }

    private void checkKeyframeTimings() {
        if (!manager.checkKeyframeTimings() && notice != null) {
            Label warn = new OwnLabel(I18n.msg("gui.keyframes.timings", Settings.settings.camrecorder.targetFps), skin);
            warn.setColor(1f, .4f, .4f, 1f);
            notice.setActor(warn);
        } else if (notice != null) {
            notice.clearActor();
        }
        pack();
    }

    private void addKeyframesToTable(Array<Keyframe> keyframes,
                                     Table table) {
        if (keyframes.size > 0) {
            int i = 0;
            double prevT = 0;
            for (Keyframe kf : keyframes) {

                // Add to UI table
                addKeyframeToTable(kf, prevT, i, table);

                prevT += kf.seconds;
                i++;
            }
        }
        addMediaControlsToTable(mediaTable);
        checkKeyframesTable();

        if (keyframesComponent != null) {
            GaiaSky.postRunnable(() -> {
                keyframesComponent.keyframes = keyframes;
                synchronized (view) {
                    view.setEntity(keyframesPathEntity);
                    view.refreshData();
                }
            });
        }
    }

    private void addKeyframeToTable(Keyframe kf,
                                    double prevT,
                                    int index,
                                    Table table) {
        addKeyframeToTable(kf, prevT, index, table, false);
    }

    private void addFrameSeconds(Keyframe kf,
                                 double prevT,
                                 int index,
                                 Table table) {
        // Seconds
        OwnLabel secondsL = new OwnLabel(secondsFormatter.format(prevT + kf.seconds), skin, "hud-subheader");
        secondsL.setWidth(126f);
        Cell<?> secondsCell;
        if (secondsCells.containsKey(kf))
            secondsCell = secondsCells.get(kf);
        else {
            secondsCell = table.add();
            secondsCells.put(kf, secondsCell);
        }
        secondsCell.setActor(secondsL).left().padRight(pad18 / 2f).padBottom(pad10);
        secondsL.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.seconds", kf.seconds, Settings.settings.camrecorder.targetFps), skin));
        // Can't modify time of first keyframe; it's always zero
        if (index > 0)
            secondsL.addListener((event) -> {
                if (event instanceof InputEvent) {
                    InputEvent ie = (InputEvent) event;
                    if (ie.getType().equals(InputEvent.Type.touchDown)) {
                        if (editing.notEmpty()) {
                            // Remove current
                            editing.revert();
                            editing.unset();
                        }
                        String valText = secondsL.getText().toString();
                        secondsL.clear();
                        secondsCells.get(kf).clearActor();
                        OwnTextField secondsInput = new OwnTextField(valText, skin, new FloatValidator(0.0001f, 500f));
                        secondsInput.setWidth(88f);
                        secondsInput.selectAll();
                        stage.setKeyboardFocus(secondsInput);
                        editing.setSeconds(kf, index, secondsInput, prevT);
                        secondsInput.addListener((evt) -> {
                            if (secondsInput.isValid() && evt instanceof InputEvent && System.currentTimeMillis() - lastMs > 1500) {
                                InputEvent ievt = (InputEvent) evt;
                                if (ievt.getType() == InputEvent.Type.keyDown && (ievt.getKeyCode() == Input.Keys.ENTER || ievt.getKeyCode() == Input.Keys.ESCAPE)) {
                                    double val = Double.parseDouble(secondsInput.getText());
                                    double t = 0;
                                    for (Keyframe k : manager.keyframes) {
                                        if (k == kf)
                                            break;
                                        t += k.seconds;
                                    }
                                    ievt.cancel();
                                    if (val > t) {
                                        kf.seconds = val - t;
                                        GaiaSky.postRunnable(() -> {
                                            secondsCells.get(kf).clearActor();
                                            secondsInput.clear();
                                            // Rebuild
                                            reinitialiseKeyframes(manager.keyframes, null);
                                        });
                                    }
                                    editing.unset();
                                } else if (ievt.getType() == InputEvent.Type.keyUp && (ievt.getKeyCode() == Input.Keys.ENTER || ievt.getKeyCode() == Input.Keys.ESCAPE)) {
                                    ievt.cancel();
                                }
                            }
                            evt.setBubbles(false);
                            return true;
                        });
                        secondsCells.get(kf).setActor(secondsInput);
                        lastMs = System.currentTimeMillis();
                    }
                }
                return true;
            });
        addHighlightListener(secondsL, kf);
    }

    private void addFrameName(Keyframe kf,
                              int index,
                              Table table) {
        // Seconds
        OwnLabel nameL = new OwnLabel((index + 1) + ": " + kf.name, skin);
        nameL.setWidth(360f);
        Cell<?> nameCell;
        if (namesCells.containsKey(kf))
            nameCell = namesCells.get(kf);
        else {
            nameCell = table.add();
            namesCells.put(kf, nameCell);
        }
        nameCell.clearActor();
        nameCell.setActor(nameL).left().padRight(pad18).padBottom(pad10);
        keyframeNames.put(kf, nameL);
        nameL.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.name"), skin));
        nameL.addListener((event) -> {
            if (event instanceof InputEvent) {
                InputEvent ie = (InputEvent) event;
                if (ie.getType() == InputEvent.Type.touchDown) {
                    if (editing.notEmpty()) {
                        // Remove current
                        editing.revert();
                        editing.unset();
                    }
                    String valText = nameL.getText().toString();
                    valText = valText.substring(valText.indexOf(":") + 2);
                    nameL.clear();
                    keyframeNames.remove(kf);
                    namesCells.get(kf).clearActor();
                    LengthValidator lengthValidator = new LengthValidator(0, 15);
                    RegexpValidator nameValidator = new RegexpValidator(lengthValidator, "^[^*&%\\+\\=\\\\\\/@#\\$&\\*()~]*$");
                    OwnTextField nameInput = new OwnTextField(valText, skin, nameValidator);
                    nameInput.setWidth(160f);
                    nameInput.selectAll();
                    stage.setKeyboardFocus(nameInput);
                    editing.setName(kf, index, nameInput);
                    nameInput.addListener((evt) -> {
                        if (nameInput.isValid() && evt instanceof InputEvent && System.currentTimeMillis() - lastMs > 1500) {
                            InputEvent ievt = (InputEvent) evt;
                            if (ievt.getType() == InputEvent.Type.keyDown && (ievt.getKeyCode() == Input.Keys.ENTER || ievt.getKeyCode() == Input.Keys.ESCAPE)) {
                                kf.name = nameInput.getText();
                                addFrameName(kf, index, table);
                                editing.unset();
                            }
                        }
                        evt.setBubbles(false);
                        return true;
                    });
                    namesCells.get(kf).setActor(nameInput);
                    lastMs = System.currentTimeMillis();
                }
            }
            return true;
        });
        addHighlightListener(nameL, kf);
    }

    private void addKeyframeToTable(Keyframe kf,
                                    double prevT,
                                    int index,
                                    Table table,
                                    boolean addToModel) {

        // Seconds
        addFrameSeconds(kf, prevT, index, table);

        // Frame number
        long frame = manager.getFrameNumber(kf);

        OwnLabel framesL = new OwnLabel("(" + frame + ")", skin);
        framesL.setWidth(86f);
        framesL.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.frames", frame, (1d / Settings.settings.camrecorder.targetFps)), skin));
        addHighlightListener(framesL, kf);
        table.add(framesL).left().padRight(pad18).padBottom(pad10);

        // Clock - time
        Image clockImg = new Image(skin.getDrawable("clock"));
        clockImg.addListener(new OwnTextTooltip(dateFormat.format(Instant.ofEpochMilli(kf.time)), skin));
        clockImg.setScale(0.7f);
        clockImg.setOrigin(Align.center);
        addHighlightListener(clockImg, kf);
        table.add(clockImg).width(clockImg.getWidth()).left().padRight(pad18).padBottom(pad10);

        // Frame name
        addFrameName(kf, index, table);

        // Go to
        OwnTextIconButton goTo = new OwnTextIconButton("", skin, "go-to");
        goTo.setSize(buttonSize, buttonSize);
        goTo.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.goto"), skin));
        goTo.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // Step to keyframe.
                EventManager.publish(Event.KEYFRAME_PLAY_FRAME, goTo, frame);
                return true;
            }
            return false;
        });
        addHighlightListener(goTo, kf);
        table.add(goTo).left().padRight(pad10).padBottom(pad10);

        // Seam
        OwnTextIconButton seam = new OwnTextIconButton("", skin, "seam", "toggle");
        seam.setSize(buttonSize, buttonSize);
        seam.setChecked(kf.seam);
        seam.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.seam"), skin));
        seam.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // Make seam
                kf.seam = seam.isChecked();
                GaiaSky.postRunnable(() -> {
                    synchronized (view) {
                        view.setEntity(keyframesPathEntity);
                        view.refreshData();
                    }
                    if (keyframesComponent.selected == kf) {
                        synchronized (vertsView) {
                            vertsView.setEntity(keyframesComponent.selectedKnot);
                            if (seam.isChecked())
                                vertsView.setColor(ColorUtils.gRed);
                            else
                                vertsView.setColor(ColorUtils.gWhite);
                        }

                    }
                });
                return true;
            }
            return false;
        });
        addHighlightListener(seam, kf);
        table.add(seam).left().padRight(pad10).padBottom(pad10);

        // Add after
        OwnTextIconButton addKeyframe = new OwnTextIconButton("", skin, "add");
        addKeyframe.setSize(buttonSizeL, buttonSize);
        addKeyframe.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.add.after"), skin));
        addKeyframe.addListener(event -> {
            if (event instanceof ChangeEvent) {
                // Work out keyframe properties
                Keyframe k0, k1;
                Vector3d pos, dir, up;
                long time;
                if (index < manager.keyframes.size - 1) {
                    // We can interpolate
                    k0 = manager.keyframes.get(index);
                    k1 = manager.keyframes.get(index + 1);
                    pos = new Vector3d().set(k0.pos).interpolate(k1.pos, 0.5, InterpolationDouble.linear);
                    dir = new Vector3d().set(k0.dir).interpolate(k1.dir, 0.5, InterpolationDouble.linear);
                    up = new Vector3d().set(k0.up).interpolate(k1.up, 0.5, InterpolationDouble.linear);
                    time = k0.time + (long) ((k1.time - k0.time) / 2d);
                } else {
                    // Last keyframe
                    k0 = manager.keyframes.get(index - 1);
                    k1 = manager.keyframes.get(index);
                    pos = new Vector3d().set(k0.pos).interpolate(k1.pos, 1.5, InterpolationDouble.linear);
                    dir = new Vector3d().set(k0.dir).interpolate(k1.dir, 1.5, InterpolationDouble.linear);
                    up = new Vector3d().set(k0.up).interpolate(k1.up, 1.5, InterpolationDouble.linear);
                    time = k1.time + (long) ((k1.time - k0.time) / 2d);
                }
                // Add at end
                return addKeyframe(index + 1, pos, dir, up, time);
            }
            return false;
        });
        addHighlightListener(addKeyframe, kf);
        table.add(addKeyframe).left().padRight(pad10).padBottom(pad10);

        // Rubbish
        OwnTextIconButton rubbish = new OwnTextIconButton("", skin, "rubbish");
        rubbish.setSize(buttonSizeL, buttonSize);
        rubbish.addListener(new OwnTextTooltip(I18n.msg("gui.tooltip.kf.remove"), skin));
        rubbish.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // Remove keyframe
                Array<Keyframe> newKfs = new Array<>(false, manager.keyframes.size - 1);
                for (Keyframe k : manager.keyframes) {
                    if (k != kf)
                        newKfs.add(k);
                }

                // Clear editing.
                if (!editing.isEmpty() && editing.kf() == kf) {
                    editing.unset();
                }

                // In case we removed the first
                if (!newKfs.isEmpty())
                    newKfs.get(0).seconds = 0;

                reinitialiseKeyframes(newKfs, null);
                logger.info(I18n.msg("gui.keyframes.removed", kf.name));
                return true;
            }
            return false;
        });
        addHighlightListener(rubbish, kf);
        Cell<?> rub = table.add(rubbish).left().padRight(pad18).padBottom(pad10);
        rub.row();
        table.pack();

        if (addToModel && keyframesComponent != null) {
            GaiaSky.postRunnable(() -> {
                // Update model data
                synchronized (view) {
                    view.setEntity(keyframesPathEntity);
                    view.addKnot(kf.pos, kf.dir, kf.up, kf.seam);
                }
                synchronized (vertsView) {
                    vertsView.setEntity(keyframesComponent.segments);
                    vertsView.addPoint(kf.pos);
                }
                if (manager.keyframes.size > 1)
                    synchronized (view) {
                        view.setEntity(keyframesPathEntity);
                        view.resamplePath();
                    }
            });
        }

    }

    private void addHighlightListener(Actor a,
                                      Keyframe kf) {
        a.addListener(event -> {
            if (event instanceof InputEvent) {
                InputEvent ie = (InputEvent) event;
                synchronized (view) {
                    view.setEntity(keyframesPathEntity);
                    if (ie.getType() == InputEvent.Type.enter) {
                        view.highlight(kf);
                    } else if (ie.getType() == InputEvent.Type.exit) {
                        view.unhighlight(kf);
                    }
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public GenericDialog show(Stage stage,
                              Action action) {
        // Subscriptions
        EventManager.instance.subscribe(this, Event.KEYFRAME_PLAY_FRAME, Event.KEYFRAMES_REFRESH, Event.KEYFRAME_SELECT, Event.KEYFRAME_UNSELECT, Event.KEYFRAME_ADD, Event.TOGGLE_VISIBILITY_CMD);
        // Re-add if necessary
        if (Mapper.graph.get(keyframesPathEntity).parent == null) {
            EventManager.publish(Event.SCENE_ADD_OBJECT_CMD, this, keyframesPathEntity, false);
        }
        return super.show(stage, action);
    }

    public void reset() {
        reinitialiseKeyframes(manager.keyframes, null);
    }

    private void reinitialiseKeyframes(Array<Keyframe> kfs,
                                       Keyframe moveTo) {
        synchronized (manager.keyframes) {
            // Clean.
            clean(kfs != manager.keyframes, false);

            // Update list.
            if (kfs != manager.keyframes)
                manager.keyframes.addAll(kfs);

        }
        // Add to table.
        addKeyframesToTable(manager.keyframes, keyframesTable);

        if (moveTo != null) {
            scrollToKeyframe(moveTo);
        } else {
            scrollToSelected();
        }
        this.pack();
    }

    private void clean() {
        clean(true, true);
    }

    private void clean(boolean cleanKeyframesList,
                       boolean cleanModel) {
        // Clean camera.
        IFocus focus = GaiaSky.instance.getICamera().getFocus();
        if (focus != null && Mapper.tagInvisible.has(((FocusView) focus).getEntity()) && focus.getName().startsWith("Keyframe")) {
            EventManager.publish(Event.FOCUS_CHANGE_CMD, this, Settings.settings.scene.homeObject);
            EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraManager.CameraMode.FREE_MODE);
        }

        if (cleanKeyframesList) {
            manager.clean();
        }

        notice.clearActor();
        namesCells.clear();
        secondsCells.clear();
        keyframesTable.clearChildren();
        mediaTable.clearChildren();
        nameInput.setText("");
        secondsInput.setText("1.0");
        if (cleanModel) {

            GaiaSky.postRunnable(() -> {
                view.setEntity(keyframesPathEntity);
                view.clear();
            });
        }

    }

    private void scrollToSelected() {
        scrollToKeyframe(keyframesComponent.selected);
    }

    private void scrollToKeyframe(Keyframe kf) {
        // Scroll to keyframe.
        if (rightScroll != null && kf != null) {
            int i = manager.keyframes.indexOf(kf, true);
            int n = manager.keyframes.size;
            if (i >= 0) {
                rightScroll.setScrollPercentY((float) i / (n - 5f));
            }
        }
    }

    @Override
    protected boolean accept() {
        // Accept not present.
        return true;
    }

    @Override
    protected void cancel() {
        clean();
        EventManager.instance.removeAllSubscriptions(this);
    }

    @Override
    public void notify(final Event event,
                       Object source,
                       final Object... data) {
        switch (event) {
            case KEYFRAME_ADD -> addKeyframe(-1);
            case KEYFRAME_PLAY_FRAME -> {
                if (source == manager) {
                    // Just need to update the widgets.

                    // Set timeline.
                    timelineSlider.setProgrammaticChangeEvents(false);
                    timelineSlider.setMappedValue(manager.currentPath.i);
                    timelineSlider.setProgrammaticChangeEvents(true);

                    // Check end.
                    if (manager.isIdle()) {
                        playPause.setProgrammaticChangeEvents(false);
                        playPause.setChecked(false);
                        playPause.setProgrammaticChangeEvents(true);
                    }
                }
            }
            case KEYFRAMES_REFRESH -> reinitialiseKeyframes(manager.keyframes, null);
            case KEYFRAME_SELECT -> {
                Keyframe kf = (Keyframe) data[0];
                OwnLabel nl = keyframeNames.get(kf);
                if (nl != null) {
                    colorBak = nl.getColor().cpy();
                    nl.setColor(skin.getColor("theme"));
                    scrollToKeyframe(kf);
                }
            }
            case KEYFRAME_UNSELECT -> {
                Keyframe kf = (Keyframe) data[0];
                OwnLabel nl = keyframeNames.get(kf);
                if (nl != null && colorBak != null) {
                    nl.setColor(colorBak);
                }
            }
            case TOGGLE_VISIBILITY_CMD -> {
                String key = (String) data[0];
                Button b = visibility;
                if (key.equals(ComponentType.Keyframes.key) && b != null && source != b) {
                    b.setProgrammaticChangeEvents(false);
                    if (data.length == 2) {
                        b.setChecked((Boolean) data[1]);
                    } else {
                        b.setChecked(!b.isChecked());
                    }
                    b.setProgrammaticChangeEvents(true);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void dispose() {
        // UI
        clear();

        // Model
        if (keyframesPathEntity != null && Mapper.graph.get(keyframesPathEntity).parent != null) {
            var graph = Mapper.graph.get(keyframesPathEntity);
            GaiaSky.postRunnable(() -> {
                var parentGraph = Mapper.graph.get(graph.parent);
                parentGraph.removeChild(keyframesPathEntity, false);

                vertsView.setEntity(keyframesPathEntity);
                vertsView.clear();
            });
        }

    }

    /**
     * Contains info on field currently being edited.
     */
    private class Editing {
        private final Map<String, Object> map;
        private int type = -1;
        private Keyframe kf;
        private int index;
        private OwnTextField tf;

        public Editing() {
            map = new HashMap<>();
        }

        public boolean notEmpty() {
            return tf != null;
        }

        public boolean isEmpty() {
            return tf == null;
        }

        public void revert() {
            if (isName()) {
                addFrameName(kf, index, keyframesTable);
            } else if (isSeconds()) {
                addFrameSeconds(kf, (Double) map.get("prevT"), index, keyframesTable);
            }
        }

        public void setParam(String key,
                             Object value) {
            map.put(key, value);
        }

        public boolean isName() {
            return !isEmpty() && type == 1;
        }

        public boolean isSeconds() {
            return !isEmpty() && type == 0;
        }

        public void set(Keyframe kf,
                        int idx,
                        OwnTextField tf) {
            this.kf = kf;
            this.index = idx;
            this.tf = tf;
        }

        public void setName(Keyframe kf,
                            int idx,
                            OwnTextField tf) {
            type = 1;
            set(kf, idx, tf);
        }

        public void setSeconds(Keyframe kf,
                               int idx,
                               OwnTextField tf,
                               double prevT) {
            type = 0;
            setParam("prevT", prevT);
            set(kf, idx, tf);
        }

        public void unset() {
            type = -1;
            kf = null;
            index = -1;
            tf = null;
            map.clear();
        }

        public Keyframe kf() {
            return kf;
        }

        public int index() {
            return index;
        }

        public OwnTextField tf() {
            return tf;
        }

    }
}
