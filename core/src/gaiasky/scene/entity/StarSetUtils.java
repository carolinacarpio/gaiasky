package gaiasky.scene.entity;

import com.badlogic.ashley.core.Entity;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.render.ComponentTypes;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.scene.Archetype;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.component.ParticleSet;
import gaiasky.scene.component.StarSet;
import gaiasky.scene.system.initialize.ParticleSetInitializer;
import gaiasky.scenegraph.StarGroup;
import gaiasky.scenegraph.camera.CameraManager;
import gaiasky.scenegraph.camera.CameraManager.CameraMode;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.util.Constants;

import java.util.List;

/**
 * Utilities to construct star sets. Used for star sets in an octree.
 */
public class StarSetUtils {

    /** Reference to the scene. **/
    private final Scene scene;

    /** Constructs a star set utils with the given scene. **/
    public StarSetUtils(Scene scene) {
       this.scene = scene;
    }

    /**
     * Creates a default star set entity with some sane parameters, given the name and the data
     *
     * @param name     The name of the star group. Any occurrence of '%%SGID%%' in name will be replaced with the id of the star group.
     * @param data     The data of the star group.
     * @param initializer  The initializer to use for the star set initialization, or null.
     *
     * @return A new star group with sane parameters
     */
    public Entity getDefaultStarSet(String name, List<IParticleRecord> data, ParticleSetInitializer initializer) {
        Archetype archetype = scene.archetypes().get(StarGroup.class);
        Entity entity = archetype.createEntity();

        var base = Mapper.base.get(entity);
        base.id = ParticleSet.idSeq++;
        base.setName(name.replace("%%SGID%%", Long.toString(base.id)));
        base.ct = new ComponentTypes(ComponentType.Stars);

        var graph = Mapper.graph.get(entity);
        graph.setParent(Scene.ROOT_NAME);

        var body = Mapper.body.get(entity);
        body.setLabelColor(new double[] { 1.0, 1.0, 1.0, 1.0 });
        body.setColor(new double[] { 1.0, 1.0, 1.0, 0.25 });
        body.setSize(6.0 * Constants.DISTANCE_SCALE_FACTOR);

        var label = Mapper.label.get(entity);
        label.setLabelposition(new double[] { 0.0, -5.0e7, -4e8 });

        var set = Mapper.starSet.get(entity);
        set.setData(data);

        if (initializer != null) {
            initializer.setUpEntity(entity);
        }
        return entity;
    }

    public void dispose(Entity entity, StarSet set) {
        set.disposed = true;
        if (GaiaSky.instance.scene != null) {
            GaiaSky.instance.scene.remove(entity, true);
        }
        // Unsubscribe from all events
        EventManager.instance.removeRadioSubscriptions(entity);
        // Data to be gc'd
        set.pointData = null;
        // Remove focus if needed
        CameraManager cam = GaiaSky.instance.getCameraManager();
        if (cam != null && cam.getFocus() != null && cam.getFocus() == entity) {
            set.setFocusIndex(-1);
            EventManager.publish(Event.CAMERA_MODE_CMD, this, CameraMode.FREE_MODE);
        }
    }
}
