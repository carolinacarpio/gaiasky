/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scenegraph;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.data.group.DatasetOptions;
import gaiasky.data.group.IStarGroupDataProvider;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.render.*;
import gaiasky.render.SceneGraphRenderer.RenderGroup;
import gaiasky.render.system.FontRenderSystem;
import gaiasky.render.system.LineRenderSystem;
import gaiasky.scenegraph.camera.CameraManager;
import gaiasky.scenegraph.camera.CameraManager.CameraMode;
import gaiasky.scenegraph.camera.FovCamera;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.component.ModelComponent;
import gaiasky.util.*;
import gaiasky.util.color.ColorUtils;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.gdx.IntModelBatch;
import gaiasky.util.gdx.g2d.ExtSpriteBatch;
import gaiasky.util.gdx.mesh.IntMesh;
import gaiasky.util.gdx.model.IntModel;
import gaiasky.util.gdx.model.IntModelInstance;
import gaiasky.util.gdx.shader.ExtShaderProgram;
import gaiasky.util.math.MathUtilsd;
import gaiasky.util.math.Matrix4d;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;
import net.jafama.FastMath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A particle group which additionally to the xyz position, supports color and
 * magnitude. id x y z pmx pmy pmz appmag absmag col size additional
 *
 * @author tsagrista
 */
public class StarGroup extends ParticleGroup implements ILineRenderable, IStarFocus, IQuadRenderable, IModelRenderable, IObserver {

    /**
     * Star model
     **/
    private static ModelComponent mc;
    // Model transform
    private static Matrix4 modelTransform;

    /**
     * Epoch in julian days
     **/
    private double epoch_jd = AstroUtils.JD_J2015_5;
    /**
     * Current computed epoch time
     **/
    private double currDeltaYears = 0;

    private static void initModel() {
        if (mc == null) {
            Texture tex = new Texture(GlobalConf.data.dataFile("tex/base/star.jpg"));
            Texture lut = new Texture(GlobalConf.data.dataFile("tex/base/lut.jpg"));
            tex.setFilter(TextureFilter.Linear, TextureFilter.Linear);

            Map<String, Object> params = new TreeMap<>();
            params.put("quality", 120l);
            params.put("diameter", 1d);
            params.put("flip", false);

            Pair<IntModel, Map<String, Material>> pair = ModelCache.cache.getModel("sphere", params, Usage.Position | Usage.Normal | Usage.Tangent | Usage.BiNormal | Usage.TextureCoordinates);
            IntModel model = pair.getFirst();
            Material mat = pair.getSecond().get("base");
            mat.clear();
            mat.set(new TextureAttribute(TextureAttribute.Diffuse, tex));
            mat.set(new TextureAttribute(TextureAttribute.Normal, lut));
            // Only to activate view vector (camera position)
            mat.set(new TextureAttribute(TextureAttribute.Specular, lut));
            mat.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA));
            modelTransform = new Matrix4();
            mc = new ModelComponent(false);
            mc.initialize();
            mc.env = new Environment();
            mc.env.set(new ColorAttribute(ColorAttribute.AmbientLight, 1f, 1f, 1f, 1f));
            mc.env.set(new FloatAttribute(FloatAttribute.Shininess, 0f));
            mc.instance = new IntModelInstance(model, modelTransform);
            // Relativistic effects
            if (GlobalConf.runtime.RELATIVISTIC_ABERRATION)
                mc.rec.setUpRelativisticEffectsMaterial(mc.instance.materials);
        }
    }

    /**
     * CLOSEST
     **/
    private final Vector3d closestPm;
    private double closestSize;
    private final float[] closestCol;

    private double modelDist;

    public StarGroup() {
        super();
        closestPm = new Vector3d();
        closestCol = new float[4];
        lastSortTime = -1;
    }

    @SuppressWarnings("unchecked")
    public void initialize() {
        /** Load data **/
        try {
            Class<?> clazz = Class.forName(provider);
            IStarGroupDataProvider provider = (IStarGroupDataProvider) clazz.getConstructor().newInstance();

            if (factor == null)
                factor = 1d;

            // Set data, generate index
            List<ParticleRecord> l = provider.loadData(datafile, factor);
            this.setData(l);

        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e);
            pointData = null;
        }

        computeMeanPosition();
        setLabelPosition();
    }

    @Override
    public void doneLoading(AssetManager manager) {
        super.doneLoading(manager);
        initSortingData();
        initModel();
    }

    /**
     * Returns the data list
     *
     * @return The data list
     */
    @SuppressWarnings("unchecked")
    public List<ParticleRecord> data() {
        return pointData;
    }

    public void setData(List<ParticleRecord> pointData, boolean regenerateIndex) {
        super.setData(pointData, regenerateIndex);
    }

    /**
     * Generates the index (maps star name and id to array index)
     *
     * @param pointData The star data
     * @return An map{string,int} mapping names/ids to indexes
     */
    public Map<String, Integer> generateIndex(Array<ParticleRecord> pointData) {
        Map<String, Integer> index = new HashMap<>();
        int n = pointData.size;
        for (int i = 0; i < n; i++) {
            ParticleRecord sb = pointData.get(i);
            if (sb.names != null) {
                for (String lcname : sb.names) {
                    lcname = lcname.toLowerCase();
                    index.put(lcname, i);
                    String lcid = Long.toString(sb.id).toLowerCase();
                    if (sb.id > 0 && !lcid.equals(lcname)) {
                        index.put(lcid, i);
                    }
                    if (sb.hip() > 0) {
                        String lchip = "hip " + sb.hip();
                        if (!lchip.equals(lcname))
                            index.put(lchip, i);
                    }
                }
            }
        }
        return index;
    }

    public void update(ITimeFrameProvider time, final Vector3d parentTransform, ICamera camera, float opacity) {
        // Fade node visibility
        if (this.isVisible() && active.length > 0) {
            // Delta years
            currDeltaYears = AstroUtils.getMsSince(time.getTime(), epoch_jd) * Nature.MS_TO_Y;

            super.update(time, parentTransform, camera, opacity);

            // Update closest star
            ParticleRecord closestStar = pointData.get(active[0]);

            closestPm.set(closestStar.pmx(), closestStar.pmy(), closestStar.pmz()).scl(currDeltaYears);
            closestAbsolutePos.set(closestStar.x(), closestStar.y(), closestStar.z()).add(closestPm);
            closestPos.set(closestAbsolutePos).sub(camera.getPos());
            closestDist = closestPos.len() - getRadius(active[0]);
            Color c = new Color();
            Color.abgr8888ToColor(c, (float) closestStar.col());
            closestCol[0] = c.r;
            closestCol[1] = c.g;
            closestCol[2] = c.b;
            closestCol[3] = c.a;
            closestSize = getSize(active[0]);
            closestName = closestStar.names[0];
            camera.checkClosestParticle(this);

            // Model dist
            modelDist = 172.4643429 * getRadius(active[0]);
        }

    }

    /**
     * Updates the parameters of the focus, if the focus is active in this group
     *
     * @param time   The time frame provider
     * @param camera The current camera
     */
    public void updateFocus(ITimeFrameProvider time, ICamera camera) {
        ParticleRecord focus = pointData.get(focusIndex);
        Vector3d aux = this.fetchPosition(focus, camera.getPos(), aux3d1.get(), currDeltaYears);

        this.focusPosition.set(aux).add(camera.getPos());
        this.focusDistToCamera = aux.len();
        this.focusSize = getFocusSize();
        this.focusViewAngle = (float) ((getRadius() / this.focusDistToCamera) / camera.getFovFactor());
        this.focusViewAngleApparent = this.focusViewAngle * GlobalConf.scene.STAR_BRIGHTNESS;
    }

    /**
     * Overrides {@link ParticleGroup}'s implementation by actually integrating
     * the position using the proper motion and the given time.
     */
    public Vector3d getPredictedPosition(Vector3d aux, ITimeFrameProvider time, ICamera camera, boolean force) {
        if (time.getDt() == 0 && !force) {
            return getAbsolutePosition(aux);
        } else {
            double deltaYears = AstroUtils.getMsSince(time.getTime(), epoch_jd) * Nature.MS_TO_Y;
            return this.fetchPosition(pointData.get(focusIndex), null, aux, deltaYears);
        }
    }

    @Override
    protected void addToRenderLists(ICamera camera) {
        addToRender(this, RenderGroup.STAR_GROUP);
        addToRender(this, RenderGroup.MODEL_VERT_STAR);
        if (GlobalConf.scene.STAR_GROUP_BILLBOARD_FLAG) {
            addToRender(this, RenderGroup.BILLBOARD_STAR);
        }
        if (SceneGraphRenderer.instance.isOn(ComponentTypes.ComponentType.VelocityVectors)
            //  || SceneGraphRenderer.instance.isOn(ComponentTypes.ComponentType.RecursiveGrid)) {
        ) {
            addToRender(this, RenderGroup.LINE);
        }
        if (renderText()) {
            addToRender(this, RenderGroup.FONT_LABEL);
        }
    }

    /**
     * Billboard rendering
     */
    @Override
    public void render(ExtShaderProgram shader, float alpha, IntMesh mesh, ICamera camera) {
        double thpointTimesFovfactor = GlobalConf.scene.STAR_THRESHOLD_POINT * camera.getFovFactor();
        double thupOverFovfactor = Constants.THRESHOLD_UP / camera.getFovFactor();
        double thdownOverFovfactor = Constants.THRESHOLD_DOWN / camera.getFovFactor();
        double innerRad = 0.006 + GlobalConf.scene.STAR_POINT_SIZE * 0.008;
        alpha = alpha * this.opacity;
        Vector3d cpos = camera.getPos();
        float fovFactor = camera.getFovFactor();

        /** GENERAL UNIFORMS **/
        shader.setUniformf("u_thpoint", (float) thpointTimesFovfactor);
        // Light glow always disabled with star groups
        shader.setUniformi("u_lightScattering", 0);
        shader.setUniformf("u_inner_rad", (float) innerRad);

        /** RENDER ACTUAL STARS **/
        boolean focusRendered = false;
        int n = Math.min(GlobalConf.scene.STAR_GROUP_N_NEAREST, pointData.size());
        for (int i = 0; i < n; i++) {
            renderCloseupStar(active[i], fovFactor, cpos, shader, mesh, thpointTimesFovfactor, thupOverFovfactor, thdownOverFovfactor, alpha);
            focusRendered = focusRendered || active[i] == focusIndex;
        }
        if (focus != null && !focusRendered) {
            renderCloseupStar(focusIndex, fovFactor, cpos, shader, mesh, thpointTimesFovfactor, thupOverFovfactor, thdownOverFovfactor, alpha);
        }

    }

    Color c = new Color();

    private void renderCloseupStar(int idx, float fovFactor, Vector3d cpos, ExtShaderProgram shader, IntMesh mesh, double thpointTimesFovfactor, double thupOverFovfactor, double thdownOverFovfactor, float alpha) {
        ParticleRecord star = pointData.get(idx);
        double size = getSize(idx);
        double radius = size * Constants.STAR_SIZE_FACTOR;
        Vector3d starPos = fetchPosition(star, cpos, aux3d1.get(), currDeltaYears);
        double distToCamera = starPos.len();
        double viewAngle = (radius / distToCamera) / fovFactor;

        Color.abgr8888ToColor(c, getColor(idx));
        if (viewAngle >= thpointTimesFovfactor) {
            double fuzzySize = getFuzzyRenderSize(size, radius, distToCamera, viewAngle, thdownOverFovfactor, thupOverFovfactor);

            Vector3 pos = starPos.put(aux3f3.get());
            shader.setUniformf("u_pos", pos);
            shader.setUniformf("u_size", (float) fuzzySize);

            shader.setUniformf("u_color", c.r, c.g, c.b, alpha);
            shader.setUniformf("u_distance", (float) distToCamera);
            shader.setUniformf("u_apparent_angle", (float) (viewAngle * GlobalConf.scene.STAR_BRIGHTNESS));
            shader.setUniformf("u_radius", (float) radius);

            // Sprite.render
            mesh.render(shader, GL20.GL_TRIANGLES, 0, 6);

        }
    }

    public double getFuzzyRenderSize(double size, double radius, double distToCamera, double viewAngle, double thDown, double thUp) {
        double computedSize = size;
        if (viewAngle > thDown) {
            double dist = distToCamera;
            if (viewAngle > thUp) {
                dist = radius / Constants.THRESHOLD_UP;
            }
            computedSize = (size * (dist / radius) * Constants.THRESHOLD_DOWN);
        }
        // Change the factor at the end here to control the stray light of stars
        computedSize *= GlobalConf.scene.STAR_BRIGHTNESS * 0.2;

        return computedSize;
    }

    /**
     * Model rendering
     */
    @Override
    public void render(IntModelBatch modelBatch, float alpha, double t, RenderingContext rc) {
        mc.touch();
        float opct = (float) MathUtilsd.lint(closestDist, modelDist / 50f, modelDist, 1f, 0f);
        if (alpha * opct > 0) {
            mc.setTransparency(alpha * opct);
            float[] col = closestCol;
            ((ColorAttribute) mc.env.get(ColorAttribute.AmbientLight)).color.set(col[0], col[1], col[2], 1f);
            ((FloatAttribute) mc.env.get(FloatAttribute.Shininess)).value = (float) t;
            // Local transform
            mc.instance.transform.idt().translate((float) closestPos.x, (float) closestPos.y, (float) closestPos.z).scl((float) (getRadius(active[0]) * 2d));
            mc.updateRelativisticEffects(GaiaSky.instance.getICamera());
            mc.updateVelocityBufferUniforms(GaiaSky.instance.getICamera());
            modelBatch.render(mc.instance, mc.env);
        }
    }

    private long getMaxProperMotionLines() {
        int n = GlobalConf.scene.STAR_GROUP_N_NEAREST * 5;
        return Math.min(pointData.size(), GlobalConf.scene.N_PM_STARS > 0 ? GlobalConf.scene.N_PM_STARS : n);
    }

    private long getMaxProjectionLines() {
        return (long) Math.min(GlobalConf.scene.STAR_GROUP_N_NEAREST * 0.02f, pointData.size());
    }

    private boolean rvLines = false;
    private final float[] rgba = new float[4];

    /**
     * Line rendering
     */
    @Override
    public void render(LineRenderSystem renderer, ICamera camera, float alpha) {
        if (SceneGraphRenderer.instance.isOn(ComponentTypes.ComponentType.VelocityVectors)) {
            renderVelocityVectors(renderer, camera, alpha);
        }
        //if (SceneGraphRenderer.instance.isOn(ComponentTypes.ComponentType.RecursiveGrid)) {
        //    renderProjectionLines(renderer, camera, alpha);
        //}
    }

    private void renderVelocityVectors(LineRenderSystem renderer, ICamera camera, float alpha) {
        alpha *= SceneGraphRenderer.alphas[ComponentTypes.ComponentType.VelocityVectors.ordinal()];
        float thPointTimesFovFactor = (float) GlobalConf.scene.STAR_THRESHOLD_POINT * camera.getFovFactor();
        int n = (int) getMaxProperMotionLines();
        for (int i = n - 1; i >= 0; i--) {
            ParticleRecord star = pointData.get(active[i]);
            if ((star.radvel() == 0 && !rvLines) || (star.radvel() != 0 && rvLines)) {
                float radius = (float) (getSize(active[i]) * Constants.STAR_SIZE_FACTOR);
                // Position
                Vector3d lpos = fetchPosition(star, camera.getPos(), aux3d1.get(), currDeltaYears);
                // Proper motion
                Vector3d pm = aux3d2.get().set(star.pmx(), star.pmy(), star.pmz()).scl(currDeltaYears);
                // Rest of attributes
                float distToCamera = (float) lpos.len();
                float viewAngle = (float) (((radius / distToCamera) / camera.getFovFactor()) * GlobalConf.scene.STAR_BRIGHTNESS);
                if (viewAngle >= thPointTimesFovFactor / GlobalConf.scene.PM_NUM_FACTOR && (star.pmx() != 0 || star.pmy() != 0 || star.pmz() != 0)) {
                    Vector3d p1 = aux3d1.get().set(star.x() + pm.x, star.y() + pm.y, star.z() + pm.z).sub(camera.getPos());
                    Vector3d ppm = aux3d2.get().set(star.pmx(), star.pmy(), star.pmz()).scl(GlobalConf.scene.PM_LEN_FACTOR);
                    double p1p2len = ppm.len();
                    Vector3d p2 = aux3d3.get().set(ppm).add(p1);

                    // Max speed in km/s, to normalize
                    double maxSpeedKms = 100;
                    float r, g, b;
                    switch (GlobalConf.scene.PM_COLOR_MODE) {
                    case 0:
                    default:
                        // DIRECTION
                        // Normalize, each component is in [-1:1], map to [0:1] and to a color channel
                        ppm.nor();
                        r = (float) (ppm.x + 1d) / 2f;
                        g = (float) (ppm.y + 1d) / 2f;
                        b = (float) (ppm.z + 1d) / 2f;
                        break;
                    case 1:
                        // LENGTH
                        ppm.set(star.pmx(), star.pmy(), star.pmz());
                        // Units/year to Km/s
                        ppm.scl(Constants.U_TO_KM / Nature.Y_TO_S);
                        double len = MathUtilsd.clamp(ppm.len(), 0d, maxSpeedKms) / maxSpeedKms;
                        ColorUtils.colormap_long_rainbow((float) (1 - len), rgba);
                        r = rgba[0];
                        g = rgba[1];
                        b = rgba[2];
                        break;
                    case 2:
                        // HAS RADIAL VELOCITY - blue: stars with RV, red: stars without RV
                        if (star.radvel() != 0) {
                            r = ColorUtils.gBlue[0] + 0.2f;
                            g = ColorUtils.gBlue[1] + 0.4f;
                            b = ColorUtils.gBlue[2] + 0.4f;
                        } else {
                            r = ColorUtils.gRed[0] + 0.4f;
                            g = ColorUtils.gRed[1] + 0.2f;
                            b = ColorUtils.gRed[2] + 0.2f;
                        }
                        break;
                    case 3:
                        // REDSHIFT from Sun - blue: -100 Km/s, red: 100 Km/s
                        double rav = star.radvel();
                        if (rav != 0) {
                            double max = maxSpeedKms;
                            // rv in [0:1]
                            double rv = ((MathUtilsd.clamp(rav, -max, max) / max) + 1) / 2;
                            ColorUtils.colormap_blue_white_red((float) rv, rgba);
                            r = rgba[0];
                            g = rgba[1];
                            b = rgba[2];
                        } else {
                            r = g = b = 1;
                        }
                        break;
                    case 4:
                        // REDSHIFT from Camera - blue: -100 Km/s, red: 100 Km/s
                        if (ppm.len2() != 0) {
                            double max = maxSpeedKms;
                            ppm.set(star.pmx(), star.pmy(), star.pmz());
                            // Units/year to Km/s
                            ppm.scl(Constants.U_TO_KM / Nature.Y_TO_S);
                            Vector3d camstar = aux3d4.get().set(p1);
                            double pr = ppm.dot(camstar.nor());
                            double projection = ((MathUtilsd.clamp(pr, -max, max) / max) + 1) / 2;
                            ColorUtils.colormap_blue_white_red((float) projection, rgba);
                            r = rgba[0];
                            g = rgba[1];
                            b = rgba[2];
                        } else {
                            r = g = b = 1;
                        }
                        break;
                    case 5:
                        // SINGLE COLOR
                        r = ColorUtils.gBlue[0] + 0.2f;
                        g = ColorUtils.gBlue[1] + 0.4f;
                        b = ColorUtils.gBlue[2] + 0.4f;
                        break;
                    }

                    // Clamp
                    r = MathUtilsd.clamp(r, 0, 1);
                    g = MathUtilsd.clamp(g, 0, 1);
                    b = MathUtilsd.clamp(b, 0, 1);

                    renderer.addLine(this, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, r, g, b, alpha * this.opacity);
                    if (GlobalConf.scene.PM_ARROWHEADS) {
                        // Add Arrow cap
                        Vector3d p3 = aux3d2.get().set(ppm).nor().scl(p1p2len * .86).add(p1);
                        p3.rotate(p2, 30);
                        renderer.addLine(this, p3.x, p3.y, p3.z, p2.x, p2.y, p2.z, r, g, b, alpha * this.opacity);
                        p3.rotate(p2, -60);
                        renderer.addLine(this, p3.x, p3.y, p3.z, p2.x, p2.y, p2.z, r, g, b, alpha * this.opacity);
                    }
                }
            }
        }
        rvLines = !rvLines;
    }

    RecursiveGrid rg = null;
    Matrix4d mat4daux = new Matrix4d();
    Vector3d a = new Vector3d();
    Vector3d b = new Vector3d();
    Vector3d p01 = new Vector3d();

    private void renderProjectionLines(LineRenderSystem renderer, ICamera camera, float alpha) {
        if (rg == null) {
            SceneGraphNode node = GaiaSky.instance.sg.getNode("Recursive Grid");
            if (node != null)
                rg = (RecursiveGrid) node;
        }
        if (rg != null) {
            //alpha *= SceneGraphRenderer.alphas[ComponentTypes.ComponentType.RecursiveGrid.ordinal()];
            float thOverFactor = (float) (GlobalConf.scene.STAR_THRESHOLD_POINT / GlobalConf.scene.LABEL_NUMBER_FACTOR / camera.getFovFactor());
            int n = (int) getMaxProjectionLines();
            for (int i = n; i >= 0; i--) {
                ParticleRecord star = pointData.get(active[i]);
                Vector3d fpos = fetchPosition(star, camera.getPos(), aux3d3.get(), currDeltaYears);
                float distToCamera = (float) fpos.len();
                float radius = (float) getRadius(active[i]);
                float viewAngle = (float) (((radius / distToCamera) / camera.getFovFactor()) * GlobalConf.scene.STAR_BRIGHTNESS * 1.5f);

                if (viewAngle >= thOverFactor && camera.isVisible(GaiaSky.instance.time, viewAngle, fpos, distToCamera) && distToCamera > radius * 100) {
                    Vector3d cpos = aux3d4.get();
                    getCFPos(rg, cpos, fpos, camera);

                    // Line in Y
                    getYLine(rg, a, b, cpos, fpos);
                    double d02 = p01.set(a).sub(b).len();
                    p01.setLength(d02 / 2d).add(b);

                    float[] col = rg.textColour();

                    renderer.addLine(this, a.x, a.y, a.z, b.x, b.y, b.z, col[0], col[1], col[2], col[3] * alpha * opacity);
                }

            }
        }
    }

    private void getCFPos(RecursiveGrid rg, Vector3d cpos, Vector3d fpos, ICamera camera) {
        Matrix4d inv = rg.getCoordinateSystemd();
        Matrix4d trf = mat4daux.set(inv).inv();
        camera.getPos().put(cpos).mul(trf);
        fpos.mul(trf);
    }

    private void getYLine(RecursiveGrid rg, Vector3d a, Vector3d b, Vector3d cpos, Vector3d fpos) {
        Matrix4d inv = rg.getCoordinateSystemd();
        a.set(fpos.x, -cpos.y, fpos.z);
        b.set(fpos.x, fpos.y, fpos.z);
        // Back to equatorial
        a.mul(inv);
        b.mul(inv);
    }

    @Override
    public float getLineWidth() {
        return 0.6f;
    }

    @Override
    public int getGlPrimitive() {
        return GL20.GL_LINES;
    }

    /**
     * Label rendering
     */
    @Override
    public void render(ExtSpriteBatch batch, ExtShaderProgram shader, FontRenderSystem sys, RenderingContext rc, ICamera camera) {
        float thOverFactor = (float) (GlobalConf.scene.STAR_THRESHOLD_POINT / GlobalConf.scene.LABEL_NUMBER_FACTOR / camera.getFovFactor());
        Vector3d cpos = camera.getPos();

        int n = Math.min(pointData.size(), GlobalConf.scene.STAR_GROUP_N_NEAREST);
        if (camera.getCurrent() instanceof FovCamera) {
            for (int i = 0; i < n; i++) {
                ParticleRecord star = pointData.get(active[i]);
                Vector3d starPosition = fetchPosition(star, cpos, aux3d1.get(), currDeltaYears);
                float distToCamera = (float) starPosition.len();
                float radius = (float) getRadius(active[i]);
                float viewAngle = (float) (((radius / distToCamera) / camera.getFovFactor()) * GlobalConf.scene.STAR_BRIGHTNESS * 6f);

                if (camera.isVisible(GaiaSky.instance.time, viewAngle, starPosition, distToCamera)) {
                    render2DLabel(batch, shader, rc, sys.font2d, camera, star.names[0], starPosition);
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                ParticleRecord star = pointData.get(active[i]);
                Vector3d starPosition = fetchPosition(star, cpos, aux3d1.get(), currDeltaYears);
                float distToCamera = (float) starPosition.len();
                float radius = (float) getRadius(active[i]);
                float viewAngle = (float) (((radius / distToCamera) / camera.getFovFactor()) * GlobalConf.scene.STAR_BRIGHTNESS * 1.5f);

                if (viewAngle >= thOverFactor && camera.isVisible(GaiaSky.instance.time, viewAngle, starPosition, distToCamera) && distToCamera > radius * 100) {
                    textPosition(camera, starPosition, distToCamera, radius);

                    shader.setUniformf("u_viewAngle", viewAngle);
                    shader.setUniformf("u_viewAnglePow", 1f);
                    shader.setUniformf("u_thOverFactor", thOverFactor);
                    shader.setUniformf("u_thOverFactorScl", camera.getFovFactor());
                    float textSize = (float) FastMath.tanh(viewAngle) * distToCamera * 1e5f;
                    float alpha = Math.min((float) FastMath.atan(textSize / distToCamera), 1.e-3f);
                    textSize = (float) FastMath.tan(alpha) * distToCamera * 0.5f;
                    render3DLabel(batch, shader, sys.fontDistanceField, camera, rc, star.names[0], starPosition, distToCamera, textScale() * camera.getFovFactor(), textSize * camera.getFovFactor());

                }
            }
        }
    }

    public double getFocusSize() {
        return focus.dataF[ParticleRecord.I_FSIZE];
    }

    // Radius in stars is different!
    public double getRadius() {
        return getSize() * Constants.STAR_SIZE_FACTOR;
    }

    // Radius in stars is different!
    public double getRadius(int i) {
        return getSize(i) * Constants.STAR_SIZE_FACTOR;
    }

    public float getAppmag() {
        return focus.dataF[ParticleRecord.I_FAPPMAG];
    }

    public float getAbsmag() {
        return focus.dataF[ParticleRecord.I_FABSMAG];
    }

    public long getId() {
        if (focus != null)
            return focus.id;
        else
            return -1;
    }

    @Override
    public double getMuAlpha() {
        if (focus != null)
            return focus.dataD[ParticleRecord.I_MUALPHA];
        else
            return 0;
    }

    @Override
    public double getMuDelta() {
        if (focus != null)
            return focus.dataD[ParticleRecord.I_MUDELTA];
        else
            return 0;
    }

    @Override
    public double getRadialVelocity() {
        if (focus != null)
            return focus.dataD[ParticleRecord.I_RADVEL];
        else
            return 0;
    }

    /**
     * Returns the size of the particle at index i
     *
     * @param i The index
     * @return The size
     */
    public double getSize(int i) {
        return pointData.get(i).size();
    }

    @Override
    public void notify(final Events event, final Object... data) {
        // Super handles FOCUS_CHANGED and CAMERA_MOTION_UPDATED event
        super.notify(event, data);
        switch (event) {
        default:
            break;
        }
    }

    @Override
    public int getCatalogSource() {
        return 1;
    }

    @Override
    public int getHip() {
        if (focus != null && focus.dataF[ParticleRecord.I_FHIP] > 0)
            return (int) focus.dataF[ParticleRecord.I_FHIP];
        return -1;
    }

    @Override
    public long getCandidateId() {
        return pointData.get(candidateFocusIndex).id;
    }

    @Override
    public String getCandidateName() {
        return pointData.get(candidateFocusIndex).names[0];
    }

    @Override
    public double getCandidateViewAngleApparent() {
        if (candidateFocusIndex >= 0) {
            ParticleRecord candidate = pointData.get(candidateFocusIndex);
            Vector3d aux = candidate.pos(aux3d1.get());
            ICamera camera = GaiaSky.instance.getICamera();
            double va = (float) ((candidate.radius() / aux.sub(camera.getPos()).len()) / camera.getFovFactor());
            return va * GlobalConf.scene.STAR_BRIGHTNESS;
        } else {
            return -1;
        }
    }

    @Override
    public double getClosestDistToCamera() {
        return this.closestDist;
    }

    @Override
    public String getClosestName() {
        return this.closestName;
    }

    @Override
    public double getClosestSize() {
        return this.closestSize;
    }

    @Override
    public Vector3d getClosestPos(Vector3d out) {
        return out.set(this.closestPos);
    }

    @Override
    public Vector3d getClosestAbsolutePos(Vector3d out) {
        return out.set(this.closestAbsolutePos);
    }

    @Override
    public float[] getClosestCol() {
        return this.closestCol;
    }

    @Override
    public boolean hasAtmosphere() {
        return false;
    }

    @Override
    public IFocus getFocus(String name) {
        if (index.containsKey(name))
            candidateFocusIndex = index.get(name);
        else
            candidateFocusIndex = -1;
        return this;
    }

    public Vector3d getAbsolutePosition(String name, Vector3d aux) {
        if (index.containsKey(name)) {
            int idx = index.get(name);
            ParticleRecord sb = pointData.get(idx);
            fetchPosition(sb, null, aux, currDeltaYears);
            return aux;
        } else {
            return null;
        }
    }

    @Override
    protected Vector3d fetchPosition(ParticleRecord pb, Vector3d campos, Vector3d destination, double deltaYears) {
        ParticleRecord sb = pb;
        Vector3d pm = aux3d2.get().set(sb.pmx(), sb.pmy(), sb.pmz()).scl(deltaYears);
        if (campos != null && !campos.hasNaN())
            return destination.set(sb.x(), sb.y(), sb.z()).sub(campos).add(pm);
        else
            return destination.set(sb.x(), sb.y(), sb.z()).add(pm);
    }

    @Override
    protected double getDeltaYears() {
        return currDeltaYears;
    }

    /**
     * Sets the epoch to use for the stars in this group
     *
     * @param epochJd The epoch in julian days (days since January 1, 4713 BCE)
     */
    public void setEpoch(Double epochJd) {
        this.epoch_jd = epochJd;
    }

    /**
     * Returns the epoch in Julian Days used for the stars in this group
     *
     * @return The epoch in julian days
     */
    public Double getEpoch() {
        return this.epoch_jd;
    }

    @Override
    public void dispose() {
        this.disposed = true;
        sg.remove(this, true);
        // Unsubscribe from all events
        EventManager.instance.removeAllSubscriptions(this);
        // Dispose of GPU datOLO
        EventManager.instance.post(Events.DISPOSE_STAR_GROUP_GPU_MESH, this.offset);
        // Data to be gc'd
        this.pointData = null;
        // Remove focus if needed
        CameraManager cam = GaiaSky.instance.getCameraManager();
        if (cam != null && cam.getFocus() != null && cam.getFocus() == this) {
            this.setFocusIndex(-1);
            EventManager.instance.post(Events.CAMERA_MODE_CMD, CameraMode.FREE_MODE);
        }
    }

    public float getColor(int index) {
        return highlighted ? Color.toFloatBits(hlc[0], hlc[1], hlc[2], hlc[3]) : (float) pointData.get(index).col();
    }

    /**
     * Creates a default star group with some parameters, given the name and data
     *
     * @param name The name of the star group. Any occurrence of '%%SGID%%' will be replaced with the id of the star group
     * @param data The data of the star group
     * @param dops The dataset options
     * @return A new star group with the given parameters
     */
    public static StarGroup getStarGroup(String name, List<ParticleRecord> data, DatasetOptions dops) {
        double[] fadeIn = dops == null || dops.fadeIn == null ? null : dops.fadeIn;
        double[] fadeOut = dops == null || dops.fadeOut == null ? null : dops.fadeOut;
        double[] labelColor = dops == null || dops.labelColor == null ? new double[] { 1.0, 1.0, 1.0, 1.0 } : dops.labelColor;

        StarGroup sg = new StarGroup();
        sg.setName(name.replace("%%SGID%%", Long.toString(sg.id)));
        sg.setParent("Universe");
        sg.setFadein(fadeIn);
        sg.setFadeout(fadeOut);
        sg.setLabelcolor(labelColor);
        sg.setColor(new double[] { 1.0, 1.0, 1.0, 0.25 });
        sg.setSize(6.0);
        sg.setLabelposition(new double[] { 0.0, -5.0e7, -4e8 });
        sg.setCt("Stars");
        sg.setData(data);
        sg.doneLoading(null);
        return sg;
    }

    /**
     * Creates a default star group with some sane parameters, given the name and the data
     *
     * @param name The name of the star group. Any occurrence of '%%SGID%%' in name will be replaced with the id of the star group
     * @param data The data of the star group
     * @return A new star group with sane parameters
     */
    public static StarGroup getDefaultStarGroup(String name, List<ParticleRecord> data) {
        return getDefaultStarGroup(name, data, true);
    }

    /**
     * Creates a default star group with some sane parameters, given the name and the data
     *
     * @param name     The name of the star group. Any occurrence of '%%SGID%%' in name will be replaced with the id of the star group
     * @param data     The data of the star group
     * @param fullInit Initializes the group right away
     * @return A new star group with sane parameters
     */
    public static StarGroup getDefaultStarGroup(String name, List<ParticleRecord> data, boolean fullInit) {
        StarGroup sg = new StarGroup();
        sg.setName(name.replace("%%SGID%%", Long.toString(sg.id)));
        sg.setParent("Universe");
        sg.setFadeout(new double[] { 2e3, 1e5 });
        sg.setLabelcolor(new double[] { 1.0, 1.0, 1.0, 1.0 });
        sg.setColor(new double[] { 1.0, 1.0, 1.0, 0.25 });
        sg.setSize(6.0);
        sg.setLabelposition(new double[] { 0.0, -5.0e7, -4e8 });
        sg.setCt("Stars");
        sg.setData(data);
        if (fullInit)
            sg.doneLoading(null);
        return sg;
    }

    /**
     * Updates the additional information array, to use for sorting.
     * In stars, we need to take into account the proper motion and the brightness.
     *
     * @param time   The current time frame provider
     * @param camera The camera
     */
    public void updateMetadata(ITimeFrameProvider time, ICamera camera) {
        Vector3d camPos = camera.getPos();
        double deltaYears = AstroUtils.getMsSince(time.getTime(), epoch_jd) * Nature.MS_TO_Y;
        if (pointData != null) {
            int n = pointData.size();
            for (int i = 0; i < n; i++) {
                ParticleRecord d = pointData.get(i);

                // Pm
                Vector3d dx = aux3d2.get().set(d.pmx(), d.pmy(), d.pmz()).scl(deltaYears);
                // Pos
                Vector3d x = aux3d1.get().set(d.x(), d.y(), d.z()).add(dx);

                metadata[i] = filter(i) ? (-(((d.size() * Constants.STAR_SIZE_FACTOR) / camPos.dst(x)) / camera.getFovFactor()) * GlobalConf.scene.STAR_BRIGHTNESS) : Double.MAX_VALUE;
            }
        }
    }
}
