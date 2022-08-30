package gaiasky.scene.system.initialize;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.entity.ConstellationRadio;
import gaiasky.scene.system.render.draw.line.LineEntityRenderSystem;
import gaiasky.scene.system.render.draw.text.LabelEntityRenderSystem;
import gaiasky.scene.view.LabelView;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;
import gaiasky.util.tree.IPosition;

import java.util.Map;

public class ConstellationInitializer extends AbstractInitSystem {

    private final Scene scene;

    public ConstellationInitializer(Scene scene, boolean setUp, Family family, int priority) {
        super(setUp, family, priority);
        this.scene = scene;
    }

    @Override
    public void initializeEntity(Entity entity) {
        var body = Mapper.body.get(entity);
        var constel = Mapper.constel.get(entity);
        var line = Mapper.line.get(entity);
        var label = Mapper.label.get(entity);

        constel.posd = new Vector3d();
        constel.alpha = 0.4f;

        if (body.color == null) {
            body.color = new float[] { 0.5f, 1f, 0.5f, constel.alpha };
            body.labelColor = new float[] { 0.5f, 1f, 0.5f, constel.alpha };
        }

        // Labels.
        label.label = true;
        label.renderConsumer = LabelEntityRenderSystem::renderConstellation;
        label.renderFunction = LabelView::renderTextBase;
        label.labelPosition = new Vector3b();

        // Lines.
        line.lineWidth = 1;
        line.renderConsumer = LineEntityRenderSystem::renderConstellation;

        EventManager.instance.subscribe(new ConstellationRadio(entity), Event.CONSTELLATION_UPDATE_CMD);

    }

    @Override
    public void setUpEntity(Entity entity) {
        var constel = Mapper.constel.get(entity);

        if (!constel.allLoaded) {
            int nPairs = constel.ids.size;
            if (constel.lines == null) {
                constel.lines = new IPosition[nPairs][];
            }
            Map<Integer, IPosition> hipMap = scene.index().getHipMap();
            constel.allLoaded = true;
            for (int i = 0; i < nPairs; i++) {
                int[] pair = constel.ids.get(i);
                IPosition s1, s2;
                s1 = hipMap.get(pair[0]);
                s2 = hipMap.get(pair[1]);
                if (constel.lines[i] == null && s1 != null && s2 != null) {
                    constel.lines[i] = new IPosition[] { s1, s2 };
                } else {
                    constel.allLoaded = false;
                }
            }
        }
    }
}
