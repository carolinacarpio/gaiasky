package gaiasky.util.camera;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import gaiasky.render.ComponentTypes;
import gaiasky.scenegraph.IFocus;
import gaiasky.scenegraph.SceneGraphNode;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.camera.NaturalCamera;
import gaiasky.scenegraph.component.RotationComponent;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.util.math.*;
import gaiasky.util.time.ITimeFrameProvider;
import gaiasky.util.tree.OctreeNode;

/**
 * Holds information on the order and properties of nearby particles to the camera.
 */
public class Proximity {
    private static final int DEFAULT_SIZE = 4;

    public NearbyRecord[] array;

    public Proximity() {
        this(DEFAULT_SIZE);
    }

    public Proximity(int size) {
        this.array = new NearbyRecord[size];
    }

    public void set(int index, IParticleRecord pr, ICamera camera) {
        this.set(index, pr, camera, 0);
    }

    public void set(int index, IParticleRecord pr, ICamera camera, double deltaYears) {
        if (this.array[index] == null) {
            this.array[index] = new NearbyRecord();
        }
        NearbyRecord c = this.array[index];
        convert(pr, c, camera, deltaYears);
    }

    public void set(int index, IFocus focus, ICamera camera) {
        if (this.array[index] == null) {
            this.array[index] = new NearbyRecord();
        }
        NearbyRecord c = this.array[index];
        convert(focus, c, camera);
    }

    /**
     * Sets the given record at the given index, overwriting
     * the current value
     *
     * @param index  The index
     * @param record The nearby record
     */
    public void set(int index, NearbyRecord record) {
        this.array[index] = record;
    }

    /**
     * Inserts the given record at the given index,
     * moving the rest of the values to the right
     *
     * @param index  The index
     * @param record The nearby record
     */
    public void insert(int index, NearbyRecord record) {
        NearbyRecord oldRecord;
        NearbyRecord newRecord = record;
        for (int i = index; i < this.array.length; i++) {
            oldRecord = this.array[i];
            this.array[i] = newRecord;
            newRecord = oldRecord;
        }
    }

    /**
     * Inserts the given object at the given index,
     * moving the rest of the values to the right
     *
     * @param index  The index
     * @param object The nearby record
     */
    public void insert(int index, IFocus object, ICamera camera) {
        NearbyRecord record = convert(object, new NearbyRecord(), camera);
        insert(index, record);
    }

    /**
     * Updates the list of proximal objects with the given {@link NearbyRecord}
     *
     * @param object The record to use for updating
     * @return Whether this proximity array was modified
     */
    public boolean update(NearbyRecord object) {
        int i = 0;
        for (NearbyRecord record : array) {
            if (record == null) {
                set(i, object);
            } else if (record == object) {
                // Already in
                return false;
            } else if (object.distToCamera < record.distToCamera) {
                insert(i, object);
                return true;
            }
            i++;
        }
        return false;
    }

    /**
     * Updates the list of proximal objects with the given {@link IFocus}
     *
     * @param object The record to use for updating
     * @return Whether this proximity array was modified
     */
    public boolean update(IFocus object, ICamera camera) {
        int i = 0;
        for (NearbyRecord record : array) {
            if (record == null) {
                set(i, object, camera);
            } else if (record == object) {
                // Already in
                return false;
            } else if (object.getName().equalsIgnoreCase(record.name)) {
                // Update

            } else if (object.getClosestDistToCamera() < record.distToCamera) {
                // Insert
                insert(i, object, camera);
                return true;
            }
            i++;
        }
        return false;
    }

    public void clear() {
        for (int i = 0; i < this.array.length; i++) {
            this.array[i] = null;
        }
    }

    public NearbyRecord convert(IParticleRecord pr, NearbyRecord c, ICamera camera, double deltaYears) {
        c.pm.set(pr.pmx(), pr.pmy(), pr.pmz()).scl(deltaYears);
        c.absolutePos.set(pr.x(), pr.y(), pr.z()).add(c.pm);
        c.pos.set(c.absolutePos).sub(camera.getPos());
        c.size = pr.size();
        c.radius = pr.radius();
        c.distToCamera = c.pos.len() - c.radius;
        c.name = pr.names()[0];

        Color col = new Color();
        Color.abgr8888ToColor(col, pr.col());
        c.col[0] = col.r;
        c.col[1] = col.g;
        c.col[2] = col.b;
        c.col[3] = col.a;
        return c;
    }

    public NearbyRecord convert(IFocus focus, NearbyRecord c, ICamera camera) {
        c.pm.set(0, 0, 0);
        Vector3b absPos = new Vector3b();
        absPos = focus.getAbsolutePosition(absPos);
        c.absolutePos.set(absPos);
        c.pos.set(c.absolutePos).sub(camera.getPos());
        c.size = focus.getSize();
        c.radius = focus.getRadius();
        c.distToCamera = focus.getClosestDistToCamera();
        c.name = focus.getName();

        float[] col = focus.getColor();
        c.col[0] = col[0];
        c.col[1] = col[1];
        c.col[2] = col[2];
        c.col[3] = 1f;
        return c;
    }

    public class NearbyRecord implements IFocus {
        public double distToCamera, size, radius;
        public Vector3d pos, pm, absolutePos;
        public float[] col;
        public String name;

        public NearbyRecord() {
            pos = new Vector3d();
            pm = new Vector3d();
            absolutePos = new Vector3d();
            col = new float[4];
        }

        @Override
        public long getId() {
            return -1;
        }

        @Override
        public long getCandidateId() {
            return -1;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String[] getNames() {
            return new String[]{name};
        }

        @Override
        public boolean hasName(String name) {
            return this.name.equalsIgnoreCase(name);
        }

        @Override
        public boolean hasName(String name, boolean matchCase) {
            return matchCase ? this.name.equals(name) : this.name.equalsIgnoreCase(name);
        }

        @Override
        public String getClosestName() {
            return name;
        }

        @Override
        public String getCandidateName() {
            return name;
        }

        @Override
        public ComponentTypes getCt() {
            return new ComponentTypes(ComponentTypes.ComponentType.Stars);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Vector3b getPos() {
            return null;
        }

        @Override
        public SceneGraphNode getFirstStarAncestor() {
            return null;
        }

        @Override
        public Vector3b getAbsolutePosition(Vector3b out) {
            return out.set(absolutePos);
        }

        @Override
        public Vector3b getAbsolutePosition(String name, Vector3b out) {
            if (name.equalsIgnoreCase(this.name))
                return out.set(absolutePos);
            else
                return out;
        }

        @Override
        public Vector3b getClosestAbsolutePos(Vector3b out) {
            return out.set(absolutePos);
        }

        @Override
        public Vector2d getPosSph() {
            return null;
        }

        @Override
        public IFocus getNext(ITimeFrameProvider time, ICamera camera, boolean force) {
            return null;
        }

        @Override
        public Vector3b getPredictedPosition(Vector3b aux, ITimeFrameProvider time, ICamera camera, boolean force) {
            return null;
        }

        @Override
        public double getDistToCamera() {
            return this.distToCamera;
        }

        @Override
        public double getClosestDistToCamera() {
            return this.distToCamera;
        }

        @Override
        public double getViewAngle() {
            return 0;
        }

        @Override
        public double getViewAngleApparent() {
            return 0;
        }

        @Override
        public double getCandidateViewAngleApparent() {
            return 0;
        }

        @Override
        public double getAlpha() {
            return 0;
        }

        @Override
        public double getDelta() {
            return 0;
        }

        @Override
        public double getSize() {
            return this.size;
        }

        @Override
        public double getRadius() {
            return this.radius;
        }

        @Override
        public double getHeight(Vector3b camPos) {
            return 0;
        }

        @Override
        public double getHeight(Vector3b camPos, boolean useFuturePosition) {
            return 0;
        }

        @Override
        public double getHeight(Vector3b camPos, Vector3b nextPos) {
            return 0;
        }

        @Override
        public double getHeightScale() {
            return 0;
        }

        @Override
        public float getAppmag() {
            return 0;
        }

        @Override
        public float getAbsmag() {
            return 0;
        }

        @Override
        public Matrix4d getOrientation() {
            return null;
        }

        @Override
        public RotationComponent getRotationComponent() {
            return null;
        }

        @Override
        public Quaterniond getOrientationQuaternion() {
            return null;
        }

        @Override
        public void addHit(int screenX, int screenY, int w, int h, int pxdist, NaturalCamera camera, Array<IFocus> hits) {

        }

        @Override
        public void addHit(Vector3d p0, Vector3d p1, NaturalCamera camera, Array<IFocus> hits) {

        }

        @Override
        public void makeFocus() {

        }

        @Override
        public IFocus getFocus(String name) {
            return null;
        }

        @Override
        public boolean isCoordinatesTimeOverflow() {
            return false;
        }

        @Override
        public int getSceneGraphDepth() {
            return 0;
        }

        @Override
        public OctreeNode getOctant() {
            return null;
        }

        @Override
        public boolean isCopy() {
            return false;
        }

        @Override
        public float[] getColor() {
            return col;
        }
    }
}
