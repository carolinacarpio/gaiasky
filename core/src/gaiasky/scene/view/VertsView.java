package gaiasky.scene.view;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import gaiasky.data.util.PointCloudData;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.render.ComponentTypes;
import gaiasky.render.api.IGPUVertsRenderable;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.*;
import gaiasky.scene.entity.EntityUtils;
import gaiasky.scenegraph.SceneGraphNode;
import gaiasky.util.math.Vector3d;

/**
 * A view which exposes common vertex buffer renderable operations.
 * Can be reused for multiple entities by using {@link #setEntity(Entity)}.
 */
public class VertsView extends BaseView implements IGPUVertsRenderable {

    /** The verts component . **/
    private Verts verts;
    /** The graph component. **/
    private GraphNode graph;
    /** The trajectory component (if any). **/
    private Trajectory trajectory;

    public VertsView() {
        super();
    }

    public VertsView(Entity entity) {
        super(entity);
    }

    @Override
    protected void entityCheck(Entity entity) {
        super.entityCheck(entity);
        check(entity, Mapper.verts, Verts.class);
        check(entity, Mapper.graph, GraphNode.class);
    }

    @Override
    protected void entityChanged() {
        super.entityChanged();
        this.verts = Mapper.verts.get(entity);
        this.graph = Mapper.graph.get(entity);
        this.trajectory = Mapper.trajectory.get(entity);
    }

    @Override
    protected void entityCleared() {
        this.verts = null;
        this.graph = null;
        this.trajectory = null;
    }

    @Override
    public void markForUpdate() {
        EventManager.publish(Event.GPU_DISPOSE_VERTS_OBJECT, Mapper.render.get(entity), verts.renderGroup);
    }

    @Override
    public PointCloudData getPointCloud() {
        return verts.pointCloudData;
    }

    @Override
    public float[] getColor() {
        return body.color;
    }

    @Override
    public double getAlpha() {
        return trajectory != null ? trajectory.alpha : body.color[3];
    }

    @Override
    public Matrix4 getLocalTransform() {
        return graph.localTransform;
    }

    @Override
    public SceneGraphNode getParent() {
        return null;
    }

    @Override
    public Entity getParentEntity() {
        return graph.parent;
    }

    @Override
    public boolean isClosedLoop() {
        return verts.closedLoop;
    }

    @Override
    public void setClosedLoop(boolean closedLoop) {
        verts.closedLoop = closedLoop;
    }

    @Override
    public void blend() {
        EntityUtils.blend(verts);
    }

    @Override
    public void depth() {
        EntityUtils.depth(verts);
    }

    @Override
    public int getGlPrimitive() {
        return verts.glPrimitive;
    }

    @Override
    public void setPrimitiveSize(float size) {
        verts.primitiveSize = size;
    }

    @Override
    public float getPrimitiveSize() {
        return verts.primitiveSize;
    }

    @Override
    public ComponentTypes getComponentType() {
        return base.ct;
    }

    @Override
    public double getDistToCamera() {
        return body.distToCamera;
    }

    @Override
    public float getOpacity() {
        return base.opacity;
    }

    /**
     * Sets the 3D points of the line in the internal reference system.
     *
     * @param points Vector with the points. If length is not multiple of 3, some points are discarded.
     */
    public void setPoints(double[] points) {
        int n = points.length;
        if (n % 3 != 0) {
            n = n - n % 3;
        }
        if (verts.pointCloudData == null)
            verts.pointCloudData = new PointCloudData(n / 3);
        else
            verts.pointCloudData.clear();

        verts.pointCloudData.addPoints(points);
        markForUpdate();
    }

    /**
     * Adds the given points to this data
     *
     * @param points The points to add
     */
    public void addPoints(double[] points) {
        if (verts.pointCloudData == null) {
            setPoints(points);
        } else {
            verts.pointCloudData.addPoints(points);
            markForUpdate();
        }
    }

    /**
     * Adds the given point ot this data
     *
     * @param point The point to add
     */
    public void addPoint(Vector3d point) {
        if (verts.pointCloudData == null) {
            setPoints(point.values());
        } else {
            verts.pointCloudData.addPoint(point);
            markForUpdate();
        }

    }

    public boolean isEmpty() {
        return verts.pointCloudData.isEmpty();
    }

    /**
     * Clears the data from this object, both in RAM and VRAM
     */
    public void clear() {
        setPoints(new double[] {});
    }
}
