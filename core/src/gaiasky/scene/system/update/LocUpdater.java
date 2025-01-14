/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.GaiaSky;
import gaiasky.scene.Mapper;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.component.GraphNode;
import gaiasky.scene.component.LocationMark;

public class LocUpdater extends AbstractUpdateSystem {

    private final ModelUpdater updater;

    public LocUpdater(Family family, int priority) {
        super(family, priority);
        this.updater = new ModelUpdater(null, 0);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        updateEntity(entity, deltaTime);
    }

    @Override
    public void updateEntity(Entity entity, float deltaTime) {
        var loc = Mapper.loc.get(entity);
        var graph = Mapper.graph.get(entity);

        ICamera cam = GaiaSky.instance.getICamera();
        updateLocalValues(entity, graph, loc, cam);
    }

    public void updateLocalValues(Entity entity, GraphNode graph, LocationMark loc, ICamera cam) {

        var label = Mapper.label.get(entity);

        Entity papa = graph.parent;
        var pBody = Mapper.body.get(papa);
        var pGraph = Mapper.graph.get(papa);

        updater.setToLocalTransform(papa, pBody, pGraph, pBody.size, loc.distFactor, graph.localTransform, false);

        loc.location3d.set(0, 0, -.5f);
        // Latitude [-90..90]
        loc.location3d.rotate(loc.location.y, 1, 0, 0);
        // Longitude [0..360]
        loc.location3d.rotate(loc.location.x + 90, 0, 1, 0);

        loc.location3d.mul(graph.localTransform);

        label.labelPosition.set(loc.location3d).add(cam.getPos());
    }
}
