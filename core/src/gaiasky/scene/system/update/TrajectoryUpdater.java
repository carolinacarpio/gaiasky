package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import gaiasky.GaiaSky;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.GraphNode;
import gaiasky.scene.component.RefSysTransform;
import gaiasky.scene.component.Trajectory;
import gaiasky.scene.component.Trajectory.OrbitOrientationModel;
import gaiasky.scene.entity.TrajectoryUtils;
import gaiasky.scenegraph.component.OrbitComponent;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.coord.Coordinates;
import gaiasky.util.math.Matrix4d;
import gaiasky.util.time.ITimeFrameProvider;

import java.time.Instant;

/**
 * Updates trajectories and orbit objects of all classes and types.
 */
public class TrajectoryUpdater extends IteratingSystem implements EntityUpdater {

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

        if (trajectory.model == OrbitOrientationModel.EXTRASOLAR_SYSTEM)
            utils.computeExtrasolarSystemTransformMatrix(graph, transform);

        // Completion
        if (verts.pointCloudData != null) {
            long now = time.getTime().toEpochMilli();
            long t0 = verts.pointCloudData.time.get(0).toEpochMilli();
            long t1 = verts.pointCloudData.time.get(verts.pointCloudData.getNumPoints() - 1).toEpochMilli();

            long t1t0 = t1 - t0;
            long nowt0 = now - t0;
            trajectory.coord = ((double) nowt0 / (double) t1t0) % 1d;
        }

        if (!trajectory.onlyBody) {
            if(Mapper.tagHeliotropic.has(entity)) {
                // Heliotropic orbit
                updateLocalTransformHeliotropic(GaiaSky.instance.time.getTime(), graph, trajectory, transform);
            } else {
                // Regular orbit
                updateLocalTransformRegular(graph, trajectory, transform);
            }
        }
    }

    protected void updateLocalTransformHeliotropic(Instant date, GraphNode graph, Trajectory trajectory, RefSysTransform transform) {
        Matrix4 localTransform = graph.localTransform;
        Matrix4d localTransformD = trajectory.localTransformD;

        double sunLongitude = AstroUtils.getSunLongitude(date);
        graph.translation.getMatrix(localTransformD).mul(Coordinates.eclToEq()).rotate(0, 1, 0, sunLongitude + 180);

        localTransformD.putIn(localTransform);
    }

    protected void updateLocalTransformRegular(GraphNode graph, Trajectory trajectory, RefSysTransform transform) {
        Matrix4 localTransform = graph.localTransform;
        Matrix4d localTransformD = trajectory.localTransformD;
        Matrix4d transformFunction = transform.matrix;
        OrbitComponent oc = trajectory.oc;

        var parentGraph = Mapper.graph.get(graph.parent);

        graph.translation.getMatrix(localTransformD);
        if (trajectory.newMethod) {
            if (transformFunction != null) {
                localTransformD.mul(transformFunction);
                localTransformD.rotate(0, 1, 0, 90);
            }
            if (parentGraph.orientation != null) {
                localTransformD.mul(parentGraph.orientation);
                localTransformD.rotate(0, 1, 0, 90);
            }
        } else {
            if (transformFunction == null && parentGraph.orientation != null)
                localTransformD.mul(parentGraph.orientation);
            if (transformFunction != null)
                localTransformD.mul(transformFunction);

            localTransformD.rotate(0, 1, 0, oc.argofpericenter);
            localTransformD.rotate(0, 0, 1, oc.i);
            localTransformD.rotate(0, 1, 0, oc.ascendingnode);
        }
        localTransformD.putIn(localTransform);
    }
}
