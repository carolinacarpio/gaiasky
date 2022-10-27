package gaiasky.scene.system.initialize;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Matrix4;
import gaiasky.data.AssetBean;
import gaiasky.render.RenderGroup;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.Focus;
import gaiasky.scene.entity.FocusActive;
import gaiasky.scene.entity.FocusHit;
import gaiasky.scene.system.render.draw.model.ModelEntityRenderSystem;
import gaiasky.scene.system.render.draw.text.LabelEntityRenderSystem;
import gaiasky.scene.view.LabelView;
import gaiasky.util.*;
import gaiasky.util.gdx.shader.Environment;
import gaiasky.util.gdx.shader.attribute.ColorAttribute;
import gaiasky.util.gdx.shader.attribute.FloatAttribute;

public class ShapeInitializer extends AbstractInitSystem {
    public ShapeInitializer(boolean setUp, Family family, int priority) {
        super(setUp, family, priority);
    }

    @Override
    public void initializeEntity(Entity entity) {
        var base = Mapper.base.get(entity);
        var modelComp = Mapper.model.get(entity);
        var mc = modelComp.model;
        mc.initialize(base.getLocalizedName());
    }

    @Override
    public void setUpEntity(Entity entity) {
        var line = Mapper.line.get(entity);
        var label = Mapper.label.get(entity);
        var shape = Mapper.shape.get(entity);
        var transform = Mapper.transform.get(entity);

        // Transform.
        if (transform.matrix != null) {
            transform.matrixf = transform.matrix.putIn(new Matrix4());
        }

        // Label.
        label.label = true;
        label.textScale = 0.2f;
        label.labelMax = 1f;
        if (label.labelFactor == 0)
            label.labelFactor = (float) (0.5e-3f * Constants.DISTANCE_SCALE_FACTOR);
        label.renderConsumer = LabelEntityRenderSystem::renderShape;
        label.renderFunction = LabelView::renderTextEssential;

        // Line.
        line.lineWidth = 1.5f;

        // Focusable.
        if (shape.focusable) {
            // Create focus component.
            var focus = new Focus();
            // Focus consumer.
            focus.hitCoordinatesConsumer = FocusHit::addHitCoordinateModel;
            focus.hitRayConsumer = FocusHit::addHitRayModel;
            focus.activeFunction = FocusActive::isFocusActiveTrue;
            entity.add(focus);

            // Solid angle.
            var sa = Mapper.sa.get(entity);
            double thPoint = sa.thresholdPoint;
            sa.thresholdNone = thPoint / 1e6;
            sa.thresholdPoint = thPoint / 3e4;
            sa.thresholdQuad = thPoint / 2.0;
        }

        initModel(entity);
    }

    public void initModel(Entity entity) {
        var body = Mapper.body.get(entity);
        var graph = Mapper.graph.get(entity);
        var modelComp = Mapper.model.get(entity);
        var rt = Mapper.renderType.get(entity);

        modelComp.renderConsumer = ModelEntityRenderSystem::renderShape;
        var mc = modelComp.model;
        graph.localTransform = new Matrix4();
        mc.doneLoading(AssetBean.manager(), graph.localTransform, body.color);

        if (mc.isStaticLight()) {
            DirectionalLight dLight = new DirectionalLight();
            dLight.set(1, 1, 1, 1, 1, 1);
            mc.env = new Environment();
            mc.env.add(dLight);
            mc.env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        }

        // Relativistic effects
        if (Settings.settings.runtime.relativisticAberration)
            mc.rec.setUpRelativisticEffectsMaterial(mc.instance.materials);
        // Gravitational waves
        if (Settings.settings.runtime.gravitationalWaves)
            mc.rec.setUpGravitationalWavesMaterial(mc.instance.materials);

        if (rt.renderGroup == null) {
            rt.renderGroup = RenderGroup.MODEL_BG;
        }

        // Model size. Used to compute an accurate solid angle.
        ModelInitializer.initializeModelSize(modelComp);
    }
}
