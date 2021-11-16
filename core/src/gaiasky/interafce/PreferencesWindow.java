/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interafce;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.desktop.util.SysUtils;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.interafce.KeyBindings.ProgramAction;
import gaiasky.interafce.beans.*;
import gaiasky.screenshot.ImageRenderer;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings.*;
import gaiasky.util.datadesc.DataDescriptor;
import gaiasky.util.datadesc.DataDescriptorUtils;
import gaiasky.util.format.INumberFormat;
import gaiasky.util.format.NumberFormatFactory;
import gaiasky.util.math.MathUtilsd;
import gaiasky.util.parse.Parser;
import gaiasky.util.scene2d.*;
import gaiasky.util.validator.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static gaiasky.util.Settings.OriginType.values;

/**
 * The default preferences window.
 */
public class PreferencesWindow extends GenericDialog implements IObserver {
    private static final Log logger = Logger.getLogger(PreferencesWindow.class);

    // Remember the last tab opened
    private static OwnTextIconButton lastTab;
    private static boolean lastTabFlag = true;

    private final Array<Actor> contents;
    private final Array<OwnLabel> labels;

    private final INumberFormat nf3;

    private CheckBox fullscreen, windowed, vsync, limitfpsCb, multithreadCb, lodFadeCb, cbAutoCamrec, real, nsl, invertx, inverty, highAccuracyPositions, shadowsCb, pointerCoords, debugInfo, crosshairFocusCb, crosshairClosestCb, crosshairHomeCb, pointerGuidesCb, exitConfirmation, recgridProjectionLinesCb;
    private OwnSelectBox<DisplayMode> fullscreenResolutions;
    private OwnSelectBox<ComboBoxBean> gquality, aa, lineRenderer, numThreads, screenshotMode, frameoutputMode, nshadows, distUnitsSelect;
    private OwnSelectBox<LangComboBoxBean> lang;
    private OwnSelectBox<ElevationComboBoxBean> elevationSb;
    private OwnSelectBox<String> recgridOrigin;
    private OwnSelectBox<StrComboBoxBean> theme;
    private OwnSelectBox<FileComboBoxBean> controllerMappings;
    private OwnTextField widthField, heightField, sswidthField, ssheightField, frameoutputPrefix, frameoutputFps, fowidthField, foheightField, camrecFps, cmResolution, plResolution, plAperture, plAngle, smResolution, limitFps;
    private OwnSlider lodTransitions, tessQuality, minimapSize, pointerGuidesWidth, uiScale;
    private OwnTextButton screenshotsLocation, frameoutputLocation;
    private ColorPicker pointerGuidesColor;
    private DatasetsWidget dw;
    private OwnLabel tessQualityLabel;
    private Cell<?> noticeHiResCell;
    private Table controllersTable;

    private GlobalResources globalResources;

    // Backup values
    private ToneMapping toneMappingBak;
    private float brightnessBak, contrastBak, hueBak, saturationBak, gammaBak, exposureBak, bloomBak, unsharpMaskBak;
    private boolean lensflareBak, lightGlowBak, debugInfoBak, motionblurBak;
    private Settings settings;

    public PreferencesWindow(final Stage stage, final Skin skin, final GlobalResources globalResources) {
        super(I18n.txt("gui.settings") + " - " + Settings.settings.version.version + " - " + I18n.txt("gui.build", Settings.settings.version.build), skin, stage);

        this.settings = Settings.settings;
        this.contents = new Array<>();
        this.labels = new Array<>();
        this.globalResources = globalResources;

        this.nf3 = NumberFormatFactory.getFormatter("0.000");

        setAcceptText(I18n.txt("gui.saveprefs"));
        setCancelText(I18n.txt("gui.cancel"));

        // Build UI
        buildSuper();

        EventManager.instance.subscribe(this, Events.CONTROLLER_CONNECTED_INFO, Events.CONTROLLER_DISCONNECTED_INFO);
    }

    private OwnTextIconButton createTab(String title, Image img, Skin skin) {
        OwnTextIconButton tab = new OwnTextIconButton(TextUtils.capString(title, 26), img, skin, "toggle-big");
        tab.addListener(new OwnTextTooltip(title, skin));
        tab.pad(pad5);
        tab.setWidth(480f);
        return tab;
    }

    @Override
    protected void build() {
        final float contentWidth = 1100f;
        final float contentHeight = 1120f;
        final float taWidth = 960f;
        final float textWidth = 104f;
        final float scrollHeight = 640f;
        final float controlsScrollWidth = 880f;
        final float controlsScrollHeight = 560f;
        final float sliderWidth = textWidth * 3f;
        final float buttonHeight = 40f;

        // Create the tab buttons
        Table group = new Table(skin);
        group.align(Align.left | Align.top);

        final OwnTextIconButton tabGraphics = createTab(I18n.txt("gui.graphicssettings"), new Image(skin.getDrawable("iconic-bolt")), skin);
        final OwnTextIconButton tabUI = createTab(I18n.txt("gui.ui.interfacesettings"), new Image(skin.getDrawable("iconic-browser")), skin);
        final OwnTextIconButton tabPerformance = createTab(I18n.txt("gui.performance"), new Image(skin.getDrawable("iconic-dial")), skin);
        final OwnTextIconButton tabControls = createTab(I18n.txt("gui.controls"), new Image(skin.getDrawable("iconic-laptop")), skin);
        final OwnTextIconButton tabScreenshots = createTab(I18n.txt("gui.screenshots"), new Image(skin.getDrawable("iconic-image")), skin);
        final OwnTextIconButton tabFrames = createTab(I18n.txt("gui.frameoutput.title"), new Image(skin.getDrawable("iconic-layers")), skin);
        final OwnTextIconButton tabCamera = createTab(I18n.txt("gui.camerarec.title"), new Image(skin.getDrawable("iconic-camera-slr")), skin);
        final OwnTextIconButton tab360 = createTab(I18n.txt("gui.360.title"), new Image(skin.getDrawable("iconic-cubemap")), skin);
        final OwnTextIconButton tabPlanetarium = createTab(I18n.txt("gui.planetarium.title"), new Image(skin.getDrawable("iconic-dome")), skin);
        final OwnTextIconButton tabData = createTab(I18n.txt("gui.data"), new Image(skin.getDrawable("iconic-clipboard")), skin);
        final OwnTextIconButton tabGaia = createTab(I18n.txt("gui.gaia"), new Image(skin.getDrawable("iconic-gaia")), skin);
        final OwnTextIconButton tabSystem = createTab(I18n.txt("gui.system"), new Image(skin.getDrawable("iconic-terminal")), skin);

        group.add(tabGraphics).row();
        group.add(tabUI).row();
        group.add(tabPerformance).row();
        group.add(tabControls).row();
        group.add(tabScreenshots).row();
        group.add(tabFrames).row();
        group.add(tabCamera).row();
        group.add(tab360).row();
        group.add(tabPlanetarium).row();
        group.add(tabData).row();
        group.add(tabGaia).row();
        group.add(tabSystem).row();
        content.add(group).align(Align.left | Align.top).padLeft(pad5);

        // Create the tab content. Just using images here for simplicity.
        Stack tabContent = new Stack();
        tabContent.setSize(contentWidth, contentHeight);

        /*
         * ==== GRAPHICS ====
         */
        final Table contentGraphicsTable = new Table(skin);
        final OwnScrollPane contentGraphics = new OwnScrollPane(contentGraphicsTable, skin, "minimalist-nobg");
        contentGraphics.setWidth(contentWidth);
        contentGraphics.setHeight(scrollHeight);
        contentGraphics.setScrollingDisabled(true, false);
        contentGraphics.setFadeScrollBars(false);
        contents.add(contentGraphics);
        contentGraphicsTable.align(Align.top | Align.left);

        // RESOLUTION/MODE
        Label titleResolution = new OwnLabel(I18n.txt("gui.resolutionmode"), skin, "header");
        Table mode = new Table();

        // Full screen mode resolutions
        Array<DisplayMode> modes = new Array<>(Gdx.graphics.getDisplayModes());
        modes.sort((o1, o2) -> Integer.compare(o2.height * o2.width, o1.height * o1.width));
        fullscreenResolutions = new OwnSelectBox<>(skin);
        fullscreenResolutions.setWidth(textWidth * 3.45f);
        fullscreenResolutions.setItems(modes);

        DisplayMode selectedMode = null;
        for (DisplayMode dm : modes) {
            if (dm.width == settings.graphics.fullScreen.resolution[0] && dm.height == settings.graphics.fullScreen.resolution[1]) {
                selectedMode = dm;
                break;
            }
        }
        if (selectedMode != null)
            fullscreenResolutions.setSelected(selectedMode);

        // Get current resolution
        Table windowedResolutions = new Table(skin);
        IValidator widthValidator = new IntValidator(100, 10000);
        widthField = new OwnTextField(Integer.toString(MathUtils.clamp(settings.graphics.resolution[0], 100, 10000)), skin, widthValidator);
        widthField.setWidth(textWidth);
        IValidator heightValidator = new IntValidator(100, 10000);
        heightField = new OwnTextField(Integer.toString(MathUtils.clamp(settings.graphics.resolution[1], 100, 10000)), skin, heightValidator);
        heightField.setWidth(textWidth);
        final OwnLabel widthLabel = new OwnLabel(I18n.txt("gui.width") + ":", skin);
        final OwnLabel heightLabel = new OwnLabel(I18n.txt("gui.height") + ":", skin);

        windowedResolutions.add(widthLabel).left().padRight(pad5);
        windowedResolutions.add(widthField).left().padRight(pad5);
        windowedResolutions.add(heightLabel).left().padRight(pad5);
        windowedResolutions.add(heightField).left().row();

        // Radio buttons
        fullscreen = new OwnCheckBox(I18n.txt("gui.fullscreen"), skin, "radio", pad5);
        fullscreen.addListener(event -> {
            if (event instanceof ChangeEvent) {
                selectFullscreen(fullscreen.isChecked(), widthField, heightField, fullscreenResolutions, widthLabel, heightLabel);
                return true;
            }
            return false;
        });
        fullscreen.setChecked(settings.graphics.fullScreen.active);

        windowed = new OwnCheckBox(I18n.txt("gui.windowed"), skin, "radio", pad5);
        windowed.addListener(event -> {
            if (event instanceof ChangeEvent) {
                selectFullscreen(!windowed.isChecked(), widthField, heightField, fullscreenResolutions, widthLabel, heightLabel);
                return true;
            }
            return false;
        });
        windowed.setChecked(!settings.graphics.fullScreen.active);
        selectFullscreen(settings.graphics.fullScreen.active, widthField, heightField, fullscreenResolutions, widthLabel, heightLabel);

        new ButtonGroup<>(fullscreen, windowed);

        // VSYNC
        vsync = new OwnCheckBox(I18n.txt("gui.vsync"), skin, "default", pad5);
        vsync.setChecked(settings.graphics.vsync);

        // LIMIT FPS
        IValidator limitFpsValidator = new DoubleValidator(Constants.MIN_FPS, Constants.MAX_FPS);
        double limitFps = settings.graphics.fpsLimit == 0 ? 60 : settings.graphics.fpsLimit;
        this.limitFps = new OwnTextField(nf3.format(MathUtilsd.clamp(limitFps, Constants.MIN_FPS, Constants.MAX_FPS)), skin, limitFpsValidator);
        this.limitFps.setDisabled(settings.graphics.fpsLimit == 0);

        limitfpsCb = new OwnCheckBox(I18n.txt("gui.limitfps"), skin, "default", pad5);
        limitfpsCb.setChecked(settings.graphics.fpsLimit > 0);
        limitfpsCb.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                enableComponents(limitfpsCb.isChecked(), this.limitFps);
                return true;
            }
            return false;
        });

        mode.add(fullscreen).left().padRight(pad5 * 2f);
        mode.add(fullscreenResolutions).left().row();
        mode.add(windowed).left().padRight(pad5 * 2f).padTop(pad5 * 2f);
        mode.add(windowedResolutions).left().padTop(pad5 * 2f).row();
        mode.add(vsync).left().padTop(pad5 * 2f).colspan(2).row();
        mode.add(limitfpsCb).left().padRight(pad20 * 4.9f);
        mode.add(this.limitFps).left();

        // Add to content
        contentGraphicsTable.add(titleResolution).left().padBottom(pad5 * 2f).row();
        contentGraphicsTable.add(mode).left().padBottom(pad5 * 4f).row();

        // GRAPHICS SETTINGS
        Label titleGraphics = new OwnLabel(I18n.txt("gui.graphicssettings"), skin, "header");
        Table graphics = new Table();

        OwnLabel graphicsQualityLabel = new OwnLabel(I18n.txt("gui.gquality"), skin);
        graphicsQualityLabel.addListener(new OwnTextTooltip(I18n.txt("gui.gquality.info"), skin));

        ComboBoxBean[] gqs = new ComboBoxBean[GraphicsQuality.values().length];
        int i = 0;
        for (GraphicsQuality q : GraphicsQuality.values()) {
            gqs[i] = new ComboBoxBean(I18n.txt(q.key), q.ordinal());
            i++;
        }
        gquality = new OwnSelectBox<>(skin);
        gquality.setItems(gqs);
        gquality.setWidth(textWidth * 3f);
        gquality.setSelected(gqs[settings.graphics.quality.ordinal()]);
        gquality.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                ComboBoxBean s = gquality.getSelected();
                GraphicsQuality gq = GraphicsQuality.values()[s.value];
                if ((DataDescriptor.currentDataDescriptor == null || !DataDescriptor.currentDataDescriptor.datasetPresent("hi-res-textures")) && (gq.isHigh() || gq.isUltra())) {
                    // Show notice
                    // Hi resolution textures notice
                    if (noticeHiResCell != null && noticeHiResCell.getActor() == null) {
                        String infoString = I18n.txt("gui.gquality.hires.info") + "\n";
                        int lines1 = GlobalResources.countOccurrences(infoString, '\n');
                        OwnTextArea noticeHiRes = new OwnTextArea(infoString, skin, "info");
                        noticeHiRes.setDisabled(true);
                        noticeHiRes.setPrefRows(lines1 + 1);
                        noticeHiRes.setWidth(taWidth);
                        noticeHiRes.clearListeners();
                        noticeHiResCell.setActor(noticeHiRes);
                    }
                } else {
                    // Hide notice
                    if (noticeHiResCell != null) {
                        noticeHiResCell.setActor(null);
                    }

                }
            }
            return false;
        });

        OwnImageButton gqualityTooltip = new OwnImageButton(skin, "tooltip");
        gqualityTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.gquality.info"), skin));

        // AA
        OwnLabel aaLabel = new OwnLabel(I18n.txt("gui.aa"), skin);
        aaLabel.addListener(new OwnTextTooltip(I18n.txt("gui.aa.info"), skin));

        ComboBoxBean[] aas = new ComboBoxBean[] { new ComboBoxBean(I18n.txt("gui.aa.no"), 0), new ComboBoxBean(I18n.txt("gui.aa.fxaa"), -1), new ComboBoxBean(I18n.txt("gui.aa.nfaa"), -2) };
        aa = new OwnSelectBox<>(skin);
        aa.setItems(aas);
        aa.setWidth(textWidth * 3f);
        aa.setSelected(aas[idxAa(2, settings.postprocess.antialias)]);

        OwnImageButton aaTooltip = new OwnImageButton(skin, "tooltip");
        aaTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.aa.info"), skin));

        // LINE RENDERER
        OwnLabel lrLabel = new OwnLabel(I18n.txt("gui.linerenderer"), skin);
        ComboBoxBean[] lineRenderers = new ComboBoxBean[] { new ComboBoxBean(I18n.txt("gui.linerenderer.normal"), LineMode.GL_LINES.ordinal()), new ComboBoxBean(I18n.txt("gui.linerenderer.quad"), LineMode.POLYLINE_QUADSTRIP.ordinal()) };
        lineRenderer = new OwnSelectBox<>(skin);
        lineRenderer.setItems(lineRenderers);
        lineRenderer.setWidth(textWidth * 3f);
        lineRenderer.setSelected(lineRenderers[settings.scene.renderer.line.ordinal()]);

        // BLOOM
        bloomBak = settings.postprocess.bloom.intensity;
        OwnLabel bloomLabel = new OwnLabel(I18n.txt("gui.bloom"), skin, "default");
        Slider bloomEffect = new OwnSlider(Constants.MIN_SLIDER, Constants.MAX_SLIDER * 0.2f, Constants.SLIDER_STEP, skin);
        bloomEffect.setName("bloom effect");
        bloomEffect.setWidth(sliderWidth);
        bloomEffect.setValue(settings.postprocess.bloom.intensity * 10f);
        bloomEffect.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.BLOOM_CMD, bloomEffect.getValue() / 10f, true);
                return true;
            }
            return false;
        });

        // UNSHARP MASK
        unsharpMaskBak = settings.postprocess.unsharpMask.factor;
        OwnLabel unsharpMaskLabel = new OwnLabel(I18n.txt("gui.unsharpmask"), skin, "default");
        Slider unsharpMaskFactor = new OwnSlider(Constants.MIN_UNSHARP_MASK_FACTOR, Constants.MAX_UNSHARP_MASK_FACTOR, Constants.SLIDER_STEP_TINY, skin);
        unsharpMaskFactor.setName("unsharp mask factor");
        unsharpMaskFactor.setWidth(sliderWidth);
        unsharpMaskFactor.setValue(settings.postprocess.unsharpMask.factor);
        unsharpMaskFactor.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.UNSHARP_MASK_CMD, unsharpMaskFactor.getValue(), true);
                return true;
            }
            return false;
        });

        // LABELS
        labels.addAll(graphicsQualityLabel, aaLabel, lrLabel, bloomLabel);

        // LENS FLARE
        lensflareBak = settings.postprocess.lensFlare;
        CheckBox lensFlare = new OwnCheckBox(I18n.txt("gui.lensflare"), skin, pad5);
        lensFlare.setName("lens flare");
        lensFlare.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.LENS_FLARE_CMD, lensFlare.isChecked(), true);
                return true;
            }
            return false;
        });
        lensFlare.setChecked(settings.postprocess.lensFlare);

        // LIGHT GLOW
        lightGlowBak = settings.postprocess.lightGlow;
        CheckBox lightGlow = new OwnCheckBox(I18n.txt("gui.lightscattering"), skin, pad5);
        lightGlow.setName("light scattering");
        lightGlow.setChecked(settings.postprocess.lightGlow);
        lightGlow.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.LIGHT_SCATTERING_CMD, lightGlow.isChecked(), true);
                return true;
            }
            return false;
        });

        // MOTION BLUR
        motionblurBak = settings.postprocess.motionBlur;
        CheckBox motionBlur = new OwnCheckBox(I18n.txt("gui.motionblur"), skin, pad5);
        motionBlur.setName("motion blur");
        motionBlur.setChecked(!settings.program.safeMode && settings.postprocess.motionBlur);
        motionBlur.setDisabled(settings.program.safeMode);
        motionBlur.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.MOTION_BLUR_CMD, motionBlur.isChecked(), true);
                return true;
            }
            return false;
        });

        graphics.add(graphicsQualityLabel).left().padRight(pad20).padBottom(pad5);
        graphics.add(gquality).left().padRight(pad10).padBottom(pad5);
        graphics.add(gqualityTooltip).left().padBottom(pad5).row();
        noticeHiResCell = graphics.add();
        noticeHiResCell.colspan(2).left().row();
        final Cell<Actor> noticeGraphicsCell = graphics.add((Actor) null);
        noticeGraphicsCell.colspan(2).left().row();
        graphics.add(aaLabel).left().padRight(pad20).padBottom(pad5);
        graphics.add(aa).left().padRight(pad10).padBottom(pad5);
        graphics.add(aaTooltip).left().padBottom(pad5).row();
        graphics.add(lrLabel).left().padRight(pad20).padBottom(pad5);
        graphics.add(lineRenderer).left().padBottom(pad5).row();
        graphics.add(bloomLabel).left().padRight(pad20).padBottom(pad5);
        graphics.add(bloomEffect).left().padBottom(pad5).row();
        graphics.add(unsharpMaskLabel).left().padRight(pad20).padBottom(pad5);
        graphics.add(unsharpMaskFactor).left().padBottom(pad5).row();
        graphics.add(lensFlare).colspan(2).left().padBottom(pad5).row();
        graphics.add(lightGlow).colspan(2).left().padBottom(pad5).row();
        graphics.add(motionBlur).colspan(2).left().padBottom(pad5).row();

        // Add to content
        contentGraphicsTable.add(titleGraphics).left().padBottom(pad5 * 2).row();
        contentGraphicsTable.add(graphics).left().padBottom(pad5 * 4).row();

        // ELEVATION
        Label titleElevation = new OwnLabel(I18n.txt("gui.elevation.title"), skin, "header");
        Table elevation = new Table();

        // ELEVATION TYPE
        OwnLabel elevationTypeLabel = new OwnLabel(I18n.txt("gui.elevation.type"), skin);
        ElevationComboBoxBean[] ecbb = new ElevationComboBoxBean[ElevationType.values().length];
        i = 0;
        for (ElevationType et : ElevationType.values()) {
            ecbb[i] = new ElevationComboBoxBean(I18n.txt("gui.elevation.type." + et.toString().toLowerCase()), et);
            i++;
        }
        elevationSb = new OwnSelectBox<>(skin);
        elevationSb.setItems(ecbb);
        elevationSb.setWidth(textWidth * 3f);
        elevationSb.setSelectedIndex(Settings.settings.scene.renderer.elevation.type.ordinal());
        elevationSb.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                enableComponents(elevationSb.getSelected().type.isTessellation(), tessQuality, tessQualityLabel);
            }
            return false;
        });

        // TESSELLATION QUALITY
        tessQualityLabel = new OwnLabel(I18n.txt("gui.elevation.tessellation.quality"), skin);
        tessQualityLabel.setDisabled(!settings.scene.renderer.elevation.type.isTessellation());

        tessQuality = new OwnSlider(Constants.MIN_TESS_QUALITY, Constants.MAX_TESS_QUALITY, 0.1f, skin);
        tessQuality.setDisabled(!settings.scene.renderer.elevation.type.isTessellation());
        tessQuality.setWidth(sliderWidth);
        tessQuality.setValue((float) settings.scene.renderer.elevation.quality);

        // LABELS
        labels.add(elevationTypeLabel, tessQualityLabel);

        elevation.add(elevationTypeLabel).left().padRight(pad20).padBottom(pad5);
        elevation.add(elevationSb).left().padRight(pad5 * 2f).padBottom(pad5).row();
        elevation.add(tessQualityLabel).left().padRight(pad20).padBottom(pad5);
        elevation.add(tessQuality).left().padRight(pad5 * 2f).padBottom(pad5);

        // Add to content
        contentGraphicsTable.add(titleElevation).left().padBottom(pad5 * 2).row();
        contentGraphicsTable.add(elevation).left().padBottom(pad5 * 4).row();

        // SHADOWS
        Label titleShadows = new OwnLabel(I18n.txt("gui.graphics.shadows"), skin, "header");
        Table shadows = new Table();

        // SHADOW MAP RESOLUTION
        OwnLabel smResolutionLabel = new OwnLabel(I18n.txt("gui.graphics.shadows.resolution"), skin);
        smResolutionLabel.setDisabled(!settings.scene.renderer.shadow.active);
        IntValidator smResValidator = new IntValidator(128, 4096);
        smResolution = new OwnTextField(Integer.toString(MathUtils.clamp(settings.scene.renderer.shadow.resolution, 128, 4096)), skin, smResValidator);
        smResolution.setWidth(textWidth * 3f);
        smResolution.setDisabled(!settings.scene.renderer.shadow.active);

        // N SHADOWS
        OwnLabel nShadowsLabel = new OwnLabel("#" + I18n.txt("gui.graphics.shadows"), skin);
        nShadowsLabel.setDisabled(!settings.scene.renderer.shadow.active);

        int nSh = 10;
        ComboBoxBean[] nsh = new ComboBoxBean[nSh];
        IntStream.rangeClosed(1, nSh).forEach(s -> nsh[s - 1] = new ComboBoxBean(String.valueOf(s), s));

        nshadows = new OwnSelectBox<>(skin);
        nshadows.setItems(nsh);
        nshadows.setWidth(textWidth * 3f);
        nshadows.setSelected(nsh[settings.scene.renderer.shadow.number - 1]);
        nshadows.setDisabled(!settings.scene.renderer.shadow.active);

        // ENABLE SHADOWS
        shadowsCb = new OwnCheckBox(I18n.txt("gui.graphics.shadows.enable"), skin, "default", pad5);
        shadowsCb.setChecked(settings.scene.renderer.shadow.active);
        shadowsCb.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                // Enable or disable resolution
                enableComponents(shadowsCb.isChecked(), smResolution, smResolutionLabel, nshadows, nShadowsLabel);
                return true;
            }
            return false;
        });

        // LABELS
        labels.add(smResolutionLabel);

        shadows.add(shadowsCb).left().padRight(pad5 * 2).padBottom(pad5).row();
        shadows.add(smResolutionLabel).left().padRight(pad5 * 4).padBottom(pad5);
        shadows.add(smResolution).left().padRight(pad5 * 2).padBottom(pad5).row();
        shadows.add(nShadowsLabel).left().padRight(pad5 * 4).padBottom(pad5);
        shadows.add(nshadows).left().padRight(pad5 * 2).padBottom(pad5);

        // Add to content
        contentGraphicsTable.add(titleShadows).left().padBottom(pad5 * 2).row();
        contentGraphicsTable.add(shadows).left().padBottom(pad5 * 4).row();

        // DISPLAY SETTINGS
        Label titleDisplay = new OwnLabel(I18n.txt("gui.graphics.imglevels"), skin, "header");
        Table display = new Table();

        brightnessBak = settings.postprocess.levels.brightness;
        contrastBak = settings.postprocess.levels.contrast;
        hueBak = settings.postprocess.levels.hue;
        saturationBak = settings.postprocess.levels.saturation;
        gammaBak = settings.postprocess.levels.gamma;
        toneMappingBak = settings.postprocess.toneMapping.type;
        exposureBak = settings.postprocess.toneMapping.exposure;

        /* Brightness */
        OwnLabel brightnessl = new OwnLabel(I18n.txt("gui.brightness"), skin, "default");
        Slider brightness = new OwnSlider(Constants.MIN_SLIDER, Constants.MAX_SLIDER, 1, skin);
        brightness.setName("brightness");
        brightness.setWidth(sliderWidth);
        brightness.setValue(MathUtilsd.lint(settings.postprocess.levels.brightness, Constants.MIN_BRIGHTNESS, Constants.MAX_BRIGHTNESS, Constants.MIN_SLIDER, Constants.MAX_SLIDER));
        brightness.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.BRIGHTNESS_CMD, MathUtilsd.lint(brightness.getValue(), Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.MIN_BRIGHTNESS, Constants.MAX_BRIGHTNESS), true);
                return true;
            }
            return false;
        });

        display.add(brightnessl).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(brightness).left().padRight(pad5 * 2).padBottom(pad5).row();

        /* Contrast */
        OwnLabel contrastl = new OwnLabel(I18n.txt("gui.contrast"), skin, "default");
        Slider contrast = new OwnSlider(Constants.MIN_SLIDER, Constants.MAX_SLIDER, 1, skin);
        contrast.setName("contrast");
        contrast.setWidth(sliderWidth);
        contrast.setValue(MathUtilsd.lint(settings.postprocess.levels.contrast, Constants.MIN_CONTRAST, Constants.MAX_CONTRAST, Constants.MIN_SLIDER, Constants.MAX_SLIDER));
        contrast.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.CONTRAST_CMD, MathUtilsd.lint(contrast.getValue(), Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.MIN_CONTRAST, Constants.MAX_CONTRAST), true);
                return true;
            }
            return false;
        });

        display.add(contrastl).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(contrast).left().padRight(pad5 * 2).padBottom(pad5).row();

        /* Hue */
        OwnLabel huel = new OwnLabel(I18n.txt("gui.hue"), skin, "default");
        Slider hue = new OwnSlider(Constants.MIN_SLIDER, Constants.MAX_SLIDER, 1, skin);
        hue.setName("hue");
        hue.setWidth(sliderWidth);
        hue.setValue(MathUtilsd.lint(settings.postprocess.levels.hue, Constants.MIN_HUE, Constants.MAX_HUE, Constants.MIN_SLIDER, Constants.MAX_SLIDER));
        hue.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.HUE_CMD, MathUtilsd.lint(hue.getValue(), Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.MIN_HUE, Constants.MAX_HUE), true);
                return true;
            }
            return false;
        });

        display.add(huel).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(hue).left().padRight(pad5 * 2).padBottom(pad5).row();

        /* Saturation */
        OwnLabel saturationl = new OwnLabel(I18n.txt("gui.saturation"), skin, "default");
        Slider saturation = new OwnSlider(Constants.MIN_SLIDER, Constants.MAX_SLIDER, 1, skin);
        saturation.setName("saturation");
        saturation.setWidth(sliderWidth);
        saturation.setValue(MathUtilsd.lint(settings.postprocess.levels.saturation, Constants.MIN_SATURATION, Constants.MAX_SATURATION, Constants.MIN_SLIDER, Constants.MAX_SLIDER));
        saturation.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SATURATION_CMD, MathUtilsd.lint(saturation.getValue(), Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.MIN_SATURATION, Constants.MAX_SATURATION), true);
                return true;
            }
            return false;
        });

        display.add(saturationl).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(saturation).left().padRight(pad5 * 2).padBottom(pad5).row();

        /* Gamma */
        OwnLabel gammal = new OwnLabel(I18n.txt("gui.gamma"), skin, "default");
        Slider gamma = new OwnSlider(Constants.MIN_GAMMA, Constants.MAX_GAMMA, 0.1f, false, skin);
        gamma.setName("gamma");
        gamma.setWidth(sliderWidth);
        gamma.setValue(settings.postprocess.levels.gamma);
        gamma.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.GAMMA_CMD, gamma.getValue(), true);
                return true;
            }
            return false;
        });

        display.add(gammal).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(gamma).left().padRight(pad5 * 2).padBottom(pad5).row();

        /* Tone Mapping */
        OwnLabel toneMappingl = new OwnLabel(I18n.txt("gui.tonemapping.type"), skin, "default");
        int nToneMapping = ToneMapping.values().length;
        ComboBoxBean[] toneMappingTypes = new ComboBoxBean[nToneMapping];
        for(int itm = 0; itm < nToneMapping; itm++){
            ToneMapping tm = ToneMapping.values()[itm];
            toneMappingTypes[itm] = new ComboBoxBean(I18n.txt("gui.tonemapping." + tm.name().toLowerCase(Locale.ROOT)), tm.ordinal());
        }

        OwnSelectBox<ComboBoxBean> toneMappingSelect = new OwnSelectBox<>(skin);
        toneMappingSelect.setItems(toneMappingTypes);
        toneMappingSelect.setWidth(textWidth * 3f);
        toneMappingSelect.setSelectedIndex(settings.postprocess.toneMapping.type.ordinal());
        display.add(toneMappingl).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(toneMappingSelect).left().padBottom(pad5).row();

        /* Exposure */
        OwnLabel exposurel = new OwnLabel(I18n.txt("gui.exposure"), skin, "default");
        exposurel.setDisabled(settings.postprocess.toneMapping.type != ToneMapping.EXPOSURE);
        Slider exposure = new OwnSlider(Constants.MIN_EXPOSURE, Constants.MAX_EXPOSURE, 0.1f, false, skin);
        exposure.setName("exposure");
        exposure.setWidth(sliderWidth);
        exposure.setValue(settings.postprocess.toneMapping.exposure);
        exposure.setDisabled(settings.postprocess.toneMapping.type != ToneMapping.EXPOSURE);
        exposure.addListener(event -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.EXPOSURE_CMD, exposure.getValue(), true);
                return true;
            }
            return false;
        });
        toneMappingSelect.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                ToneMapping newTM = ToneMapping.values()[toneMappingSelect.getSelectedIndex()];
                EventManager.instance.post(Events.TONEMAPPING_TYPE_CMD, newTM, true);
                boolean disabled = newTM != ToneMapping.EXPOSURE;
                exposurel.setDisabled(disabled);
                exposure.setDisabled(disabled);
                return true;
            }
            return false;
        });

        display.add(exposurel).left().padRight(pad5 * 4).padBottom(pad5);
        display.add(exposure).left().padRight(pad5 * 2).padBottom(pad5).row();

        // LABELS
        labels.addAll(brightnessl, contrastl, huel, saturationl, gammal);

        // Add to content
        contentGraphicsTable.add(titleDisplay).left().padBottom(pad5 * 2).row();
        contentGraphicsTable.add(display).left();
        /*
         * ==== UI ====
         */
        float labelWidth = 400f;

        final Table contentUI = new Table(skin);
        contentUI.setWidth(contentWidth);
        contentUI.align(Align.top | Align.left);
        contents.add(contentUI);

        OwnLabel titleUI = new OwnLabel(I18n.txt("gui.ui.interfacesettings"), skin, "header");

        Table ui = new Table();

        // LANGUAGE
        OwnLabel langLabel = new OwnLabel(I18n.txt("gui.ui.language"), skin);
        langLabel.setWidth(labelWidth);
        File i18nfolder = new File(settings.ASSETS_LOC + File.separator + "i18n");
        String i18nname = "gsbundle";
        String[] files = i18nfolder.list();
        assert files != null;
        LangComboBoxBean[] langs = new LangComboBoxBean[files.length];
        i = 0;
        for (String file : files) {
            if (file.startsWith("gsbundle") && file.endsWith(".properties")) {
                String locale = file.substring(i18nname.length(), file.length() - ".properties".length());
                // Default locale
                if (locale.isEmpty())
                    locale = "-en-GB";

                // Remove underscore _
                locale = locale.substring(1).replace("_", "-");
                Locale loc = Locale.forLanguageTag(locale);
                langs[i] = new LangComboBoxBean(loc);
            }
            i++;
        }
        Arrays.sort(langs);

        lang = new OwnSelectBox<>(skin);
        lang.setWidth(textWidth * 3f);
        lang.setItems(langs);
        if (settings.program.locale != null) {
            lang.setSelected(langs[idxLang(settings.program.locale, langs)]);
        } else {
            // Empty locale
            int localeIndex = idxLang(null, langs);
            if (localeIndex < 0 || localeIndex >= langs.length) {
                localeIndex = idxLang(Locale.getDefault().toLanguageTag(), langs);
                if (localeIndex < 0 || localeIndex >= langs.length) {
                    // Default is en_GB
                    localeIndex = 2;
                }
            }
            lang.setSelected(langs[localeIndex]);
        }

        // THEME
        OwnLabel themeLabel = new OwnLabel(I18n.txt("gui.ui.theme"), skin);
        themeLabel.setWidth(labelWidth);

        StrComboBoxBean[] themes = new StrComboBoxBean[] { new StrComboBoxBean(I18n.txt("gui.theme.darkgreen"), "dark-green"), new StrComboBoxBean(I18n.txt("gui.theme.darkblue"), "dark-blue"), new StrComboBoxBean(I18n.txt("gui.theme.darkorange"), "dark-orange"), new StrComboBoxBean(I18n.txt("gui.theme.nightred"), "night-red") };
        theme = new OwnSelectBox<>(skin);
        theme.setWidth(textWidth * 3f);
        theme.setItems(themes);
        int themeIndex;
        if (settings.program.ui.theme.contains("dark-green")) {
            themeIndex = 0;
        } else if (settings.program.ui.theme.contains("dark-blue")) {
            themeIndex = 1;
        } else if (settings.program.ui.theme.contains("dark-orange")) {
            themeIndex = 2;
        } else {
            themeIndex = 3;
        }
        theme.setSelectedIndex(themeIndex);

        // SCALING
        OwnLabel uiScalelabel = new OwnLabel(I18n.txt("gui.ui.theme.scale"), skin);
        uiScalelabel.setWidth(labelWidth);
        uiScale = new OwnSlider(Constants.UI_SCALE_MIN, Constants.UI_SCALE_MAX, Constants.SLIDER_STEP_SMALL, Constants.UI_SCALE_INTERNAL_MIN, Constants.UI_SCALE_INTERNAL_MAX, skin);
        uiScale.setWidth(textWidth * 3f);
        uiScale.setMappedValue(settings.program.ui.scale);
        OwnTextButton applyUiScale = new OwnTextButton(I18n.txt("gui.apply"), skin);
        applyUiScale.pad(0, pad10, 0, pad10);
        applyUiScale.setHeight(buttonHeight);
        applyUiScale.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.UI_SCALE_CMD, uiScale.getMappedValue());
                return true;
            }
            return false;
        });

        // POINTER COORDINATES
        pointerCoords = new OwnCheckBox(I18n.txt("gui.ui.pointercoordinates"), skin, "default", pad5);
        pointerCoords.setChecked(settings.program.pointer.coordinates);

        // MINIMAP SIZE
        OwnLabel minimapSizeLabel = new OwnLabel(I18n.txt("gui.ui.minimap.size"), skin, "default");
        minimapSizeLabel.setWidth(labelWidth);
        minimapSize = new OwnSlider(Constants.MIN_MINIMAP_SIZE, Constants.MAX_MINIMAP_SIZE, 1f, skin);
        minimapSize.setName("minimapSize");
        minimapSize.setWidth(sliderWidth);
        minimapSize.setValue(settings.program.minimap.size);

        // PREFERRED DISTANCE UNITS
        OwnLabel distUnitsLabel = new OwnLabel(I18n.txt("gui.ui.distance.units"), skin, "default");
        distUnitsLabel.setWidth(labelWidth);
        DistanceUnits[] dus = DistanceUnits.values();
        ComboBoxBean[] distUnits = new ComboBoxBean[dus.length];
        for(int idu = 0; idu < dus.length; idu++){
            DistanceUnits du = dus[idu];
            distUnits[idu] = new ComboBoxBean(I18n.txt("gui.ui.distance.units." + du.name().toLowerCase(Locale.ROOT)), du.ordinal());
        }
        distUnitsSelect = new OwnSelectBox<>(skin);
        distUnitsSelect.setItems(distUnits);
        distUnitsSelect.setWidth(textWidth * 3f);
        distUnitsSelect.setSelectedIndex(settings.program.ui.distanceUnits.ordinal());

        // LABELS
        labels.addAll(langLabel, themeLabel);

        // Add to table
        ui.add(langLabel).left().padRight(pad5 * 4).padBottom(pad10);
        ui.add(lang).colspan(2).left().padBottom(pad10).row();
        ui.add(themeLabel).left().padRight(pad5 * 4).padBottom(pad10);
        ui.add(theme).colspan(2).left().padBottom(pad10).row();
        ui.add(uiScalelabel).left().padRight(pad5 * 4).padBottom(pad10);
        ui.add(uiScale).left().padRight(pad5).padBottom(pad10);
        ui.add(applyUiScale).left().padBottom(pad10).row();
        ui.add(pointerCoords).colspan(3).left().padRight(pad5).padBottom(pad10).row();
        ui.add(minimapSizeLabel).left().padRight(pad5).padBottom(pad10);
        ui.add(minimapSize).colspan(2).left().padRight(pad5).padBottom(pad10).row();
        ui.add(distUnitsLabel).left().padRight(pad5).padBottom(pad10);
        ui.add(distUnitsSelect).colspan(2).left().padRight(pad5).padBottom(pad10).row();


        /* CROSSHAIR AND MARKERS */
        OwnLabel titleCrosshair = new OwnLabel(I18n.txt("gui.ui.crosshair"), skin, "header");
        Table ch = new Table();

        // CROSSHAIR FOCUS
        crosshairFocusCb = new OwnCheckBox("" + I18n.txt("gui.ui.crosshair.focus"), skin, pad10);
        crosshairFocusCb.setName("ch focus");
        crosshairFocusCb.setChecked(settings.scene.crosshair.focus);

        // CROSSHAIR CLOSEST
        crosshairClosestCb = new OwnCheckBox("" + I18n.txt("gui.ui.crosshair.closest"), skin, pad10);
        crosshairClosestCb.setName("ch closest");
        crosshairClosestCb.setChecked(settings.scene.crosshair.closest);

        // CROSSHAIR HOME
        crosshairHomeCb = new OwnCheckBox("" + I18n.txt("gui.ui.crosshair.home"), skin, pad10);
        crosshairHomeCb.setName("ch home");
        crosshairHomeCb.setChecked(settings.scene.crosshair.home );

        // Add to table
        ch.add(crosshairFocusCb).left().padBottom(pad5).row();
        ch.add(crosshairClosestCb).left().padBottom(pad5).row();
        ch.add(crosshairHomeCb).left().padBottom(pad5).row();

        /* POINTER GUIDES */
        OwnLabel titleGuides = new OwnLabel(I18n.txt("gui.ui.pointer.guides"), skin, "header");
        Table pg = new Table();

        // GUIDES CHECKBOX
        pointerGuidesCb = new OwnCheckBox("" + I18n.txt("gui.ui.pointer.guides.display"), skin, pad10);
        pointerGuidesCb.setName("pointer guides cb");
        pointerGuidesCb.setChecked(settings.program.pointer.guides.active);
        OwnImageButton guidesTooltip = new OwnImageButton(skin, "tooltip");
        guidesTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.ui.pointer.guides.info"), skin));
        HorizontalGroup pointerGuidesCbGroup = new HorizontalGroup();
        pointerGuidesCbGroup.space(pad10);
        pointerGuidesCbGroup.addActor(pointerGuidesCb);
        pointerGuidesCbGroup.addActor(guidesTooltip);

        // GUIDES COLOR
        float colorPickerSize = 32f;
        pointerGuidesColor = new ColorPicker(stage, skin);
        pointerGuidesColor.setPickedColor(settings.program.pointer.guides.color);

        // GUIDES WIDTH
        OwnLabel guidesWidthLabel = new OwnLabel(I18n.txt("gui.ui.pointer.guides.width"), skin, "default");
        guidesWidthLabel.setWidth(labelWidth);
        pointerGuidesWidth = new OwnSlider(Constants.MIN_POINTER_GUIDES_WIDTH, Constants.MAX_POINTER_GUIDES_WIDTH, Constants.SLIDER_STEP_TINY, skin);
        pointerGuidesWidth.setName("pointerguideswidth");
        pointerGuidesWidth.setWidth(sliderWidth);
        pointerGuidesWidth.setValue(settings.program.pointer.guides.width);

        // Add to table
        pg.add(pointerGuidesCbGroup).left().colspan(2).padBottom(pad5).row();
        pg.add(new OwnLabel(I18n.txt("gui.ui.pointer.guides.color"), skin)).left().padBottom(pad5).padRight(pad10);
        pg.add(pointerGuidesColor).left().size(colorPickerSize).padBottom(pad5).row();
        pg.add(guidesWidthLabel).left().padBottom(pad5).padRight(pad10);
        pg.add(pointerGuidesWidth).left().padBottom(pad5).padRight(pad10);

        /* RECURSIVE GRID */
        OwnLabel titleRecgrid = new OwnLabel(I18n.txt("gui.ui.recursivegrid"), skin, "header");
        Table rg = new Table();

        // ORIGIN
        OwnLabel originLabel = new OwnLabel(I18n.txt("gui.ui.recursivegrid.origin"), skin);
        originLabel.setWidth(labelWidth);
        String[] origins = new String[] { I18n.txt("gui.ui.recursivegrid.origin.refsys"), I18n.txt("gui.ui.recursivegrid.origin.focus") };
        recgridOrigin = new OwnSelectBox<>(skin);
        recgridOrigin.setWidth(textWidth * 3f);
        recgridOrigin.setItems(origins);
        recgridOrigin.setSelectedIndex(settings.program.recursiveGrid.origin.ordinal());

        // PROJECTION LINES
        recgridProjectionLinesCb = new OwnCheckBox("" + I18n.txt("gui.ui.recursivegrid.projlines"), skin, pad10);
        recgridProjectionLinesCb.setName("origin projection lines cb");
        recgridProjectionLinesCb.setChecked(settings.program.recursiveGrid.projectionLines);

        // Add to table
        rg.add(originLabel).left().padBottom(pad5).padRight(pad10);
        rg.add(recgridOrigin).left().padBottom(pad5).row();
        rg.add(recgridProjectionLinesCb).colspan(2).left().padBottom(pad5);

        // Add to content
        contentUI.add(titleUI).left().padBottom(pad5 * 2f).row();
        contentUI.add(ui).left().padBottom(pad5 * 4f).row();
        contentUI.add(titleCrosshair).left().padBottom(pad5 * 2f).row();
        contentUI.add(ch).left().padBottom(pad5 * 4f).row();
        contentUI.add(titleGuides).left().padBottom(pad5 * 2f).row();
        contentUI.add(pg).left().padBottom(pad5 * 4f).row();
        contentUI.add(titleRecgrid).left().padBottom(pad5 * 2f).row();
        contentUI.add(rg).left();


        /*
         * ==== PERFORMANCE ====
         */
        final Table contentPerformance = new Table(skin);
        contentPerformance.setWidth(contentWidth);
        contentPerformance.align(Align.top | Align.left);
        contents.add(contentPerformance);

        // MULTITHREADING
        OwnLabel titleMultiThread = new OwnLabel(I18n.txt("gui.multithreading"), skin, "header");

        Table multiThread = new Table(skin);

        OwnLabel numThreadsLabel = new OwnLabel(I18n.txt("gui.thread.number"), skin);
        int maxThreads = Runtime.getRuntime().availableProcessors();
        ComboBoxBean[] cbs = new ComboBoxBean[maxThreads + 1];
        cbs[0] = new ComboBoxBean(I18n.txt("gui.letdecide"), 0);
        for (i = 1; i <= maxThreads; i++) {
            cbs[i] = new ComboBoxBean(I18n.txt("gui.thread", i), i);
        }
        numThreads = new OwnSelectBox<>(skin);
        numThreads.setWidth(textWidth * 3f);
        numThreads.setItems(cbs);
        numThreads.setSelectedIndex(settings.performance.numberThreads);

        multithreadCb = new OwnCheckBox(I18n.txt("gui.thread.enable"), skin, "default", pad5);
        multithreadCb.addListener(event -> {
            if (event instanceof ChangeEvent) {
                numThreads.setDisabled(!multithreadCb.isChecked());
                // Add notice
                return true;
            }
            return false;
        });
        multithreadCb.setChecked(settings.performance.multithreading);
        numThreads.setDisabled(!multithreadCb.isChecked());

        // Add to table
        multiThread.add(multithreadCb).colspan(2).left().padBottom(pad5).row();
        multiThread.add(numThreadsLabel).left().padRight(pad5 * 4).padBottom(pad5);
        multiThread.add(numThreads).left().padBottom(pad5).row();
        final Cell<Actor> noticeMultiThreadCell = multiThread.add((Actor) null);
        noticeMultiThreadCell.colspan(2).left();

        multithreadCb.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (noticeMultiThreadCell.getActor() == null) {
                    String nextInfoStr = I18n.txt("gui.ui.info") + '\n';
                    int lines = GlobalResources.countOccurrences(nextInfoStr, '\n');
                    TextArea nextTimeInfo = new OwnTextArea(nextInfoStr, skin, "info");
                    nextTimeInfo.setDisabled(true);
                    nextTimeInfo.setPrefRows(lines + 1);
                    nextTimeInfo.setWidth(taWidth);
                    nextTimeInfo.clearListeners();
                    noticeMultiThreadCell.setActor(nextTimeInfo);
                }
                return true;
            }
            return false;
        });

        // Add to content
        contentPerformance.add(titleMultiThread).left().padBottom(pad5 * 2).row();
        contentPerformance.add(multiThread).left().padBottom(pad5 * 4).row();

        // DRAW DISTANCE
        OwnLabel titleLod = new OwnLabel(I18n.txt("gui.lod"), skin, "header");

        Table lod = new Table(skin);

        // Smooth transitions
        lodFadeCb = new OwnCheckBox(I18n.txt("gui.lod.fade"), skin, "default", pad5);
        lodFadeCb.setChecked(settings.scene.octree.fade);

        // Draw distance
        OwnLabel ddLabel = new OwnLabel(I18n.txt("gui.lod.thresholds"), skin);
        lodTransitions = new OwnSlider(Constants.MIN_SLIDER, Constants.MAX_SLIDER, 0.1f, Constants.MIN_LOD_TRANS_ANGLE_DEG, Constants.MAX_LOD_TRANS_ANGLE_DEG, false, skin);
        lodTransitions.setDisplayValueMapped(true);
        lodTransitions.setWidth(sliderWidth);
        lodTransitions.setMappedValue(settings.scene.octree.threshold[0] * MathUtilsd.radDeg);

        OwnImageButton lodTooltip = new OwnImageButton(skin, "tooltip");
        lodTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.lod.thresholds.info"), skin));

        // LABELS
        labels.addAll(numThreadsLabel, ddLabel);

        // Add to table
        lod.add(lodFadeCb).colspan(3).left().padBottom(pad5).row();
        lod.add(ddLabel).left().padRight(pad5 * 4).padBottom(pad5);
        lod.add(lodTransitions).left().padRight(pad10).padBottom(pad5);
        lod.add(lodTooltip).left().padBottom(pad5);

        // Add to content
        contentPerformance.add(titleLod).left().padBottom(pad5 * 2).row();
        contentPerformance.add(lod).left();

        /*
         * ==== CONTROLS ====
         */
        final Table contentControls = new Table(skin);
        contentControls.setWidth(contentWidth);
        contentControls.align(Align.top | Align.left);
        contents.add(contentControls);

        OwnLabel titleController = new OwnLabel(I18n.txt("gui.controller"), skin, "header");

        // DETECTED CONTROLLER NAMES
        controllersTable = new Table(skin);
        OwnLabel detectedLabel = new OwnLabel(I18n.txt("gui.controller.detected"), skin);
        generateControllersList(controllersTable);

        // CONTROLLER MAPPINGS
        OwnLabel mappingsLabel = new OwnLabel(I18n.txt("gui.controller.mappingsfile"), skin);
        controllerMappings = new OwnSelectBox<>(skin);
        reloadControllerMappings(null);

        // INVERT X
        invertx = new OwnCheckBox(I18n.txt("gui.controller.axis.invert", "X"), skin, "default", pad5);
        invertx.setChecked(settings.controls.gamepad.invertX);
        // INVERT Y
        inverty = new OwnCheckBox(I18n.txt("gui.controller.axis.invert", "Y"), skin, "default", pad5);
        inverty.setChecked(settings.controls.gamepad.invertY);

        // KEY BINDINGS
        OwnLabel titleKeybindings = new OwnLabel(I18n.txt("gui.keymappings"), skin, "header");

        Map<ProgramAction, Array<TreeSet<Integer>>> keyboardMappings = KeyBindings.instance.getSortedMappingsInv();
        String[][] data = new String[keyboardMappings.size()][];

        i = 0;
        for (ProgramAction action : keyboardMappings.keySet()) {
            Array<TreeSet<Integer>> keys = keyboardMappings.get(action);

            String[] act = new String[1 + keys.size];
            act[0] = action.actionName;
            for (int j = 0; j < keys.size; j++) {
                act[j + 1] = keysToString(keys.get(j));
            }

            data[i] = act;
            i++;
        }

        Table controls = new Table(skin);
        controls.align(Align.left | Align.top);
        // Header
        controls.add(new OwnLabel(I18n.txt("gui.keymappings.action"), skin, "header")).left();
        controls.add(new OwnLabel(I18n.txt("gui.keymappings.keys"), skin, "header")).left().row();

        controls.add(new OwnLabel(I18n.txt("action.forward"), skin)).left().padRight(pad10);
        controls.add(new OwnLabel(Keys.toString(Keys.UP).toUpperCase(), skin, "mono-pink")).left().row();
        controls.add(new OwnLabel(I18n.txt("action.backward"), skin)).left().padRight(pad10);
        controls.add(new OwnLabel(Keys.toString(Keys.DOWN).toUpperCase(), skin, "mono-pink")).left().row();
        controls.add(new OwnLabel(I18n.txt("action.left"), skin)).left().padRight(pad10);
        controls.add(new OwnLabel(Keys.toString(Keys.LEFT).toUpperCase(), skin, "mono-pink")).left().row();
        controls.add(new OwnLabel(I18n.txt("action.right"), skin)).left().padRight(pad10);
        controls.add(new OwnLabel(Keys.toString(Keys.RIGHT).toUpperCase(), skin, "mono-pink")).left().row();

        // Controls
        for (String[] action : data) {
            HorizontalGroup keysGroup = new HorizontalGroup();
            keysGroup.space(pad5);
            for (int j = 1; j < action.length; j++) {
                String[] keys = action[j].split("\\+");
                for (int k = 0; k < keys.length; k++) {
                    keysGroup.addActor(new OwnLabel(keys[k].trim(), skin, "mono-pink"));
                    if (k < keys.length - 1)
                        keysGroup.addActor(new OwnLabel("+", skin));
                }
                if (j < action.length - 1)
                    keysGroup.addActor(new OwnLabel("/", skin));
            }
            controls.add(new OwnLabel(action[0], skin)).left().padRight(pad10);
            controls.add(keysGroup).left().row();
        }

        OwnScrollPane controlsScroll = new OwnScrollPane(controls, skin, "minimalist-nobg");
        controlsScroll.setWidth(controlsScrollWidth);
        controlsScroll.setHeight(controlsScrollHeight);
        controlsScroll.setScrollingDisabled(true, false);
        controlsScroll.setSmoothScrolling(true);
        controlsScroll.setFadeScrollBars(false);
        scrolls.add(controlsScroll);

        // Add to content
        contentControls.add(titleController).colspan(2).left().padBottom(pad10).row();
        contentControls.add(detectedLabel).left().padBottom(pad10).padRight(pad10);
        contentControls.add(controllersTable).left().padBottom(pad10).row();
        contentControls.add(mappingsLabel).left().padBottom(pad10).padRight(pad10);
        contentControls.add(controllerMappings).left().padBottom(pad10).row();
        contentControls.add(invertx).left().colspan(2).padBottom(pad10).row();
        contentControls.add(inverty).left().colspan(2).padBottom(pad10).row();
        contentControls.add(titleKeybindings).colspan(2).left().padBottom(pad10).row();
        contentControls.add(controlsScroll).colspan(2).left();

        /*
         * ==== SCREENSHOTS ====
         */
        final Table contentScreenshots = new Table(skin);
        contentScreenshots.setWidth(contentWidth);
        contentScreenshots.align(Align.top | Align.left);
        contents.add(contentScreenshots);

        // SCREEN CAPTURE
        OwnLabel titleScreenshots = new OwnLabel(I18n.txt("gui.screencapture"), skin, "header");

        Table screenshots = new Table(skin);

        // Info
        String ssInfoStr = I18n.txt("gui.screencapture.info") + '\n';
        int ssLines = GlobalResources.countOccurrences(ssInfoStr, '\n');
        TextArea screenshotsInfo = new OwnTextArea(ssInfoStr, skin, "info");
        screenshotsInfo.setDisabled(true);
        screenshotsInfo.setPrefRows(ssLines + 1);
        screenshotsInfo.setWidth(taWidth);
        screenshotsInfo.clearListeners();

        // Save location
        OwnLabel screenshotsLocationLabel = new OwnLabel(I18n.txt("gui.screenshots.save"), skin);
        screenshotsLocationLabel.pack();
        screenshotsLocation = new OwnTextButton(settings.screenshot.location, skin);
        screenshotsLocation.pad(pad5);
        screenshotsLocation.addListener(event -> {
            if (event instanceof ChangeEvent) {
                FileChooser fc = new FileChooser(I18n.txt("gui.screenshots.directory.choose"), skin, stage, Paths.get(settings.screenshot.location), FileChooser.FileChooserTarget.DIRECTORIES);
                fc.setShowHidden(settings.program.fileChooser.showHidden);
                fc.setShowHiddenConsumer((showHidden) -> settings.program.fileChooser.showHidden = showHidden);
                fc.setResultListener((success, result) -> {
                    if (success) {
                        // do stuff with result
                        screenshotsLocation.setText(result.toString());
                    }
                    return true;
                });
                fc.show(stage);

                return true;
            }
            return false;
        });

        // Size
        final OwnLabel screenshotsSizeLabel = new OwnLabel(I18n.txt("gui.screenshots.size"), skin);
        screenshotsSizeLabel.setDisabled(settings.screenshot.isSimpleMode());
        final OwnLabel xLabel = new OwnLabel("x", skin);
        IValidator screenshotsSizeValidator = new IntValidator(ScreenshotSettings.MIN_SCREENSHOT_SIZE, ScreenshotSettings.MAX_SCREENSHOT_SIZE);
        sswidthField = new OwnTextField(Integer.toString(MathUtils.clamp(settings.screenshot.resolution[0], ScreenshotSettings.MIN_SCREENSHOT_SIZE, ScreenshotSettings.MAX_SCREENSHOT_SIZE)), skin, screenshotsSizeValidator);
        sswidthField.setWidth(textWidth);
        sswidthField.setDisabled(settings.screenshot.isSimpleMode());
        ssheightField = new OwnTextField(Integer.toString(MathUtils.clamp(settings.screenshot.resolution[1], ScreenshotSettings.MIN_SCREENSHOT_SIZE, ScreenshotSettings.MAX_SCREENSHOT_SIZE)), skin, screenshotsSizeValidator);
        ssheightField.setWidth(textWidth);
        ssheightField.setDisabled(settings.screenshot.isSimpleMode());
        HorizontalGroup ssSizeGroup = new HorizontalGroup();
        ssSizeGroup.space(pad5 * 2);
        ssSizeGroup.addActor(sswidthField);
        ssSizeGroup.addActor(xLabel);
        ssSizeGroup.addActor(ssheightField);

        // Mode
        OwnLabel ssModeLabel = new OwnLabel(I18n.txt("gui.screenshots.mode"), skin);
        ComboBoxBean[] screenshotModes = new ComboBoxBean[] { new ComboBoxBean(I18n.txt("gui.screenshots.mode.simple"), 0), new ComboBoxBean(I18n.txt("gui.screenshots.mode.redraw"), 1) };
        screenshotMode = new OwnSelectBox<>(skin);
        screenshotMode.setItems(screenshotModes);
        screenshotMode.setWidth(textWidth * 3f);
        screenshotMode.addListener(event -> {
            if (event instanceof ChangeEvent) {
                // Simple
                // Redraw
                enableComponents(screenshotMode.getSelected().value != 0, sswidthField, ssheightField, screenshotsSizeLabel, xLabel);
                return true;
            }
            return false;
        });
        screenshotMode.setSelected(screenshotModes[settings.screenshot.mode.ordinal()]);
        screenshotMode.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.screenshotmode"), skin));

        OwnImageButton screenshotsModeTooltip = new OwnImageButton(skin, "tooltip");
        screenshotsModeTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.screenshotmode"), skin));

        HorizontalGroup ssModeGroup = new HorizontalGroup();
        ssModeGroup.space(pad5);
        ssModeGroup.addActor(screenshotMode);
        ssModeGroup.addActor(screenshotsModeTooltip);

        // LABELS
        labels.addAll(screenshotsLocationLabel, ssModeLabel, screenshotsSizeLabel);

        // Add to table
        screenshots.add(screenshotsInfo).colspan(2).left().padBottom(pad5).row();
        screenshots.add(screenshotsLocationLabel).left().padRight(pad5 * 4).padBottom(pad5);
        screenshots.add(screenshotsLocation).left().expandX().padBottom(pad5).row();
        screenshots.add(ssModeLabel).left().padRight(pad5 * 4).padBottom(pad5);
        screenshots.add(ssModeGroup).left().expandX().padBottom(pad5).row();
        screenshots.add(screenshotsSizeLabel).left().padRight(pad5 * 4).padBottom(pad5);
        screenshots.add(ssSizeGroup).left().expandX().padBottom(pad5).row();

        // Add to content
        contentScreenshots.add(titleScreenshots).left().padBottom(pad5 * 2).row();
        contentScreenshots.add(screenshots).left();

        /*
         * ==== FRAME OUTPUT ====
         */
        final Table contentFrames = new Table(skin);
        contentFrames.setWidth(contentWidth);
        contentFrames.align(Align.top | Align.left);
        contents.add(contentFrames);

        // FRAME OUTPUT CONFIG
        OwnLabel titleFrameoutput = new OwnLabel(I18n.txt("gui.frameoutput"), skin, "header");

        Table frameoutput = new Table(skin);

        // Info
        String foinfostr = I18n.txt("gui.frameoutput.info") + '\n';
        ssLines = GlobalResources.countOccurrences(foinfostr, '\n');
        TextArea frameoutputInfo = new OwnTextArea(foinfostr, skin, "info");
        frameoutputInfo.setDisabled(true);
        frameoutputInfo.setPrefRows(ssLines + 1);
        frameoutputInfo.setWidth(taWidth);
        frameoutputInfo.clearListeners();

        // Save location
        OwnLabel frameoutputLocationLabel = new OwnLabel(I18n.txt("gui.frameoutput.location"), skin);
        frameoutputLocation = new OwnTextButton(settings.frame.location, skin);
        frameoutputLocation.pad(pad5);
        frameoutputLocation.addListener(event -> {
            if (event instanceof ChangeEvent) {
                FileChooser fc = new FileChooser(I18n.txt("gui.frameoutput.directory.choose"), skin, stage, Paths.get(settings.frame.location), FileChooser.FileChooserTarget.DIRECTORIES);
                fc.setShowHidden(settings.program.fileChooser.showHidden);
                fc.setShowHiddenConsumer((showHidden) -> settings.program.fileChooser.showHidden = showHidden);
                fc.setResultListener((success, result) -> {
                    if (success) {
                        // do stuff with result
                        frameoutputLocation.setText(result.toString());
                    }
                    return true;
                });
                fc.show(stage);

                return true;
            }
            return false;
        });

        // Prefix
        OwnLabel prefixLabel = new OwnLabel(I18n.txt("gui.frameoutput.prefix"), skin);
        frameoutputPrefix = new OwnTextField(settings.frame.prefix, skin, new RegexpValidator("^\\w+$"));
        frameoutputPrefix.setWidth(textWidth * 3f);

        // FPS
        OwnLabel fpsLabel = new OwnLabel(I18n.txt("gui.target.fps"), skin);
        frameoutputFps = new OwnTextField(nf3.format(settings.frame.targetFps), skin, new DoubleValidator(Constants.MIN_FPS, Constants.MAX_FPS));
        frameoutputFps.setWidth(textWidth * 3f);

        // Size
        final OwnLabel frameoutputSizeLabel = new OwnLabel(I18n.txt("gui.frameoutput.size"), skin);
        frameoutputSizeLabel.setDisabled(settings.frame.isSimpleMode());
        final OwnLabel xLabelfo = new OwnLabel("x", skin);
        IValidator frameoutputSizeValidator = new IntValidator(ScreenshotSettings.MIN_SCREENSHOT_SIZE, ScreenshotSettings.MAX_SCREENSHOT_SIZE);
        fowidthField = new OwnTextField(Integer.toString(MathUtils.clamp(settings.frame.resolution[0], ScreenshotSettings.MIN_SCREENSHOT_SIZE, ScreenshotSettings.MAX_SCREENSHOT_SIZE)), skin, frameoutputSizeValidator);
        fowidthField.setWidth(textWidth);
        fowidthField.setDisabled(settings.frame.isSimpleMode());
        foheightField = new OwnTextField(Integer.toString(MathUtils.clamp(settings.frame.resolution[1], ScreenshotSettings.MIN_SCREENSHOT_SIZE, ScreenshotSettings.MAX_SCREENSHOT_SIZE)), skin, frameoutputSizeValidator);
        foheightField.setWidth(textWidth);
        foheightField.setDisabled(settings.frame.isSimpleMode());
        HorizontalGroup foSizeGroup = new HorizontalGroup();
        foSizeGroup.space(pad5 * 2);
        foSizeGroup.addActor(fowidthField);
        foSizeGroup.addActor(xLabelfo);
        foSizeGroup.addActor(foheightField);

        // Mode
        OwnLabel fomodeLabel = new OwnLabel(I18n.txt("gui.screenshots.mode"), skin);
        ComboBoxBean[] frameoutputModes = new ComboBoxBean[] { new ComboBoxBean(I18n.txt("gui.screenshots.mode.simple"), 0), new ComboBoxBean(I18n.txt("gui.screenshots.mode.redraw"), 1) };
        frameoutputMode = new OwnSelectBox<>(skin);
        frameoutputMode.setItems(frameoutputModes);
        frameoutputMode.setWidth(textWidth * 3f);
        frameoutputMode.addListener(event -> {
            if (event instanceof ChangeEvent) {
                // Simple
                // Redraw
                enableComponents(frameoutputMode.getSelected().value != 0, fowidthField, foheightField, frameoutputSizeLabel, xLabelfo);
                return true;
            }
            return false;
        });
        frameoutputMode.setSelected(frameoutputModes[settings.frame.mode.ordinal()]);
        frameoutputMode.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.screenshotmode"), skin));

        OwnImageButton frameoutputModeTooltip = new OwnImageButton(skin, "tooltip");
        frameoutputModeTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.screenshotmode"), skin));

        HorizontalGroup foModeGroup = new HorizontalGroup();
        foModeGroup.space(pad5);
        foModeGroup.addActor(frameoutputMode);
        foModeGroup.addActor(frameoutputModeTooltip);

        // Counter
        OwnLabel counterLabel = new OwnLabel(I18n.txt("gui.frameoutput.sequence"), skin);
        HorizontalGroup counterGroup = new HorizontalGroup();
        counterGroup.space(pad5);
        OwnLabel counter = new OwnLabel(ImageRenderer.getSequenceNumber() + "", skin);
        counter.setWidth(textWidth * 3f);
        OwnTextButton resetCounter = new OwnTextButton(I18n.txt("gui.frameoutput.sequence.reset"), skin);
        resetCounter.pad(pad10);
        resetCounter.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                ImageRenderer.resetSequenceNumber();
                counter.setText("0");
            }
            return false;
        });

        counterGroup.addActor(counter);

        // LABELS
        labels.addAll(frameoutputLocationLabel, prefixLabel, fpsLabel, fomodeLabel, frameoutputSizeLabel);

        // Add to table
        frameoutput.add(frameoutputInfo).colspan(2).left().padBottom(pad5).row();
        frameoutput.add(frameoutputLocationLabel).left().padRight(pad20).padBottom(pad5);
        frameoutput.add(frameoutputLocation).left().expandX().padBottom(pad5).row();
        frameoutput.add(prefixLabel).left().padRight(pad20).padBottom(pad5);
        frameoutput.add(frameoutputPrefix).left().padBottom(pad5).row();
        frameoutput.add(fpsLabel).left().padRight(pad20).padBottom(pad5);
        frameoutput.add(frameoutputFps).left().padBottom(pad5).row();
        frameoutput.add(fomodeLabel).left().padRight(pad20).padBottom(pad5);
        frameoutput.add(foModeGroup).left().expandX().padBottom(pad5).row();
        frameoutput.add(frameoutputSizeLabel).left().padRight(pad20).padBottom(pad5);
        frameoutput.add(foSizeGroup).left().expandX().padBottom(pad5).row();
        frameoutput.add(counterLabel).left().padRight(pad20).padBottom(pad5);
        frameoutput.add(counterGroup).left().expandX().padBottom(pad5).row();
        frameoutput.add().padRight(pad20);
        frameoutput.add(resetCounter).left();

        // Add to content
        contentFrames.add(titleFrameoutput).left().padBottom(pad5 * 2).row();
        contentFrames.add(frameoutput).left();

        /*
         * ==== CAMERA ====
         */
        final Table contentCamera = new Table(skin);
        contentCamera.setWidth(contentWidth);
        contentCamera.align(Align.top | Align.left);
        contents.add(contentCamera);

        // CAMERA RECORDING
        Table camrec = new Table(skin);

        OwnLabel titleCamrec = new OwnLabel(I18n.txt("gui.camerarec.title"), skin, "header");

        // fps
        OwnLabel camfpsLabel = new OwnLabel(I18n.txt("gui.target.fps"), skin);
        camrecFps = new OwnTextField(nf3.format(settings.camrecorder.targetFps), skin, new DoubleValidator(Constants.MIN_FPS, Constants.MAX_FPS));
        camrecFps.setWidth(textWidth * 3f);
        OwnImageButton camrecFpsTooltip = new OwnImageButton(skin, "tooltip");
        camrecFpsTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.playcamera.targetfps"), skin));

        // Keyframe preferences
        Button keyframePrefs = new OwnTextIconButton(I18n.txt("gui.keyframes.preferences"), skin, "preferences");
        keyframePrefs.setName("keyframe preferences");
        keyframePrefs.pad(pad10);
        keyframePrefs.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.kf.editprefs"), skin));
        keyframePrefs.addListener((event) -> {
            if (event instanceof ChangeListener.ChangeEvent) {
                KeyframePreferencesWindow kpw = new KeyframePreferencesWindow(stage, skin);
                kpw.setAcceptRunnable(() -> {
                    if (kpw.camrecFps != null && kpw.camrecFps.isValid()) {
                        camrecFps.setText(kpw.camrecFps.getText());
                    }
                });
                kpw.show(stage);
                return true;
            }
            return false;
        });

        // Activate automatically
        cbAutoCamrec = new OwnCheckBox(I18n.txt("gui.camerarec.frameoutput"), skin, "default", pad5);
        cbAutoCamrec.setChecked(settings.camrecorder.auto);
        cbAutoCamrec.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.playcamera.frameoutput"), skin));
        OwnImageButton camrecAutoTooltip = new OwnImageButton(skin, "tooltip");
        camrecAutoTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.playcamera.frameoutput"), skin));

        HorizontalGroup cbGroup = new HorizontalGroup();
        cbGroup.space(pad5);
        cbGroup.addActor(cbAutoCamrec);
        cbGroup.addActor(camrecAutoTooltip);

        // LABELS
        labels.add(camfpsLabel);

        // Add to table
        camrec.add(camfpsLabel).left().padRight(pad5 * 4).padBottom(pad5);
        camrec.add(camrecFps).left().expandX().padBottom(pad5);
        camrec.add(camrecFpsTooltip).left().padLeft(pad5).padBottom(pad5).row();
        camrec.add(cbGroup).colspan(3).left().padBottom(pad5 * 2).row();
        camrec.add(keyframePrefs).colspan(3).left().row();

        // Add to content
        contentCamera.add(titleCamrec).left().padBottom(pad5 * 2).row();
        contentCamera.add(camrec).left();

        /*
         * ==== PANORAMA ====
         */
        final Table content360 = new Table(skin);
        content360.setWidth(contentWidth);
        content360.align(Align.top | Align.left);
        contents.add(content360);

        // CUBEMAP
        OwnLabel titleCubemap = new OwnLabel(I18n.txt("gui.360"), skin, "header");
        Table cubemap = new Table(skin);

        // Info
        String cminfostr = I18n.txt("gui.360.info") + '\n';
        ssLines = GlobalResources.countOccurrences(cminfostr, '\n');
        TextArea cmInfo = new OwnTextArea(cminfostr, skin, "info");
        cmInfo.setDisabled(true);
        cmInfo.setPrefRows(ssLines + 1);
        cmInfo.setWidth(taWidth);
        cmInfo.clearListeners();

        // Resolution
        OwnLabel cmResolutionLabel = new OwnLabel(I18n.txt("gui.360.resolution"), skin);
        cmResolution = new OwnTextField(Integer.toString(settings.program.modeCubemap.faceResolution), skin, new IntValidator(20, 15000));
        cmResolution.setWidth(textWidth * 3f);
        cmResolution.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                if (cmResolution.isValid()) {
                    plResolution.setText(cmResolution.getText());
                }
                return true;
            }
            return false;
        });

        // LABELS
        labels.add(cmResolutionLabel);

        // Add to table
        cubemap.add(cmInfo).colspan(2).left().padBottom(pad5).row();
        cubemap.add(cmResolutionLabel).left().padRight(pad5 * 4).padBottom(pad5);
        cubemap.add(cmResolution).left().expandX().padBottom(pad5).row();

        // Add to content
        content360.add(titleCubemap).left().padBottom(pad5 * 2).row();
        content360.add(cubemap).left();

        /*
         * ==== PLANETARIUM ====
         */
        final Table contentPlanetarium = new Table(skin);
        contentPlanetarium.setWidth(contentWidth);
        contentPlanetarium.align(Align.top | Align.left);
        contents.add(contentPlanetarium);

        // CUBEMAP
        OwnLabel titlePlanetarium = new OwnLabel(I18n.txt("gui.planetarium"), skin, "header");
        Table planetarium = new Table(skin);

        // Aperture
        Label apertureLabel = new OwnLabel(I18n.txt("gui.planetarium.aperture"), skin);
        plAperture = new OwnTextField(Float.toString(settings.program.modeCubemap.planetarium.aperture), skin, new FloatValidator(30, 360));
        plAperture.setWidth(textWidth * 3f);

        // Skew angle
        Label plAngleLabel = new OwnLabel(I18n.txt("gui.planetarium.angle"), skin);
        plAngle = new OwnTextField(Float.toString(settings.program.modeCubemap.planetarium.angle), skin, new FloatValidator(-180, 180));
        plAngle.setWidth(textWidth * 3f);

        // Info
        String plInfoStr = I18n.txt("gui.planetarium.info") + '\n';
        ssLines = GlobalResources.countOccurrences(plInfoStr, '\n');
        TextArea plInfo = new OwnTextArea(plInfoStr, skin, "info");
        plInfo.setDisabled(true);
        plInfo.setPrefRows(ssLines + 1);
        plInfo.setWidth(taWidth);
        plInfo.clearListeners();

        // Resolution
        OwnLabel plResolutionLabel = new OwnLabel(I18n.txt("gui.360.resolution"), skin);
        plResolution = new OwnTextField(Integer.toString(settings.program.modeCubemap.faceResolution), skin, new IntValidator(20, 15000));
        plResolution.setWidth(textWidth * 3f);
        plResolution.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                if (plResolution.isValid()) {
                    cmResolution.setText(plResolution.getText());
                }
                return true;
            }
            return false;
        });

        // LABELS
        labels.add(plResolutionLabel);

        // Add to table
        planetarium.add(apertureLabel).left().padRight(pad20).padBottom(pad10 * 3f);
        planetarium.add(plAperture).left().expandX().padBottom(pad10 * 3f).row();
        planetarium.add(plAngleLabel).left().padRight(pad20).padBottom(pad10 * 3f);
        planetarium.add(plAngle).left().expandX().padBottom(pad10 * 3f).row();
        planetarium.add(plInfo).colspan(2).left().padBottom(pad5).row();
        planetarium.add(plResolutionLabel).left().padRight(pad5 * 4).padBottom(pad5);
        planetarium.add(plResolution).left().expandX().padBottom(pad5).row();

        // Add to content
        contentPlanetarium.add(titlePlanetarium).left().padBottom(pad5 * 2).row();
        contentPlanetarium.add(planetarium).left();


        /*
         * ==== DATA ====
         */
        final Table contentDataTable = new Table(skin);
        contentDataTable.setWidth(contentWidth);
        contentDataTable.align(Align.top | Align.left);
        final OwnScrollPane contentData = new OwnScrollPane(contentDataTable, skin, "minimalist-nobg");
        contentData.setWidth(contentWidth);
        contentData.setHeight(scrollHeight);
        contentData.setScrollingDisabled(true, false);
        contentData.setFadeScrollBars(false);
        contents.add(contentData);

        // GENERAL OPTIONS
        OwnLabel titleGeneralData = new OwnLabel(I18n.txt("gui.data.options"), skin, "header");
        highAccuracyPositions = new OwnCheckBox(I18n.txt("gui.data.highaccuracy"), skin, pad5);
        highAccuracyPositions.setChecked(settings.data.highAccuracy);
        highAccuracyPositions.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.data.highaccuracy"), skin));
        OwnImageButton highAccTooltip = new OwnImageButton(skin, "tooltip");
        highAccTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.data.highaccuracy"), skin));

        HorizontalGroup haGroup = new HorizontalGroup();
        haGroup.space(pad5);
        haGroup.addActor(highAccuracyPositions);
        haGroup.addActor(highAccTooltip);

        // DATA SOURCE
        final OwnLabel titleData = new OwnLabel(I18n.txt("gui.data.source"), skin, "header");

        // Info
        String dsInfoStr = I18n.txt("gui.data.source.info") + '\n';
        int dsLines = GlobalResources.countOccurrences(dsInfoStr, '\n');
        final TextArea dataSourceInfo = new OwnTextArea(dsInfoStr, skin, "info");
        dataSourceInfo.setDisabled(true);
        dataSourceInfo.setPrefRows(dsLines + 1);
        dataSourceInfo.setWidth(taWidth);
        dataSourceInfo.clearListeners();

        dw = new DatasetsWidget(stage, skin);
        dw.reloadLocalCatalogs();
        final Actor dataSource = dw.buildDatasetsWidget(false, 20, 220f);

        final OwnTextButton dataDownload = new OwnTextButton(I18n.txt("gui.download.title"), skin);
        dataDownload.pad(pad20, pad20 * 2f, pad20, pad20 * 2f);
        dataDownload.setHeight(buttonHeight);
        dataDownload.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                if (DataDescriptor.currentDataDescriptor != null) {
                    DownloadDataWindow ddw = new DownloadDataWindow(stage, skin, DataDescriptor.currentDataDescriptor, false, I18n.txt("gui.close"));
                    ddw.setModal(true);
                    ddw.show(stage);
                } else {
                    // Try again
                    FileHandle dataDescriptor = Gdx.files.absolute(SysUtils.getTempDir(settings.data.location) + "/gaiasky-data.json");
                    DownloadHelper.downloadFile(settings.program.url.dataDescriptor, dataDescriptor, null, null, (digest) -> {
                        DataDescriptor dd = DataDescriptorUtils.instance().buildDatasetsDescriptor(dataDescriptor);
                        DownloadDataWindow ddw = new DownloadDataWindow(stage, skin, dd, false, I18n.txt("gui.close"));
                        ddw.setModal(true);
                        ddw.show(stage);
                    }, () -> {
                        // Fail?
                        logger.error("No internet connection or server is down!");
                        GuiUtils.addNoConnectionWindow(skin, stage);
                    }, null);
                }
                return true;
            }
            return false;
        });

        // Add to content
        contentDataTable.add(titleGeneralData).left().padBottom(pad5 * 2).row();
        contentDataTable.add(haGroup).left().padBottom(pad5 * 4).row();
        contentDataTable.add(titleData).left().padBottom(pad5 * 2).row();
        contentDataTable.add(dataSourceInfo).left().padBottom(pad5).row();
        contentDataTable.add(dataSource).left().padLeft(pad5).padBottom(pad5 * 4).row();
        contentDataTable.add(dataDownload).left();

        /*
         * ==== GAIA ====
         */
        final Table contentGaia = new Table(skin);
        contentGaia.setWidth(contentWidth);
        contentGaia.align(Align.top | Align.left);
        contents.add(contentGaia);

        // ATTITUDE
        OwnLabel titleAttitude = new OwnLabel(I18n.txt("gui.gaia.attitude"), skin, "header");
        Table attitude = new Table(skin);

        real = new OwnCheckBox(I18n.txt("gui.gaia.real"), skin, "radio", pad5);
        real.setChecked(settings.data.realGaiaAttitude);
        nsl = new OwnCheckBox(I18n.txt("gui.gaia.nsl"), skin, "radio", pad5);
        nsl.setChecked(!settings.data.realGaiaAttitude);

        new ButtonGroup<>(real, nsl);

        // Add to table
        attitude.add(nsl).left().padBottom(pad5).row();
        attitude.add(real).left().padBottom(pad5).row();
        final Cell<Actor> noticeAttCell = attitude.add((Actor) null);
        noticeAttCell.colspan(2).left();

        EventListener attNoticeListener = event -> {
            if (event instanceof ChangeEvent) {
                if (noticeAttCell.getActor() == null) {
                    String nextInfoStr = I18n.txt("gui.ui.info") + '\n';
                    int lines1 = GlobalResources.countOccurrences(nextInfoStr, '\n');
                    TextArea nextTimeInfo = new OwnTextArea(nextInfoStr, skin, "info");
                    nextTimeInfo.setDisabled(true);
                    nextTimeInfo.setPrefRows(lines1 + 1);
                    nextTimeInfo.setWidth(taWidth);
                    nextTimeInfo.clearListeners();
                    noticeAttCell.setActor(nextTimeInfo);
                }
                return true;
            }
            return false;
        };
        real.addListener(attNoticeListener);
        nsl.addListener(attNoticeListener);

        // Add to content
        contentGaia.add(titleAttitude).left().padBottom(pad5 * 2).row();
        contentGaia.add(attitude).left();

        /*
         * ==== SYSTEM ====
         */
        final Table contentSystem = new Table(skin);
        contentSystem.setWidth(contentWidth);
        contentSystem.align(Align.top | Align.left);
        contents.add(contentSystem);

        // STATS
        OwnLabel titleStats = new OwnLabel(I18n.txt("gui.system.reporting"), skin, "header");
        Table stats = new Table(skin);

        debugInfoBak = settings.program.debugInfo;
        debugInfo = new OwnCheckBox(I18n.txt("gui.system.debuginfo"), skin, pad5);
        debugInfo.setChecked(settings.program.debugInfo);
        debugInfo.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                EventManager.instance.post(Events.SHOW_DEBUG_CMD, !settings.program.debugInfo);
                return true;
            }
            return false;
        });

        // EXIT CONFIRMATION
        exitConfirmation = new OwnCheckBox(I18n.txt("gui.quit.confirmation"), skin, pad5);
        exitConfirmation.setChecked(settings.program.exitConfirmation);

        // RELOAD DEFAULTS
        OwnTextButton reloadDefaults = new OwnTextButton(I18n.txt("gui.system.reloaddefaults"), skin);
        reloadDefaults.addListener(event -> {
            if (event instanceof ChangeEvent) {
                reloadDefaultPreferences();
                me.hide();
                // Prevent saving current state
                GaiaSky.instance.saveState = false;
                Gdx.app.exit();
                return true;
            }

            return false;
        });
        reloadDefaults.pad(0, pad10, 0, pad10);
        reloadDefaults.setHeight(buttonHeight);

        OwnLabel warningLabel = new OwnLabel(I18n.txt("gui.system.reloaddefaults.warn"), skin, "default-red");

        // Add to table
        stats.add(debugInfo).left().padBottom(pad5).row();
        stats.add(exitConfirmation).left().padBottom(pad5).row();
        stats.add(warningLabel).left().padBottom(pad5).row();
        stats.add(reloadDefaults).left();

        // Add to content
        contentSystem.add(titleStats).left().padBottom(pad5 * 2).row();
        contentSystem.add(stats).left();

        /* COMPUTE LABEL WIDTH */
        float maxLabelWidth = 0;
        for (OwnLabel l : labels) {
            l.pack();
            if (l.getWidth() > maxLabelWidth)
                maxLabelWidth = l.getWidth();
        }
        maxLabelWidth = Math.max(textWidth * 2, maxLabelWidth);
        for (OwnLabel l : labels)
            l.setWidth(maxLabelWidth);

        /* ADD ALL CONTENT */
        tabContent.addActor(contentGraphics);
        tabContent.addActor(contentUI);
        tabContent.addActor(contentPerformance);
        tabContent.addActor(contentControls);
        tabContent.addActor(contentScreenshots);
        tabContent.addActor(contentFrames);
        tabContent.addActor(contentCamera);
        tabContent.addActor(content360);
        tabContent.addActor(contentPlanetarium);
        tabContent.addActor(contentData);
        tabContent.addActor(contentGaia);
        tabContent.addActor(contentSystem);

        /* ADD TO MAIN TABLE */
        content.add(tabContent).left().padLeft(pad15).expand().fill();

        // Listen to changes in the tab button checked states
        // Set visibility of the tab content to match the checked state
        ChangeListener tab_listener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (((Button) actor).isChecked()) {
                    contentGraphics.setVisible(tabGraphics.isChecked());
                    contentUI.setVisible(tabUI.isChecked());
                    contentPerformance.setVisible(tabPerformance.isChecked());
                    contentControls.setVisible(tabControls.isChecked());
                    contentScreenshots.setVisible(tabScreenshots.isChecked());
                    contentFrames.setVisible(tabFrames.isChecked());
                    contentCamera.setVisible(tabCamera.isChecked());
                    content360.setVisible(tab360.isChecked());
                    contentPlanetarium.setVisible(tabPlanetarium.isChecked());
                    contentData.setVisible(tabData.isChecked());
                    contentGaia.setVisible(tabGaia.isChecked());
                    contentSystem.setVisible(tabSystem.isChecked());
                    if (lastTabFlag)
                        lastTab = (OwnTextIconButton) actor;
                }
            }
        };
        tabGraphics.addListener(tab_listener);
        tabUI.addListener(tab_listener);
        tabPerformance.addListener(tab_listener);
        tabControls.addListener(tab_listener);
        tabScreenshots.addListener(tab_listener);
        tabFrames.addListener(tab_listener);
        tabCamera.addListener(tab_listener);
        tab360.addListener(tab_listener);
        tabPlanetarium.addListener(tab_listener);
        tabData.addListener(tab_listener);
        tabGaia.addListener(tab_listener);
        tabSystem.addListener(tab_listener);

        lastTabFlag = false;
        // Let only one tab button be checked at a time
        ButtonGroup<Button> tabs = new ButtonGroup<>();
        tabs.setMinCheckCount(1);
        tabs.setMaxCheckCount(1);
        tabs.add(tabGraphics);
        tabs.add(tabUI);
        tabs.add(tabPerformance);
        tabs.add(tabControls);
        tabs.add(tabScreenshots);
        tabs.add(tabFrames);
        tabs.add(tabCamera);
        tabs.add(tab360);
        tabs.add(tabPlanetarium);
        tabs.add(tabData);
        tabs.add(tabGaia);
        tabs.add(tabSystem);
        lastTabFlag = true;

        if (lastTab != null)
            tabs.setChecked(lastTab.getText().toString());

    }

    protected void reloadControllerMappings(Path selectedFile) {
        Array<FileComboBoxBean> controllerMappingsFiles = new Array<>();
        Path mappingsAssets = Path.of(settings.ASSETS_LOC, SysUtils.getMappingsDirName());
        Path mappingsData = SysUtils.getDefaultMappingsDir();
        Array<Path> mappingFiles = new Array<>();
        GlobalResources.listRecursive(mappingsAssets, mappingFiles, ".inputListener", ".controller");
        GlobalResources.listRecursive(mappingsData, mappingFiles, ".inputListener", ".controller");
        FileComboBoxBean selected = null;
        for (Path path : mappingFiles) {
            FileComboBoxBean fileBean = new MappingFileComboBoxBean(path);
            controllerMappingsFiles.add(fileBean);
            if (selectedFile == null && settings.controls.gamepad.mappingsFile.endsWith(path.getFileName().toString())) {
                selected = fileBean;
            } else if (selectedFile != null && selectedFile.toAbsolutePath().toString().endsWith(path.getFileName().toString())) {
                selected = fileBean;
            }
        }

        controllerMappings.setItems(controllerMappingsFiles);
        controllerMappings.setSelected(selected);
        controllerMappings.pack();
    }

    protected void generateControllersList(Table table) {
        Array<Controller> controllers = Controllers.getControllers();

        Array<OwnLabel> controllerNames = new Array<>();
        for (Controller c : controllers) {
            OwnLabel cl = new OwnLabel(c.getName(), skin, "default-blue");
            cl.setName(c.getName());
            if (settings.controls.gamepad.isControllerBlacklisted(c.getName())) {
                cl.setText(cl.getText() + " [*]");
                cl.setColor(1, 0, 0, 1);
                cl.addListener(new OwnTextTooltip(I18n.txt("gui.tooltip.controller.blacklist"), skin));
            }
            controllerNames.add(cl);
        }
        if (controllerNames.isEmpty()) {
            controllerNames.add(new OwnLabel(I18n.txt("gui.controller.nocontrollers"), skin));

        }

        if (table == null)
            table = new Table(skin);
        table.clear();
        int i = 0;
        for (OwnLabel cn : controllerNames) {
            String controllerName = cn.getName();
            table.add(cn).left().padBottom(i == controllerNames.size - 1 ? 0f : pad5 * 2f).padRight(pad10 * 2f);
            if (controllerName != null && !settings.controls.gamepad.isControllerBlacklisted(controllerName)) {
                OwnTextButton config = new OwnTextButton("Configure", skin);
                config.pad(pad5, pad5 * 2f, pad5, pad5 * 2f);
                config.addListener(event -> {
                    if (event instanceof ChangeEvent) {
                        // Get currently selected mappings
                        ControllerMappings cm = new ControllerMappings(controllerName, Path.of(controllerMappings.getSelected().file));
                        ControllerConfigWindow ccw = new ControllerConfigWindow(controllerName, cm, stage, skin);
                        ccw.setAcceptRunnable(() -> {
                            if (ccw.savedFile != null) {
                                // File was saved, reload, select
                                reloadControllerMappings(ccw.savedFile);
                            }
                        });
                        ccw.show(stage);
                        return true;
                    }
                    return false;
                });
                table.add(config).left().padBottom(i == controllerNames.size - 1 ? 0f : pad5 * 2f).row();
            } else {
                table.add().left().row();
            }
            i++;
        }
        table.pack();

    }

    @Override
    protected void accept() {
        saveCurrentPreferences();
        unsubscribe();
    }

    @Override
    protected void cancel() {
        revertLivePreferences();
        unsubscribe();
    }

    @Override
    public void dispose() {

    }

    private void reloadDefaultPreferences() {
        // User config file
        Path userFolder = SysUtils.getConfigDir();
        Path userFolderConfFile = userFolder.resolve("config.yaml");

        // Internal config
        Path confFolder = Paths.get(settings.ASSETS_LOC, "conf" + File.separator);
        Path internalFolderConfFile = confFolder.resolve("config.yaml");

        // Delete current conf
        if (Files.exists(userFolderConfFile)) {
            try {
                Files.delete(userFolderConfFile);
            } catch (IOException e) {
                logger.error(e);
            }
        }

        // Copy file
        try {
            if (Files.exists(confFolder) && Files.isDirectory(confFolder)) {
                // Running released package
                GlobalResources.copyFile(internalFolderConfFile, userFolderConfFile, true);
                logger.info("Default configuration file applied successfully! Gaia Sky will shut down now");
            } else {
                throw new IOException("File " + confFolder + " does not exist!");
            }

        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e, "Error copying default preferences file to user folder: " + userFolderConfFile);
        }

    }

    private void saveCurrentPreferences() {
        // Add all properties to settings.instance

        final boolean reloadFullScreenMode = fullscreen.isChecked() != settings.graphics.fullScreen.active;
        final boolean reloadScreenMode = reloadFullScreenMode || (settings.graphics.fullScreen.active && (settings.graphics.fullScreen.resolution[0] != fullscreenResolutions.getSelected().width || settings.graphics.fullScreen.resolution[1] != fullscreenResolutions.getSelected().height)) || (!settings.graphics.fullScreen.active && (settings.graphics.resolution[0] != Integer.parseInt(widthField.getText())) || settings.graphics.resolution[1] != Integer.parseInt(heightField.getText()));

        settings.graphics.fullScreen.active = fullscreen.isChecked();

        // Full screen options
        settings.graphics.fullScreen.resolution[0] = fullscreenResolutions.getSelected().width;
        settings.graphics.fullScreen.resolution[1] = fullscreenResolutions.getSelected().height;

        // Windowed options
        settings.graphics.resolution[0] = Integer.parseInt(widthField.getText());
        settings.graphics.resolution[1] = Integer.parseInt(heightField.getText());

        // Graphics
        ComboBoxBean bean = gquality.getSelected();
        if (settings.graphics.quality.ordinal() != bean.value) {
            settings.graphics.quality = GraphicsQuality.values()[bean.value];
            EventManager.instance.post(Events.GRAPHICS_QUALITY_UPDATED, settings.graphics.quality);
        }

        bean = aa.getSelected();
        Antialias newAntiAlias = settings.postprocess.getAntialias(bean.value);
        if (settings.postprocess.antialias != newAntiAlias) {
            settings.postprocess.antialias = settings.postprocess.getAntialias(bean.value);
            EventManager.instance.post(Events.ANTIALIASING_CMD, settings.postprocess.antialias);
        }

        settings.graphics.vsync = vsync.isChecked();
        try {
            // Windows backend crashes for some reason
            Gdx.graphics.setVSync(settings.graphics.vsync);
        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e);
        }

        if (limitfpsCb.isChecked()) {
            EventManager.instance.post(Events.LIMIT_FPS_CMD, Parser.parseDouble(limitFps.getText()));
        } else {
            EventManager.instance.post(Events.LIMIT_FPS_CMD, 0.0);
        }

        // Line renderer
        boolean reloadLineRenderer = settings.scene.renderer.line != LineMode.values()[lineRenderer.getSelected().value];
        bean = lineRenderer.getSelected();
        settings.scene.renderer.line = LineMode.values()[bean.value];

        // Elevation representation
        ElevationType newType = elevationSb.getSelected().type;
        boolean reloadElevation = newType != settings.scene.renderer.elevation.type;
        if (reloadElevation) {
            EventManager.instance.post(Events.ELEVATION_TYPE_CMD, newType);
        }

        // Tess quality
        EventManager.instance.post(Events.TESSELLATION_QUALITY_CMD, tessQuality.getValue());

        // Shadow mapping
        settings.scene.renderer.shadow.active = shadowsCb.isChecked();
        int newShadowResolution = Integer.parseInt(smResolution.getText());
        int newShadowNumber = nshadows.getSelected().value;
        final boolean reloadShadows = shadowsCb.isChecked() && (settings.scene.renderer.shadow.resolution != newShadowResolution || settings.scene.renderer.shadow.number != newShadowNumber);

        // Interface
        LangComboBoxBean languageBean = lang.getSelected();
        StrComboBoxBean newTheme = theme.getSelected();
        // UI scale
        float factor = uiScale.getMappedValue();
        EventManager.instance.post(Events.UI_SCALE_CMD, factor);

        boolean reloadUI = !settings.program.ui.theme.equals(newTheme.value) || !languageBean.locale.toLanguageTag().equals(settings.program.locale) || settings.program.minimap.size != minimapSize.getValue();
        settings.program.locale = languageBean.locale.toLanguageTag();
        I18n.forceInit(new FileHandle(settings.ASSETS_LOC + File.separator + "i18n/gsbundle"));
        settings.program.ui.theme = newTheme.value;
        boolean previousPointerCoords = settings.program.pointer.coordinates;
        settings.program.pointer.coordinates = pointerCoords.isChecked();
        if (previousPointerCoords != settings.program.pointer.coordinates) {
            EventManager.instance.post(Events.DISPLAY_POINTER_COORDS_CMD, settings.program.pointer.coordinates);
        }

        // Cross-hairs
        EventManager.instance.post(Events.CROSSHAIR_FOCUS_CMD, crosshairFocusCb.isChecked());
        EventManager.instance.post(Events.CROSSHAIR_CLOSEST_CMD, crosshairClosestCb.isChecked());
        EventManager.instance.post(Events.CROSSHAIR_HOME_CMD, crosshairHomeCb.isChecked());

        // Pointer guides
        EventManager.instance.post(Events.POINTER_GUIDES_CMD, pointerGuidesCb.isChecked(), pointerGuidesColor.getPickedColor(), pointerGuidesWidth.getMappedValue());

        // Recursive grid
        settings.program.recursiveGrid.origin = values()[recgridOrigin.getSelectedIndex()];
        settings.program.recursiveGrid.projectionLines = recgridProjectionLinesCb.isChecked();

        // Minimap size
        settings.program.minimap.size = minimapSize.getValue();

        // Distance units
        settings.program.ui.distanceUnits = DistanceUnits.values()[distUnitsSelect.getSelectedIndex()];

        // Performance
        bean = numThreads.getSelected();
        settings.performance.numberThreads = bean.value;
        settings.performance.multithreading = multithreadCb.isChecked();

        settings.scene.octree.fade = lodFadeCb.isChecked();
        settings.scene.octree.threshold[0] = MathUtilsd.lint(lodTransitions.getValue(), Constants.MIN_SLIDER, Constants.MAX_SLIDER, Constants.MIN_LOD_TRANS_ANGLE_DEG, Constants.MAX_LOD_TRANS_ANGLE_DEG) * (float) MathUtilsd.degRad;
        // Here we use a 0.4 rad between the thresholds
        settings.scene.octree.threshold[1] = settings.scene.octree.fade ? settings.scene.octree.threshold[0] + 0.4f : settings.scene.octree.threshold[0];

        // Data
        boolean highAccuracy = settings.data.highAccuracy;
        settings.data.highAccuracy = highAccuracyPositions.isChecked();

        if (highAccuracy != settings.data.highAccuracy) {
            // Event
            EventManager.instance.post(Events.HIGH_ACCURACY_CMD, settings.data.highAccuracy);
        }
        settings.data.catalogFiles.clear();
        for (Button b : dw.cbs) {
            if (b.isChecked()) {
                settings.data.catalogFiles.add(dw.candidates.get(b));
            }
        }

        // Screenshots
        File ssfile = new File(screenshotsLocation.getText().toString());
        if (ssfile.exists() && ssfile.isDirectory())
            settings.screenshot.location = ssfile.getAbsolutePath();
        ScreenshotMode prev = settings.screenshot.mode;
        settings.screenshot.mode = ScreenshotMode.values()[screenshotMode.getSelectedIndex()];
        int ssw = Integer.parseInt(sswidthField.getText());
        int ssh = Integer.parseInt(ssheightField.getText());
        boolean ssupdate = ssw != settings.screenshot.resolution[0] || ssh != settings.screenshot.resolution[1] || !prev.equals(settings.screenshot.mode);
        settings.screenshot.resolution[0] = ssw;
        settings.screenshot.resolution[1] = ssh;
        if (ssupdate)
            EventManager.instance.post(Events.SCREENSHOT_SIZE_UPDATE, settings.screenshot.resolution[0], settings.screenshot.resolution[1]);

        // Frame output
        File fofile = new File(frameoutputLocation.getText().toString());
        if (fofile.exists() && fofile.isDirectory())
            settings.frame.location = fofile.getAbsolutePath();
        String text = frameoutputPrefix.getText();
        if (text.matches("^\\w+$")) {
            settings.frame.prefix = text;
        }
        prev = settings.frame.mode;
        settings.frame.mode = ScreenshotMode.values()[frameoutputMode.getSelectedIndex()];
        int fow = Integer.parseInt(fowidthField.getText());
        int foh = Integer.parseInt(foheightField.getText());
        boolean frameOutputUpdate = fow != settings.frame.resolution[0] || foh != settings.frame.resolution[1] || !prev.equals(settings.frame.mode);
        settings.frame.resolution[0] = fow;
        settings.frame.resolution[1] = foh;
        settings.frame.targetFps = Parser.parseDouble(frameoutputFps.getText());
        if (frameOutputUpdate)
            EventManager.instance.post(Events.FRAME_SIZE_UPDATE, settings.frame.resolution[0], settings.frame.resolution[1]);

        // Camera recording
        EventManager.instance.post(Events.CAMRECORDER_FPS_CMD, Parser.parseDouble(camrecFps.getText()));
        settings.camrecorder.auto = cbAutoCamrec.isChecked();

        // Cubemap resolution (same as plResolution)
        int newResolution = Integer.parseInt(cmResolution.getText());
        if (newResolution != settings.program.modeCubemap.faceResolution)
            EventManager.instance.post(Events.CUBEMAP_RESOLUTION_CMD, newResolution);

        // Planetarium aperture
        float ap = Float.parseFloat(plAperture.getText());
        if (ap != settings.program.modeCubemap.planetarium.aperture) {
            EventManager.instance.post(Events.PLANETARIUM_APERTURE_CMD, ap);
        }

        // Planetarium angle
        float pa = Float.parseFloat(plAngle.getText());
        if (pa != settings.program.modeCubemap.planetarium.angle) {
            EventManager.instance.post(Events.PLANETARIUM_ANGLE_CMD, pa);
        }

        // Controllers
        if (controllerMappings.getSelected() != null) {
            String mappingsFile = controllerMappings.getSelected().file;
            if (!mappingsFile.equals(settings.controls.gamepad.mappingsFile)) {
                settings.controls.gamepad.mappingsFile = mappingsFile;
                EventManager.instance.post(Events.RELOAD_CONTROLLER_MAPPINGS, mappingsFile);
            }
        }
        settings.controls.gamepad.invertX = invertx.isChecked();
        settings.controls.gamepad.invertY = inverty.isChecked();

        // Gaia attitude
        settings.data.realGaiaAttitude = real.isChecked();

        // System
        if (settings.program.debugInfo != debugInfoBak) {
            EventManager.instance.post(Events.SHOW_DEBUG_CMD, !debugInfoBak);
        }
        settings.program.exitConfirmation = exitConfirmation.isChecked();

        // Save configuration
        SettingsManager.instance.persistSettings(new File(System.getProperty("properties.file")));

        EventManager.instance.post(Events.PROPERTIES_WRITTEN);

        if (reloadScreenMode) {
            GaiaSky.postRunnable(() -> EventManager.instance.post(Events.SCREEN_MODE_CMD));
        }

        if (reloadLineRenderer) {
            GaiaSky.postRunnable(() -> EventManager.instance.post(Events.LINE_RENDERER_UPDATE));
        }

        if (reloadShadows) {
            GaiaSky.postRunnable(() -> {
                settings.scene.renderer.shadow.resolution = newShadowResolution;
                settings.scene.renderer.shadow.number = newShadowNumber;

                EventManager.instance.post(Events.REBUILD_SHADOW_MAP_DATA_CMD);
            });
        }

        if (reloadUI) {
            reloadUI(globalResources);
        }

    }

    private void unsubscribe() {
        EventManager.instance.removeAllSubscriptions(this);
    }

    /**
     * Reverts preferences which have been modified live. It needs backup values.
     */
    private void revertLivePreferences() {
        EventManager.instance.post(Events.BRIGHTNESS_CMD, brightnessBak, true);
        EventManager.instance.post(Events.CONTRAST_CMD, contrastBak, true);
        EventManager.instance.post(Events.HUE_CMD, hueBak, true);
        EventManager.instance.post(Events.SATURATION_CMD, saturationBak, true);
        EventManager.instance.post(Events.GAMMA_CMD, gammaBak, true);
        EventManager.instance.post(Events.MOTION_BLUR_CMD, motionblurBak, true);
        EventManager.instance.post(Events.LENS_FLARE_CMD, lensflareBak, true);
        EventManager.instance.post(Events.LIGHT_SCATTERING_CMD, lightGlowBak, true);
        EventManager.instance.post(Events.BLOOM_CMD, bloomBak, true);
        EventManager.instance.post(Events.UNSHARP_MASK_CMD, unsharpMaskBak, true);
        EventManager.instance.post(Events.EXPOSURE_CMD, exposureBak, true);
        EventManager.instance.post(Events.TONEMAPPING_TYPE_CMD, toneMappingBak, true);
        EventManager.instance.post(Events.SHOW_DEBUG_CMD, debugInfoBak);
    }

    private void reloadUI(final GlobalResources globalResources) {
        EventManager.instance.post(Events.UI_RELOAD_CMD, globalResources);
    }

    private void selectFullscreen(boolean fullscreen, OwnTextField widthField, OwnTextField heightField, SelectBox<DisplayMode> fullScreenResolutions, OwnLabel widthLabel, OwnLabel heightLabel) {
        if (fullscreen) {
            settings.graphics.resolution[0] = fullScreenResolutions.getSelected().width;
            settings.graphics.resolution[1] = fullScreenResolutions.getSelected().height;
        } else {
            settings.graphics.resolution[0] = Integer.parseInt(widthField.getText());
            settings.graphics.resolution[1] = Integer.parseInt(heightField.getText());
        }

        enableComponents(!fullscreen, widthField, heightField, widthLabel, heightLabel);
        enableComponents(fullscreen, fullScreenResolutions);
    }

    private int idxAa(int base, Antialias x) {
        if (x.getAACode() == -1)
            return 1;
        if (x.getAACode() == -2)
            return 2;
        if (x.getAACode() == 0)
            return 0;
        return (int) (Math.log(x.getAACode()) / Math.log(base) + 1e-10) + 2;
    }

    private int idxLang(String code, LangComboBoxBean[] langs) {
        if (code == null || code.isEmpty()) {
            code = I18n.bundle.getLocale().toLanguageTag();
        }
        for (int i = 0; i < langs.length; i++) {
            if (langs[i].locale.toLanguageTag().equals(code)) {
                return i;
            }
        }
        return -1;
    }

    private String keysToString(TreeSet<Integer> keys) {
        StringBuilder s = new StringBuilder();

        int i = 0;
        int n = keys.size();
        for (Integer key : keys) {
            s.append(keyToString(key).toUpperCase().replace(' ', '_'));
            if (i < n - 1) {
                s.append("+");
            }

            i++;
        }

        return s.toString();
    }

    private String keyToString(int key) {
        if (key == Keys.PLUS) {
            return "+";
        }
        return Keys.toString(key);
    }

    @Override
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case CONTROLLER_CONNECTED_INFO, CONTROLLER_DISCONNECTED_INFO -> generateControllersList(controllersTable);
        default -> {
        }
        }
    }
}
