/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.record;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader.TextureParameter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;
import gaiasky.GaiaSky;
import gaiasky.data.AssetBean;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.BlendMode;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.gdx.model.IntModel;
import gaiasky.util.gdx.model.IntModelInstance;
import gaiasky.util.gdx.shader.Material;
import gaiasky.util.gdx.shader.attribute.*;
import gaiasky.util.i18n.I18n;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static gaiasky.scene.record.MaterialComponent.convertToComponent;

public class CloudComponent extends NamedComponent implements IObserver, IMaterialProvider {
    /** Default texture parameters **/
    protected static final TextureParameter textureParams;
    private static final Log logger = Logger.getLogger(CloudComponent.class);

    static {
        textureParams = new TextureParameter();
        textureParams.genMipMaps = true;
        textureParams.magFilter = TextureFilter.Linear;
        textureParams.minFilter = TextureFilter.MipMapLinearNearest;
    }

    public int quality;
    public float size;
    public NoiseComponent nc;
    public ModelComponent mc;
    public Matrix4 localTransform;
    /** RGB color of generated clouds **/
    public float[] color = new float[] { 1f, 1f, 1f, 0.7f };
    public String diffuse, diffuseUnpacked;
    /** The material component associated to the same model. **/
    public MaterialComponent materialComponent;
    // Cubemap.
    public CubemapComponent diffuseCubemap;
    // Virtual texture.
    public VirtualTextureComponent diffuseSvt;
    // Model parameters
    public Map<String, Object> params;
    Vector3 aux;
    Vector3d aux3;
    private AssetManager manager;
    private final AtomicBoolean generated = new AtomicBoolean(false);
    private Texture cloudTex;
    private Material material;
    private boolean texInitialised, texLoading;

    public CloudComponent() {
        localTransform = new Matrix4();
        mc = new ModelComponent(false);
        mc.setBlendMode(BlendMode.COLOR);
        mc.initialize(null);
        aux = new Vector3();
        aux3 = new Vector3d();
    }

    public void initialize(String name, boolean force) {
        super.initialize(name);
        this.initialize(force);
    }

    private void initialize(boolean force) {
        if (!Settings.settings.scene.initialization.lazyTexture || force) {
            if (diffuse != null && !diffuse.endsWith(Constants.GEN_KEYWORD)) {
                // Add textures to load
                diffuseUnpacked = addToLoad(diffuse);
                if (diffuseUnpacked != null)
                    logger.info(I18n.msg("notif.loading", diffuseUnpacked));
            }
            if (diffuseCubemap != null)
                diffuseCubemap.initialize(manager);
            if (diffuseSvt != null)
                diffuseSvt.initialize("diffuseSvt", this);
        }
        this.generated.set(false);
    }

    public boolean isFinishedLoading(AssetManager manager) {
        return ComponentUtils.isLoaded(diffuseUnpacked, manager) && ComponentUtils.isLoaded(diffuseCubemap, manager);
    }

    /**
     * Adds the texture to load and unpacks any star (*) with the current
     * quality setting.
     */
    private String addToLoad(String tex) {
        if (tex == null)
            return null;
        tex = GlobalResources.unpackAssetPath(tex);
        AssetBean.addAsset(tex, Texture.class, textureParams);
        return tex;
    }

    public void doneLoading(AssetManager manager) {
        this.manager = manager;
        Pair<IntModel, Map<String, Material>> pair = ModelCache.cache.getModel("sphere", params, Bits.indexes(Usage.Position, Usage.Normal, Usage.Tangent, Usage.BiNormal, Usage.TextureCoordinates), GL20.GL_TRIANGLES);
        IntModel cloudModel = pair.getFirst();
        Material material = pair.getSecond().get("base");
        material.clear();

        // CREATE CLOUD MODEL
        mc.instance = new IntModelInstance(cloudModel, this.localTransform);

        if (!Settings.settings.scene.initialization.lazyTexture) {
            initMaterial();
        }

        // Subscribe to new graphics quality setting event
        EventManager.instance.subscribe(this, Event.GRAPHICS_QUALITY_UPDATED);

        // Initialised
        texInitialised = !Settings.settings.scene.initialization.lazyTexture;
        // Loading
        texLoading = false;
    }

    public void touch() {
        if (Settings.settings.scene.initialization.lazyTexture && !texInitialised) {

            if (!texLoading) {
                initialize(true);
                // Set to loading
                texLoading = true;
            } else if (isFinishedLoading(manager)) {
                GaiaSky.postRunnable(this::initMaterial);

                // Set to initialised
                texInitialised = true;
                texLoading = false;
            }
        }

    }

    public void update(Vector3b transform) {
        transform.setToTranslation(localTransform).scl(size);
    }

    public void initMaterial() {
        material = mc.instance.materials.first();

        if (diffuse != null && material.get(TextureAttribute.Diffuse) == null) {
            if (!diffuse.endsWith(Constants.GEN_KEYWORD)) {
                Texture tex = manager.get(diffuseUnpacked, Texture.class);
                material.set(new TextureAttribute(TextureAttribute.Diffuse, tex));
            } else {
                initializeGenCloudData();
            }
        }
        if (diffuseCubemap != null) {
            diffuseCubemap.prepareCubemap(manager);
            material.set(new CubemapAttribute(CubemapAttribute.DiffuseCubemap, diffuseCubemap.cubemap));
        }
        if (diffuseSvt != null && materialComponent != null) {
            if (materialComponent.diffuseSvt != null) {
                addSVTAttributes(material, diffuseSvt, materialComponent.diffuseSvt.id);
                materialComponent.svts.add(diffuseSvt);
            }
        }
        material.set(new BlendingAttribute(1.0f));
        // Do not cull
        material.set(new IntAttribute(IntAttribute.CullFace, 0));
    }

    private void addSVTAttributes(Material material, VirtualTextureComponent svt, int id) {
        // Set ID.
        svt.id = id;
        // Set attributes.
        double svtResolution = svt.tileSize * Math.pow(2.0, svt.tree.depth);
        material.set(new Vector2Attribute(Vector2Attribute.SvtResolution, new Vector2((float) (svtResolution * svt.tree.root.length), (float) svtResolution)));
        material.set(new FloatAttribute(FloatAttribute.SvtTileSize, svt.tileSize));
        material.set(new FloatAttribute(FloatAttribute.SvtDepth, svt.tree.depth));
        material.set(new FloatAttribute(FloatAttribute.SvtId, svt.id));
    }

    public void setGenerated(boolean generated) {
        this.generated.set(generated);
    }

    private synchronized void initializeGenCloudData() {
        if (!generated.get()) {
            generated.set(true);
            GaiaSky.instance.getExecutorService().execute(() -> {
                // Begin
                EventManager.publish(Event.PROCEDURAL_GENERATION_CLOUD_INFO, this, true);

                final int N = Settings.settings.graphics.quality.texWidthTarget;
                final int M = Settings.settings.graphics.quality.texHeightTarget;
                long start = TimeUtils.millis();
                GaiaSky.postRunnable(() -> logger.info(I18n.msg("gui.procedural.info.generate", I18n.msg("gui.procedural.cloud"), N, M)));

                if (nc == null) {
                    nc = new NoiseComponent();
                    Random noiseRandom = new Random();
                    nc.randomizeAll(noiseRandom, noiseRandom.nextBoolean(), true);
                }
                Pixmap cloudPixmap = nc.generateData(N, M, color, I18n.msg("gui.procedural.progress", I18n.msg("gui.procedural.cloud"), name));
                // Write to disk if necessary
                if (Settings.settings.program.saveProceduralTextures) {
                    SysUtils.saveProceduralPixmap(cloudPixmap, this.name + "-cloud");
                }
                GaiaSky.postRunnable(() -> {
                    if (cloudPixmap != null) {
                        cloudTex = new Texture(cloudPixmap, true);
                        cloudTex.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
                        material.set(new TextureAttribute(TextureAttribute.Diffuse, cloudTex));
                    }
                    long elapsed = TimeUtils.millis() - start;
                    logger.info(I18n.msg("gui.procedural.info.done", I18n.msg("gui.procedural.cloud"), elapsed / 1000d));
                });

                // End
                EventManager.publish(Event.PROCEDURAL_GENERATION_CLOUD_INFO, this, false);
            });
        }
    }

    public void disposeTexture(AssetManager manager, Material material, String name, String nameUnpacked, int texAttributeIndex, Texture tex) {
        if (name != null && manager != null && manager.isLoaded(nameUnpacked)) {
            unload(material, texAttributeIndex);
            manager.unload(nameUnpacked);
        }
        if (tex != null) {
            unload(material, texAttributeIndex);
            tex.dispose();
        }
    }

    public void disposeCubemap(AssetManager manager, Material mat, int attributeIndex, CubemapComponent cubemap) {
        if (cubemap != null && cubemap.isLoaded(manager)) {
            unload(material, attributeIndex);
            manager.unload(cubemap.cmBack);
            manager.unload(cubemap.cmFront);
            manager.unload(cubemap.cmUp);
            manager.unload(cubemap.cmDown);
            manager.unload(cubemap.cmRight);
            manager.unload(cubemap.cmLeft);
            cubemap.dispose();
        }
    }

    /**
     * Disposes and unloads all currently loaded textures immediately
     *
     * @param manager The asset manager
     **/
    public void disposeTextures(AssetManager manager) {
        disposeTexture(manager, material, diffuse, diffuseUnpacked, TextureAttribute.Diffuse, cloudTex);
        disposeCubemap(manager, material, CubemapAttribute.DiffuseCubemap, diffuseCubemap);
        texLoading = false;
        texInitialised = false;
    }

    private void unload(Material mat, int attrIndex) {
        if (mat != null) {
            Attribute attr = mat.get(attrIndex);
            mat.remove(attrIndex);
            if (attr instanceof TextureAttribute) {
                Texture tex = ((TextureAttribute) attr).textureDescription.texture;
                tex.dispose();
            }
        }
    }

    public void removeAtmosphericScattering(Material mat) {
        mat.remove(AtmosphereAttribute.CameraHeight);
    }

    public void setQuality(Long quality) {
        this.quality = quality.intValue();
    }

    public void setSize(Double size) {
        this.size = (float) (size * Constants.KM_TO_U);
    }

    public void setMc(ModelComponent mc) {
        this.mc = mc;
    }

    public void setLocalTransform(Matrix4 localTransform) {
        this.localTransform = localTransform;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public void setCloud(String diffuse) {
        setDiffuse(diffuse);
    }

    public void setDiffuse(String diffuse) {
        this.diffuse = Settings.settings.data.dataFile(diffuse);
    }

    public void setNoise(NoiseComponent noise) {
        this.nc = noise;
    }

    public void setDiffuseCubemap(String diffuseCubemap) {
        this.diffuseCubemap = new CubemapComponent();
        this.diffuseCubemap.setLocation(diffuseCubemap);
    }

    public void setDiffuseSVT(VirtualTextureComponent virtualTextureComponent) {
        this.diffuseSvt = virtualTextureComponent;
    }

    public void setDiffuseSVT(Map<Object, Object> virtualTexture) {
        setDiffuseSVT(convertToComponent(virtualTexture));
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        if (event == Event.GRAPHICS_QUALITY_UPDATED) {
            GaiaSky.postRunnable(() -> {
                if (texInitialised) {
                    // Remove current textures
                    this.disposeTextures(this.manager);
                    // Set generated status to false
                    this.generated.set(false);
                }
            });
        }
    }

    /**
     * Creates a random cloud component using the given seed and the base
     * body size. Generates a random cloud texture.
     *
     * @param seed The seed to use.
     * @param size The body size in internal units.
     */
    public void randomizeAll(long seed, double size) {
        Random rand = new Random(seed);

        // Size
        double sizeKm = size * Constants.U_TO_KM;
        setSize(sizeKm + gaussian(rand, 30.0, 8.0, 12.0));
        // Cloud
        setDiffuse("generate");
        // Color
        if (rand.nextBoolean()) {
            // White
            color[0] = 1f;
            color[1] = 1f;
            color[2] = 1f;
            color[3] = 0.7f;
        } else {
            // Random
            color[0] = rand.nextFloat();
            color[1] = rand.nextFloat();
            color[2] = rand.nextFloat();
            color[3] = rand.nextFloat();
        }
        // Params
        setParams(createModelParameters(200L, 1.0, false));
        // Noise
        NoiseComponent nc = new NoiseComponent();
        nc.randomizeAll(rand, rand.nextBoolean(), true);
        setNoise(nc);
    }

    public void copyFrom(CloudComponent other) {
        this.size = other.size;
        this.diffuse = other.diffuse;
        this.params = other.params;
        this.nc = new NoiseComponent();
        if (other.color != null) {
            this.color = Arrays.copyOf(other.color, other.color.length);
        }
        if (other.nc != null)
            this.nc.copyFrom(other.nc);
        else
            this.nc.randomizeAll(new Random());
    }

    public void print(Log log) {
        log.debug("Size: " + size);
        log.debug("---Noise---");
        if (nc != null) {
            nc.print(log);
        }
    }

    @Override
    public void dispose() {
        disposeTextures(manager);
        EventManager.instance.removeAllSubscriptions(this);
    }
}
