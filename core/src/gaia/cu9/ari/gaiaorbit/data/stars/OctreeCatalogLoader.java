package gaia.cu9.ari.gaiaorbit.data.stars;

import gaia.cu9.ari.gaiaorbit.data.octreegen.MetadataBinaryIO;
import gaia.cu9.ari.gaiaorbit.data.octreegen.ParticleDataBinaryIO;
import gaia.cu9.ari.gaiaorbit.scenegraph.CelestialBody;
import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode;
import gaia.cu9.ari.gaiaorbit.scenegraph.Star;
import gaia.cu9.ari.gaiaorbit.scenegraph.octreewrapper.AbstractOctreeWrapper;
import gaia.cu9.ari.gaiaorbit.scenegraph.octreewrapper.OctreeWrapper;
import gaia.cu9.ari.gaiaorbit.scenegraph.octreewrapper.OctreeWrapperConcurrent;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.I18n;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;
import gaia.cu9.ari.gaiaorbit.util.tree.LoadStatus;
import gaia.cu9.ari.gaiaorbit.util.tree.OctreeNode;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;

public class OctreeCatalogLoader implements ICatalogLoader {

    String metadata, particles;

    @Override
    public List<? extends SceneGraphNode> loadCatalog() throws FileNotFoundException {
        Logger.info(this.getClass().getSimpleName(), I18n.bundle.format("notif.limitmag", GlobalConf.data.LIMIT_MAG_LOAD));

        MetadataBinaryIO metadataReader = new MetadataBinaryIO();
        OctreeNode<SceneGraphNode> root = (OctreeNode<SceneGraphNode>) metadataReader.readMetadata(Gdx.files.internal(metadata).read());

        ParticleDataBinaryIO particleReader = new ParticleDataBinaryIO();
        List<CelestialBody> particleList = particleReader.readParticles(Gdx.files.internal(particles).read());

        /**
         * CREATE OCTREE WRAPPER WITH ROOT NODE
         */
        AbstractOctreeWrapper octreeWrapper = null;
        if (GlobalConf.performance.MULTITHREADING) {
            octreeWrapper = new OctreeWrapperConcurrent("Universe", root);
        } else {
            octreeWrapper = new OctreeWrapper("Universe", root);
        }
        List<SceneGraphNode> result = new ArrayList<SceneGraphNode>(1);
        result.add(octreeWrapper);

        /**
         * ADD STARS
         */
        // Update model
        for (SceneGraphNode sgn : particleList) {
            Star s = (Star) sgn;
            OctreeNode<SceneGraphNode> octant = metadataReader.nodesMap.get(s.pageId).getFirst();
            octant.add(s);
            s.page = octant;
            // Update status
            octant.setStatus(LoadStatus.LOADED);

            // Add objects to octree wrapper node
            octreeWrapper.add(s, octant);
        }

        /**
         * MANUALLY ADD SUN
         */
        // Manually add sun
        Star sun = new Star(new Vector3d(0, 0, 0), 4.83f, 4.83f, 0.656f, "Sol", System.currentTimeMillis());
        sun.initialize();

        // Find out octant of sun
        OctreeNode<SceneGraphNode> candidate = root.getBestOctant(sun.pos);
        if (candidate == null) {
            Logger.error(new RuntimeException("No octant candidate for the Sun found!"));
        } else {
            sun.pageId = candidate.pageId;
            sun.page = candidate;
            // Add objects to octree wrapper node
            octreeWrapper.add(sun, candidate);
            candidate.add(sun);
        }

        Logger.info(this.getClass().getSimpleName(), I18n.bundle.format("notif.catalog.init", particleList.size()));

        return result;
    }

}
