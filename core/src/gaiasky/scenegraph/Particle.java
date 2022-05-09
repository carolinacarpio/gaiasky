/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scenegraph;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector3;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.ComponentTypes;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.api.ILineRenderable;
import gaiasky.render.api.IRenderable;
import gaiasky.render.RenderingContext;
import gaiasky.render.RenderGroup;
import gaiasky.render.system.LineRenderSystem;
import gaiasky.scenegraph.camera.FovCamera;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.util.Constants;
import gaiasky.util.Nature;
import gaiasky.util.Settings;
import gaiasky.util.color.ColorUtils;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.coord.Coordinates;
import gaiasky.util.gdx.IntModelBatch;
import gaiasky.util.math.Vector2d;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;
import net.jafama.FastMath;

import java.util.Random;

/**
 * A single point particle.
 */
public class Particle extends CelestialBody implements IStarFocus, ILineRenderable {

    protected static final float DISC_FACTOR = 1.5f;

    private static final Random rnd = new Random();

    protected static float thpointTimesFovfactor;
    protected static float thupOverFovfactor;
    protected static float thdownOverFovfactor;
    protected static float innerRad;
    protected static float fovFactor;
    protected static ParamUpdater paramUpdater;

    protected static class ParamUpdater implements IObserver {
        public ParamUpdater() {
            super();
            EventManager.instance.subscribe(this, Event.FOV_CHANGE_NOTIFICATION, Event.STAR_POINT_SIZE_CMD);
        }

        @Override
        public void notify(final Event event, Object source, final Object... data) {
            switch (event) {
            case FOV_CHANGE_NOTIFICATION:
                fovFactor = (Float) data[1];
                thpointTimesFovfactor = (float) Settings.settings.scene.star.threshold.point;
                thupOverFovfactor = (float) Constants.THRESHOLD_UP;
                thdownOverFovfactor = (float) Constants.THRESHOLD_DOWN;
                break;
            case STAR_POINT_SIZE_CMD:
                innerRad = (0.004f * DISC_FACTOR + (Float) data[0] * 0.008f) * 1.5f;
                break;
            default:
                break;
            }
        }
    }

    static {
        if (GaiaSky.instance != null) {
            fovFactor = GaiaSky.instance.getCameraManager().getFovFactor();
        } else {
            fovFactor = 1f;
        }
        Settings settings = Settings.settings;
        thpointTimesFovfactor = (float) settings.scene.star.threshold.point;
        thupOverFovfactor = (float) Constants.THRESHOLD_UP / fovFactor;
        thdownOverFovfactor = (float) Constants.THRESHOLD_DOWN / fovFactor;
        float pSize = settings.scene.star.pointSize < 0 ? 8 : settings.scene.star.pointSize;
        innerRad = (0.004f * DISC_FACTOR + pSize * 0.008f) * 1.5f;
        paramUpdater = new ParamUpdater();
    }

    /**
     * Proper motion in cartesian coordinates [U/yr]
     **/
    public Vector3 pm;
    /**
     * MuAlpha [mas/yr], Mudelta [mas/yr], radvel [km/s]
     **/
    public Vector3 pmSph;

    /**
     * Source of this star:
     * <ul>
     * <li>-1: Unknown</li>
     * <li>1: Gaia</li>
     * <li>2: Hipparcos (HYG)</li>
     * <li>3: Tycho</li>
     * </ul>
     */
    public byte catalogSource = -1;

    public double computedSize;
    double radius;
    boolean hasPm = false;

    RenderGroup billboardRenderGroup;

    public Particle() {
        super();
        this.pm = new Vector3();
        this.pmSph = new Vector3();
        this.parentName = ROOT_NAME;

        // Defaults
        this.thresholdNone = Settings.settings.scene.star.threshold.none;
        this.thresholdPoint = Settings.settings.scene.star.threshold.point;
        this.thresholdQuad = Settings.settings.scene.star.threshold.quad;

        this.textScale = 0.2f;
        this.labelFactor = 1.3e-1f;
        this.labelMax = 0.01f;

        this.primitiveRenderScale = 1;
        this.billboardRenderGroup = RenderGroup.BILLBOARD_STAR;

        this.TH_OVER_FACTOR = (float) (thresholdPoint / Settings.settings.scene.label.number);
    }

    public Particle(double thNone, double thPoint, double thQuad, float textScale, float labelFactor, float labelMax, float primitiveRenderScale, RenderGroup bbRenderGroup) {
        super();
        this.pm = new Vector3();
        this.pmSph = new Vector3();
        this.parentName = ROOT_NAME;

        this.thresholdNone = thNone;
        this.thresholdPoint = thPoint;
        this.thresholdQuad = thQuad;

        this.textScale = textScale;
        this.labelFactor = labelFactor;
        this.labelMax = labelMax;

        this.primitiveRenderScale = primitiveRenderScale;
        this.billboardRenderGroup = bbRenderGroup;

    }

    public Particle(Vector3b pos, float appmag, float absmag, float colorbv, String[] names, float ra, float dec, long starid, double thNone, double thPoint, double thQuad, float textScale, float labelFactor, float labelMax, float primitiveRenderScale, RenderGroup bbRenderGroup) {
        this(thNone, thPoint, thQuad, textScale, labelFactor, labelMax, primitiveRenderScale, bbRenderGroup);

        this.pos = pos;
        this.names = names;
        this.appmag = appmag;
        this.absmag = absmag;
        this.colorbv = colorbv;
        this.id = starid;

        if (this.names == null || this.names.length == 0) {
            this.setName("star_" + rnd.nextInt(10000000));
        }
        this.pm = new Vector3();
        this.pmSph = new Vector3();
        this.posSph = new Vector2d(ra, dec);
    }

    /**
     * Creates a new star.
     *
     * @param pos     Cartesian position, in equatorial coordinates and in internal
     *                units.
     * @param appmag  Apparent magnitude.
     * @param absmag  Absolute magnitude.
     * @param colorbv The B-V color index.
     * @param names   The labels or names.
     * @param starid  The star unique id.
     */
    public Particle(Vector3b pos, float appmag, float absmag, float colorbv, String[] names, long starid) {
        this();
        this.pos = pos;
        this.names = names;
        this.appmag = appmag;
        this.absmag = absmag;
        this.colorbv = colorbv;
        this.id = starid;

        if (this.names == null || this.names.length == 0) {
            this.setName("star_" + rnd.nextInt(10000000));
        }
        this.pm = new Vector3();
        this.pmSph = new Vector3();
    }

    public Particle(Vector3b pos, float appmag, float absmag, float colorbv, String[] names, float ra, float dec, long starid) {
        this(pos, appmag, absmag, colorbv, names, starid);
        this.posSph = new Vector2d(ra, dec);

    }

    public Particle(Vector3b pos, Vector3 pm, Vector3 pmSph, float appmag, float absmag, float colorbv, String[] names, float ra, float dec, long starid) {
        this(pos, appmag, absmag, colorbv, names, starid);
        this.posSph = new Vector2d(ra, dec);
        this.pm.set(pm);
        this.pmSph.set(pmSph);
        this.hasPm = this.pm.len2() != 0;

    }

    @Override
    public void initialize() {
        this.TH_OVER_FACTOR = (float) (thresholdPoint / Settings.settings.scene.label.number);
        setDerivedAttributes();
        if (ct == null)
            ct = new ComponentTypes(ComponentType.Galaxies);
        // Relation between our star size and actual star size (normalized for
        // the Sun, 695700 Km of radius
        radius = size * Constants.STAR_SIZE_FACTOR;
    }

    protected void setDerivedAttributes() {
        double flux = Math.pow(10, -absmag / 2.5f);
        setRGB(colorbv);

        // Calculate size - This contains arbitrary boundary values to make
        // things nice on the render side
        size = (float) (Math.log(Math.pow(flux, 10.0)) * Constants.PC_TO_U);
        computedSize = 0;
    }

    @Override
    public void update(ITimeFrameProvider time, final Vector3b parentTransform, ICamera camera) {
        update(time, parentTransform, camera, 1f);
    }

    /**
     * Re-implementation of update method of {@link CelestialBody} and
     * {@link SceneGraphNode}.
     */
    @Override
    public void update(ITimeFrameProvider time, final Vector3b parentTransform, ICamera camera, float opacity) {
        this.updateLocalValues(time, camera);
        translation.set(parentTransform).add(pos);
        this.opacity = opacity * this.getVisibilityOpacityFactor();
        if (hasPm) {
            Vector3d pmv = D31.get().set(pm).scl(AstroUtils.getMsSince(time.getTime(), AstroUtils.JD_J2015_5) * Nature.MS_TO_Y);
            translation.add(pmv);
        }
        distToCamera = translation.lend();
        Settings settings = Settings.settings;

        if (!copy) {
            viewAngle = (radius / distToCamera);
            viewAngleApparent = viewAngle * settings.scene.star.brightness / camera.getFovFactor();

            addToRenderLists(camera);
        }

        // Compute nested
        if (children != null) {
            for (int i = 0; i < children.size; i++) {
                SceneGraphNode child = children.get(i);
                child.update(time, translation, camera, opacity);
            }
        }

        innerRad = 0.01f * DISC_FACTOR + settings.scene.star.pointSize * 0.005f;
    }

    @Override
    protected void addToRenderLists(ICamera camera) {
        if (this.shouldRender()) {
            camera.checkClosestBody(this);

            addToRender(this, RenderGroup.POINT_STAR);
            if (!(camera.getCurrent() instanceof FovCamera)) {

                addToRender(this, billboardRenderGroup);

                if (viewAngleApparent >= thpointTimesFovfactor / Settings.settings.scene.properMotion.number && this.hasPm) {
                    addToRender(this, RenderGroup.LINE);
                }
            }
            if (renderText() && camera.isVisible(this)) {
                addToRender(this, RenderGroup.FONT_LABEL);
            }
        }
    }

    protected boolean addToRender(IRenderable renderable, RenderGroup rg) {
        if (shouldRender()) {
            GaiaSky.instance.sgr.renderListsFront().get(rg.ordinal()).add(renderable);
            return true;
        }
        return false;
    }

    /**
     * Model rendering
     */
    @Override
    public void render(IntModelBatch modelBatch, float alpha, double t, RenderingContext rc, RenderGroup group) {
        // Void
    }

    /**
     * Sets the color
     *
     * @param bv B-V color index
     */
    protected void setRGB(float bv) {
        if (cc == null)
            cc = ColorUtils.BVtoRGB(bv);
        setColor2Data();
    }

    @Override
    public float getInnerRad() {
        return innerRad;
    }

    @Override
    public double getRadius() {
        return radius;
    }

    public boolean isStar() {
        return true;
    }

    @Override
    public boolean renderText() {
        return computedSize > 0 && GaiaSky.instance.isOn(ComponentType.Labels) && viewAngleApparent >= (TH_OVER_FACTOR / GaiaSky.instance.cameraManager.getFovFactor());
    }

    @Override
    public float labelSizeConcrete() {
        float textSize = (float) (FastMath.tanh(viewAngle) * distToCamera * 1e5d);
        float alpha = Math.min((float) FastMath.atan(textSize / distToCamera), 1.e-3f);
        textSize = (float) (FastMath.tan(alpha) * distToCamera * 0.5d);
        return textSize * 1e2f;
    }

    public float getFuzzyRenderSize(ICamera camera) {
        computedSize = this.size;
        if (viewAngle > thdownOverFovfactor) {
            double dist = distToCamera;
            if (viewAngle > thupOverFovfactor) {
                dist = (float) radius / Constants.THRESHOLD_UP;
            }
            computedSize *= (dist / this.radius) * Constants.THRESHOLD_DOWN;
        }

        computedSize *= Settings.settings.scene.star.pointSize * 0.2f;
        return (float) (computedSize / Constants.DISTANCE_SCALE_FACTOR);
    }

    @Override
    public void doneLoading(AssetManager manager) {
        super.doneLoading(manager);
    }

    @Override
    public void updateLocalValues(ITimeFrameProvider time, ICamera camera) {
        forceUpdateLocalValues(time, false);
    }

    protected void forceUpdateLocalValues(ITimeFrameProvider time, boolean force) {
        if (coordinates != null && (time.getHdiff() != 0 || force)) {
            Vector3d aux3 = D31.get();
            // Load this objects' equatorial cartesian coordinates into pos
            coordinatesTimeOverflow = coordinates.getEquatorialCartesianCoordinates(time.getTime(), pos) == null;

            // Convert to cartesian coordinates and put them in aux3 vector
            if (pos.len2d() == 0) {
                // Sun!
            } else {
                Coordinates.cartesianToSpherical(pos, aux3);
                posSph.set((float) (Nature.TO_DEG * aux3.x), (float) (Nature.TO_DEG * aux3.y));
            }
            // Update angle
            if (rc != null)
                rc.update(time);
        }
    }

    @Override
    public int getStarCount() {
        return 1;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends SceneGraphNode> T getSimpleCopy() {
        Particle copy = super.getSimpleCopy();
        copy.pm = this.pm;
        copy.hasPm = this.hasPm;
        return (T) copy;
    }

    /**
     * Line renderer. Renders proper motions
     */
    @Override
    public void render(LineRenderSystem renderer, ICamera camera, float alpha) {
        Vector3 p1 = translation.setVector3(F31.get());
        Vector3 ppm = F32.get().set(pm).scl((float) Settings.settings.scene.properMotion.number);
        Vector3 p2 = ppm.add(p1);

        // Mualpha -> red channel
        // Mudelta -> green channel
        // Radvel -> blue channel
        // Min value per channel = 0.2
        final double mumin = -80;
        final double mumax = 80;
        final double maxmin = mumax - mumin;
        renderer.addLine(this, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, (float) ((pmSph.x - mumin) / maxmin) * 0.8f + 0.2f, (float) ((pmSph.y - mumin) / maxmin) * 0.8f + 0.2f, pmSph.z * 0.8f + 0.2f, alpha * this.opacity);
    }

    @Override
    public int getGlPrimitive() {
        return GL20.GL_LINES;
    }

    protected float getThOverFactorScl() {
        return fovFactor;
    }

    @Override
    protected boolean checkHitCondition() {
        return this.octant == null || this.octant.observed;
    }

    @Override
    public int getCatalogSource() {
        return catalogSource;
    }

    @Override
    public int getHip() {
        return -1;
    }

    @Override
    public double getClosestDistToCamera() {
        return this.distToCamera;
    }

    @Override
    public String getClosestName() {
        return this.getName();
    }

    @Override
    public Vector3d getClosestPos(Vector3d out) {
        return translation.put(out);
    }

    @Override
    public Vector3b getClosestAbsolutePos(Vector3b out) {
        return getAbsolutePosition(out);
    }

    @Override
    public float[] getClosestCol() {
        return cc;
    }

    @Override
    public double getClosestSize() {
        return this.size;
    }

    @Override
    public double getMuAlpha() {
        if (this.pmSph != null)
            return this.pmSph.x;
        else
            return 0;
    }

    @Override
    public double getMuDelta() {
        if (this.pmSph != null)
            return this.pmSph.y;
        else
            return 0;
    }

    @Override
    public double getRadialVelocity() {
        if (this.pmSph != null)
            return this.pmSph.z;
        else
            return 0;
    }

    @Override
    public float getLineWidth() {
        return 1;
    }

    public void setBillboardRenderGroup(String bbRG) {
        this.billboardRenderGroup = RenderGroup.valueOf(bbRG);
    }

}
