/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scenegraph.component;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import gaiasky.GaiaSky;
import gaiasky.data.AssetBean;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings.ElevationType;
import gaiasky.util.gdx.loader.PFMTextureLoader.PFMTextureParameter;
import gaiasky.util.gdx.model.IntModelInstance;
import gaiasky.util.gdx.shader.FloatExtAttribute;
import gaiasky.util.gdx.shader.TextureExtAttribute;
import gaiasky.util.gdx.shader.Vector2Attribute;
import gaiasky.util.math.MathUtilsd;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A basic component that contains the info on a material
 */
public class MaterialComponent implements IObserver {
    private static final Log logger = Logger.getLogger(MaterialComponent.class);

    /** Generated height keyword **/
    public static final String GEN_HEIGHT_KEYWORD = "generate";
    /** Default texture parameters **/
    protected static final TextureParameter textureParamsMipMap, textureParams;
    protected static final PFMTextureParameter pfmTextureParams;

    static {
        textureParamsMipMap = new TextureParameter();
        textureParamsMipMap.genMipMaps = true;
        textureParamsMipMap.magFilter = TextureFilter.Linear;
        textureParamsMipMap.minFilter = TextureFilter.MipMapLinearLinear;

        textureParams = new TextureParameter();
        textureParams.genMipMaps = false;
        textureParams.magFilter = TextureFilter.Linear;
        textureParams.minFilter = TextureFilter.Linear;

        pfmTextureParams = new PFMTextureParameter(textureParams);
        pfmTextureParams.invert = false;
        pfmTextureParams.internalFormat = GL20.GL_RGB;
    }

    private static TextureParameter getTP(String tex) {
        return getTP(tex, false);
    }

    private static TextureParameter getTP(String tex, boolean mipmap) {
        if (tex != null && tex.endsWith(".pfm")) {
            return pfmTextureParams;
        } else {
            if (mipmap)
                return textureParamsMipMap;
            else
                return textureParams;
        }
    }

    // TEXTURES
    public boolean texInitialised, texLoading;
    public String diffuse, specular, normal, emissive, ring, height, ringnormal, roughness, metallic, ao;
    public String diffuseUnpacked, specularUnpacked, normalUnpacked, emissiveUnpacked, ringUnpacked, heightUnpacked, ringnormalUnpacked, roughnessUnapcked, metallicUnpacked, aoUnapcked;

    // Material properties
    public Float albedo;
    public float[] metallicColor;
    public float[] emissiveColor;

    // SPECULAR
    public float specularIndex = -1;

    // HEIGHT
    public Float heightScale = 0.005f;
    public Vector2 heightSize = new Vector2();
    public float[][] heightMap;
    public ElevationComponent ec;

    /** The actual material **/
    private Material material, ringMaterial;

    // Noise seed
    private long noiseSeed = 0L;

    // Biome lookup texture
    private String biomelookup = "data/tex/base/biome-lookup.png";

    /** Add also color even if texture is present **/
    public boolean coloriftex = false;

    public MaterialComponent() {
        super();
        EventManager.instance.subscribe(this, Events.ELEVATION_TYPE_CMD, Events.ELEVATION_MULTIPLIER_CMD, Events.TESSELLATION_QUALITY_CMD);
    }

    public void initialize(AssetManager manager) {
        // Add textures to load
        diffuseUnpacked = addToLoad(diffuse, getTP(diffuse, true), manager);
        normalUnpacked = addToLoad(normal, getTP(normal), manager);
        specularUnpacked = addToLoad(specular, getTP(specular, true), manager);
        emissiveUnpacked = addToLoad(emissive, getTP(emissive, true), manager);
        ringUnpacked = addToLoad(ring, getTP(ring, true), manager);
        ringnormalUnpacked = addToLoad(ringnormal, getTP(ringnormal, true), manager);
        roughnessUnapcked = addToLoad(roughness, getTP(roughness, true), manager);
        metallicUnpacked = addToLoad(metallic, getTP(metallic, true), manager);
        aoUnapcked = addToLoad(ao, getTP(ao, true), manager);
        if (height != null)
            if (!height.endsWith(GEN_HEIGHT_KEYWORD))
                heightUnpacked = addToLoad(height, getTP(height, true), manager);
    }

    public void initialize() {
        // Add textures to load
        diffuseUnpacked = addToLoad(diffuse, getTP(diffuse, true));
        normalUnpacked = addToLoad(normal, getTP(normal));
        specularUnpacked = addToLoad(specular, getTP(specular, true));
        emissiveUnpacked = addToLoad(emissive, getTP(emissive, true));
        ringUnpacked = addToLoad(ring, getTP(ring, true));
        ringnormalUnpacked = addToLoad(ringnormal, getTP(ringnormal, true));
        roughnessUnapcked = addToLoad(roughness, getTP(roughness, true));
        metallicUnpacked = addToLoad(metallic, getTP(metallic, true));
        aoUnapcked = addToLoad(ao, getTP(ao, true));
        if (height != null)
            if (!height.endsWith(GEN_HEIGHT_KEYWORD))
                heightUnpacked = addToLoad(height, getTP(height, true));
    }

    public boolean isFinishedLoading(AssetManager manager) {
        return isFL(diffuseUnpacked, manager) && isFL(normalUnpacked, manager) && isFL(specularUnpacked, manager) && isFL(emissiveUnpacked, manager) && isFL(ringUnpacked, manager) && isFL(ringnormalUnpacked, manager) && isFL(heightUnpacked, manager) && isFL(roughnessUnapcked, manager) && isFL(metallicUnpacked, manager) && isFL(aoUnapcked, manager);
    }

    public boolean isFL(String tex, AssetManager manager) {
        if (tex == null)
            return true;
        return manager.isLoaded(tex);
    }

    /**
     * Adds the texture to load and unpacks any star (*) with the current
     * quality setting.
     *
     * @param tex The texture file to load.
     *
     * @return The actual loaded texture path
     */
    private String addToLoad(String tex, TextureParameter texParams, AssetManager manager) {
        if (tex == null)
            return null;

        tex = GlobalResources.unpackAssetPath(tex);
        logger.info(I18n.txt("notif.loading", tex));
        manager.load(tex, Texture.class, texParams);

        return tex;
    }

    /**
     * Adds the texture to load and unpacks any "%QUALITY%" with the current
     * quality setting.
     *
     * @param tex The texture file to load.
     *
     * @return The actual loaded texture path
     */
    private String addToLoad(String tex, TextureParameter texParams) {
        if (tex == null)
            return null;

        tex = GlobalResources.unpackAssetPath(tex);
        logger.info(I18n.txt("notif.loading", tex));
        AssetBean.addAsset(tex, Texture.class, texParams);

        return tex;
    }

    public Material initMaterial(AssetManager manager, IntModelInstance instance, float[] cc, boolean culling) {
        return initMaterial(manager, instance.materials.get(0), instance.materials.size > 1 ? instance.materials.get(1) : null, cc, culling);
    }

    public Material initMaterial(AssetManager manager, Material mat, Material ring, float[] cc, boolean culling) {
        SkyboxComponent.prepareSkybox();
        this.material = mat;
        if (diffuse != null && material.get(TextureAttribute.Diffuse) == null) {
            Texture tex = manager.get(diffuseUnpacked, Texture.class);
            material.set(new TextureAttribute(TextureAttribute.Diffuse, tex));
        }
        if (cc != null && (coloriftex || diffuse == null)) {
            // Add diffuse colour
            material.set(new ColorAttribute(ColorAttribute.Diffuse, cc[0], cc[1], cc[2], cc[3]));
        }

        if (normal != null && material.get(TextureAttribute.Normal) == null) {
            Texture tex = manager.get(normalUnpacked, Texture.class);
            material.set(new TextureAttribute(TextureAttribute.Normal, tex));
        }
        if (specular != null && material.get(TextureAttribute.Specular) == null) {
            Texture tex = manager.get(specularUnpacked, Texture.class);
            material.set(new TextureAttribute(TextureAttribute.Specular, tex));
            if (specularIndex < 0)
                material.set(new ColorAttribute(ColorAttribute.Specular, 0.7f, 0.7f, 0.7f, 1f));
        }
        if (material.get(ColorAttribute.Specular) == null) {
            if (specularIndex >= 0) {
                // Control amount of specularity with specular index
                material.set(new ColorAttribute(ColorAttribute.Specular, specularIndex, specularIndex, specularIndex, 1f));
            } else {
                material.set(new ColorAttribute(ColorAttribute.Specular, 0, 0, 0, 1f));
            }
        }
        if (emissive != null && material.get(TextureAttribute.Emissive) == null) {
            Texture tex = manager.get(emissiveUnpacked, Texture.class);
            material.set(new TextureExtAttribute(TextureAttribute.Emissive, tex));
        }
        if (emissiveColor != null) {
            material.set(new ColorAttribute(ColorAttribute.Emissive, emissiveColor[0], emissiveColor[1], emissiveColor[2], 1f));
        }
        if (height != null && material.get(TextureExtAttribute.Height) == null) {
            if (!height.endsWith(GEN_HEIGHT_KEYWORD)) {
                Texture tex = manager.get(heightUnpacked, Texture.class);
                if (!Settings.settings.scene.renderer.elevation.type.isNone()) {
                    initializeElevationData(tex);
                }
            } else {
                initializeGenElevationData();
            }
        }
        if (ring != null) {
            // Ring material
            ringMaterial = ring;
            if (ringMaterial.get(TextureAttribute.Diffuse) == null) {
                ringMaterial.set(new TextureAttribute(TextureAttribute.Diffuse, manager.get(ringUnpacked, Texture.class)));
            }
            if (ringnormal != null && ringMaterial.get(TextureAttribute.Normal) == null) {
                ringMaterial.set(new TextureAttribute(TextureAttribute.Normal, manager.get(ringnormalUnpacked, Texture.class)));
            }
            ringMaterial.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));
            if (!culling)
                ringMaterial.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));
        }
        if (!culling) {
            material.set(new IntAttribute(IntAttribute.CullFace, GL20.GL_NONE));
        }
        if (metallic != null || metallicColor != null) {
            SkyboxComponent.prepareSkybox();
            // Use reflection texture
            material.set(new CubemapAttribute(CubemapAttribute.EnvironmentMap, SkyboxComponent.skybox));
            if (metallic != null && material.get(TextureAttribute.Reflection) == null) {
                Texture tex = manager.get(metallicUnpacked, Texture.class);
                material.set(new TextureExtAttribute(TextureAttribute.Reflection, tex));
            }
            // Use reflection color
            if (metallicColor != null) {
                material.set(new ColorAttribute(ColorAttribute.Reflection, metallicColor[0], metallicColor[1], metallicColor[2], 1f));
            }
        }
        if (roughness != null && material.get(TextureExtAttribute.Roughness) == null) {
            Texture tex = manager.get(roughnessUnapcked, Texture.class);
            material.set(new TextureExtAttribute(TextureExtAttribute.Roughness, tex));
        }
        if (albedo != null) {
            material.set(new FloatExtAttribute(FloatExtAttribute.Albedo, albedo));
        }
        if (ao != null && material.get(TextureExtAttribute.AO) == null) {
            Texture tex = manager.get(aoUnapcked, Texture.class);
            material.set(new TextureExtAttribute(TextureExtAttribute.AO, tex));
        }

        return material;
    }

    private void initializeGenElevationData() {
        Thread t = new Thread(() -> {
            final int N = Settings.settings.graphics.quality.texWidthTarget;
            final int M = Settings.settings.graphics.quality.texHeightTarget;

            Trio<float[][], float[][], Pixmap> trio = ec.generateElevation(N, M, heightScale, noiseSeed);
            float[][] elevationData = trio.getFirst();
            float[][] moistureData = trio.getSecond();
            Pixmap heightPixmap = trio.getThird();

            try {
                BufferedImage lut = ImageIO.read(Settings.settings.data.dataFileHandle(biomelookup).file());
                int iw = lut.getWidth() - 1;
                int ih = lut.getHeight() - 1;

                final Pixmap diffusePixmap;
                final Pixmap specularPixmap;
                if (diffuse == null) {
                    diffusePixmap = new Pixmap(N, M, Pixmap.Format.RGBA8888);
                } else {
                    diffusePixmap = null;
                }
                if (specular == null) {
                    specularPixmap = new Pixmap(N, M, Pixmap.Format.RGBA8888);
                } else {
                    specularPixmap = null;
                }
                Color col = new Color();
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < M; j++) {
                        // Normalize height
                        float height = elevationData[i][j] / heightScale;
                        float moisture = moistureData[i][j];

                        int x = (int) (iw * MathUtilsd.clamp(moisture, 0, 1));
                        int y = (int) (ih - ih * MathUtilsd.clamp(height, 0, 1));

                        java.awt.Color argb = new java.awt.Color(lut.getRGB(x, y));
                        col.set(argb.getRed() / 255f, argb.getGreen() / 255f, argb.getBlue() / 255f, 1f);

                        diffusePixmap.drawPixel(i, j, Color.rgba8888(col));
                        boolean water = height <= 0.02f;
                        boolean snow = height > 0.85f;
                        if (water) {
                            if (specularPixmap != null) {
                                // White
                                specularPixmap.drawPixel(i, j, Color.rgba8888(1f, 1f, 1f, 1f));
                            }
                        } else if (snow) {
                            if (specularPixmap != null) {
                                // Whitish
                                specularPixmap.drawPixel(i, j, Color.rgba8888(0.5f, 0.5f, 0.5f, 1f));
                            }
                        } else {
                            if (specularPixmap != null) {
                                // Black
                                specularPixmap.drawPixel(i, j, Color.rgba8888(0f, 0f, 0f, 1f));
                            }
                        }
                    }
                }
                // Write to disk if necessary
                if (Settings.settings.runtime.saveProceduralTextures) {
                    long timestamp = TimeUtils.millis();
                    savePixmap(heightPixmap, timestamp, "height");
                    savePixmap(diffusePixmap, timestamp, "albedo");
                    savePixmap(specularPixmap, timestamp, "specular");
                }

                GaiaSky.postRunnable(() -> {
                    if (heightPixmap != null) {
                        // Create texture, populate material
                        heightMap = elevationData;
                        Texture heightTex = new Texture(heightPixmap, true);
                        heightTex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);

                        heightSize.set(heightTex.getWidth(), heightTex.getHeight());
                        material.set(new TextureExtAttribute(TextureExtAttribute.Height, heightTex));
                        material.set(new FloatExtAttribute(FloatExtAttribute.HeightScale, heightScale * (float) Settings.settings.scene.renderer.elevation.multiplier));
                        material.set(new Vector2Attribute(Vector2Attribute.HeightSize, new Vector2(N, M)));
                        material.set(new FloatExtAttribute(FloatExtAttribute.TessQuality, (float) Settings.settings.scene.renderer.elevation.quality));
                    }
                    if (diffusePixmap != null) {
                        Texture diffuseTex = new Texture(diffusePixmap, true);
                        diffuseTex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
                        material.set(new TextureAttribute(TextureAttribute.Diffuse, diffuseTex));
                    }
                    if (specularPixmap != null) {
                        Texture specularTex = new Texture(specularPixmap, true);
                        specularTex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
                        material.set(new TextureAttribute(TextureAttribute.Specular, specularTex));
                        if (specularIndex < 0)
                            material.set(new ColorAttribute(ColorAttribute.Specular, 0.7f, 0.7f, 0.7f, 1f));
                    }
                });
            } catch (IOException e) {
                logger.error(e);
            }
        });
        t.start();
    }

    private void savePixmap(Pixmap p, long timestamp, String name) {
        if (p != null) {
            FileHandle imgFile = Gdx.files.absolute("/tmp/" + timestamp + "-" + name + ".png");
            PixmapIO.writePNG(imgFile, p);
            logger.info(TextUtils.capitalise(name) + " texture written to " + imgFile.path());
        }

    }

    private void initializeElevationData(Texture tex) {
        Thread t = new Thread(() -> {
            // Construct RAM height map from texture
            String heightUnpacked = GlobalResources.unpackAssetPath(height);
            logger.info("Constructing elevation data from texture: " + heightUnpacked);
            Pixmap heightPixmap = new Pixmap(new FileHandle(heightUnpacked));
            float[][] partialData = new float[heightPixmap.getWidth()][heightPixmap.getHeight()];
            for (int i = 0; i < heightPixmap.getWidth(); i++) {
                for (int j = 0; j < heightPixmap.getHeight(); j++) {
                    Color col = new Color(heightPixmap.getPixel(i, j));
                    partialData[i][j] = (1f - col.r) * heightScale;
                }
            }

            GaiaSky.postRunnable(() -> {
                // Populate material
                heightMap = partialData;
                heightSize.set(tex.getWidth(), tex.getHeight());
                material.set(new TextureExtAttribute(TextureExtAttribute.Height, tex));
                material.set(new FloatExtAttribute(FloatExtAttribute.HeightScale, heightScale * (float) Settings.settings.scene.renderer.elevation.multiplier));
                material.set(new Vector2Attribute(Vector2Attribute.HeightSize, heightSize));
                material.set(new FloatExtAttribute(FloatExtAttribute.TessQuality, (float) Settings.settings.scene.renderer.elevation.quality));
            });
        });
        t.start();
    }

    private void removeElevationData() {
        heightMap = null;
        material.remove(TextureExtAttribute.Height);
        material.remove(FloatExtAttribute.HeightScale);
        material.remove(Vector2Attribute.HeightSize);
        material.remove(FloatExtAttribute.HeightNoiseSize);
        material.remove(FloatExtAttribute.TessQuality);
    }

    /**
     * @deprecated use {@link MaterialComponent#setDiffuse(String)} instead
     */
    @Deprecated
    public void setBase(String diffuse) {
        this.setDiffuse(diffuse);
    }

    public void setDiffuse(String diffuse) {
        this.diffuse = Settings.settings.data.dataFile(diffuse);
    }

    public void setSpecular(String specular) {
        this.specular = Settings.settings.data.dataFile(specular);
    }

    public void setSpecular(Double specular) {
        this.specularIndex = specular.floatValue();
    }

    public void setNormal(String normal) {
        this.normal = Settings.settings.data.dataFile(normal);
    }

    /**
     * @deprecated use {@link MaterialComponent#setEmissive(String)} instead
     */
    @Deprecated
    public void setNight(String emissive) {
        this.setEmissive(emissive);
    }

    public void setEmissive(String emissive) {
        this.emissive = Settings.settings.data.dataFile(emissive);
    }

    public void setEmissive(Double emissive) {
        float r = emissive.floatValue();
        this.emissiveColor = new float[] { r, r, r };
    }

    public void setEmissive(double[] emissive) {
        if (emissive.length > 1) {
            this.emissiveColor = new float[] { (float) emissive[0], (float) emissive[1], (float) emissive[2] };
        } else {
            float r = (float) emissive[0];
            this.emissiveColor = new float[] { r, r, r };
        }
    }

    public void setRing(String ring) {
        this.ring = Settings.settings.data.dataFile(ring);
    }

    public void setRingnormal(String ringnormal) {
        this.ringnormal = Settings.settings.data.dataFile(ringnormal);
    }

    public void setHeight(String height) {
        this.height = Settings.settings.data.dataFile(height);
    }

    public void setHeightScale(Double heightScale) {
        this.heightScale = (float) (heightScale * Constants.KM_TO_U);
    }

    public void setColoriftex(Boolean coloriftex) {
        this.coloriftex = coloriftex;
    }

    public void setElevation(ElevationComponent ec) {
        this.ec = ec;
    }

    public void setBiomelookup(String biomeLookupTex) {
        this.biomelookup = biomeLookupTex;
    }

    /**
     * @deprecated use {@link MaterialComponent#setMetallic(String)} instead
     */
    @Deprecated
    public void setReflection(Double metallicColor) {
        this.setMetallic(metallicColor);
    }

    public void setMetallic(Double metallicColor) {
        float r = metallicColor.floatValue();
        this.metallicColor = new float[] { r, r, r };
    }

    public void setMetallic(String metallic) {
        this.metallic = Settings.settings.data.dataFile(metallic);
    }

    public void setReflection(double[] metallic) {
        if (metallic.length > 1) {
            this.metallicColor = new float[] { (float) metallic[0], (float) metallic[1], (float) metallic[2] };
        } else {
            float r = (float) metallic[0];
            this.metallicColor = new float[] { r, r, r };
        }
    }

    public void setRoughness(String roughness) {
        this.roughness = Settings.settings.data.dataFile(roughness);
    }

    public void setAlbedo(Double albedo) {
        this.albedo = albedo.floatValue();
    }

    public void setAo(String ao) {
        this.ao = Settings.settings.data.dataFile(ao);
    }

    public void setSeed(Long seed) {
        this.noiseSeed = seed;
    }

    public boolean hasHeight() {
        return this.height != null && !this.height.isEmpty();
    }

    /**
     * Disposes and unloads all currently loaded textures immediately
     *
     * @param manager The asset manager
     **/
    public void disposeTextures(AssetManager manager) {
        if (diffuse != null && manager.isLoaded(diffuseUnpacked)) {
            manager.unload(diffuseUnpacked);
            diffuseUnpacked = null;
            unload(material, TextureAttribute.Diffuse);
        }
        if (normal != null && manager.isLoaded(normalUnpacked)) {
            manager.unload(normalUnpacked);
            normalUnpacked = null;
            unload(material, TextureAttribute.Normal);
        }
        if (specular != null && manager.isLoaded(specularUnpacked)) {
            manager.unload(specularUnpacked);
            specularUnpacked = null;
            unload(material, TextureAttribute.Specular);
        }
        if (emissive != null && manager.isLoaded(emissiveUnpacked)) {
            manager.unload(emissiveUnpacked);
            emissiveUnpacked = null;
            unload(material, TextureAttribute.Emissive);
        }
        if (ring != null && manager.isLoaded(ringUnpacked)) {
            manager.unload(ringUnpacked);
            ringUnpacked = null;
            unload(ringMaterial, TextureAttribute.Diffuse);
        }
        if (ringnormal != null && manager.isLoaded(ringnormalUnpacked)) {
            manager.unload(ringnormalUnpacked);
            ringnormalUnpacked = null;
            unload(ringMaterial, TextureAttribute.Normal);
        }
        if (height != null && manager.isLoaded(heightUnpacked)) {
            manager.unload(heightUnpacked);
            heightUnpacked = null;
            heightMap = null;
            unload(material, TextureExtAttribute.Height);
        }
        if (metallic != null && manager.isLoaded(metallicUnpacked)) {
            manager.unload(metallicUnpacked);
            metallicUnpacked = null;
            unload(material, TextureAttribute.Reflection);
        }
        if (roughness != null && manager.isLoaded(roughnessUnapcked)) {
            manager.unload(roughnessUnapcked);
            roughnessUnapcked = null;
            unload(material, TextureExtAttribute.Roughness);
        }
        if (ao != null && manager.isLoaded(aoUnapcked)) {
            manager.unload(aoUnapcked);
            aoUnapcked = null;
            unload(material, TextureExtAttribute.AO);
        }
        texLoading = false;
        texInitialised = false;
    }

    private void unload(Material mat, long attrMask) {
        if (mat != null) {
            Attribute attr = mat.get(attrMask);
            mat.remove(attrMask);
            if (attr instanceof TextureAttribute) {
                Texture tex = ((TextureAttribute) attr).textureDescription.texture;
                tex.dispose();
            }
        }
    }

    @Override
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case ELEVATION_TYPE_CMD:
            if (this.hasHeight() && this.material != null) {
                ElevationType newType = (ElevationType) data[0];
                GaiaSky.postRunnable(() -> {
                    if (newType.isNone()) {
                        removeElevationData();
                    } else {
                        if (heightMap == null) {
                            if (height.endsWith(GEN_HEIGHT_KEYWORD))
                                initializeGenElevationData();
                            else {
                                if (this.material.has(TextureExtAttribute.Height)) {
                                    initializeElevationData(((TextureAttribute) this.material.get(TextureExtAttribute.Height)).textureDescription.texture);
                                } else if (AssetBean.manager().isLoaded(heightUnpacked)) {
                                    if (!height.endsWith(GEN_HEIGHT_KEYWORD)) {
                                        Texture tex = AssetBean.manager().get(heightUnpacked, Texture.class);
                                        if (!Settings.settings.scene.renderer.elevation.type.isNone()) {
                                            initializeElevationData(tex);
                                        }
                                    } else {
                                        initializeGenElevationData();
                                    }

                                }
                            }
                        }
                    }
                });
            }
            break;
        case ELEVATION_MULTIPLIER_CMD:
            if (this.hasHeight() && this.material != null) {
                float newMultiplier = (Float) data[0];
                GaiaSky.postRunnable(() -> this.material.set(new FloatExtAttribute(FloatExtAttribute.HeightScale, heightScale * newMultiplier)));
            }
            break;
        case TESSELLATION_QUALITY_CMD:
            if (this.hasHeight() && this.material != null) {
                float newQuality = (Float) data[0];
                GaiaSky.postRunnable(() -> this.material.set(new FloatExtAttribute(FloatExtAttribute.TessQuality, newQuality)));
            }
            break;
        default:
            break;
        }
    }

    public String getTexturesString() {
        StringBuilder sb = new StringBuilder();
        if (diffuse != null)
            sb.append(diffuse);
        if (normal != null)
            sb.append(",").append(normal);
        if (specular != null)
            sb.append(",").append(specular);
        if (emissive != null)
            sb.append(",").append(emissive);
        if (ring != null)
            sb.append(",").append(ring);
        if (ringnormal != null)
            sb.append(",").append(ringnormal);
        if (height != null)
            sb.append(",").append(height);
        return sb.toString();
    }

    @Override
    public String toString() {
        return diffuse;
    }
}
