package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.GaiaSky;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.GraphNode;
import gaiasky.scene.component.LocationMark;
import gaiasky.scenegraph.camera.ICamera;
import net.jafama.FastMath;

public class LocUpdater extends AbstractUpdateSystem {

    private ModelUpdater updater;

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
        var graph = Mapper.graph.get(entity);

        var parentBody = Mapper.body.get(graph.parent);
        var parentSa = Mapper.sa.get(graph.parent);

        ICamera cam = GaiaSky.instance.getICamera();
        if (parentBody.solidAngle > parentSa.thresholdQuad * 30f || cam.isFocus(entity)) {
            updateLocalValues(entity, graph, Mapper.loc.get(entity), cam);
        }
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
