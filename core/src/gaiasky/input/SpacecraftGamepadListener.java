/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.input;

import com.badlogic.gdx.controllers.Controller;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.scene.camera.SpacecraftCamera;
import gaiasky.scene.view.SpacecraftView;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import net.jafama.FastMath;

public class SpacecraftGamepadListener extends AbstractGamepadListener {
    private static final Log logger = Logger.getLogger(SpacecraftGamepadListener.class);

    private final SpacecraftCamera cam;
    private Controller lastController;

    public SpacecraftGamepadListener(SpacecraftCamera cam, String mappingsFile) {
        super(mappingsFile);
        this.cam = cam;
    }

    @Override
    public boolean pollAxes() {
        return false;
    }

    @Override
    public boolean pollButtons() {
        return false;
    }

    @Override
    public void update() {
        if (lastController != null) {
            double thrust = lastController.getAxis(mappings.getAxisLstickV());
            double thrustFwd = lastController.getAxis(mappings.getAxisRT());
            double thrustBwd = lastController.getAxis(mappings.getAxisLT());

            if (Math.abs(thrust) < 0.05 && FastMath.abs(thrustFwd) < 0.05 && FastMath.abs(thrustBwd) < 0.05) {
                cam.getSpacecraftView().setCurrentEnginePower(0);
            }
        }
    }

    @Override
    public boolean buttonDown(Controller controller, int buttonCode) {
        logger.debug("button down [inputListener/code]: " + controller.getName() + " / " + buttonCode);

        cam.setGamepadInput(true);

        SpacecraftView sc = cam.getSpacecraftView();
        if (buttonCode == mappings.getButtonRB()) {
            sc.setRollPower(-1);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
        } else if (buttonCode == mappings.getButtonLB()) {
            sc.setRollPower(1);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
        }

        return true;
    }

    @Override
    public boolean buttonUp(Controller controller, final int buttonCode) {
        logger.debug("button up [inputListener/code]: " + controller.getName() + " / " + buttonCode);
        SpacecraftView sc = cam.getSpacecraftView();

        if (buttonCode == mappings.getButtonX()) {
            // stop spaceship
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, true);
        } else if (buttonCode == mappings.getButtonY()) {
            // level spaceship
            EventManager.publish(Event.SPACECRAFT_STABILISE_CMD, this, true);
        } else if (buttonCode == mappings.getButtonA()) {
            em.post(Event.TOGGLE_VISIBILITY_CMD, this, "element.labels");
        } else if (buttonCode == mappings.getButtonB()) {
            em.post(Event.TOGGLE_VISIBILITY_CMD, this, "element.orbits");
        } else if (buttonCode == mappings.getButtonDpadUp()) {
            // Increase thrust factor
            sc.increaseThrustFactorIndex(true);
        } else if (buttonCode == mappings.getButtonDpadDown()) {
            // Decrease thrust length
            sc.decreaseThrustFactorIndex(true);
        } else if (buttonCode == mappings.getButtonDpadLeft()) {
            em.post(Event.TIME_STATE_CMD, this, false);
        } else if (buttonCode == mappings.getButtonDpadRight()) {
            em.post(Event.TIME_STATE_CMD, this, true);
        } else if (buttonCode == mappings.getButtonRB()) {
            // Stop roll.
            sc.setRollPower(0);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
        } else if (buttonCode == mappings.getButtonLB()) {
            // Stop roll.
            sc.setRollPower(0);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
        }
        cam.setGamepadInput(true);

        lastController = controller;
        return true;
    }

    @Override
    public boolean axisMoved(Controller controller, int axisCode, float value) {
        logger.debug("axis moved [inputListener/code/value]: " + controller.getName() + " / " + axisCode + " / " + value);

        SpacecraftView sc = cam.getSpacecraftView();
        boolean treated = false;

        // Zero point
        value = (float) applyZeroPoint(value);

        // Apply power function to axis reading.
        double val = FastMath.signum(value) * FastMath.pow(Math.abs(value), mappings.getAxisValuePower());

        if (axisCode == mappings.getAxisLstickH()) {
            double effValue = -val * mappings.getAxisLstickHSensitivity();
            sc.setRollPower(effValue);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
            treated = true;
        } else if (axisCode == mappings.getAxisLstickV()) {
            double effValue = -val * mappings.getAxisLstickVSensitivity();
            sc.setCurrentEnginePower(effValue);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
            treated = true;
        } else if (axisCode == mappings.getAxisRstickH()) {
            double effValue = -(Settings.settings.controls.gamepad.invertX ? -1.0 : 1.0) * val * mappings.getAxisRstickVSensitivity();
            sc.setYawPower(effValue);
            EventManager.publish(Event.SPACECRAFT_STABILISE_CMD, this, false);
            treated = true;
        } else if (axisCode == mappings.getAxisRstickV()) {
            double effValue = (Settings.settings.controls.gamepad.invertY ? 1.0 : -1.0) * val * mappings.getAxisRstickHSensitivity();
            sc.setPitchPower(effValue);
            EventManager.publish(Event.SPACECRAFT_STABILISE_CMD, this, false);
            treated = true;
        } else if (axisCode == mappings.getAxisRT()) {
            double effValue = val * mappings.getAxisRTSensitivity();
            sc.setCurrentEnginePower(effValue);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
            treated = true;
        } else if (axisCode == mappings.getAxisLT()) {
            double effValue = -val * mappings.getAxisLTSensitivity();
            sc.setCurrentEnginePower(effValue);
            EventManager.publish(Event.SPACECRAFT_STOP_CMD, this, false);
            treated = true;
        }

        if (treated)
            cam.setGamepadInput(true);

        lastController = controller;
        return treated;
    }
}
