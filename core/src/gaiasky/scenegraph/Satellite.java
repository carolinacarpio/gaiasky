/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scenegraph;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.math.Matrix4;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.component.RotationComponent;
import gaiasky.util.Logger;
import gaiasky.util.Nature;
import gaiasky.util.Settings;
import gaiasky.util.coord.Coordinates;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;

public abstract class Satellite extends ModelBody {

    protected static final double TH_ANGLE_NONE = ModelBody.TH_ANGLE_POINT / 1e18;
    protected static final double TH_ANGLE_POINT = ModelBody.TH_ANGLE_POINT / 3.3e10;
    protected static final double TH_ANGLE_QUAD = ModelBody.TH_ANGLE_POINT / 8;

    protected boolean parentOrientation = false;
    protected boolean hidden = false;
    protected Matrix4 orientationf;
    protected RotationComponent parentrc;

    @Override
    public double THRESHOLD_NONE() {
        return TH_ANGLE_NONE;
    }

    @Override
    public double THRESHOLD_POINT() {
        return TH_ANGLE_POINT;
    }

    @Override
    public double THRESHOLD_QUAD() {
        return TH_ANGLE_QUAD;
    }

    @Override
    public void doneLoading(AssetManager manager) {
        super.doneLoading(manager);

        if (parentOrientation) {
            this.parentrc = ((ModelBody) parent).rc;
        }
        this.orientationf = new Matrix4();
    }

    @Override
    public void updateLocalValues(ITimeFrameProvider time, ICamera camera) {
        forceUpdatePosition(time, false);
    }

    /**
     * Default implementation, only sets the result of the coordinates call to
     * pos
     *
     * @param time  Time to get the coordinates
     * @param force Whether to force the update
     */
    protected void forceUpdatePosition(ITimeFrameProvider time, boolean force) {
        if (time.getHdiff() != 0 || force) {
            coordinatesTimeOverflow = coordinates.getEquatorialCartesianCoordinates(time.getTime(), pos) == null;
            // Convert to cartesian coordinates and put them in aux3 vector
            Vector3d aux3 = D31.get();
            Coordinates.cartesianToSpherical(pos, aux3);
            posSph.set((float) (Nature.TO_DEG * aux3.x), (float) (Nature.TO_DEG * aux3.y));

            if(rc != null)
                rc.update(time);
        }
    }

    @Override
    protected void updateLocalTransform() {
        setToLocalTransform(sizeScaleFactor, localTransform, true);
    }

    /**
     * Sets the local transform of this satellite
     */
    public void setToLocalTransform(float sizeFactor, Matrix4 localTransform, boolean forceUpdate) {
        super.setToLocalTransform(sizeFactor, localTransform, forceUpdate);

    }

    @Override
    protected float labelFactor() {
        return .5e1f;
    }

    @Override
    public boolean renderText() {
        return !hidden && super.renderText();
    }

    @Override
    protected float labelMax() {
        return super.labelMax() * 2;
    }

    protected float getViewAnglePow() {
        return 1f;
    }

    protected float getThOverFactorScl() {
        return 5e3f;
    }

    public float getFuzzyRenderSize(ICamera camera) {
        float thAngleQuad = (float) THRESHOLD_QUAD() * camera.getFovFactor();
        double size = 0f;
        if (viewAngle >= THRESHOLD_POINT() * camera.getFovFactor()) {
            size = Math.tan(thAngleQuad) * distToCamera * 10f;
        }
        return (float) size;
    }

    public void setParentorientation(String parentorientation) {
        try {
            this.parentOrientation = Boolean.parseBoolean(parentorientation);
        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e);
        }
    }

    public void setHidden(String hidden) {
        try {
            this.hidden = Boolean.parseBoolean(hidden);
        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e);
        }
    }

    @Override
    public RotationComponent getRotationComponent() {
        if (parentOrientation && parentrc != null) {
            return parentrc;
        }
        return super.getRotationComponent();
    }

    @Override
    public void setSize(Long size) {
        super.setSize(size * (Settings.settings.runtime.openVr ? 4000L : 1L));
    }

    @Override
    public void setSize(Double size) {
        super.setSize(size * (Settings.settings.runtime.openVr ? 4000d : 1d));
    }
}
