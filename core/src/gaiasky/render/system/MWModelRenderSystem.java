/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.render.IRenderable;
import gaiasky.render.SceneGraphRenderer.RenderGroup;
import gaiasky.scenegraph.MilkyWay;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.util.Constants;
import gaiasky.util.GlobalResources;
import gaiasky.util.Settings;
import gaiasky.util.Settings.GraphicsQuality;
import gaiasky.util.gdx.mesh.IntMesh;
import gaiasky.util.gdx.shader.ExtShaderProgram;
import gaiasky.util.math.MathUtilsd;
import gaiasky.util.math.StdRandom;
import gaiasky.util.tree.LoadStatus;
import org.lwjgl.opengl.GL30;

import java.util.List;

public class MWModelRenderSystem extends ImmediateRenderSystem implements IObserver {
    private static final String texFolder = "data/galaxy/sprites/";

    private final Vector3 aux3f1;
    private MeshData dust, bulge, stars, hii, gas;
    private GpuData dustA, bulgeA, starsA, hiiA, gasA;

    private TextureArray ta;
    // Max sizes for dust, star, bulge, gas and hii
    private final float[] maxSizes;

    private enum PType {
        DUST(0, new int[] { 3, 5, 7 }),
        STAR(1, new int[] { 0, 1 }),
        BULGE(2, new int[] { 0, 1 }),
        GAS(3, new int[] { 0, 1, 2, 3, 4, 5, 6, 7 }),
        HII(4, new int[] { 2, 3, 4, 5, 6, 7 });

        // The particle type id
        public int id;
        // The layers it can use
        public int[] layers;
        // The modulus to skip particles, usually 0
        public int modulus;

        PType(int id, int[] layers, int modulus) {
            this.id = id;
            this.layers = layers;
            this.modulus = modulus;
        }

        PType(int id, int[] layers) {
            this(id, layers, 0);
        }

    }

    public MWModelRenderSystem(RenderGroup rg, float[] alphas, ExtShaderProgram[] starShaders) {
        super(rg, alphas, starShaders);
        aux3f1 = new Vector3();
        this.maxSizes = new float[PType.values().length];
        initializeMaxSizes(Settings.settings.graphics.quality);
        EventManager.instance.subscribe(this, Events.GRAPHICS_QUALITY_UPDATED);
    }

    /**
     * Initializes the maximum size per component regarding the given graphics quality
     *
     * @param gq The graphics quality
     */
    private void initializeMaxSizes(GraphicsQuality gq) {
        if (gq.isUltra()) {
            this.maxSizes[PType.DUST.ordinal()] = 4000f;
            this.maxSizes[PType.STAR.ordinal()] = 150f;
            this.maxSizes[PType.BULGE.ordinal()] = 300f;
            this.maxSizes[PType.GAS.ordinal()] = 4000f;
            this.maxSizes[PType.HII.ordinal()] = 4000f;
        } else if (gq.isHigh()) {
            this.maxSizes[PType.DUST.ordinal()] = 1000f;
            this.maxSizes[PType.STAR.ordinal()] = 20f;
            this.maxSizes[PType.BULGE.ordinal()] = 250f;
            this.maxSizes[PType.GAS.ordinal()] = 1200f;
            this.maxSizes[PType.HII.ordinal()] = 400f;
        } else if (gq.isNormal()) {
            this.maxSizes[PType.DUST.ordinal()] = 60f;
            this.maxSizes[PType.STAR.ordinal()] = 10f;
            this.maxSizes[PType.BULGE.ordinal()] = 60f;
            this.maxSizes[PType.GAS.ordinal()] = 120f;
            this.maxSizes[PType.HII.ordinal()] = 70f;
        } else if (gq.isLow()) {
            this.maxSizes[PType.DUST.ordinal()] = 50f;
            this.maxSizes[PType.STAR.ordinal()] = 10f;
            this.maxSizes[PType.BULGE.ordinal()] = 50f;
            this.maxSizes[PType.GAS.ordinal()] = 100f;
            this.maxSizes[PType.HII.ordinal()] = 60f;
        }
    }

    @Override
    protected void initShaderProgram() {
        Gdx.gl.glEnable(GL30.GL_POINT_SPRITE);
        Gdx.gl.glEnable(GL30.GL_VERTEX_PROGRAM_POINT_SIZE);

        for (ExtShaderProgram shaderProgram : programs) {
            shaderProgram.begin();
            shaderProgram.setUniformf("u_pointAlphaMin", 0.1f);
            shaderProgram.setUniformf("u_pointAlphaMax", 1.0f);
            shaderProgram.end();
        }
        initializeTextureArray(Settings.settings.graphics.quality);
    }

    private void initializeTextureArray(GraphicsQuality gq) {
        // Create TextureArray with 8 layers
        FileHandle s00 = unpack("star-00" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle s01 = unpack("star-01" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle d00 = unpack("dust-00" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle d01 = unpack("dust-01" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle d02 = unpack("dust-02" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle d03 = unpack("dust-03" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle d04 = unpack("dust-04" + Constants.STAR_SUBSTITUTE + ".png", gq);
        FileHandle d05 = unpack("dust-05" + Constants.STAR_SUBSTITUTE + ".png", gq);
        ta = new TextureArray(true, Pixmap.Format.RGBA8888, s00, s01, d00, d01, d02, d03, d04, d05);
        ta.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    private FileHandle unpack(String texName, GraphicsQuality gq) {
        return Settings.settings.data.dataFileHandle(GlobalResources.unpackAssetPath(texFolder + texName, gq));
    }

    private void disposeTextureArray() {
        ta.dispose();
    }

    @Override
    public void dispose() {
        super.dispose();
        disposeTextureArray();
    }

    @Override
    protected void initVertices() {
    }

    private MeshData toMeshData(GpuData ad, MeshData md) {
        if (ad != null && ad.vertices != null) {
            if (md != null) {
                md.dispose();
            }
            md = new MeshData();
            VertexAttribute[] attribs = buildVertexAttributes();
            md.mesh = new IntMesh(true, ad.vertices.length / 6, 0, attribs);
            md.vertexSize = md.mesh.getVertexAttributes().vertexSize / 4;
            md.colorOffset = md.mesh.getVertexAttribute(Usage.ColorPacked) != null ? md.mesh.getVertexAttribute(Usage.ColorPacked).offset / 4 : 0;
            md.vertexIdx = ad.vertexIdx;
            md.mesh.setVertices(ad.vertices, 0, md.vertexIdx);

            ad.vertices = null;
            return md;
        }
        return null;
    }

    /**
     * Converts a given list of particle records to GPU data
     * 0 - dust
     * 1 - star
     * 2 - bulge
     * 3 - gas
     * 4 - hii
     *
     * @param data List with the particle records
     * @param cg   The color generator
     * @param type The type
     * @return The GPU data object
     */
    private GpuData convertDataToGpu(List<IParticleRecord> data, ColorGenerator cg, PType type) {
        GpuData ad = new GpuData();

        int vertexSize = 3 + 1 + 3;
        int colorOffset = 3;
        int additionalOffset = 4;
        ad.vertices = new float[data.size() * vertexSize];

        int nLayers = type.layers.length;

        int i = 0;
        for (IParticleRecord particle : data) {
            if (type.modulus == 0 || i % type.modulus == 0) {
                // COLOR
                double[] doubleData = particle.rawDoubleData();
                float[] col = doubleData.length >= 7 ? new float[] { (float) doubleData[4], (float) doubleData[5], (float) doubleData[6] } : cg.generateColor();
                col[0] = MathUtilsd.clamp(col[0], 0f, 1f);
                col[1] = MathUtilsd.clamp(col[1], 0f, 1f);
                col[2] = MathUtilsd.clamp(col[2], 0f, 1f);
                ad.vertices[ad.vertexIdx + colorOffset] = Color.toFloatBits(col[0], col[1], col[2], 1f);

                // SIZE, TYPE, TEX LAYER
                double starSize = particle.size();
                ad.vertices[ad.vertexIdx + additionalOffset] = (float) starSize;
                ad.vertices[ad.vertexIdx + additionalOffset + 1] = (float) type.id;
                ad.vertices[ad.vertexIdx + additionalOffset + 2] = (float) type.layers[StdRandom.uniform(nLayers)];

                // POSITION
                aux3f1.set((float) particle.x(), (float) particle.y(), (float) particle.z());
                final int idx = ad.vertexIdx;
                ad.vertices[idx] = aux3f1.x;
                ad.vertices[idx + 1] = aux3f1.y;
                ad.vertices[idx + 2] = aux3f1.z;

                ad.vertexIdx += vertexSize;
            }
            i++;
        }
        return ad;
    }

    private void convertDataToGpuFormat(MilkyWay mw) {
        logger.info("Converting galaxy data to VRAM format");
        StarColorGenerator scg = new StarColorGenerator();
        bulgeA = convertDataToGpu(mw.bulgeData, scg, PType.BULGE);
        starsA = convertDataToGpu(mw.starData, scg, PType.STAR);
        hiiA = convertDataToGpu(mw.hiiData, scg, PType.HII);
        gasA = convertDataToGpu(mw.gasData, scg, PType.GAS);
        dustA = convertDataToGpu(mw.dustData, new DustColorGenerator(), PType.DUST);
    }

    private void streamToGpu() {
        logger.info("Streaming galaxy to GPU");
        bulge = toMeshData(bulgeA, bulge);
        bulgeA = null;

        stars = toMeshData(starsA, stars);
        starsA = null;

        hii = toMeshData(hiiA, hii);
        hiiA = null;

        gas = toMeshData(gasA, gas);
        gasA = null;

        dust = toMeshData(dustA, dust);
        dustA = null;
    }

    @Override
    public void renderStud(Array<IRenderable> renderables, ICamera camera, double t) {
        if (renderables.size > 0) {
            MilkyWay mw = (MilkyWay) renderables.get(0);

            switch (mw.status) {
            case NOT_LOADED:
                // PRELOAD
                mw.status = LoadStatus.LOADING;
                Thread loader = new Thread(() -> {
                    convertDataToGpuFormat(mw);
                    mw.status = LoadStatus.READY;
                });
                loader.start();
                break;
            case READY:
                // TO GPU
                streamToGpu();
                mw.status = LoadStatus.LOADED;
                break;
            case LOADED:
                // RENDER
                float alpha = getAlpha(mw);
                if (alpha > 0) {
                    ExtShaderProgram shaderProgram = getShaderProgram();

                    shaderProgram.begin();

                    ta.bind(0);
                    shaderProgram.setUniformi("u_textures", 0);

                    shaderProgram.setUniformMatrix("u_projModelView", camera.getCamera().combined);
                    shaderProgram.setUniformf("u_camPos", camera.getCurrent().getPos().put(aux3f1));
                    shaderProgram.setUniformf("u_alpha", mw.opacity * alpha);
                    shaderProgram.setUniformf("u_ar", Settings.settings.program.modeStereo.isStereoHalfWidth() ? 2f : 1f);
                    shaderProgram.setUniformf("u_edges", mw.getFadeIn().y, mw.getFadeOut().y);
                    double pointScaleFactor = ((Settings.settings.program.modeStereo.isStereoFullWidth() ? 1f : 2f) * rc.scaleFactor) / camera.getFovFactor();

                    // Rel, grav, z-buffer
                    addEffectsUniforms(shaderProgram, camera);

                    // General settings for all
                    Gdx.gl20.glEnable(GL20.GL_DEPTH_TEST);
                    Gdx.gl20.glEnable(GL20.GL_BLEND);

                    // PART 1: DUST - depth enabled - depth writes
                    Gdx.gl20.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    Gdx.gl20.glDepthMask(true);

                    //  Dust
                    shaderProgram.setUniformf("u_maxPointSize", maxSizes[PType.DUST.ordinal()]);
                    shaderProgram.setUniformf("u_sizeFactor", (float) (1.6e13 * pointScaleFactor));
                    shaderProgram.setUniformf("u_intensity", 2.2f);
                    dust.mesh.render(shaderProgram, ShapeType.Point.getGlType());

                    // PART2: BULGE + STARS + HII + GAS - depth enabled - no depth writes
                    Gdx.gl20.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
                    Gdx.gl20.glDepthMask(false);

                    // HII
                    shaderProgram.setUniformf("u_maxPointSize", maxSizes[PType.HII.ordinal()]);
                    shaderProgram.setUniformf("u_sizeFactor", (float) (7e11 * pointScaleFactor));
                    shaderProgram.setUniformf("u_intensity", 1.2f);
                    hii.mesh.render(shaderProgram, ShapeType.Point.getGlType());

                    // Gas
                    shaderProgram.setUniformf("u_maxPointSize", maxSizes[PType.GAS.ordinal()]);
                    shaderProgram.setUniformf("u_sizeFactor", (float) (1.9e12 * pointScaleFactor));
                    shaderProgram.setUniformf("u_intensity", 0.8f);
                    gas.mesh.render(shaderProgram, ShapeType.Point.getGlType());

                    // Bulge
                    shaderProgram.setUniformf("u_maxPointSize", maxSizes[PType.BULGE.ordinal()]);
                    shaderProgram.setUniformf("u_sizeFactor", (float) (2e12 * pointScaleFactor));
                    shaderProgram.setUniformf("u_intensity", 0.5f);
                    bulge.mesh.render(shaderProgram, ShapeType.Point.getGlType());

                    // Stars
                    shaderProgram.setUniformf("u_maxPointSize", maxSizes[PType.STAR.ordinal()]);
                    shaderProgram.setUniformf("u_sizeFactor", (float) (0.3e11 * pointScaleFactor));
                    shaderProgram.setUniformf("u_intensity", 2.5f);
                    stars.mesh.render(shaderProgram, ShapeType.Point.getGlType());

                    shaderProgram.end();

                }
                break;
            }
        }

    }

    protected VertexAttribute[] buildVertexAttributes() {
        Array<VertexAttribute> attribs = new Array<>();
        attribs.add(new VertexAttribute(Usage.Position, 3, ExtShaderProgram.POSITION_ATTRIBUTE));
        attribs.add(new VertexAttribute(Usage.ColorPacked, 4, ExtShaderProgram.COLOR_ATTRIBUTE));
        attribs.add(new VertexAttribute(Usage.Generic, 3, "a_additional"));

        VertexAttribute[] array = new VertexAttribute[attribs.size];
        for (int i = 0; i < attribs.size; i++)
            array[i] = attribs.get(i);
        return array;
    }

    @Override
    public void notify(final Events event, final Object... data) {
        if (event == Events.GRAPHICS_QUALITY_UPDATED) {
            GraphicsQuality gq = (GraphicsQuality) data[0];
            GaiaSky.postRunnable(() -> {
                disposeTextureArray();
                initializeTextureArray(gq);
                initializeMaxSizes(gq);
            });
        }
    }

    private interface ColorGenerator {
        float[] generateColor();
    }

    private static class StarColorGenerator implements ColorGenerator {
        public float[] generateColor() {
            float r = (float) StdRandom.gaussian() * 0.15f;
            if (StdRandom.uniform(2) == 0) {
                // Blue/white star
                return new float[] { 0.95f - r, 0.8f - r, 0.6f };
            } else {
                // Red/white star
                return new float[] { 0.95f, 0.8f - r, 0.6f - r };
            }
        }
    }

    private static class DustColorGenerator implements ColorGenerator {
        @Override
        public float[] generateColor() {
            float r = (float) Math.abs(StdRandom.uniform() * 0.19);
            return new float[] { r, r, r };
        }
    }

    private static class GpuData {
        float[] vertices;
        int vertexIdx;
    }
}
