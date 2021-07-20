/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scenegraph.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.input.GestureDetector.GestureAdapter;
import gaiasky.GaiaSky;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.scenegraph.IFocus;
import gaiasky.scenegraph.Spacecraft;
import gaiasky.scenegraph.camera.CameraManager.CameraMode;
import gaiasky.util.Constants;
import gaiasky.util.GlobalConf;
import gaiasky.util.Pair;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;
import org.apfloat.Apfloat;

/**
 * Implements a spacecraft-like movement. The spacecraft is modeled as a rigid
 * solid and it has a mass and an engine model. The rest is physics
 */
public class SpacecraftCamera extends AbstractCamera implements IObserver {

    /**
     * Direction and up vectors
     **/
    public Vector3d direction, up;
    public Vector3b relpos;

    private Spacecraft sc;

    /**
     * Camera to render the attitude indicator system
     **/
    private final PerspectiveCamera guiCam;

    /**
     * The input inputListener attached to this camera
     **/
    private final SpacecraftInputController inputController;

    /**
     * Controller listener
     **/
    //private SpacecraftControllerListener controllerListener;

    /**
     * Closest body apart from the spacecraft (second closest)
     **/
    private IFocus secondClosest;

    private final Vector3d aux1, aux2;
    private final Vector3b aux1b;
    private final Vector3b todesired;
    private final Vector3b desired;
    private final Vector3d scthrust;
    private final Vector3d scforce;
    private final Vector3d scaccel;
    private final Vector3d scvel;
    private Vector3b scpos;
    private final Vector3d scdir;
    private final Vector3d scup;
    private final Pair<Vector3d, Vector3d> dirup;

    private double targetDistance;

    public SpacecraftCamera(CameraManager parent) {
        super(parent);

        // Vectors
        direction = new Vector3d(1, 0, 0);
        up = new Vector3d(0, 1, 0);
        relpos = new Vector3b();
        todesired = new Vector3b();
        desired = new Vector3b();
        aux1 = new Vector3d();
        aux2 = new Vector3d();
        aux1b = new Vector3b();
        scthrust = new Vector3d();
        scforce = new Vector3d();
        scaccel = new Vector3d();
        scvel = new Vector3d();
        scpos = new Vector3b();
        scdir = new Vector3d();
        scup = new Vector3d();

        dirup = new Pair<>(scdir, scup);

        // init camera
        camera = new PerspectiveCamera(40, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = (float) CAM_NEAR;
        camera.far = (float) CAM_FAR;

        // init cameras vector
        cameras = new PerspectiveCamera[] { camera, camLeft, camRight };

        // init gui camera
        guiCam = new PerspectiveCamera(30, 300, 300);
        guiCam.near = (float) CAM_NEAR;
        guiCam.far = (float) CAM_FAR;

        // aspect ratio
        ar = (float) Gdx.graphics.getWidth() / (float) Gdx.graphics.getHeight();

        // fov factor
        fovFactor = camera.fieldOfView / 40f;

        inputController = new SpacecraftInputController(new GestureAdapter());

        // FOCUS_MODE is changed from GUI
        EventManager.instance.subscribe(this, Events.FOV_CHANGED_CMD, Events.SPACECRAFT_LOADED, Events.SPACECRAFT_MACHINE_SELECTION_INFO);
    }

    @Override
    public PerspectiveCamera getCamera() {
        return this.camera;
    }

    @Override
    public void setCamera(PerspectiveCamera perspectiveCamera) {
        this.camera = perspectiveCamera;
    }

    @Override
    public PerspectiveCamera[] getFrontCameras() {
        return new PerspectiveCamera[] { camera };
    }

    @Override
    public Vector3d getDirection() {
        return direction;
    }

    @Override
    public void setDirection(Vector3d dir) {
        this.direction.set(dir);
    }

    @Override
    public Vector3d getUp() {
        return up;
    }

    @Override
    public Vector3d[] getDirections() {
        return new Vector3d[] { direction };
    }

    @Override
    public int getNCameras() {
        return 1;
    }

    public void update(double dt, ITimeFrameProvider time) {
        /* FUTURE POS OF SC */

        // We use the simulation time for the integration
        //double sdt = time.getDt() * Constants.H_TO_S;

        scthrust.set(sc.thrust);
        scforce.set(sc.force);
        scaccel.set(sc.accel);
        scvel.set(sc.vel);
        scpos.set(sc.pos);
        scpos = sc.computePosition(dt, secondClosest, sc.currentEnginePower, scthrust, sc.direction, scforce, scaccel, scvel, scpos);
        scdir.set(sc.direction);
        scup.set(sc.up);
        sc.computeDirectionUp(dt, dirup);

        /* ACTUAL UPDATE */
        updateHard(dt);

        /* POST */
        distance = pos.lend();

        // Update camera
        updatePerspectiveCamera();

        // Broadcast nearest info
        String clname = null;
        double cldist = -1d;
        if (closestStar != null) {
            double closestStarDist = closestStar.getClosestDistToCamera();
            String closestStarName = closestStar.getClosestName();
            if (secondClosest != null) {
                if (secondClosest.getDistToCamera() < closestStarDist) {
                    clname = secondClosest.getName();
                    cldist = secondClosest.getDistToCamera();
                } else {
                    clname = closestStarName;
                    cldist = closestStarDist;
                }
            } else {
                clname = closestStarName;
                cldist = closestStarDist;
            }
        }
        EventManager.instance.post(Events.SPACECRAFT_NEAREST_INFO, clname, cldist);

    }

    /**
     * Updates the position and direction of the camera using a hard analytical algorithm.
     */
    public void updateHard(double dt) {
        if (sc != null) {
            // POSITION
            double tDistOverFov = targetDistance / fovFactor;
            desired.set(scdir).nor().scl(-tDistOverFov);
            aux1b.set(scup).nor().scl(tDistOverFov * 0.125d);
            desired.add(aux1b);
            todesired.set(desired).sub(relpos);
            todesired.scl(dt * sc.getResponsiveness()).scl(3e-6d);
            relpos.add(todesired);
            pos.set(scpos).add(relpos);

            // DIRECTION
            aux1.set(scup).nor().scl(targetDistance);
            aux2.set(scdir).nor().scl(tDistOverFov * 50d).add(aux1);
            aux1b.set(scpos).add(aux2).sub(pos).nor();
            aux1b.put(direction);

            // UP
            desired.set(scup);
            todesired.set(desired).sub(up);
            todesired.scl(dt * sc.getResponsiveness()).scl(1e-8d);
            up.add(todesired).nor();
        }
    }

    protected void updatePerspectiveCamera() {
        camera.fieldOfView = 40;
        fovFactor = camera.fieldOfView / 40f;
        camera.position.set(0, 0, 0);
        direction.put(camera.direction);
        up.put(camera.up);

        camera.update();

        posinv.set(pos).scl(new Apfloat(-1, Constants.PREC));

    }

    @Override
    public void updateMode(ICamera previousCam, CameraMode previousMode, CameraMode mode, boolean centerFocus, boolean postEvent) {
        InputProcessor ip = Gdx.input.getInputProcessor();
        if (ip instanceof InputMultiplexer) {
            InputMultiplexer im = (InputMultiplexer) ip;
            if (mode == CameraMode.SPACECRAFT_MODE && sc != null && previousMode != CameraMode.SPACECRAFT_MODE) {
                // Enter SC mode
                GaiaSky.postRunnable(() -> {
                    // Register input inputListener
                    if (!im.getProcessors().contains(inputController, true))
                        im.addProcessor(inputController);
                    // Register inputListener listener
                    Controllers.clearListeners();
                    sc.stopAllMovement();

                    // Put spacecraft at location of previous camera
                    sc.pos.set(previousCam.getPos());
                    sc.direction.set(previousCam.getDirection());
                    sc.up.set(sc.pos).crs(sc.direction);

                    pos.set(sc.pos);
                    direction.set(sc.direction);
                    up.set(sc.up);

                    updateAngleEdge(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                });
            } else {
                // Exit SC mode
                if (sc != null)
                    GaiaSky.postRunnable(() -> {
                        // Unregister input inputListener
                        im.removeProcessor(inputController);
                        // Unregister inputListener listener
                        sc.stopAllMovement();
                    });
            }
        }
    }

    @Override
    public CameraMode getMode() {
        return parent.mode;
    }

    @Override
    public double getSpeed() {
        return parent.getSpeed();
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
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case SPACECRAFT_LOADED:
            this.sc = (Spacecraft) data[0];
            updateTargetDistance();
            break;
        case SPACECRAFT_MACHINE_SELECTION_INFO:
            updateTargetDistance();
            break;
        default:
            break;
        }
    }

    private void updateTargetDistance(){
        this.targetDistance = sc.size * 3.5;
    }

    @Override
    public void render(int rw, int rh) {
    }

    /**
     * Input inputListener for the spacecraft camera
     */
    private class SpacecraftInputController extends GestureDetector {

        public SpacecraftInputController(GestureListener listener) {
            super(listener);
        }

        @Override
        public boolean keyDown(int keycode) {
            if (sc != null && GlobalConf.runtime.INPUT_ENABLED) {
                double step = 0.01;
                switch (keycode) {
                case Keys.W:
                    // power 1
                    sc.setCurrentEnginePower(sc.currentEnginePower + step);
                    EventManager.instance.post(Events.SPACECRAFT_STOP_CMD, false);
                    break;
                case Keys.S:
                    // power -1
                    sc.setCurrentEnginePower(sc.currentEnginePower - step);
                    EventManager.instance.post(Events.SPACECRAFT_STOP_CMD, false);
                    break;
                case Keys.A:
                    // roll 1
                    sc.setRollPower(sc.rollp + step);
                    EventManager.instance.post(Events.SPACECRAFT_STOP_CMD, false);
                    break;
                case Keys.D:
                    // roll -1
                    sc.setRollPower(sc.rollp - step);
                    EventManager.instance.post(Events.SPACECRAFT_STABILISE_CMD, false);
                    break;
                case Keys.DOWN:
                    // pitch 1
                    sc.setPitchPower(sc.pitchp + step);
                    EventManager.instance.post(Events.SPACECRAFT_STABILISE_CMD, false);
                    break;
                case Keys.UP:
                    // pitch -1
                    sc.setPitchPower(sc.pitchp - step);
                    EventManager.instance.post(Events.SPACECRAFT_STABILISE_CMD, false);
                    break;
                case Keys.LEFT:
                    // yaw 1
                    sc.setYawPower(sc.yawp + step);
                    EventManager.instance.post(Events.SPACECRAFT_STABILISE_CMD, false);
                    break;
                case Keys.RIGHT:
                    // yaw -1
                    sc.setYawPower(sc.yawp - step);
                    EventManager.instance.post(Events.SPACECRAFT_STABILISE_CMD, false);
                    break;
                default:
                    break;
                }
            }
            return false;

        }

        @Override
        public boolean keyUp(int keycode) {
            if (sc != null && GlobalConf.runtime.INPUT_ENABLED) {
                switch (keycode) {
                case Keys.W:
                case Keys.S:
                    // power 0
                    sc.setCurrentEnginePower(0);
                    break;
                case Keys.D:
                case Keys.A:
                    // roll 0
                    sc.setRollPower(0);
                    break;
                case Keys.UP:
                case Keys.DOWN:
                    // pitch 0
                    sc.setPitchPower(0);
                    break;
                case Keys.RIGHT:
                case Keys.LEFT:
                    // yaw 0
                    sc.setYawPower(0);
                    break;
                case Keys.L:
                    // level spaceship
                    EventManager.instance.post(Events.SPACECRAFT_STABILISE_CMD, true);
                    break;
                case Keys.K:
                    // stop spaceship
                    EventManager.instance.post(Events.SPACECRAFT_STOP_CMD, true);
                    break;
                case Keys.PAGE_UP:
                    // Increase thrust factor
                    sc.increaseThrustFactorIndex(true);
                    break;
                case Keys.PAGE_DOWN:
                    // Decrease thrust length
                    sc.decreaseThrustFactorIndex(true);
                    break;
                default:
                    break;
                }
            }
            return false;

        }

    }

    @Override
    public void checkClosestBody(IFocus cb) {
        super.checkClosestBody(cb);
        if (sc != null)
            if (secondClosest == null || (cb != sc && cb.getDistToCamera() < secondClosest.getDistToCamera())) //-V6007
                secondClosest = cb;
    }

    @Override
    public IFocus getSecondClosestBody() {
        return secondClosest;
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public Vector3d getVelocity() {
        return scvel;
    }

    @Override
    public double speedScaling() {
        return Math.max(0.001, scvel.len());
    }

}
