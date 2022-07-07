package gaiasky.scene.component;

import com.badlogic.ashley.core.Component;
import gaiasky.GaiaSky;
import gaiasky.scene.Index;
import gaiasky.scenegraph.ParticleSetUpdaterTask;
import gaiasky.scenegraph.SceneGraphNode;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.scenegraph.particle.ParticleRecord;
import gaiasky.scenegraph.particle.PointParticleRecord;
import gaiasky.util.Constants;
import gaiasky.util.GlobalResources;
import gaiasky.util.Settings;
import gaiasky.util.camera.Proximity;
import gaiasky.util.coord.Coordinates;
import gaiasky.util.math.MathUtilsd;
import gaiasky.util.math.Vector2d;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;

import java.util.*;

public class ParticleSet implements Component {

    public static long idSeq = 0;

    /**
     * List that contains the point data. It contains only [x y z]
     */
    public List<IParticleRecord> pointData;

    /** Flag indicating whether the particle set holds stars or particles. **/
    public boolean isStars;

    /**
     * Fully qualified name of data provider class
     */
    public String provider;

    /**
     * Path of data file
     */
    public String datafile;

    // Parameters for the data provider
    public Map<String, Object> providerParams;

    /**
     * Profile decay of the particles in the shader
     */
    public float profileDecay = 0.2f;

    /**
     * Noise factor for the color in [0,1]
     */
    public float colorNoise = 0;

    /**
     * Particle size limits
     */
    public double[] particleSizeLimitsPoint = new double[] { 2d, 50d };
    /**
     * Particle size limits for the GL_TRIANGLES renderer. This will be multiplied by
     * the distance to the particle in the shader, so that <code>size = tan(angle) * dist</code>
     */
    public double[] particleSizeLimits = new double[] { Math.tan(Math.toRadians(0.1)), Math.tan(Math.toRadians(6.0)) };

    /**
     * Temporary storage for the mean position of this particle set, if it is given externally.
     * If this is set, the mean position is not computed from the positions of all the particles automatically.
     **/
    public Vector3d meanPosition;

    /**
     * Factor to apply to the data points, usually to normalise distances
     */
    public Double factor = null;

    /**
     * Mapping colors
     */
    public float[] ccMin = null, ccMax = null;

    /**
     * Stores the time when the last sort operation finished, in ms
     */
    public long lastSortTime;

    /**
     * The mean distance from the origin of all points in this group.
     * Gives a sense of the scale.
     */
    public double meanDistance;
    public double maxDistance, minDistance;

    /**
     * Geometric centre at epoch, for render sorting
     */
    public static Vector3d geomCentre;

    /**
     * Reference to the current focus.
     */
    public IParticleRecord focus;
    /**
     * Index of the particle acting as focus. Negative if we have no focus here.
     */
    public int focusIndex;

    /**
     * Candidate to focus.
     */
    public int candidateFocusIndex;

    /**
     * Position of the current focus
     */
    public Vector3d focusPosition;

    /**
     * Position in equatorial coordinates of the current focus in radians
     */
    public Vector2d focusPositionSph;

    /**
     * FOCUS_MODE attributes
     */
    public double focusDistToCamera, focusViewAngle, focusViewAngleApparent, focusSize;

    /**
     * Proximity particles
     */
    public Proximity proximity;

    // Has been disposed
    public boolean disposed = false;

    // Name index
    public Map<String, Integer> index;

    // Metadata, for sorting - holds distances from each particle to the camera, squared.
    public double[] metadata;

    // Comparator
    private Comparator<Integer> comp;

    // Indices list buffer 1
    public Integer[] indices1;
    // Indices list buffer 2
    public Integer[] indices2;
    // Active indices list
    public Integer[] active;
    // Background indices list (the one we sort)
    public Integer[] background;

    // Visibility array with 1 (visible) or 0 (hidden) for each particle
    public byte[] visibilityArray;

    // Is it updating?
    public volatile boolean updating = false;

    // Updater task
    public ParticleSetUpdaterTask updaterTask;

    // Last sort position
    public Vector3d lastSortCameraPos, cPosD;

    public final Vector3d D31 = new Vector3d();

    public float[] getColorMin() {
        return ccMin;
    }

    public float[] getColorMax() {
        return ccMax;
    }

    public double getMeanDistance() {
        return meanDistance;
    }

    public double getMinDistance() {
        return minDistance;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    /**
     * Returns the list of particles.
     */
    public List<IParticleRecord> data() {
        return pointData;
    }

    public void setData(List<IParticleRecord> pointData) {
        setData(pointData, true);
    }

    public void setData(List<IParticleRecord> pointData, boolean regenerateIndex) {
        this.pointData = pointData;

        // Regenerate index
        if (regenerateIndex)
            regenerateIndex();
        // Initialize visibility - all visible
        this.visibilityArray = new byte[pointData.size()];
        for (int i = 0; i < pointData.size(); i++) {
            this.visibilityArray[i] = (byte) 1;
        }
    }

    /**
     * Regenerates the name index
     */
    public void regenerateIndex() {
        index = generateIndex(data());
    }

    /**
     * Generates the index (maps name to array index)
     * and computes the geometric center of this group
     *
     * @param pointData The data
     *
     * @return An map{string,int} mapping names to indices
     */
    public Map<String, Integer> generateIndex(List<IParticleRecord> pointData) {
        Map<String, Integer> index = new HashMap<>((int) (pointData.size() * 1.25));
        int n = pointData.size();
        for (int i = 0; i < n; i++) {
            IParticleRecord pb = pointData.get(i);
            if (pb.names() != null) {
                final int idx = i;
                Arrays.stream(pb.names()).forEach(name -> index.put(name.toLowerCase(), idx));
            }
        }
        return index;
    }

    public void setPosition(double[] pos) {
        this.meanPosition = new Vector3d(pos[0], pos[1], pos[2]);
    }

    public void setPosKm(double[] pos) {
        setPositionKm(pos);
    }

    public void setPositionKm(double[] pos) {
        this.meanPosition = new Vector3d(pos[0] * Constants.KM_TO_U, pos[1] * Constants.KM_TO_U, pos[2] * Constants.KM_TO_U);
    }

    public void setPosPc(double[] pos) {
        setPositionPc(pos);
    }

    public void setPositionPc(double[] pos) {
        this.meanPosition = new Vector3d(pos[0] * Constants.PC_TO_U, pos[1] * Constants.PC_TO_U, pos[2] * Constants.PC_TO_U);
    }

    public void setPosition(int[] pos) {
        setPosition(new double[] { pos[0], pos[1], pos[2] });
    }

    public void setDatafile(String datafile) {
        this.datafile = datafile;
    }

    public void setProviderparams(Map<String, Object> params) {
        this.providerParams = params;
    }

    public void setFactor(Double factor) {
        this.factor = factor * Constants.DISTANCE_SCALE_FACTOR;
    }

    public void setProfiledecay(Double profiledecay) {
        setProfileDecay(profiledecay);
    }

    public void setProfileDecay(Double profileDecay) {
        this.profileDecay = profileDecay.floatValue();
    }

    public void setColornoise(Double colorNoise) {
        setColorNoise(colorNoise);
    }
    public void setColorNoise(Double colorNoise) {
        this.colorNoise = colorNoise.floatValue();
    }

    public void setColorMin(double[] colorMin) {
        this.ccMin = GlobalResources.toFloatArray(colorMin);
    }

    public void setColorMin(float[] colorMin) {
        this.ccMin = colorMin;
    }

    public void setColorMax(double[] colorMax) {
        this.ccMax = GlobalResources.toFloatArray(colorMax);
    }

    public void setColorMax(float[] colorMax) {
        this.ccMax = colorMax;
    }

    public void setParticlesizelimits(double[] sizeLimits) {
        if (sizeLimits[0] > sizeLimits[1])
            sizeLimits[0] = sizeLimits[1];
        this.particleSizeLimits = sizeLimits;
    }

    public IParticleRecord get(int index) {
        return pointData.get(index);
    }

    // FOCUS_MODE size
    public double getSize() {
        return focusSize;
    }

    /**
     * Returns the size of the particle at index i
     *
     * @param i The index
     *
     * @return The size
     */
    public double getSize(int i) {
        return pointData.get(i).size();
    }

    /**
     * Default size if not in data, 1e5 km
     *
     * @return The size
     */
    public double getFocusSize() {
        return isStars ? focus.size() : 1e5 * Constants.KM_TO_U;
    }

    // Radius in stars is different!
    public double getRadius() {
        return isStars ? getSize() * Constants.STAR_SIZE_FACTOR : getSize() / 2.0;
    }

    // Radius in stars is different!
    public double getRadius(int i) {
        return isStars ? getSize(i) * Constants.STAR_SIZE_FACTOR : getRadius();
    }

    /**
     * Updates the parameters of the focus, if the focus is active in this group
     *
     * @param camera The current camera
     */
    public void updateFocus(ICamera camera) {
        Vector3d aux = D31.set(focusPosition);
        focusDistToCamera = aux.sub(camera.getPos()).len();
        focusSize = getFocusSize();
        focusViewAngle = (float) ((getRadius() / focusDistToCamera) / camera.getFovFactor());
        focusViewAngleApparent = focusViewAngle * Settings.settings.scene.star.brightness;
    }

    public void updateFocusDataPos() {
        if (focusIndex < 0) {
            focus = null;
        } else {
            focus = pointData.get(focusIndex);
            focusPosition.set(focus.x(), focus.y(), focus.z());
            Vector3d posSph = Coordinates.cartesianToSpherical(focusPosition, D31);
            focusPositionSph.set((float) (MathUtilsd.radDeg * posSph.x), (float) (MathUtilsd.radDeg * posSph.y));
            updateFocus(GaiaSky.instance.getICamera());
        }
    }

    public void setFocusIndex(int index) {
        if (index >= 0 && index < pointData.size()) {
            candidateFocusIndex = index;
            makeFocus();
        }
    }

    public void makeFocus() {
        focusIndex = candidateFocusIndex;
        updateFocusDataPos();
    }

    /**
     * Checks whether the particle with the given index is visible
     *
     * @param index The index of the particle
     *
     * @return The visibility of the particle
     */
    public boolean isVisible(int index) {
        return visibilityArray != null && visibilityArray[index] != (byte) 0;
    }

    public Vector3b getAbsolutePosition(String name, Vector3b out) {
        if (index.containsKey(name)) {
            int idx = index.get(name);
            IParticleRecord pb = pointData.get(idx);
            out.set(pb.x(), pb.y(), pb.z());
            return out;
        } else {
            return null;
        }
    }

    /**
     * Fetches the real position of the particle. It will apply the necessary
     * integrations (i.e. proper motion).
     *
     * @param pb          The particle bean
     * @param campos      The position of the camera. If null, the camera position is
     *                    not subtracted so that the coordinates are given in the global
     *                    reference system instead of the camera reference system.
     * @param destination The destination factor
     * @param deltaYears  The delta years
     *
     * @return The vector for chaining
     */
    public Vector3d fetchPosition(IParticleRecord pb, Vector3d campos, Vector3d destination, double deltaYears) {
        if (campos != null)
            return destination.set(pb.x(), pb.y(), pb.z()).sub(campos);
        else
            return destination.set(pb.x(), pb.y(), pb.z());
    }

    /**
     * Returns the delta years to integrate the proper motion.
     *
     * @return The current delta years.
     */
    public double getDeltaYears() {
        return 0;
    }
}
