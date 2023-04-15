package gaiasky.scene;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter.Particle;
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
     *
     * @param key The key.
     *
     * @return The component class.
     */
    public Class<? extends Component> get(String key) {
        return attributeMap.get(key);
    }

    /**
     * Checks whether the given key is in the attribute map.
     *
     * @param key The key.
     *
     * @return <code>true</code> if this map contains a mapping with the specified key.
     */
    public boolean containsKey(String key) {
        return attributeMap.containsKey(key);
    }

    public Map<String, Class<? extends Component>> initialize() {
        // Base
        putAll(Base.class,
                "id", "name", "names", "altName", "altname", "opacity",
                "ct", "componentType", "forceLabel");

        // Body
        putAll(Body.class,
                "position", "positionKm", "positionPc", "pos", "posKm", "posPc",
                "size", "sizeKm", "sizePc", "sizepc", "sizeM", "sizeAU", "radius", "radiusKm",
                "radiusPc", "diameter", "diameterKm", "color", "labelcolor", "labelColor");

        // GraphNode
        putAll(GraphNode.class, "parent");

        // Coordinates
        putAll(Coordinates.class, "coordinates");

        // Rotation
        putAll(Rotation.class, "rotation");

        // Celestial
        putAll(Celestial.class, "wikiname", "colorbv", "colorBv");

        // Magnitude
        putAll(Magnitude.class, "appmag", "appMag", "absmag", "absMag");

        // Proper motion
        putAll(ProperMotion.class, "muAlphaMasYr", "muAlpha", "muDeltaMasYr",
                "muDelta", "rv", "rvKms", "radialVelocity", "radialVelocityKms", "epochYear", "epochJd");

        // SolidAngleThresholds
        putAll(SolidAngle.class, "thresholdNone", "thresholdPoint", "thresholdQuad");

        // ModelScaffolding
        putAll(ModelScaffolding.class,
                "refplane", "randomize", "seed", "sizescalefactor", "locvamultiplier",
                "locVaMultiplier", "locthoverfactor", "locThresholdLabel", "shadowvalues");

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
        putAll(Trajectory.class,
                "provider", "orbit", "model:Orbit", "pointcolor",
                "pointsize", "trail", "orbittrail", "orbitTrail", "trailMap", "newmethod",
                "newMethod", "onlybody", "onlyBody", "numSamples", "fadeDistanceUp",
                "fadeDistanceDown");

        // RefSysTransform
        putAll(RefSysTransform.class,
                "transformName", "transformFunction", "transformValues", "transformMatrix");

        // AffineTransformations
        putAll(AffineTransformations.class,
                "transformations", "scale", "rotate", "translate", "translatePc", "translateKm");

        // Fade
        putAll(Fade.class,
                "fadein", "fadeIn", "fadeInMap", "fadeout", "fadeOut", "fadeOutMap",
                "fade", "fadepc", "fadePc", "positionobjectname", "fadeObjectName", "fadePosition");

        // DatasetDescription
        putAll(DatasetDescription.class,
                "catalogInfo", "cataloginfo", "datasetInfo", "description:MeshObject");

        // Label
        putAll(Label.class,
                "label", "labelposition", "labelPosition",
                "labelPositionKm", "labelPositionPc", "labelFactor", "labelMax", "textScale");

        // RenderType
        putAll(RenderType.class, "rendergroup", "renderGroup", "billboardRenderGroup:Particle");

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
        putAll(Boundaries.class, "boundaries", "boundariesEquatorial");

        // ParticleSet
        putAll(ParticleSet.class,
                "provider:ParticleGroup", "position:ParticleGroup", "datafile", "dataFile",
                "providerparams", "providerParams", "factor", "profiledecay",
                "profileDecay", "colornoise", "colorNoise", "particlesizelimits",
                "particleSizeLimits", "colorMin", "colorMax", "fixedAngularSize", "fixedAngularSizeDeg",
                "fixedAngularSizeRad", "renderParticles");

        // StarSet
        putAll(StarSet.class,
                "provider:StarGroup", "datafile:StarGroup", "dataFile:StarGroup", "providerparams:StarGroup", "providerParams:StarGroup",
                "factor:StarGroup", "profiledecay:StarGroup", "profileDecay:StarGroup",
                "colornoise:StarGroup", "colorNoise:StarGroup", "particlesizelimits:StarGroup",
                "epoch:StarGroup", "variabilityEpoch:StarGroup", "fixedAngularSize:StarGroup", "fixedAngularSizeDeg:StarGroup",
                "fixedAngularSizeRad:StarGroup", "renderParticles:StarGroup");

        // Attitude
        putAll(Attitude.class,
                "provider:HeliotropicSatellite", "attitudeLocation");

        // ParticleExtra
        putAll(ParticleExtra.class, "primitiveRenderScale");

        // Mesh
        putAll(Mesh.class, "shading", "additiveblending", "additiveBlending");

        // Shape
        putAll(Shape.class, "focusable");

        // Invisible
        putAll(Raymarching.class, "shader");

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
