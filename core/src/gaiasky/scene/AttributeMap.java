package gaiasky.scene;

import com.badlogic.ashley.core.Component;
import gaiasky.scene.component.*;
import gaiasky.util.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds a map with the relations of old object attributes to contained component.
 */
public class AttributeMap {
    private static final Logger.Log logger = Logger.getLogger(AttributeMap.class);

    private final Map<String, Class<? extends Component>> attributeMap;

    public AttributeMap() {
        this.attributeMap = new HashMap<>();
    }

    /**
     * Returns the component class to which the specified key is mapped.
     * @param key The key.
     * @return The component class.
     */
    public Class<? extends Component> get(String key) {
        return attributeMap.get(key);
    }

    /**
     * Checks whether the given key is in the attribute map.
     * @param key The key.
     * @return <code>true</code> if this map contains a mapping with the specified key.
     */
    public boolean containsKey(String key) {
        return attributeMap.containsKey(key);
    }

    public Map<String, Class<? extends Component>> initialize() {
        // Base
        putAll(Base.class, "id", "name", "names", "opacity", "ct");

        // Body
        putAll(Body.class, "position", "positionKm", "positionPc", "pos", "posKm", "posPc", "size", "sizeKm", "sizePc", "sizepc", "sizeM", "sizeAU", "radius", "radiusKm", "diameter", "diameterKm", "color", "labelcolor");

        // GraphNode
        putAll(GraphNode.class, "parent");

        // Coordinates
        putAll(Coordinates.class, "coordinates");

        // Rotation
        putAll(Rotation.class, "rotation");

        // Celestial
        putAll(Celestial.class, "wikiname", "colorbv");

        // Magnitude
        putAll(Magnitude.class, "appmag", "absmag");

        // SolidAngleThresholds
        putAll(SolidAngle.class, "thresholdNone", "thresholdPoint", "thresholdQuad");

        // Text
        putAll(Text.class, "labelFactor", "labelMax", "textScale");

        // ModelScaffolding
        putAll(ModelScaffolding.class, "refplane", "randomize", "seed", "sizescalefactor", "locvamultiplier", "locthoverfactor", "shadowvalues");

        // Model
        putAll(Model.class, "model");

        // Atmosphere
        putAll(Atmosphere.class, "atmosphere");

        // Cloud
        putAll(Cloud.class, "cloud");

        // RenderFlags
        putAll(RenderFlags.class, "renderquad");

        // Machine
        putAll(MotorEngine.class, "machines");

        // Trajectory
        putAll(Trajectory.class, "provider", "orbit", "model:Orbit", "pointcolor", "trail", "orbittrail", "newmethod", "onlybody");

        // RefSysTransform
        putAll(RefSysTransform.class, "transformName", "transformFunction", "transformValues");

        // AffineTransformations
        putAll(AffineTransformations.class, "transformations");

        // Fade
        putAll(Fade.class, "fadein", "fadeout", "fade", "fadepc", "positionobjectname");

        // DatasetDescription
        putAll(DatasetDescription.class, "catalogInfo", "cataloginfo");

        // Label
        putAll(Label.class, "label", "label2d", "labelposition");

        // RenderType
        putAll(RenderType.class, "rendergroup", "billboardRenderGroup:Particle");

        // BillboardDataset
        putAll(BillboardSet.class, "data:BillboardGroup");

        // Title
        putAll(Title.class, "scale:Text2D", "lines:Text2D", "align:Text2D");

        // Axis
        putAll(Axis.class, "axesColors");

        // LocationMark
        putAll(LocationMark.class, "location", "distFactor");

        // Constel
        putAll(Constel.class, "ids");

        // Boundaries
        putAll(Boundaries.class, "boundaries");

        // ParticleSet
        putAll(ParticleSet.class, "provider:ParticleGroup", "datafile", "providerparams", "factor", "profiledecay", "colornoise", "particlesizelimits");

        // StarSet
        putAll(StarSet.class, "provider:StarGroup", "datafile:StarGroup", "providerparams:StarGroup", "factor:StarGroup", "profiledecay:StarGroup", "colornoise:StarGroup", "particlesizelimits:StarGroup");

        // Attitude
        putAll(Attitude.class, "provider:HeliotropicSatellite", "attitudeLocation");

        // ParticleExtra
        putAll(ParticleExtra.class, "primitiveRenderScale");

        return attributeMap;
    }

    private void putAll(Class<? extends Component> clazz, String... attributes) {
        for (String attribute : attributes) {
            if (attributeMap.containsKey(attribute)) {
                logger.warn("Attribute already defined: " + attribute);
                throw new RuntimeException("Attribute already defined: " + attribute);
            } else {
                attributeMap.put(attribute, clazz);
            }
        }
    }
}
