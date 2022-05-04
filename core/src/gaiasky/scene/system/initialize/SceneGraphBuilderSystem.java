package gaiasky.scene.system.initialize;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.GraphNode;
import gaiasky.scenegraph.SceneGraphNode;
import gaiasky.util.i18n.I18n;
import gaiasky.util.math.Vector3b;

import java.util.Map;

/**
 * Builds the scene graph once all nodes are in the index.
 */
public class SceneGraphBuilderSystem extends InitSystem {

    /** The index. **/
    private Map<String, Entity> index;

    public SceneGraphBuilderSystem(final Map<String, Entity> index, Family family, int priority) {
        super(false, family, priority);
        this.index = index;
    }

    @Override
    public void initializeEntity(Entity entity) {
        GraphNode graph = Mapper.graph.get(entity);
        if (graph.parentName != null)  {
            Entity parent = getNode(graph.parentName);
            if(parent != null) {
                addChild(parent, entity, true);
            } else {
                throw new RuntimeException(I18n.msg("error.parent.notfound", Mapper.base.get(entity).getName(), graph.parentName));
            }
        }

    }

    @Override
    public void setUpEntity(Entity entity) {

    }

    /**
     * Adds a child to the given node and updates the number of children in this
     * node and in all ancestors.
     *
     * @param child               The child node to add.
     * @param updateAncestorCount Whether to update the ancestors number of children.
     */
    public final void addChild(Entity parent, Entity child, boolean updateAncestorCount) {
        GraphNode graph = Mapper.graph.get(child);
        GraphNode parentGraph = Mapper.graph.get(parent);
        if (parentGraph.children == null) {
            parentGraph.children = new Array<>(false, parentGraph.parent == null ? 100 : 1);
        }
        parentGraph.children.add(child);
        graph.parent = parent;
        parentGraph.numChildren++;

        if (updateAncestorCount) {
            // Update num children in ancestors
            Entity ancestor = parentGraph.parent;
            while (ancestor != null) {
                GraphNode ancestorGraph = Mapper.graph.get(ancestor);
                ancestorGraph.numChildren++;
                ancestor = ancestorGraph.parent;
            }
        }
    }

    public Entity getNode(String name) {
        name = name.toLowerCase().strip();
        return index.get(name);
    }
}
