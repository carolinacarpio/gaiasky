/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.data;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.TimeUtils;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.scenegraph.Constellation;
import gaiasky.scenegraph.SceneGraphNode;
import gaiasky.scenegraph.octreewrapper.AbstractOctreeWrapper;
import gaiasky.util.i18n.I18n;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import gaiasky.util.concurrent.ServiceThread;
import gaiasky.util.tree.LoadStatus;
import gaiasky.util.tree.OctreeNode;
import uk.ac.starlink.util.DataSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Contains the infrastructure common to all multi-file octree loaders which
 * streams data on-demand from disk and unloads unused data.
 */
public abstract class StreamingOctreeLoader implements IObserver, ISceneGraphLoader {
    private static final Log logger = Logger.getLogger(StreamingOctreeLoader.class);
    /**
     * Data will be pre-loaded at startup down to this octree depth.
     */
    protected static final int PRELOAD_DEPTH = 3;
    /**
     * Default load queue size in octants.
     */
    protected static final int LOAD_QUEUE_MAX_SIZE = 100;

    /**
     * Minimum time to pass to be able to clear the queue again.
     */
    protected static final long MIN_QUEUE_CLEAR_MS = 2000;

    /**
     * Maximum number of pages to send to load every batch.
     **/
    protected static final int MAX_LOAD_CHUNK = 5;

    public static StreamingOctreeLoader instance;

    /**
     * Current number of stars that are loaded.
     **/
    protected int nLoadedStars = 0;
    /**
     * Max number of stars loaded at once.
     **/
    protected final long maxLoadedStars;

    /**
     * The octant loading queue.
     **/
    protected Queue<OctreeNode> toLoadQueue;

    /**
     * Whether loading is paused or not.
     **/
    protected boolean loadingPaused = false;

    /**
     * Last time of a queue clear event went through.
     **/
    protected long lastQueueClearMs = 0;

    // Dataset name and description.
    protected String name, description;
    // Dataset parameters
    protected Map<String, Object> params;

    /**
     * This queue is sorted ascending by access date, so that we know which
     * element to release if needed (oldest).
     **/
    protected Queue<OctreeNode> toUnloadQueue;

    /**
     * Loaded octant ids, for logging.
     **/
    protected long[] loadedIds;
    protected int loadedObjects;
    protected int maxLoadedIds, idxLoadedIds;

    protected String metadata, particles;

    /**
     * Daemon thread that gets the data loading requests and serves them.
     **/
    protected OctreeLoaderThread daemon;

    public StreamingOctreeLoader() {
        // TODO Use memory info to figure this out
        // We assume 1Gb of graphics memory.
        // GPU ~ 32 byte/star
        // CPU ~ 136 byte/star
        maxLoadedStars = Settings.settings.scene.octree.maxStars;
        logger.info("Maximum loaded stars setting: " + maxLoadedStars);

        Comparator<OctreeNode> depthComparator = Comparator.comparingInt((OctreeNode o) -> o.depth);
        toLoadQueue = new PriorityBlockingQueue<>(LOAD_QUEUE_MAX_SIZE, depthComparator);
        toUnloadQueue = new ArrayBlockingQueue<>(LOAD_QUEUE_MAX_SIZE);

        maxLoadedIds = 50;
        idxLoadedIds = 0;
        loadedIds = new long[maxLoadedIds];

        EventManager.instance.subscribe(this, Event.DISPOSE, Event.PAUSE_BACKGROUND_LOADING, Event.RESUME_BACKGROUND_LOADING);
    }

    @Override
    public void initialize(DataSource ds) {
    }

    @Override
    public void initialize(String[] files) throws RuntimeException {
        if (files == null || files.length < 2) {
            throw new RuntimeException("Error loading octree files: " + (files != null ? files.length : "files array is null"));
        }
        particles = files[0];
        metadata = files[1];
    }

    @Override
    public Array<? extends SceneGraphNode> loadData() {
        AbstractOctreeWrapper octreeWrapper = loadOctreeData();

        if (octreeWrapper != null) {
            // Initialize daemon loader thread.
            daemon = new OctreeLoaderThread(octreeWrapper, this);
            daemon.setDaemon(true);
            daemon.setName("gaiasky-worker-octreeload");
            daemon.setPriority(Thread.MIN_PRIORITY);
            daemon.start();

            // Initialize timer to flush the queue at regular intervals.
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    flushLoadQueue();
                }

            }, 1000, 1000);

            // Add octreeWrapper to result list and return.
            Array<SceneGraphNode> result = new Array<>(false, 1);
            result.add(octreeWrapper);

            logger.info(I18n.msg("notif.catalog.init", octreeWrapper.root.countObjects()));

            return result;
        } else {
            return new Array<>(false, 1);
        }
    }

    protected void addLoadedInfo(long id, int nobjects) {
        if (idxLoadedIds >= maxLoadedIds) {
            flushLoadedIds();
        }
        loadedIds[idxLoadedIds++] = id;
        loadedObjects += nobjects;
    }

    protected void flushLoadedIds() {
        if (idxLoadedIds > 0) {
            String str = "[" + loadedIds[0] + ", ..., " + loadedIds[idxLoadedIds - 1] + "]";
            logger.info(I18n.msg("notif.octantsloaded", loadedObjects, idxLoadedIds, str));

            idxLoadedIds = 0;
            loadedObjects = 0;
        }

    }

    /**
     * Loads the nodes and the octree.
     */
    protected abstract AbstractOctreeWrapper loadOctreeData();

    /**
     * Adds the octant to the load queue.
     */
    public static void queue(OctreeNode octant) {
        if (instance != null && instance.daemon != null) {
            instance.addToQueue(octant);
        }
    }

    /**
     * Clears the current load queue.
     */
    public static void clearQueue() {
        if (instance != null && instance.daemon != null) {
            if (TimeUtils.millis() - instance.lastQueueClearMs > MIN_QUEUE_CLEAR_MS) {
                instance.emptyLoadQueue();
                instance.lastQueueClearMs = TimeUtils.millis();
            }
            instance.abortCurrentLoading();
        }
    }

    public static int getLoadQueueSize() {
        if (instance != null && instance.daemon != null) {
            return instance.toLoadQueue.size();
        } else {
            return -1;
        }
    }

    public static int getNLoadedStars() {
        if (instance != null && instance.daemon != null) {
            return instance.nLoadedStars;
        } else {
            return -1;
        }
    }

    /**
     * Moves the octant to the end of the unload queue.
     */
    public static void touch(OctreeNode octant) {
        if (instance != null && instance.daemon != null) {
            instance.touchOctant(octant);
        }
    }

    /**
     * Removes all octants from the current load queue. This happens when the
     * camera viewport changes radically (velocity is high, direction changes a
     * lot, etc.) so that the old octants are dropped and newly observed octants
     * are loaded right away.
     */
    public void emptyLoadQueue() {
        int n = toLoadQueue.size();
        if (n > 0) {
            for (OctreeNode octant : toLoadQueue) {
                octant.setStatus(LoadStatus.NOT_LOADED);
            }
            toLoadQueue.clear();
            //logger.info(I18n.txt("notif.loadingoctants.emtpied", n));
        }
    }

    public void addToQueue(OctreeNode octant) {
        // Add only if there is room.
        if (!loadingPaused) {
            if (toLoadQueue.size() >= LOAD_QUEUE_MAX_SIZE) {
                OctreeNode out = toLoadQueue.poll();
                out.setStatus(LoadStatus.NOT_LOADED);
            }
            toLoadQueue.add(octant);
            octant.setStatus(LoadStatus.QUEUED);
        }
    }

    /**
     * Puts it at the end of the toUnloadQueue.
     **/
    public void touchOctant(OctreeNode octant) {
        // Since higher levels are always observed, or 'touched',
        // it follows naturally that lower levels will always be kept
        // at the head of the queue, whereas higher level octants
        // are always at the tail and are the last to be unloaded
        toUnloadQueue.remove(octant);
        // Only attempt to unload the octants with a depth larger than preload_depth
        if (octant.depth > PRELOAD_DEPTH)
            toUnloadQueue.offer(octant);
    }

    /**
     * Tells the loader to start loading the octants in the queue.
     */
    public void flushLoadQueue() {
        if (!daemon.isAwake() && !toLoadQueue.isEmpty() && !loadingPaused) {
            synchronized (daemon.getThreadLock()) {
                EventManager.publish(Event.BACKGROUND_LOADING_INFO, this);
                daemon.getThreadLock().notifyAll();
            }
        }
    }

    /**
     * Tells the daemon to immediately stop the loading of
     * octants and wait for new data
     */
    public void abortCurrentLoading() {
        daemon.abort();
    }

    /**
     * Loads all the levels of detail until the given one.
     *
     * @param lod           The level of detail to load.
     * @param octreeWrapper The octree wrapper.
     *
     * @throws IOException When any of the level's files fails to load.
     */
    public void loadLod(final Integer lod, final AbstractOctreeWrapper octreeWrapper) throws IOException {
        loadOctant(octreeWrapper.root, octreeWrapper, lod);
    }

    /**
     * Loads the data of the given octant and its children down to the given
     * level.
     *
     * @param octant        The octant to load.
     * @param octreeWrapper The octree wrapper.
     * @param level         The depth to load.
     *
     * @throws IOException When the octant's file fails to load.
     */
    public void loadOctant(final OctreeNode octant, final AbstractOctreeWrapper octreeWrapper, Integer level) throws IOException {
        if (level >= 0) {
            loadOctant(octant, octreeWrapper, false);
            if (octant.children != null) {
                for (OctreeNode child : octant.children) {
                    if (child != null && child.numObjectsRec > 0)
                        loadOctant(child, octreeWrapper, level - 1);
                }
            }
        }
    }

    /**
     * Loads the objects of the given octants.
     *
     * @param octants       The list holding the octants to load.
     * @param octreeWrapper The octree wrapper.
     * @param abort         State variable that will be set to true if an abort is called.
     *
     * @return The actual number of loaded octants.
     *
     * @throws IOException When any of the octants' files fail to load.
     */
    public int loadOctants(final Array<OctreeNode> octants, final AbstractOctreeWrapper octreeWrapper, final AtomicBoolean abort) throws IOException {
        int loaded = 0;
        if (octants.size > 0) {
            int i = 0;
            OctreeNode octant = octants.get(0);
            while (i < octants.size && !abort.get()) {
                if (loadOctant(octant, octreeWrapper, true))
                    loaded++;
                i += 1;
                octant = octants.get(i);
            }
            flushLoadedIds();

            if (abort.get()) {
                // We aborted, roll back status of rest of octants
                for (int j = i; j < octants.size; j++) {
                    octants.get(j).setStatus(LoadStatus.NOT_LOADED);
                }
            }
        }
        return loaded;
    }

    /**
     * Unloads the given octant.
     */
    public void unloadOctant(OctreeNode octant, final AbstractOctreeWrapper octreeWrapper) {
        List<SceneGraphNode> objects = octant.objects;
        if (objects != null) {
            GaiaSky.postRunnable(() -> {
                synchronized (octant) {
                    try {
                        int unloaded = 0;
                        for (SceneGraphNode object : objects) {
                            int count = object.getStarCount();
                            object.dispose();
                            object.octant = null;
                            octreeWrapper.removeParenthood(object);
                            // Aux info
                            if (GaiaSky.instance != null && GaiaSky.instance.sceneGraph != null)
                                GaiaSky.instance.sceneGraph.removeNodeAuxiliaryInfo(object);

                            nLoadedStars -= count;
                            unloaded += count;
                        }
                        objects.clear();
                        octant.setStatus(LoadStatus.NOT_LOADED);
                        octant.touch(unloaded);
                    } catch (Exception e) {
                        logger.error("Error disposing octant's objects " + octant.pageId, e);
                        logger.info(Settings.APPLICATION_NAME + " will attempt to continue");
                    }
                }
            });
        }
    }

    /**
     * Loads the data of the given octant.
     *
     * @param octant        The octant to load.
     * @param octreeWrapper The octree wrapper.
     * @param fullInit      Whether to fully initialise the objects (on-demand load) or
     *                      not (startup)
     *
     * @return True if the octant was loaded, false otherwise
     *
     * @throws IOException When the octant file could not be read.
     */
    public abstract boolean loadOctant(final OctreeNode octant, final AbstractOctreeWrapper octreeWrapper, boolean fullInit) throws IOException;

    /**
     * The daemon loader thread.
     */
    protected static class OctreeLoaderThread extends ServiceThread {
        private final AbstractOctreeWrapper octreeWrapper;
        private final Array<OctreeNode> toLoad;
        private final AtomicBoolean abort;

        public OctreeLoaderThread(AbstractOctreeWrapper aow, StreamingOctreeLoader loader) {
            super();
            this.octreeWrapper = aow;
            this.toLoad = new Array<>();
            this.abort = new AtomicBoolean(false);

            this.task = () -> {
                /* ----------- PROCESS OCTANTS ----------- */
                while (!instance.toLoadQueue.isEmpty()) {
                    toLoad.clear();
                    int i = 0;
                    while (instance.toLoadQueue.peek() != null && i <= MAX_LOAD_CHUNK) {
                        OctreeNode octant = instance.toLoadQueue.poll();
                        toLoad.add(octant);
                        i++;
                    }

                    // Load octants, if any.
                    if (toLoad.size > 0) {
                        try {
                            loader.loadOctants(toLoad, octreeWrapper, abort);
                        } catch (Exception e) {
                            // This will happen when the queue has been cleared during processing.
                            logger.debug(I18n.msg("notif.loadingoctants.queue.clear"));
                        }
                    }

                    // Release resources if needed.
                    int nUnloaded = 0;
                    int nStars = loader.nLoadedStars;
                    if (running && nStars >= loader.maxLoadedStars) //-V6007
                        while (true) {
                            // Get first in queue (non-accessed for the longest time)
                            // and release it.
                            OctreeNode octant = loader.toUnloadQueue.poll();
                            if (octant != null && octant.getStatus() == LoadStatus.LOADED) {
                                loader.unloadOctant(octant, octreeWrapper);
                            }
                            if (octant != null && octant.objects != null && octant.objects.size() > 0) {
                                SceneGraphNode sg = octant.objects.get(0);
                                nUnloaded += sg.getStarCount();
                                if (nStars - nUnloaded < loader.maxLoadedStars * 0.85) {
                                    break;
                                }
                            }
                        }

                    // Update constellations :S
                    GaiaSky.postRunnable(() -> Constellation.updateConstellations(GaiaSky.instance.sceneGraph));

                }
                this.abort.set(false);
            };
        }

        public void abort() {
            this.abort.set(true);
        }

    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        switch (event) {
        case PAUSE_BACKGROUND_LOADING:
            loadingPaused = true;
            clearQueue();
            logger.info("Background data loading thread paused");
            break;
        case RESUME_BACKGROUND_LOADING:
            loadingPaused = false;
            clearQueue();
            logger.info("Background data loading thread resumed");
            break;
        case DISPOSE:
            if (daemon != null) {
                daemon.stopDaemon();
            }
            break;
        default:
            break;
        }

    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
