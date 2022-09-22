/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.data;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.data.orbit.OrbitSamplerDataProvider;
import gaiasky.data.util.OrbitDataLoader.OrbitDataLoaderParameters;
import gaiasky.data.util.PointCloudData;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.scene.Mapper;
import gaiasky.scene.entity.TrajectoryUtils;
import gaiasky.scene.view.VertsView;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.concurrent.ServiceThread;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Refreshes sampled orbit data from the underlying data provider algorithms.
 */
public class OrbitRefresher implements IObserver {
    private static final Log logger = Logger.getLogger(OrbitRefresher.class);

    // Maximum size of load queue.
    private static final int LOAD_QUEUE_MAX_SIZE = 15;
    // Maximum number of pages to send to load every batch.
    protected static final int MAX_LOAD_CHUNK = 5;

    private final Queue<OrbitDataLoaderParameters> toLoadQueue;
    private final OrbitUpdaterThread daemon;
    private final boolean loadingPaused = false;

    public OrbitRefresher(String threadName) {
        super();
        toLoadQueue = new ArrayBlockingQueue<>(LOAD_QUEUE_MAX_SIZE);

        // Start daemon
        daemon = new OrbitUpdaterThread(this);
        daemon.setDaemon(true);
        daemon.setName(threadName);
        daemon.setPriority(Thread.MIN_PRIORITY);
        daemon.start();

        EventManager.instance.subscribe(this, Event.DISPOSE);
    }

    public OrbitRefresher() {
        this("gaiasky-worker-orbitupdate");
    }

    public void queue(OrbitDataLoaderParameters params) {
        if (!loadingPaused && toLoadQueue.size() < LOAD_QUEUE_MAX_SIZE - 1) {
            toLoadQueue.remove(params);
            toLoadQueue.add(params);
            if(params.entity != null) {
                Mapper.trajectory.get(params.entity).refreshing = true;
            }
            daemon.wakeUp();
        }
    }

    @Override
    public void notify(Event event, Object source, Object... data) {
        if (event == Event.DISPOSE && daemon != null) {
            daemon.stopDaemon();
        }
    }

    /**
     * The orbit refresher thread.
     */
    protected static class OrbitUpdaterThread extends ServiceThread {
        private final OrbitSamplerDataProvider provider;
        private final Array<OrbitDataLoaderParameters> toLoad;

        public OrbitUpdaterThread(final OrbitRefresher orbitRefresher) {
            super();
            this.toLoad = new Array<>();
            this.provider = new OrbitSamplerDataProvider();
            this.task = () -> {
                /* ----------- PROCESS REQUESTS ----------- */
                while (!orbitRefresher.toLoadQueue.isEmpty()) {
                    toLoad.clear();
                    int i = 0;
                    while (orbitRefresher.toLoadQueue.peek() != null && i <= MAX_LOAD_CHUNK) {
                        OrbitDataLoaderParameters param = orbitRefresher.toLoadQueue.poll();
                        toLoad.add(param);
                        i++;
                    }

                    // Generate orbits if any
                    if (toLoad.size > 0) {
                        try {
                            for (OrbitDataLoaderParameters params : toLoad) {
                                if (params.entity != null) {
                                    Entity entity = params.entity;
                                    // Generate data
                                    provider.load(null, params);
                                    final PointCloudData pcd = provider.getData();
                                    // Post new data to object
                                    GaiaSky.postRunnable(() -> {
                                        // Update orbit object
                                        var utils = new TrajectoryUtils();
                                        var vertsView = new VertsView(entity);

                                        var body = Mapper.body.get(entity);
                                        var verts = Mapper.verts.get(entity);
                                        var trajectory = Mapper.trajectory.get(entity);
                                        verts.pointCloudData = pcd;
                                        utils.initOrbitMetadata(body, trajectory, verts);
                                        vertsView.markForUpdate();

                                        trajectory.refreshing = false;
                                    });

                                }
                            }
                        } catch (Exception e) {
                            // This will happen when the queue has been cleared during processing.
                            logger.debug("Refreshing orbits operation failed.");
                        }
                    }
                }
            };
        }
    }
}
