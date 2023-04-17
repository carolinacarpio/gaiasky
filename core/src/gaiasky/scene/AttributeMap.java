package gaiasky.scene;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import gaiasky.scene.component.*;
import gaiasky.util.Logger;

import java.util.*;

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
        // Load the attribute map from the JSON definition.
        var attributeMapFile = Gdx.files.internal("archetypes/attributemap.json");
        var reader = new JsonReader();
        var root = reader.parse(attributeMapFile);

        if (root.has("components")) {
            var numComponents = 0;
            var components = root.get("components");
            var component = components.child;
            while (component != null) {
                // Process component.
                var name = component.name;
                var className = "gaiasky.scene.component." + name;
                var attributes = new Array<String>();
                var attribute = component.child;
                while (attribute != null) {
                    if (!attribute.name.equalsIgnoreCase("description")) {
                        attributes.add(attribute.name);
                        if (attribute.hasChild("aliases")) {
                            // Add all aliases.
                            var aliases = attribute.get("aliases").asStringArray();
                            attributes.addAll(aliases);
                        }
                    }
                    attribute = attribute.next();
                }

                try {
                    Class<Component> clazz = (Class<Component>) Class.forName(className);
                    putAll(clazz, attributes);
                } catch (ClassNotFoundException e) {
                    logger.error("Component Class not found: " + className);
                } catch (ClassCastException e) {
                    logger.error("Component Class does not implement gaiasky.scene.component.Component: " + className);
                }

                numComponents++;
                component = component.next();
            }
            logger.info("Processed " + numComponents + " components");
        }

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

    private void putAll(Class<? extends Component> clazz, Array<String> attributes) {
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
