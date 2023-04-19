/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.system.render.extract;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.render.RenderGroup;
import gaiasky.scene.Mapper;
import gaiasky.scene.system.update.ClusterUpdater;

public class ClusterExtractor extends AbstractExtractSystem {

    public ClusterExtractor(Family family, int priority) {
        super(family, priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var base = Mapper.base.get(entity);

        if (mustRender(base)) {
            var body = Mapper.body.get(entity);
            var render = Mapper.render.get(entity);
            var label = Mapper.label.get(entity);
            var sa = Mapper.sa.get(entity);

            if (body.solidAngleApparent >= sa.thresholdPoint) {
                addToRender(render, RenderGroup.MODEL_VERT_ADDITIVE);
            }
            if (body.solidAngleApparent >= sa.thresholdPoint || label.forceLabel) {
                addToRender(render, RenderGroup.FONT_LABEL);
            }

            if (body.solidAngleApparent < sa.thresholdQuad) {
                addToRender(render, RenderGroup.BILLBOARD_SPRITE);
            }
        }
    }
}
