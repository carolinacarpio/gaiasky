package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.GaiaSky;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.GraphNode;
import gaiasky.scene.component.RefSysTransform;
import gaiasky.scene.component.Trajectory;
import gaiasky.scene.component.Trajectory.OrbitOrientationModel;
import gaiasky.scene.entity.TrajectoryUtils;
import gaiasky.scene.record.OrbitComponent;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.coord.Coordinates;
import gaiasky.util.math.Matrix4d;
import gaiasky.util.time.ITimeFrameProvider;

import java.time.Instant;

/**
 * Updates trajectories and orbit objects of all classes and types.
 */
public class TrajectoryUpdater extends AbstractUpdateSystem {

    private final TrajectoryUtils utils;
    private final ITimeFrameProvider time;

    public TrajectoryUpdater(Family family, int priority) {
        super(family, priority);
        this.time = GaiaSky.instance.time;
        this.utils = new TrajectoryUtils();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        updateEntity(entity, deltaTime);
    }

    @Override
    public void updateEntity(Entity entity, float deltaTime) {
        var graph = Mapper.graph.get(entity);
        var transform = Mapper.transform.get(entity);
        var trajectory = Mapper.trajectory.get(entity);
        var verts = Mapper.verts.get(entity);

        if (trajectory.model == OrbitOrientationModel.EXTRASOLAR_SYSTEM) {
            utils.computeExtrasolarSystemTransformMatrix(graph, transform);
        }

        // Compute position percentage in the trajectory.
        if (verts.pointCloudData != null && verts.pointCloudData.hasTime()) {
            long now = time.getTime().toEpochMilli();
            long t0 = verts.pointCloudData.time.get(0).toEpochMilli();
            long t1 = verts.pointCloudData.time.get(verts.pointCloudData.getNumPoints() - 1).toEpochMilli();

            long t1t0 = t1 - t0;
            long nowt0 = now - t0;
            trajectory.coord = ((double) nowt0 / (double) t1t0) % 1d;
        }

        if (!trajectory.onlyBody) {
            if(Mapper.tagHeliotropic.has(entity)) {
                // Heliotropic orbit.
                updateLocalTransformHeliotropic(GaiaSky.instance.time.getTime(), graph, trajectory);
            } else {
                // Regular orbit.
                updateLocalTransformRegular(graph, trajectory, transform);
            }
        }
    }

    protected void updateLocalTransformHeliotropic(Instant date, GraphNode graph, Trajectory trajectory) {
        Matrix4d localTransformD = trajectory.localTransformD;

        double sunLongitude = AstroUtils.getSunLongitude(date);
        graph.translation.setToTranslation(localTransformD)
                .mul(Coordinates.eclToEq())
                .rotate(0, 1, 0, sunLongitude + 180);

        localTransformD.putIn(graph.localTransform);
    }

    protected void updateLocalTransformRegular(GraphNode graph, Trajectory trajectory, RefSysTransform transform) {
        var localTransformD = trajectory.localTransformD;
        var transformFunction = transform.matrix;

        var parentGraph = Mapper.graph.get(graph.parent);

        graph.translation.setToTranslation(localTransformD);
        if (trajectory.newMethod) {
            if (transformFunction != null) {
                localTransformD.mul(transformFunction);
            }
            if (parentGraph.orientation != null) {
                localTransformD.mul(parentGraph.orientation);

            }
            if(trajectory.model.isExtrasolar()) {
               localTransformD.rotate(0, 1, 0, 90);
            }
        } else if (trajectory.oc != null) {
            OrbitComponent oc = trajectory.oc;

            if (transformFunction == null && parentGraph.orientation != null)
                localTransformD.mul(parentGraph.orientation);
            if (transformFunction != null)
                localTransformD.mul(transformFunction);

            localTransformD.rotate(0, 1, 0, oc.argofpericenter)
                    .rotate(0, 0, 1, oc.i)
                    .rotate(0, 1, 0, oc.ascendingnode);
        }
        localTransformD.putIn(graph.localTransform);
    }
}
