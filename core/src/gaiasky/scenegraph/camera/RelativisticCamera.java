/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scenegraph.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import gaiasky.scenegraph.IFocus;
import gaiasky.scenegraph.camera.CameraManager.CameraMode;
import gaiasky.util.Settings;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;

public class RelativisticCamera extends AbstractCamera {

    public Vector3d direction, up;

    public RelativisticCamera(CameraManager parent) {
        super(parent);
    }

    public RelativisticCamera(AssetManager assetManager, CameraManager parent) {
        super(parent);
        initialize(assetManager);

    }

    private void initialize(AssetManager manager) {
        camera = new PerspectiveCamera(Settings.settings.scene.camera.fov, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = (float) CAM_NEAR;
        camera.far = (float) CAM_FAR;

        fovFactor = camera.fieldOfView / 40f;

        up = new Vector3d(1, 0, 0);
        direction = new Vector3d(0, 1, 0);
    }

    @Override
    public PerspectiveCamera getCamera() {
        return camera;
    }

    @Override
    public void setCamera(PerspectiveCamera perspectiveCamera) {
    }

    @Override
    public PerspectiveCamera[] getFrontCameras() {
        return null;
    }

    @Override
    public void setDirection(Vector3d dir) {
    }

    @Override
    public Vector3d getDirection() {
        return null;
    }

    @Override
    public Vector3d getUp() {
        return null;
    }

    @Override
    public Vector3d[] getDirections() {
        return null;
    }

    @Override
    public int getNCameras() {
        return 0;
    }

    @Override
    public double speedScaling() {
        return 0;
    }

    @Override
    public void update(double dt, ITimeFrameProvider time) {
    }

    @Override
    public void updateMode(ICamera previousCam, CameraMode previousMode, CameraMode newMode, boolean centerFocus, boolean postEvent) {
    }

    @Override
    public CameraMode getMode() {
        return null;
    }

    @Override
    public double getSpeed() {
        return 0;
    }

    @Override
    public IFocus getFocus() {
        return null;
    }

    @Override
    public boolean isFocus(IFocus cb) {
        return false;
    }

    @Override
    public void resize(int width, int height) {

    }

}
