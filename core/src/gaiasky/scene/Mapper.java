package gaiasky.scene;

import com.badlogic.ashley.core.ComponentMapper;
import gaiasky.scene.component.*;

/**
 * Centralized repository of component mappers.
 */
public class Mapper {
    public static final ComponentMapper<Base> base = ComponentMapper.getFor(Base.class);
    public static final ComponentMapper<Id> id = ComponentMapper.getFor(Id.class);
    public static final ComponentMapper<Hip> hip = ComponentMapper.getFor(Hip.class);
    public static final ComponentMapper<Body> body = ComponentMapper.getFor(Body.class);
    public static final ComponentMapper<Celestial> celestial = ComponentMapper.getFor(Celestial.class);
    public static final ComponentMapper<Flags> flags = ComponentMapper.getFor(Flags.class);
    public static final ComponentMapper<Label> label = ComponentMapper.getFor(Label.class);
    public static final ComponentMapper<RefSysTransform> transform = ComponentMapper.getFor(RefSysTransform.class);
    public static final ComponentMapper<AffineTransformations> affine = ComponentMapper.getFor(AffineTransformations.class);
    public static final ComponentMapper<GraphNode> graph = ComponentMapper.getFor(GraphNode.class);
    public static final ComponentMapper<Octant> octant = ComponentMapper.getFor(Octant.class);
    public static final ComponentMapper<Fade> fade = ComponentMapper.getFor(Fade.class);
    public static final ComponentMapper<DatasetDescription> datasetDescription = ComponentMapper.getFor(DatasetDescription.class);
    public static final ComponentMapper<ParticleSet> particleSet = ComponentMapper.getFor(ParticleSet.class);
    public static final ComponentMapper<StarSet> starSet = ComponentMapper.getFor(StarSet.class);
    public static final ComponentMapper<Magnitude> magnitude = ComponentMapper.getFor(Magnitude.class);
    public static final ComponentMapper<Size> size = ComponentMapper.getFor(Size.class);
    public static final ComponentMapper<Model> model = ComponentMapper.getFor(Model.class);
    public static final ComponentMapper<Atmosphere> atmosphere = ComponentMapper.getFor(Atmosphere.class);
    public static final ComponentMapper<Cloud> cloud = ComponentMapper.getFor(Cloud.class);
    public static final ComponentMapper<ModelScaffolding> modelScaffolding = ComponentMapper.getFor(ModelScaffolding.class);
    public static final ComponentMapper<Attitude> attitude = ComponentMapper.getFor(Attitude.class);
    public static final ComponentMapper<MotorEngine> engine = ComponentMapper.getFor(MotorEngine.class);

}
