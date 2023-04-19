/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Matrix4;
import gaiasky.scene.record.RotationComponent;

public class ParentOrientation implements Component {

    public boolean parentOrientation = false;
    public Matrix4 orientationf;
    public RotationComponent parentrc;

}
