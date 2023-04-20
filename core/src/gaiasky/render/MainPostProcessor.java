/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.api.IPostProcessor;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.camera.CameraManager;
import gaiasky.scene.record.MaterialComponent;
import gaiasky.scene.record.ModelComponent;
import gaiasky.scene.view.BaseView;
import gaiasky.scene.view.FocusView;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings.*;
import gaiasky.util.Settings.PostprocessSettings.LensFlareSettings;
import gaiasky.util.Settings.PostprocessSettings.LightGlowSettings;
import gaiasky.util.Settings.SceneSettings.StarSettings;
import gaiasky.util.coord.StaticCoordinates;
import gaiasky.util.gdx.contrib.postprocess.PostProcessor;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.effects.*;
import gaiasky.util.gdx.contrib.utils.ShaderLoader;
import gaiasky.util.gdx.loader.PFMData;
import gaiasky.util.gdx.loader.PFMReader;
import gaiasky.util.i18n.I18n;
import gaiasky.util.math.Vector3b;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * Main post-processor of Gaia Sky, aggregating all post-processing effects for all render targets.
 */
public class MainPostProcessor implements IPostProcessor, IObserver {
    private static final Log logger = Logger.getLogger(MainPostProcessor.class);
    /**
     * Contains a map by name with
     * [0:shader{string}, 1:enabled {bool}, 2:entity{Entity}, 3:additional{float4}, 4:texture2{string}, 5:texture3{string}]] for raymarching post-processors
     */
    private final Map<String, Object[]> raymarchingDef;
    /** Aspect ratio cache. **/
    float ar;
    Entity blurObject;
    BaseView blurObjectView;
    FocusView focusView;
    boolean blurObjectAdded = false;
    Vector3b auxb1, auxb2, prevCampos;
    Vector3 auxf;
    Matrix4 prevViewProj;
    Matrix4 projection, combined, view;
    Matrix4 frustumCorners;
    /** The asset manager. **/
    private AssetManager manager;
    /** The actual post processors. **/
    private PostProcessBean[] pps;
    /** Reference to the scene. **/
    private Scene scene;
    private String starTextureName, lensDirtName, lensColorName, lensStarburstName;

    public MainPostProcessor(Scene scene) {
        ShaderLoader.BasePath = "shader/postprocess/";

        this.scene = scene;
        this.auxb1 = new Vector3b();
        this.auxb2 = new Vector3b();
        this.auxf = new Vector3();
        this.prevCampos = new Vector3b();
        this.focusView = new FocusView();
        this.prevViewProj = new Matrix4();
        this.view = new Matrix4();
        this.projection = new Matrix4();
        this.combined = new Matrix4();
        this.frustumCorners = new Matrix4();
        this.raymarchingDef = new HashMap<>();

        EventManager.instance.subscribe(this, Event.RAYMARCHING_CMD);
    }

    private void addRayMarchingDef(String name, Object[] list) {
        if (!raymarchingDef.containsKey(name))
            raymarchingDef.put(name, list);
    }

    public void initialize(AssetManager manager) {
        this.manager = manager;
        logger.info(I18n.msg("notif.postproc.init"));
        LensFlareSettings settings = Settings.settings.postprocess.lensFlare;
        lensDirtName = Settings.settings.data.dataFile(GlobalResources.unpackAssetPath(settings.texLensDirt));
        lensColorName = Settings.settings.data.dataFile(settings.texLensColor);
        lensStarburstName = Settings.settings.data.dataFile(settings.texLensStarburst);

        starTextureName = Settings.settings.scene.star.getStarTexture();
        manager.load(starTextureName, Texture.class);
        manager.load(lensDirtName, Texture.class);
        manager.load(lensColorName, Texture.class);
        manager.load(lensStarburstName, Texture.class);
    }

    public void doneLoading(AssetManager manager) {
        pps = new PostProcessBean[RenderType.values().length];
        EventManager.instance.subscribe(this, Event.SCREENSHOT_SIZE_UPDATE, Event.FRAME_SIZE_UPDATE, Event.BLOOM_CMD, Event.UNSHARP_MASK_CMD, Event.LENS_FLARE_CMD, Event.SSR_CMD, Event.MOTION_BLUR_CMD, Event.LIGHT_POS_2D_UPDATE, Event.LIGHT_GLOW_CMD, Event.REPROJECTION_CMD, Event.CUBEMAP_CMD, Event.ANTIALIASING_CMD, Event.BRIGHTNESS_CMD, Event.CONTRAST_CMD, Event.HUE_CMD, Event.SATURATION_CMD, Event.GAMMA_CMD, Event.TONEMAPPING_TYPE_CMD, Event.EXPOSURE_CMD, Event.STEREO_PROFILE_CMD, Event.STEREOSCOPIC_CMD, Event.FPS_INFO, Event.FOV_CHANGE_NOTIFICATION, Event.STAR_BRIGHTNESS_CMD, Event.STAR_GLOW_FACTOR_CMD, Event.STAR_POINT_SIZE_CMD, Event.CAMERA_MOTION_UPDATE, Event.CAMERA_ORIENTATION_UPDATE, Event.GRAPHICS_QUALITY_UPDATED, Event.BILLBOARD_TEXTURE_IDX_CMD, Event.SCENE_LOADED, Event.INDEXOFREFRACTION_CMD, Event.BACKBUFFER_SCALE_CMD, Event.UPSCALE_FILTER_CMD, Event.CHROMATIC_ABERRATION_CMD);
    }

    public void initializeOffscreenPostProcessors() {
        int[] screenshot, frame;
        screenshot = getSize(RenderType.screenshot);
        frame = getSize(RenderType.frame);
        // Screenshots.
        pps[RenderType.screenshot.index] = newPostProcessor(RenderType.screenshot, screenshot[0], screenshot[1], screenshot[0], screenshot[1], manager);
        // Frame output mode.
        pps[RenderType.frame.index] = newPostProcessor(RenderType.frame, frame[0], frame[1], frame[0], frame[1], manager);
    }

    private int[] getSize(RenderType type) {
        return switch (type) {
            case screen -> new int[] { (int) Math.round(Settings.settings.graphics.resolution[0] * Settings.settings.graphics.backBufferScale), (int) Math.round(Settings.settings.graphics.resolution[1] * Settings.settings.graphics.backBufferScale) };
            case screenshot -> new int[] { Settings.settings.screenshot.resolution[0], Settings.settings.screenshot.resolution[1] };
            case frame -> new int[] { Settings.settings.frame.resolution[0], Settings.settings.frame.resolution[1] };
        };
    }

    private PostProcessBean newPostProcessor(RenderType rt, float width, float height, float targetWidth, float targetHeight, AssetManager manager) {
        logger.info("Initialising " + rt.name() + " post-processor");
        PostProcessBean ppb = new PostProcessBean();

        Settings settings = Settings.settings;
        StarSettings ss = settings.scene.star;
        GraphicsQuality gq = settings.graphics.quality;
        boolean safeMode = settings.program.safeMode;
        boolean vr = settings.runtime.openXr;

        ar = width / height;

        ppb.pp = new PostProcessor(rt, Math.round(width), Math.round(height), true, false, true, !safeMode, !safeMode, !safeMode, safeMode);
        ppb.pp.setViewport(new Rectangle(0, 0, targetWidth, targetHeight));

        // LIGHT GLOW
        LightGlowSettings glowSettings = Settings.settings.postprocess.lightGlow;
        Texture glow = manager.get(starTextureName);
        glow.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        LightGlow lightGlow = new LightGlow(5, 5);
        lightGlow.setLightGlowTexture(glow);
        lightGlow.setTextureScale(getGlowTextureScale(ss.brightness, ss.glowFactor, ss.pointSize, GaiaSky.instance.cameraManager.getFovFactor(), settings.program.modeCubemap.active));
        lightGlow.setSpiralScale(getGlowSpiralScale(ss.brightness, ss.pointSize, GaiaSky.instance.cameraManager.getFovFactor()));
        lightGlow.setBackbufferScale(settings.runtime.openXr ? (float) settings.graphics.backBufferScale : 1);
        lightGlow.setEnabled(!SysUtils.isMac() && glowSettings.active);
        lightGlow.setEnabledOptions(false, true);
        ppb.set(lightGlow);
        updateGlow(ppb, gq);

        // RAY MARCHING SHADERS
        raymarchingDef.forEach((key, list) -> {
            Raymarching rm = new Raymarching((String) list[0], width, height);
            // Fixed uniforms
            float zFar = (float) GaiaSky.instance.getCameraManager().current.getFar();
            float k = Constants.getCameraK();
            var entity = (Entity) list[2];
            var body = Mapper.body.get(entity);
            // We normalize the size.
            rm.setSize(body.size * 0.1f);
            rm.setZfarK(zFar, k);
            if (list.length > 3 && list[3] != null) {
                // Additional
                rm.setAdditional((float[]) list[3]);
            }
            if (list.length > 4) {
                // u_texture2
                try {
                    Texture tex = new Texture((String) list[4]);
                    rm.setAdditionalTexture(tex);
                } catch (Exception e) {
                    logger.error(e);
                }
            }
            rm.setEnabled((boolean) list[1]);
            ppb.set(key, rm);
        });

        // COPY
        //DrawTexture copy = new DrawTexture();
        //ppb.set(copy);

        // DEPTH BUFFER
        //DepthBuffer depthBuffer = new DepthBuffer();
        //ppb.set(depthBuffer);

        // SSR
        SSR ssrEffect = new SSR();
        ssrEffect.setZfarK((float) GaiaSky.instance.getCameraManager().current.getFar(), Constants.getCameraK());
        ssrEffect.setEnabled(settings.postprocess.ssr.active && !vr && !safeMode);
        ppb.set(ssrEffect);

        // CAMERA MOTION BLUR
        CameraMotion cameraBlur = new CameraMotion(width, height);
        cameraBlur.setBlurScale(.8f);
        cameraBlur.setEnabled(settings.postprocess.motionBlur.active && !vr && !safeMode);
        cameraBlur.setEnabledOptions(false, false);
        ppb.set(cameraBlur);
        updateCameraBlur(ppb, gq);

        // Add to scene graph
        initializeBlurObject();
        if (blurObject != null && !blurObjectAdded) {
            GaiaSky.postRunnable(() -> {
                scene.engine.addEntity(blurObject);
                EventManager.publish(Event.SCENE_ADD_OBJECT_NO_POST_CMD, this, blurObject, false);
            });
            blurObjectView = new BaseView(blurObject);
            blurObjectAdded = true;
        }

        /*
         TODO
         This is a pretty brutal patch for macOS. For some obscure reason,
         the sucker will welcome you with a nice cozy blank screen if
         the activation of the light glow effect is
         not delayed. No time or willpower to get to the bottom of this.
         */
        if (SysUtils.isMac() && glowSettings.active) {
            Task enableLG = new Task() {
                @Override
                public void run() {
                    logger.info("Enabling light glow effect...");
                    ppb.get(LightGlow.class).setEnabled(glowSettings.active);
                }
            };
            Timer.schedule(enableLG, 5);
        }

        // LENS FLARE
        LensFlareSettings lensFlareSettings = settings.postprocess.lensFlare;
        if (lensFlareSettings.type.isPseudoLensFlare()) {
            // PSEUDO LENS FLARE
            Texture lensColor = manager.get(lensColorName);
            lensColor.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            Texture lensDirt = manager.get(lensDirtName);
            lensDirt.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            Texture lensStarBurst = manager.get(lensStarburstName);
            lensStarBurst.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            PseudoLensFlare pseudoLensFlare = new PseudoLensFlare((int) (width * lensFlareSettings.fboScale), (int) (height * lensFlareSettings.fboScale));
            pseudoLensFlare.setGhosts(lensFlareSettings.numGhosts);
            pseudoLensFlare.setHaloWidth(lensFlareSettings.haloWidth);
            pseudoLensFlare.setLensColorTexture(lensColor);
            pseudoLensFlare.setLensDirtTexture(lensDirt);
            pseudoLensFlare.setLensStarburstTexture(lensStarBurst);
            pseudoLensFlare.setFlareIntesity(lensFlareSettings.active ? lensFlareSettings.intensity : 0f);
            pseudoLensFlare.setFlareSaturation(lensFlareSettings.flareSaturation);
            pseudoLensFlare.setBaseIntesity(1f);
            pseudoLensFlare.setBias(lensFlareSettings.bias);
            pseudoLensFlare.setBlurPasses(lensFlareSettings.blurPasses);
            pseudoLensFlare.setEnabledOptions(false, true);
            ppb.set(pseudoLensFlare);
        } else {
            // TRUE LENS FLARE
            LensFlare lensFlare = new LensFlare((int) width, (int) height, lensFlareSettings.intensity, lensFlareSettings.type.ordinal());
            lensFlare.setColor(new float[] { 1f, 1f, 1f });
            lensFlare.setEnabled(lensFlareSettings.active);
            lensFlare.setEnabledOptions(false, true);
            ppb.set(lensFlare);
        }

        // UNSHARP MASK
        UnsharpMask unsharp = new UnsharpMask();
        unsharp.setSharpenFactor(settings.postprocess.unsharpMask.factor);
        unsharp.setEnabled(settings.postprocess.unsharpMask.factor > 0);
        ppb.set(unsharp);

        // ANTI-ALIAS
        initAntiAliasing(settings.postprocess.antialias, width, height, ppb);

        // BLOOM
        Bloom bloom = new Bloom((int) (width * settings.postprocess.bloom.fboScale), (int) (height * settings.postprocess.bloom.fboScale));
        bloom.setBloomIntesnity(settings.postprocess.bloom.intensity);
        bloom.setBlurPasses(20);
        bloom.setBlurAmount(10);
        bloom.setThreshold(0f);
        bloom.setEnabled(settings.postprocess.bloom.intensity > 0);
        ppb.set(bloom);

        // DISTORTION (STEREOSCOPIC MODE)
        Curvature curvature = new Curvature();
        curvature.setDistortion(1.2f);
        curvature.setZoom(0.75f);
        curvature.setEnabled(settings.program.modeStereo.active && settings.program.modeStereo.profile == StereoProfile.VR_HEADSET);
        curvature.setEnabledOptions(false, false);
        ppb.set(curvature);

        // RE-PROJECTION
        Reprojection reprojection = new Reprojection(width, height);
        reprojection.setFov(GaiaSky.instance.cameraManager.getCamera().fieldOfView);
        reprojection.setMode(settings.postprocess.reprojection.mode.mode);
        reprojection.setEnabled(settings.postprocess.reprojection.active);
        reprojection.setEnabledOptions(false, false);
        ppb.set(reprojection);

        // CHROMATIC ABERRATION
        ChromaticAberration aberration = new ChromaticAberration(Settings.settings.postprocess.chromaticAberration.amount * GaiaSky.instance.cameraManager.getFovFactor());
        aberration.setEnabledOptions(false, false);
        ppb.set(aberration);

        // LEVELS - BRIGHTNESS, CONTRAST, HUE, SATURATION, GAMMA CORRECTION and HDR TONE MAPPING
        initLevels(ppb);

        // SLAVE DISTORTION
        if (settings.program.net.isSlaveInstance() && SlaveManager.projectionActive() && SlaveManager.instance.isWarpOrBlend()) {
            Path warpFile = SlaveManager.instance.pfm;
            Path blendFile = SlaveManager.instance.blend;

            PFMData data;
            if (warpFile != null) {
                // Load from file
                data = manager.get(warpFile.toString());
            } else {
                // Generate identity
                data = PFMReader.constructPFMData(50, 50, x -> x, y -> y);
            }
            GeometryWarp geometryWarp;
            if (blendFile != null) {
                // Set up blend texture
                Texture blendTex = manager.get(blendFile.toString());
                geometryWarp = new GeometryWarp(data, blendTex);
            } else {
                // No blend
                geometryWarp = new GeometryWarp(data, width, height);
            }
            geometryWarp.setEnabled(true);
            geometryWarp.setEnabledOptions(false, false);
            ppb.set(geometryWarp);

        }

        // GEOMETRY WARP TEST
        // PFMData data = PFMReader.readPFMData(Gdx.files.absolute("/home/tsagrista/Documents/mpcdi/warp-natural.pfm"), false, false);
        // GeometryWarp warp = new GeometryWarp(data, width, height);
        // warp.setEnabled(true);
        // warp.setEnabledOptions(false, false);
        // ppb.set(warp);

        // UPSCALE (only screen, last effect in chain!)
        if (rt == RenderType.screen) {
            XBRZ upscaleFilter = new XBRZ();
            ppb.set(upscaleFilter);
            updateUpscaleFilter(settings.postprocess.upscaleFilter, settings.graphics.backBufferScale, upscaleFilter, ppb);
        }

        return ppb;
    }

    private void initializeBlurObject() {
        if (blurObject == null) {
            var at = scene.archetypes().get("gaiasky.scenegraph.BackgroundModel");
            var entity = at.createEntity();

            var base = Mapper.base.get(entity);
            base.setName("BlurBackgroundSkybox");
            base.setCt("");

            var body = Mapper.body.get(entity);
            body.setColor(new float[] { 0, 0, 0, 0 });
            body.setSize(1.0);

            var label = Mapper.label.get(entity);
            label.label = false;

            var graph = Mapper.graph.get(entity);
            graph.setParent(Scene.ROOT_NAME);

            var coordinates = Mapper.coordinates.get(entity);
            StaticCoordinates sc = new StaticCoordinates();
            sc.setPosition(new double[] { 0, 0, 0 });
            coordinates.coordinates = sc;

            var model = Mapper.model.get(entity);
            ModelComponent mc = new ModelComponent(true);
            mc.setType("sphere");
            Map<String, Object> params = new HashMap<>();
            params.put("quality", 50L);
            params.put("diameter", 1.0d);
            params.put("flip", true);
            mc.setParams(params);
            MaterialComponent mtc = new MaterialComponent();
            mc.setMaterial(mtc);
            model.model = mc;

            scene.initializeEntity(entity);
            scene.setUpEntity(entity);

            blurObject = entity;
        }
    }

    /**
     * Updates the post-processing effects' attributes using the new graphics quality
     *
     * @param ppb The post process bean
     * @param gq  The graphics quality
     */
    private void updateGraphicsQuality(PostProcessBean ppb, GraphicsQuality gq) {
        updateGlow(ppb, gq);
        updateCameraBlur(ppb, gq);
        updateFxaa(ppb, gq);
    }

    private void updateGlow(PostProcessBean ppb, GraphicsQuality gq) {
        int samples, lgw, lgh;
        if (gq.isUltra()) {
            samples = 15;
            lgw = 1920;
        } else if (gq.isHigh()) {
            samples = 12;
            lgw = 1500;
        } else if (gq.isNormal()) {
            samples = 10;
            lgw = 1280;
        } else {
            samples = 4;
            lgw = 1000;
        }
        lgh = Math.round(lgw / ar);
        LightGlow lightglow = (LightGlow) ppb.get(LightGlow.class);
        if (lightglow != null) {
            lightglow.setNSamples(samples);
            lightglow.setViewportSize(lgw, lgh);
        }
        Settings.settings.postprocess.lightGlow.samples = samples;
    }

    private void updateCameraBlur(PostProcessBean ppb, GraphicsQuality gq) {
        CameraMotion cameraMotionBlur = (CameraMotion) ppb.get(CameraMotion.class);
        if (gq.isUltra()) {
            cameraMotionBlur.setBlurMaxSamples(60);
        } else if (gq.isHigh()) {
            cameraMotionBlur.setBlurMaxSamples(50);
        } else if (gq.isNormal()) {
            cameraMotionBlur.setBlurMaxSamples(35);
        } else {
            cameraMotionBlur.setBlurMaxSamples(20);
        }
    }

    private void updateFxaa(PostProcessBean ppb, GraphicsQuality gq) {
        Fxaa fxaa = (Fxaa) ppb.get(Fxaa.class);
        if (fxaa != null)
            fxaa.updateQuality(getFxaaQuality(gq));
    }

    private void initLevels(PostProcessBean ppb) {
        Levels levels = new Levels();
        levels.setBrightness(Settings.settings.postprocess.levels.brightness);
        levels.setContrast(Settings.settings.postprocess.levels.contrast);
        levels.setHue(Settings.settings.postprocess.levels.hue);
        levels.setSaturation(Settings.settings.postprocess.levels.saturation);
        levels.setGamma(Settings.settings.postprocess.levels.gamma);

        switch (Settings.settings.postprocess.toneMapping.type) {
        case AUTO -> levels.enableToneMappingAuto();
        case EXPOSURE -> {
            levels.enableToneMappingExposure();
            levels.setExposure(Settings.settings.postprocess.toneMapping.exposure);
        }
        case ACES -> levels.enableToneMappingACES();
        case UNCHARTED -> levels.enableToneMappingUncharted();
        case FILMIC -> levels.enableToneMappingFilmic();
        case NONE -> levels.disableToneMapping();
        }

        ppb.set(levels);
    }

    private int getFxaaQuality(GraphicsQuality gq) {
        return switch (gq) {
            case LOW -> 0;
            case NORMAL -> 1;
            default -> 2;
        };
    }

    private void initAntiAliasing(Antialias aavalue, float width, float height, PostProcessBean ppb) {
        Antialiasing antialiasing = null;
        if (aavalue.equals(Antialias.FXAA)) {
            antialiasing = new Fxaa(width, height, getFxaaQuality(Settings.settings.graphics.quality));
            Logger.getLogger(this.getClass()).debug(I18n.msg("notif.selected", "FXAA"));
        } else if (aavalue.equals(Antialias.NFAA)) {
            antialiasing = new Nfaa(width, height);
            Logger.getLogger(this.getClass()).debug(I18n.msg("notif.selected", "NFAA"));
        }
        if (antialiasing != null) {
            antialiasing.setEnabled(Settings.settings.postprocess.antialias.isPostProcessAntialias());
            ppb.set(antialiasing);
        }
    }

    @Override
    public PostProcessBean getPostProcessBean(RenderType type) {
        return pps[type.index];
    }

    @Override
    public void resize(final int width, final int height, final int targetWidth, final int targetHeight) {
        GaiaSky.postRunnable(() -> resizeImmediate(width, height, targetWidth, targetHeight));
    }

    @Override
    public void resizeImmediate(final int width, final int height, int targetWidth, int targetHeight) {
        replace(RenderType.screen, width, height, targetWidth, targetHeight);
    }

    @Override
    public void dispose() {
        if (pps != null)
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    ppb.dispose();
                }
            }
    }

    private float getGlowTextureScale(double starBrightness, double glowFactor, float pointSize, float fovFactor, boolean cubemap) {
        glowFactor = glowFactor / 0.06f;
        if (cubemap) {
            float ts = (float) starBrightness * (float) glowFactor * pointSize * 7e-2f / fovFactor;
            return Math.min(ts * 0.2f, 5e-1f);
        } else {
            return (float) starBrightness * (float) glowFactor * 0.2f;
        }
    }

    private float getGlowSpiralScale(double starBrightness, float starSize, float fovFactor) {
        return (float) starBrightness * starSize * 0.5e-4f / fovFactor;
    }

    @Override
    public void notify(Event event, Object source, final Object... data) {
        switch (event) {
        case SCENE_LOADED -> {
            this.scene = (Scene) data[0];
            initializeOffscreenPostProcessors();
        }
        case RAYMARCHING_CMD -> {
            var name = (String) data[0];
            var status = (Boolean) data[1];
            var entity = (Entity) data[2];
            if (data.length > 3) {
                // Add effect description for later initialization.
                String shader = (String) data[3];
                float[] additional = data[4] != null ? (float[]) data[4] : null;
                Object[] l = new Object[] { shader, false, entity, additional };
                addRayMarchingDef(name, l);
                logger.info("Ray marching effect definition added: [" + name + " | " + shader + " | " + entity + "]");
            } else {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        PostProcessorEffect effect = ppb.get(name, Raymarching.class);
                        if (effect != null) {
                            effect.setEnabled(status);
                            logger.info("Ray marching effect " + (status ? "enabled" : "disabled") + ": " + name);
                        }
                    }
                }
            }
        }
        case RAYMARCHING_ADDITIONAL_CMD -> {
            var name = (String) data[0];
            var additional = (float[]) data[1];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    // Update ray marching additional data
                    Map<String, PostProcessorEffect> rms = ppb.getAll(Raymarching.class);
                    if (rms != null) {
                        PostProcessorEffect ppe = rms.get(name);
                        if (ppe != null)
                            ((Raymarching) ppe).setAdditional(additional);
                    }
                }
            }
        }
        case STAR_BRIGHTNESS_CMD -> {
            var brightness = (Float) data[0];
            GaiaSky.postRunnable(() -> {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        LightGlow lightglow = (LightGlow) ppb.get(LightGlow.class);
                        if (lightglow != null) {
                            lightglow.setTextureScale(getGlowTextureScale(brightness, Settings.settings.scene.star.glowFactor, Settings.settings.scene.star.pointSize, GaiaSky.instance.cameraManager.getFovFactor(), Settings.settings.program.modeCubemap.active));
                            lightglow.setSpiralScale(getGlowSpiralScale(brightness, Settings.settings.scene.star.pointSize, GaiaSky.instance.cameraManager.getFovFactor()));
                        }
                    }
                }
            });
        }
        case STAR_GLOW_FACTOR_CMD -> {
            var glowFactor = (Float) data[0];
            GaiaSky.postRunnable(() -> {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        LightGlow lightglow = (LightGlow) ppb.get(LightGlow.class);
                        if (lightglow != null) {
                            lightglow.setTextureScale(getGlowTextureScale(Settings.settings.scene.star.brightness, glowFactor, Settings.settings.scene.star.pointSize, GaiaSky.instance.cameraManager.getFovFactor(), Settings.settings.program.modeCubemap.active));
                        }
                    }
                }
            });
        }
        case STAR_POINT_SIZE_CMD -> {
            var size = (Float) data[0];
            GaiaSky.postRunnable(() -> {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        LightGlow lightglow = (LightGlow) ppb.get(LightGlow.class);
                        if (lightglow != null) {
                            lightglow.setTextureScale(getGlowTextureScale(Settings.settings.scene.star.brightness, Settings.settings.scene.star.glowFactor, size, GaiaSky.instance.cameraManager.getFovFactor(), Settings.settings.program.modeCubemap.active));
                            lightglow.setSpiralScale(getGlowSpiralScale(Settings.settings.scene.star.brightness, size, GaiaSky.instance.cameraManager.getFovFactor()));
                        }
                    }
                }
            });
        }
        case LIGHT_POS_2D_UPDATE -> {
            var nLights = (Integer) data[0];
            var lightPos = (float[]) data[1];
            var angles = (float[]) data[2];
            var colors = (float[]) data[3];
            var prePass = (Texture) data[4];
            var lensFlareSettings = Settings.settings.postprocess.lensFlare;
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    LightGlow lightGlow = (LightGlow) ppb.get(LightGlow.class);
                    if (lightGlow != null && lightGlow.isEnabled()) {
                        lightGlow.setLightPositions(nLights, lightPos);
                        lightGlow.setLightViewAngles(angles);
                        lightGlow.setLightColors(colors);
                        if (prePass != null)
                            lightGlow.setPrePassTexture(prePass);
                    }
                    if (!lensFlareSettings.type.isPseudoLensFlare()) {
                        LensFlare lensFlare = (LensFlare) ppb.get(LensFlare.class);
                        if (lensFlare != null && lensFlare.isEnabled()) {
                            lensFlare.setLightPositions(nLights, lightPos);
                            if (nLights <= 0) {
                                lensFlare.setIntensity(0);
                            } else {
                                lensFlare.setIntensity(lensFlareSettings.strength);
                            }
                        }
                    }
                }
            }
        }
        case LIGHT_GLOW_CMD -> {
            var lightGlowActive = (Boolean) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    LightGlow lightglow = (LightGlow) ppb.get(LightGlow.class);
                    if (lightglow != null) {
                        lightglow.setEnabled(lightGlowActive);
                    }
                }
            }
        }
        case FOV_CHANGE_NOTIFICATION -> {
            var newFov = (Float) data[0];
            GaiaSky.postRunnable(() -> {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        // Light glow.
                        LightGlow lightGlow = (LightGlow) ppb.get(LightGlow.class);
                        if (lightGlow != null) {
                            lightGlow.setTextureScale(getGlowTextureScale(Settings.settings.scene.star.brightness, Settings.settings.scene.star.glowFactor, Settings.settings.scene.star.pointSize, GaiaSky.instance.cameraManager.getFovFactor(), Settings.settings.program.modeCubemap.active));
                            lightGlow.setSpiralScale(getGlowSpiralScale(Settings.settings.scene.star.brightness, Settings.settings.scene.star.pointSize, GaiaSky.instance.cameraManager.getFovFactor()));
                        }
                        // Reprojection.
                        Reprojection reprojection = (Reprojection) ppb.get(Reprojection.class);
                        if (reprojection != null)
                            reprojection.setFov(newFov);
                        // Aberration.
                        ChromaticAberration aberration = (ChromaticAberration) ppb.get(ChromaticAberration.class);
                        if (aberration != null && aberration.getAberrationAmount() > 0) {
                            float amount = Settings.settings.postprocess.chromaticAberration.amount * (newFov / 40f);
                            aberration.setAberrationAmount(amount);
                            aberration.setEnabled(amount > 0);
                        }
                    }
                }
            });
        }
        case SCREENSHOT_SIZE_UPDATE -> {
            if (pps != null && Settings.settings.screenshot.isAdvancedMode()) {
                var newWidth = (Integer) data[0];
                var newHeight = (Integer) data[1];
                if (pps[RenderType.screenshot.index] != null) {
                    if (changed(pps[RenderType.screenshot.index].pp, newWidth, newHeight)) {
                        GaiaSky.postRunnable(() -> replace(RenderType.screenshot, newWidth, newHeight, newWidth, newHeight));
                    }
                } else {
                    pps[RenderType.screenshot.index] = newPostProcessor(RenderType.screenshot, newWidth, newHeight, newWidth, newHeight, manager);
                }
            }
        }
        case FRAME_SIZE_UPDATE -> {
            if (pps != null && Settings.settings.frame.isAdvancedMode()) {
                var newWidth = (Integer) data[0];
                var newHeight = (Integer) data[1];
                if (pps[RenderType.frame.index] != null) {
                    if (changed(pps[RenderType.frame.index].pp, newWidth, newHeight)) {
                        GaiaSky.postRunnable(() -> {
                            replace(RenderType.frame, newWidth, newHeight, newWidth, newHeight);
                        });
                    }
                } else {
                    GaiaSky.postRunnable(() -> {
                        replace(RenderType.frame, newWidth, newHeight, newWidth, newHeight);
                    });
                }
            }
        }
        case BLOOM_CMD -> GaiaSky.postRunnable(() -> {
            var intensity = (float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Bloom bloom = (Bloom) ppb.get(Bloom.class);
                    bloom.setBloomIntesnity(intensity);
                    bloom.setEnabled(intensity > 0);
                }
            }
        });
        case UNSHARP_MASK_CMD -> GaiaSky.postRunnable(() -> {
            var sharpenFactor = (float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    UnsharpMask unsharp = (UnsharpMask) ppb.get(UnsharpMask.class);
                    unsharp.setSharpenFactor(sharpenFactor);
                    unsharp.setEnabled(sharpenFactor > 0);
                }
            }
        });
        case CHROMATIC_ABERRATION_CMD -> GaiaSky.postRunnable(() -> {
            var amount = (float) data[0] * GaiaSky.instance.getCameraManager().getFovFactor();
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    ChromaticAberration aberration = (ChromaticAberration) ppb.get(ChromaticAberration.class);
                    aberration.setAberrationAmount(amount);
                    aberration.setEnabled(amount > 0);
                }
            }
        });
        case LENS_FLARE_CMD -> {
            var lensFlareActive = ((Float) data[0]) != 0;
            var lensFlareSettings = Settings.settings.postprocess.lensFlare;
            if (lensFlareSettings.type.isPseudoLensFlare()) {
                // Pseudo lens flare.
                var numGhosts = lensFlareActive ? lensFlareSettings.numGhosts : 0;
                var intensity = lensFlareActive ? lensFlareSettings.intensity : 0;
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        PseudoLensFlare lensFlare = (PseudoLensFlare) ppb.get(PseudoLensFlare.class);
                        lensFlare.setGhosts(numGhosts);
                        lensFlare.setFlareIntesity(intensity);
                    }
                }
            } else {
                // Real lens flare.
                for (int i = 0; i < RenderType.values().length; i++) {
                    PostProcessBean ppb = pps[i];
                    LensFlare lensFlare = (LensFlare) ppb.get(LensFlare.class);
                    lensFlare.setEnabled(lensFlareActive);
                }

            }
        }
        case CAMERA_MOTION_UPDATE -> {
            var camera = (PerspectiveCamera) data[3];
            var campos = (Vector3b) data[0];
            var zdt = GaiaSky.instance.time.getTime().atZone(ZoneId.systemDefault());
            float secs = (float) ((float) zdt.getSecond() + (double) zdt.getNano() * 1e-9d);
            float cameraOffset = (camera.direction.x + camera.direction.y + camera.direction.z);
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    PseudoLensFlare flare = (PseudoLensFlare) ppb.get(PseudoLensFlare.class);
                    if (flare != null)
                        flare.setStarburstOffset(cameraOffset);
                    LightGlow glow = (LightGlow) ppb.get(LightGlow.class);
                    if (glow != null)
                        glow.setOrientation(cameraOffset * 50f);

                    // Update ray marching shaders
                    Map<String, PostProcessorEffect> rms = ppb.getAll(Raymarching.class);
                    if (rms != null)
                        rms.forEach((key, rmEffect) -> {
                            if (rmEffect.isEnabled()) {
                                var rmEntity = (Entity) raymarchingDef.get(key)[2];
                                focusView.setScene(scene);
                                focusView.setEntity(rmEntity);
                                focusView.getPredictedPosition(auxb2, GaiaSky.instance.time, GaiaSky.instance.getICamera(), true);
                                var camPos = auxb1.set(campos).sub(auxb2).put(auxf);
                                Raymarching raymarching = (Raymarching) rmEffect;
                                raymarching.setTime(secs);
                                raymarching.setPos(camPos);
                            }
                        });
                }
            }
            // Update previous projectionView matrix
            prevViewProj = camera.combined;
        }
        case CAMERA_ORIENTATION_UPDATE -> {
            var camera = (PerspectiveCamera) data[0];
            var w = (Integer) data[1];
            var h = (Integer) data[2];
            CameraManager.getFrustumCornersEye(camera, frustumCorners);
            view.set(camera.view);
            projection.set(camera.projection);
            combined.set(camera.combined);
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    // Update all raymarching and SSR shaders
                    Map<String, PostProcessorEffect> rms = ppb.getAll(Raymarching.class);
                    if (rms != null)
                        rms.forEach((key, rmEffect) -> {
                            if (rmEffect.isEnabled()) {
                                Raymarching raymarching = (Raymarching) rmEffect;
                                raymarching.setFrustumCorners(frustumCorners);
                                raymarching.setView(view);
                                raymarching.setCombined(combined);
                                raymarching.setViewportSize(w, h);
                            }
                        });
                    Map<String, PostProcessorEffect> ssrs = ppb.getAll(SSR.class);
                    if (ssrs != null)
                        ssrs.forEach((key, ssrEffect) -> {
                            if (ssrEffect.isEnabled()) {
                                SSR ssr = (SSR) ssrEffect;
                                ssr.setFrustumCorners(frustumCorners);
                                ssr.setView(view);
                                ssr.setProjection(projection);
                                ssr.setCombined(combined);
                            }
                        });
                }
            }
        }
        case REPROJECTION_CMD -> {
            var active = (Boolean) data[0];
            var mode = (ReprojectionMode) data[1];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Reprojection reprojection = (Reprojection) ppb.get(Reprojection.class);
                    if (reprojection != null) {
                        reprojection.setEnabled(active);
                        reprojection.setMode(mode.mode);
                    }
                    LightGlow glow = (LightGlow) ppb.get(LightGlow.class);
                    if (glow != null) {
                        glow.setNSamples(active ? 1 : Settings.settings.postprocess.lightGlow.samples);
                    }
                }
            }
        }
        case SSR_CMD -> {
            var enabled = (boolean) data[0] && !Settings.settings.program.safeMode && !Settings.settings.runtime.openXr;
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    SSR ssr = (SSR) ppb.get(SSR.class);
                    if (ssr != null)
                        ssr.setEnabled(enabled);
                }
            }
        }
        case MOTION_BLUR_CMD -> {
            var enabled = (boolean) data[0] && !Settings.settings.program.safeMode && !Settings.settings.runtime.openXr;
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    CameraMotion cameraMotion = (CameraMotion) ppb.get(CameraMotion.class);
                    if (cameraMotion != null)
                        cameraMotion.setEnabled(enabled);
                }
            }
            if (enabled && blurObjectAdded) {
                blurObjectView.setVisible(true);
            } else if (blurObject != null) {
                blurObjectView.setVisible(true);
            }
        }
        case CUBEMAP_CMD -> {
            var cubemap = (Boolean) data[0];
            var enabled = !cubemap && Settings.settings.postprocess.motionBlur.active && !Settings.settings.runtime.openXr;
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    ppb.get(CameraMotion.class).setEnabled(enabled);
                    LightGlow lightglow = (LightGlow) ppb.get(LightGlow.class);
                    if (lightglow != null) {
                        lightglow.setNSamples(enabled ? 1 : Settings.settings.postprocess.lightGlow.samples);
                        lightglow.setTextureScale(getGlowTextureScale(Settings.settings.scene.star.brightness, Settings.settings.scene.star.glowFactor, Settings.settings.scene.star.pointSize, GaiaSky.instance.cameraManager.getFovFactor(), Settings.settings.program.modeCubemap.active));
                    }
                }
            }
        }
        case STEREOSCOPIC_CMD -> updateStereo((boolean) data[0], Settings.settings.program.modeStereo.profile);
        case STEREO_PROFILE_CMD -> updateStereo(Settings.settings.program.modeStereo.active, StereoProfile.values()[(Integer) data[0]]);
        case ANTIALIASING_CMD -> {
            final Antialias antiAliasingValue = (Antialias) data[0];
            GaiaSky.postRunnable(() -> {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        Antialiasing antialiasing = getAA(ppb);
                        if (antiAliasingValue.isPostProcessAntialias()) {
                            // clean
                            if (antialiasing != null) {
                                ppb.remove(antialiasing.getClass());
                            }
                            // update
                            initAntiAliasing(antiAliasingValue, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), ppb);
                            // ensure motion blur and levels go after
                            ppb.remove(Levels.class);
                            initLevels(ppb);
                        } else {
                            // remove
                            if (antialiasing != null) {
                                ppb.remove(antialiasing.getClass());
                            }
                        }
                    }
                }
            });
        }
        case BRIGHTNESS_CMD -> {
            var br = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        levels.setBrightness(br);
                }
            }
        }
        case CONTRAST_CMD -> {
            var cn = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        levels.setContrast(cn);
                }
            }
        }
        case HUE_CMD -> {
            var hue = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        levels.setHue(hue);
                }
            }
        }
        case SATURATION_CMD -> {
            var sat = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        levels.setSaturation(sat);
                }
            }
        }
        case GAMMA_CMD -> {
            var gamma = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        levels.setGamma(gamma);
                }
            }
        }
        case TONEMAPPING_TYPE_CMD -> {
            ToneMapping tm;
            if (data[0] instanceof String) {
                tm = ToneMapping.valueOf((String) data[0]);
            } else {
                tm = (ToneMapping) data[0];
            }
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        switch (tm) {
                        case AUTO -> levels.enableToneMappingAuto();
                        case EXPOSURE -> levels.enableToneMappingExposure();
                        case ACES -> levels.enableToneMappingACES();
                        case UNCHARTED -> levels.enableToneMappingUncharted();
                        case FILMIC -> levels.enableToneMappingFilmic();
                        case NONE -> levels.disableToneMapping();
                        }
                }
            }
        }
        case EXPOSURE_CMD -> {
            var exposure = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    Levels levels = (Levels) ppb.get(Levels.class);
                    if (levels != null)
                        levels.setExposure(exposure);
                }
            }
        }
        case FPS_INFO -> {
            var fps = (Float) data[0];
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    CameraMotion cameraMotionBlur = (CameraMotion) ppb.get(CameraMotion.class);
                    if (cameraMotionBlur != null)
                        cameraMotionBlur.setVelocityScale(fps / 60f);
                }
            }
        }
        case GRAPHICS_QUALITY_UPDATED -> {
            // Update graphics quality
            var gq = (GraphicsQuality) data[0];
            GaiaSky.postRunnable(() -> {
                for (int i = 0; i < RenderType.values().length; i++) {
                    if (pps[i] != null) {
                        PostProcessBean ppb = pps[i];
                        updateGraphicsQuality(ppb, gq);
                    }
                }
            });
        }
        case BILLBOARD_TEXTURE_IDX_CMD -> GaiaSky.postRunnable(() -> {
            var starTex = new Texture(Settings.settings.data.dataFileHandle(Settings.settings.scene.star.getStarTexture()), true);
            starTex.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            for (int i = 0; i < RenderType.values().length; i++) {
                if (pps[i] != null) {
                    PostProcessBean ppb = pps[i];
                    ((LightGlow) ppb.get(LightGlow.class)).setLightGlowTexture(starTex);
                }
            }
        });
        case BACKBUFFER_SCALE_CMD -> updateUpscaleFilters(Settings.settings.postprocess.upscaleFilter, (Float) data[0]);
        case UPSCALE_FILTER_CMD -> {
            var upscaleFilter = (UpscaleFilter) data[0];
            updateUpscaleFilters(upscaleFilter, (float) Settings.settings.graphics.backBufferScale);
        }
        default -> {
        }
        }
    }

    private Antialiasing getAA(PostProcessBean ppb) {
        PostProcessorEffect ppe = ppb.get(Fxaa.class);
        if (ppe == null) {
            ppe = ppb.get(Nfaa.class);
            if (ppe == null)
                return null;
        }
        return (Antialiasing) ppe;
    }

    private void replace(RenderType rt, final float width, final float height, final float targetWidth, final float targetHeight) {
        // Dispose of old post processor, if exists
        if (pps[rt.index] != null)
            pps[rt.index].dispose(false);
        // Create new
        pps[rt.index] = newPostProcessor(rt, width, height, targetWidth, targetHeight, manager);
    }

    private boolean changed(PostProcessor postProcess, int width, int height) {
        return (postProcess.getCombinedBuffer().width != width || postProcess.getCombinedBuffer().height != height);
    }

    @Override
    public boolean isEnabled(Class<? extends PostProcessorEffect> clazz) {
        if (pps == null || pps[RenderType.screen.index] == null) {
            return false;
        }
        var effect = pps[RenderType.screen.index].get(clazz);
        return (effect != null && effect.isEnabled());
    }

    @Override
    public boolean isLightScatterEnabled() {
        return isEnabled(LightGlow.class);
    }

    @Override
    public boolean isLensFlareEnabled() {
        return isEnabled(LensFlare.class);
    }

    private void updateStereo(boolean stereo, StereoProfile profile) {
        boolean curvatureEnabled = stereo && profile == StereoProfile.VR_HEADSET;
        boolean viewportHalved = stereo && !profile.isAnaglyph() && profile != StereoProfile.HORIZONTAL_3DTV;

        for (int i = 0; i < RenderType.values().length; i++) {
            if (pps[i] != null) {
                PostProcessBean ppb = pps[i];
                ppb.get(Curvature.class).setEnabled(curvatureEnabled);

                RenderType currentRenderType = RenderType.values()[i];
                int[] size = getSize(currentRenderType);
                ((LightGlow) ppb.get(LightGlow.class)).setViewportSize(size[0] / (viewportHalved ? 2 : 1), size[1]);
            }
        }
    }

    private void updateUpscaleFilters(UpscaleFilter upscaleFilter, double backBufferScale) {
        if (pps[RenderType.screen.index] != null) {
            PostProcessBean ppb = pps[RenderType.screen.index];

            XBRZ filter = (XBRZ) ppb.get(XBRZ.class);
            updateUpscaleFilter(upscaleFilter, backBufferScale, filter, ppb);
        }
    }

    private void updateUpscaleFilter(UpscaleFilter upscaleFilter, double backBufferScale, XBRZ filter, PostProcessBean ppb) {
        // Actual filter.
        boolean enabled = backBufferScale < 1 && upscaleFilter.equals(UpscaleFilter.XBRZ);
        filter.setEnabled(enabled);
        if (enabled) {
            int w = ppb.pp.getCombinedBuffer().width;
            int h = ppb.pp.getCombinedBuffer().height;
            filter.setInputSize(w, h);
            filter.setOutputSize((int) (w / backBufferScale), (int) (h / backBufferScale));
        }

        // Texture filter.
        ppb.pp.setBufferTextureFilter(upscaleFilter.minification, upscaleFilter.magnification);
    }

}
