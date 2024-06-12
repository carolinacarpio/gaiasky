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
import gaiasky.scene.api.IParticleRecord;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.component.DatasetDescription;
import gaiasky.scene.component.ParticleSet;
import gaiasky.scene.component.StarSet;
import gaiasky.scene.entity.ParticleUtils;
import gaiasky.util.Nature;
import gaiasky.util.coord.AstroUtils;

import java.nio.file.Files;

public class ParticleSetUpdater extends AbstractUpdateSystem {

    private final ParticleUtils utils;

    public ParticleSetUpdater(Family family,
                              int priority) {
        super(family, priority);
        this.utils = new ParticleUtils();
    }

    @Override
    protected void processEntity(Entity entity,
                                 float deltaTime) {
        updateEntity(entity, deltaTime);
    }

    @Override
    public void updateEntity(Entity entity,
                             float deltaTime) {
        var camera = GaiaSky.instance.cameraManager;
        var set = Mapper.particleSet.has(entity) ? Mapper.particleSet.get(entity) : Mapper.starSet.get(entity);
        if (set != null) {
            updateCommon(set);
            if (set instanceof StarSet ss) {
                updateStarSet(camera, ss, Mapper.datasetDescription.get(entity));
            } else {
                updateParticleSet(camera, set);
            }
        }
    }

    private void updateCommon(ParticleSet set) {
        // Update proximity loading.
        if (set.proximityLoadingFlag) {
            int idxNearest = set.active[0];
            var bean = set.pointData.get(idxNearest);
            if (bean != null) {
                if (!set.proximityLoaded.contains(idxNearest)) {
                    var sa = set.getSolidAngleApparent(idxNearest);
                    // About 4 degrees.
                    if (sa > 0.069) {
                        // Load descriptor file, if it exists.
                        var name = bean.names()[0];
                        var path = set.proximityDescriptorsPath.resolve(name + ".json");
                        if (Files.exists(path)) {
                            GaiaSky.postRunnable(()->{
                                GaiaSky.instance.scripting().loadJsonDataset(name, path.toString());
                            });
                            set.proximityLoaded.add(idxNearest);
                        } else {
                            set.proximityLoaded.add(idxNearest);
                        }
                    }
                }
            }
        }
    }

    private void updateParticleSet(ICamera camera,
                                   ParticleSet particleSet) {
        // Delta years
        particleSet.currDeltaYears = AstroUtils.getMsSince(GaiaSky.instance.time.getTime(), particleSet.epochJd) * Nature.MS_TO_Y;

        if (particleSet.pointData != null) {
            particleSet.cPosD.set(camera.getPos());

            if (particleSet.focusIndex >= 0) {
                particleSet.updateFocus(camera);
            }

            // Touch task.
            if (particleSet.updaterTask != null) {
                particleSet.updaterTask.update(camera);
            }

        }
    }

    private void updateStarSet(ICamera camera,
                               StarSet set,
                               DatasetDescription datasetDesc) {
        // Fade node visibility
        if (set.active != null && set.active.length > 0 && set.pointData != null) {
            updateParticleSet(camera, set);

            // Update close stars
            int j = 0;
            for (int i = 0; i < Math.min(set.proximity.updating.length, set.pointData.size()); i++) {
                if (utils.filter(set.active[i], set, datasetDesc)
                        && set.isVisible(set.active[i])) {
                    IParticleRecord closeStar = set.pointData.get(set.active[i]);
                    set.proximity.set(j, set.active[i], closeStar, camera, set.currDeltaYears);
                    camera.checkClosestParticle(set.proximity.updating[j]);

                    // Model distance
                    if (j == 0) {
                        set.modelDist = 172.4643429 * closeStar.radius();
                    }
                    j++;
                }
            }
        }
    }
}
