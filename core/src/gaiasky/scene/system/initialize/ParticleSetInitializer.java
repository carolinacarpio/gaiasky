package gaiasky.scene.system.initialize;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.GaiaSky;
import gaiasky.data.AssetBean;
import gaiasky.data.group.IParticleGroupDataProvider;
import gaiasky.data.group.IStarGroupDataProvider;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.*;
import gaiasky.scene.entity.ParticleUtils;
import gaiasky.scenegraph.ParticleSetUpdaterTask;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.scenegraph.particle.VariableRecord;
import gaiasky.util.*;
import gaiasky.util.CatalogInfo.CatalogInfoSource;
import gaiasky.util.Logger.Log;
import gaiasky.util.camera.Proximity;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Initializes old ParticleGroup and StarGroup objects.
 */
public class ParticleSetInitializer extends InitSystem {
    private static final Log logger = Logger.getLogger(ParticleSetInitializer.class);

    private final ParticleUtils utils;

    public ParticleSetInitializer(boolean setUp, Family family, int priority) {
        super(setUp, family, priority);
        this.utils = new ParticleUtils();
    }

    @Override
    public void initializeEntity(Entity entity) {
        var particleSet = Mapper.particleSet.get(entity);
        var starSet = Mapper.starSet.get(entity);

        // Initialize particle set
        if (starSet == null) {
            initializeCommon(particleSet);
            initializeParticleSet(entity, particleSet);
        } else {
            initializeCommon(starSet);
            initializeStarSet(entity, starSet);
        }
    }

    @Override
    public void setUpEntity(Entity entity) {
        var starSet = Mapper.starSet.get(entity);

        if (starSet != null) {
            // Is it a catalog of variable stars?
            starSet.variableStars = starSet.pointData.size() > 0 && starSet.pointData.get(0) instanceof VariableRecord;
            initSortingData(entity, starSet);
            // Load model in main thread
            GaiaSky.postRunnable(() -> utils.initModel(AssetBean.manager(), Mapper.model.get(entity)));
        }

    }

    private void initializeCommon(ParticleSet set) {
        if (set.factor == null)
            set.factor = 1d;
        set.lastSortTime = -1;
        set.cPosD = new Vector3d();
        set.lastSortCameraPos = new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        set.proximity = new Proximity(Constants.N_DIR_LIGHTS);
    }

    private void initializeParticleSet(Entity entity, ParticleSet set) {
        initializeParticleSet(entity, set, true, true);
    }

    /**
     * Initializes a particle set. It loads the data from the provider
     *
     * @param entity            The entity.
     * @param set               The particle set.
     * @param dataLoad          Whether to load the data.
     * @param createCatalogInfo Whether to initialize the catalog info.
     */
    private void initializeParticleSet(Entity entity, ParticleSet set, boolean dataLoad, boolean createCatalogInfo) {
        // Load data
        try {
            set.isStars = false;

            if (dataLoad) {
                Class<?> clazz = Class.forName(set.provider);
                IParticleGroupDataProvider provider = (IParticleGroupDataProvider) clazz.getConstructor().newInstance();
                provider.setProviderParams(set.providerParams);

                set.setData(provider.loadData(set.datafile, set.factor));
            }

            computeMinMeanMaxDistances(set);
            computeMeanPosition(entity, set);
            setLabelPosition(entity);

            if (createCatalogInfo && Mapper.datasetDescription.has(entity)) {
                Base base = entity.getComponent(Base.class);
                DatasetDescription desc = entity.getComponent(DatasetDescription.class);
                // Create catalog info and broadcast
                CatalogInfo ci = new CatalogInfo(base.names[0], base.names[0], null, CatalogInfoSource.INTERNAL, 1f, entity);
                ci.nParticles = set.pointData != null ? set.pointData.size() : -1;
                Path df = Path.of(Settings.settings.data.dataFile(set.datafile));
                ci.sizeBytes = Files.exists(df) && Files.isRegularFile(df) ? df.toFile().length() : -1;

                // Insert
                EventManager.publish(Event.CATALOG_ADD, this, ci, false);
            }

        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e);
            set.pointData = null;
        }
    }

    /**
     * Initializes a star set. Loads the data from the provider and computes mean and
     * label positions.
     *
     * @param entity The entity.
     * @param set    The star set.
     */
    public void initializeStarSet(Entity entity, StarSet set) {
        // Load data
        try {
            set.isStars = true;

            Class<?> clazz = Class.forName(set.provider);
            IStarGroupDataProvider provider = (IStarGroupDataProvider) clazz.getConstructor().newInstance();
            provider.setProviderParams(set.providerParams);

            // Set data, generate index
            List<IParticleRecord> l = provider.loadData(set.datafile, set.factor);
            set.setData(l);

        } catch (Exception e) {
            Logger.getLogger(this.getClass()).error(e);
            set.pointData = null;
        }

        computeMeanPosition(entity, set);
        setLabelPosition(entity);

        // Default epochs, if not set
        if (set.epochJd <= 0) {
            set.epochJd = AstroUtils.JD_J2015_5;
        }

        if (set.variabilityEpochJd <= 0) {
            set.variabilityEpochJd = AstroUtils.JD_J2010;
        }

        // Maps.
        set.forceLabelStars = new HashSet<>();
        set.labelColors = new HashMap<>();
    }

    public void computeMinMeanMaxDistances(ParticleSet set) {
        set.meanDistance = 0;
        set.maxDistance = Double.MIN_VALUE;
        set.minDistance = Double.MAX_VALUE;
        List<Double> distances = new ArrayList<>();
        for (IParticleRecord point : set.data()) {
            // Add sample to mean distance
            double dist = len(point.x(), point.y(), point.z());
            if (Double.isFinite(dist)) {
                distances.add(dist);
                set.maxDistance = Math.max(set.maxDistance, dist);
                set.minDistance = Math.min(set.minDistance, dist);
            }
        }
        // Mean is computed as half of the 90th percentile to avoid outliers
        distances.sort(Double::compare);
        int idx = (int) Math.ceil((90d / 100d) * (double) distances.size());
        set.meanDistance = distances.get(idx - 1) / 2d;
    }

    public void computeMeanPosition(Entity entity, ParticleSet set) {
        var body = Mapper.body.get(entity);
        if (set.meanPosition != null) {
            body.pos.set(set.meanPosition);
        } else {
            // Mean position
            for (IParticleRecord point : set.data()) {
                body.pos.add(point.x(), point.y(), point.z());
            }
            body.pos.scl(1d / set.data().size());
        }
    }

    public void setLabelPosition(Entity entity) {
        // Label position
        if (Mapper.label.has(entity) && Mapper.body.has(entity)) {
            Label label = Mapper.label.get(entity);
            Body body = Mapper.body.get(entity);
            if (label.labelPosition == null) {
                label.labelPosition = new Vector3b(body.pos);
            }
        } else {
            Base base = entity.getComponent(Base.class);
            logger.warn("Particle set entity does not have label or body (or both): " + base.getName());
        }
    }

    private double len(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void initSortingData(Entity entity, StarSet starSet) {
        var pointData = starSet.pointData;

        // Metadata
        starSet.metadata = new double[pointData.size()];

        // Initialise indices list with natural order
        starSet.indices1 = new Integer[pointData.size()];
        starSet.indices2 = new Integer[pointData.size()];
        for (int i = 0; i < pointData.size(); i++) {
            starSet.indices1[i] = i;
            starSet.indices2[i] = i;
        }
        starSet.active = starSet.indices1;
        starSet.background = starSet.indices2;

        // Initialize updater task
        starSet.updaterTask = new ParticleSetUpdaterTask(entity, starSet, starSet);
    }
}
