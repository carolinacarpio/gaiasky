/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.api;

import gaiasky.util.math.Vector3d;

public interface ISpacecraft {

    Vector3d force();

    Vector3d accel();

    Vector3d vel();

    Vector3d direction();

    Vector3d up();

    Vector3d thrust();

    double currentEnginePower();

    void currentEnginePower(double power);

    double thrustMagnitude();

    double[] thrustFactor();

    double relativisticSpeedCap();

    double drag();

    double mass();

    int thrustFactorIndex();

    boolean leveling();

    boolean stopping();

    void stopAllMovement();

}
