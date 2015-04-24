package gaia.cu9.ari.gaiaorbit.scenegraph;

import gaia.cu9.ari.gaiaorbit.render.IPointRenderable;
import gaia.cu9.ari.gaiaorbit.render.SceneGraphRenderer.ComponentType;
import gaia.cu9.ari.gaiaorbit.util.Constants;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.color.ColourUtils;
import gaia.cu9.ari.gaiaorbit.util.math.Vector2d;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;
import gaia.cu9.ari.gaiaorbit.util.time.ITimeFrameProvider;
import gaia.cu9.ari.gaiaorbit.util.tree.OctreeNode;

import java.util.Random;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer;
import com.badlogic.gdx.math.Vector3;

/**
 * A point particle which may represent a star, a galaxy, etc.
 * @author Toni Sagrista
 *
 */
public class Particle extends CelestialBody implements IPointRenderable {

    private static final float TH_ANGLE_POINT = (float) Math.toRadians(2e-7f);
    private static final float TH_ANGLE_NONE = 0;
    private static ThreadLocal<Random> rnd = new ThreadLocal<Random>() {
	@Override
	public Random initialValue() {
	    return new Random();
	}
    };

    @Override
    public double THRESHOLD_ANGLE_NONE() {
	return TH_ANGLE_NONE;
    }

    @Override
    public double THRESHOLD_ANGLE_POINT() {
	return TH_ANGLE_POINT;
    }

    @Override
    public double THRESHOLD_ANGLE_QUAD() {
	return 0;
    }

    double computedSize;
    double radius;
    boolean randomName = false;

    /**
     * Object server properties
     */

    /** The id of the octant it belongs to, if any **/
    public long pageId;
    /** Its page **/
    public OctreeNode<? extends SceneGraphNode> page;
    /** Particle type
     * 90 - real star
     * 92 - virtual particle
     */
    public int type = 90;
    public int nparticles = 1;

    public Particle() {
	this.parentName = ROOT_NAME;
    }

    /**
     * Creates a new star.
     * @param pos Cartesian position, in equatorial coordinates and in internal units.
     * @param appmag Apparent magnitude.
     * @param absmag Absolute magnitude.
     * @param colorbv The B-V color index.
     * @param name The label or name.
     * @param starid The star unique id.
     */
    public Particle(Vector3d pos, float appmag, float absmag, float colorbv, String name, long starid) {
	this();
	this.pos = pos;
	this.name = name;
	this.appmag = appmag;
	this.absmag = absmag;
	this.colorbv = colorbv;
	this.id = starid;

	if (this.name == null) {
	    randomName = true;
	    this.name = "star_" + rnd.get().nextInt(10000000);
	}
    }

    public Particle(Vector3d pos, float appmag, float absmag, float colorbv, String name, double ra, double dec, long starid) {
	this(pos, appmag, absmag, colorbv, name, starid);
	this.posSph = new Vector2d(ra, dec);

    }

    @Override
    public void initialize() {
	setDerivedAttributes();
	ct = ComponentType.Galaxies;
	// Relation between our star size and actual star size (normalized for the Sun, 1391600 Km of diameter
	radius = size * Constants.STAR_SIZE_FACTOR;
    }

    private void setDerivedAttributes() {
	this.flux = (float) Math.pow(10, -absmag / 2.5f);
	setRGB(colorbv);

	// Calculate size
	size = (float) Math.min((Math.pow(flux, 0.5f) * Constants.PC_TO_U * 0.16f), 1e8f);
    }

    @Override
    public void update(ITimeFrameProvider time, final Transform parentTransform, ICamera camera) {
	update(time, parentTransform, camera, 1f);
    }

    /**
     * Re-implementation of update method of {@link CelestialBody} and {@link SceneGraphNode} 
     * that ignores the stars that are behind the camera.
     */
    @Override
    public void update(ITimeFrameProvider time, final Transform parentTransform, ICamera camera, float opacity) {
	if (appmag <= GlobalConf.runtime.LIMIT_MAG_RUNTIME) {
	    this.opacity = opacity * (page != null ? page.opacity : 1);
	    transform.position.set(parentTransform.position).add(pos);

	    distToCamera = (float) transform.position.len();
	    addToRender(this, RenderGroup.POINT);
	    boolean visible = computeVisible(time, camera, GlobalConf.scene.COMPUTE_GAIA_SCAN) || camera.isFocus(this);
	    if (visible && !copy) {
		viewAngle = ((float) radius / distToCamera) / camera.getFovFactor();
		viewAngleApparent = viewAngle * GlobalConf.scene.STAR_BRIGHTNESS;

		addToRenderLists(camera);
	    }

	    if (distToCamera < size) {
		if (!expandedFlag) {
		    // Update computed to true
		    setComputedFlag(children, true);
		}
		// Compute nested
		if (children != null) {
		    int size = children.size();
		    for (int i = 0; i < size; i++) {
			SceneGraphNode child = children.get(i);
			child.update(time, parentTransform, camera);
		    }
		}
		expandedFlag = true;
	    } else {
		if (expandedFlag) {
		    // Set computed to false
		    setComputedFlag(children, false);
		}
		expandedFlag = false;
	    }
	}
    }

    @Override
    protected void addToRenderLists(ICamera camera) {
	if (camera.getCurrent() instanceof FovCamera) {
	    // Only shader for FovCamera
	    addToRender(this, RenderGroup.SHADER);
	} else {

	    if (viewAngleApparent >= THRESHOLD_ANGLE_POINT() * camera.getFovFactor()) {
		addToRender(this, RenderGroup.SHADER);
	    }
	    //		if (viewAngleApparent < THRESHOLD_ANGLE_POINT() * camera.getFovFactor()) {
	    //		    // Update opacity
	    //		    opacity *= MathUtilsd.lint(viewAngleApparent, 0, THRESHOLD_ANGLE_POINT(), GlobalConf.scene.POINT_ALPHA_MIN, GlobalConf.scene.POINT_ALPHA_MAX);
	    //
	    //		    addToRender(this, RenderGroup.POINT);
	    //		} else {
	    //		    addToRender(this, RenderGroup.POINT);
	    //		    addToRender(this, RenderGroup.SHADER);
	    //		}
	}
	if (renderLabel()) {
	    addToRender(this, RenderGroup.LABEL);
	}

    }

    public void render(Object... params) {
	Object first = params[0];
	if (first instanceof ImmediateModeRenderer) {
	    // POINT
	    render((ImmediateModeRenderer) first, (Float) params[1], (Boolean) params[2]);
	} else {
	    super.render(params);
	}
    }

    /**
     * Point rendering.
     */
    @Override
    public void render(ImmediateModeRenderer renderer, float alpha, boolean colorTransit) {
	float[] col = colorTransit ? ccTransit : cc;
	renderer.color(col[0], col[1], col[2], opacity * alpha);
	Vector3 aux = auxVector3f.get();
	transform.getTranslationf(aux);
	renderer.vertex(aux.x, aux.y, aux.z);
    }

    @Override
    public void render(ModelBatch modelBatch, float alpha) {
	// Void
    }

    /**
     * Sets the color
     * @param bv B-V color index
     */
    private void setRGB(float bv) {
	cc = ColourUtils.BVtoRGB(bv);
	setColor2Data();
    }

    @Override
    public float getInnerRad() {
	return 0.04f;
    }

    @Override
    public float getRadius() {
	return (float) radius;
    }

    public boolean isStar() {
	return true;
    }

    @Override
    public float labelSizeConcrete() {
	return (float) computedSize;
    }

    @Override
    protected float labelFactor() {
	return 2e-1f;
    }

    @Override
    protected float labelMax() {
	return 0.02f;
    }

    public float getFuzzyRenderSize(ICamera camera) {
	computedSize = this.size;
	if (viewAngle > Constants.TH_ANGLE_DOWN / camera.getFovFactor()) {
	    double dist = distToCamera;
	    if (viewAngle > Constants.TH_ANGLE_UP / camera.getFovFactor()) {
		dist = getRadius() / Constants.TAN_TH_ANGLE_UP;
	    }
	    computedSize = dist * Constants.TAN_TH_ANGLE_DOWN * Constants.STAR_SIZE_FACTOR_INV;

	}
	computedSize *= GlobalConf.scene.STAR_BRIGHTNESS;
	return (float) computedSize;
    }

    @Override
    public void doneLoading(AssetManager manager) {
    }

    @Override
    public void updateLocalValues(ITimeFrameProvider time, ICamera camera) {
    }

    @Override
    public int getStarCount() {
	return 1;
    }

    @Override
    public Object getStars() {
	return this;
    }

}
