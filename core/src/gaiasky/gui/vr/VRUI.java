package gaiasky.gui.vr;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.gui.FocusInfoInterface;
import gaiasky.gui.IGui;
import gaiasky.render.BlendMode;
import gaiasky.render.ComponentTypes;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.RenderGroup;
import gaiasky.scene.Archetype;
import gaiasky.scene.Mapper;
import gaiasky.scene.Scene;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.component.VRDevice;
import gaiasky.scene.component.tag.TagNoClosest;
import gaiasky.scene.record.ModelComponent;
import gaiasky.util.Constants;
import gaiasky.util.coord.StaticCoordinates;
import gaiasky.util.gdx.shader.attribute.TextureAttribute;
import gaiasky.util.math.MathUtilsDouble;
import gaiasky.util.math.Matrix4d;
import gaiasky.util.math.Vector3d;
import gaiasky.util.scene2d.*;
import org.apache.commons.math3.geometry.euclidean.threed.Line;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VRUI implements IGui, IObserver, Disposable {

    private final int WIDTH = 1280;
    private final int HEIGHT = 720;

    Scene scene;
    Stage stage;
    FrameBuffer buffer;
    Texture uiTexture;
    Entity entity;
    // 5 pointers
    Vector2[] pointer = new Vector2[5];

    FocusInfoInterface focusInfoInterface;
    ShapeRenderer shapeRenderer;
    Set<VRDevice> vrControllers;

    public VRUI() {
        for (int i = 0; i < pointer.length; i++) {
            pointer[i] = new Vector2(Float.NaN, Float.NaN);
        }
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    public void initialize(AssetManager manager, SpriteBatch batch) {
        // Create stage.
        Viewport vp = new StretchViewport(WIDTH, HEIGHT);
        stage = new Stage(vp, batch);
        shapeRenderer = new ShapeRenderer(100, GaiaSky.instance.getGlobalResources().getShapeShader());
        shapeRenderer.setAutoShapeType(true);
        // Create controllers set.
        vrControllers = new HashSet<>();

        // Create buffer.
        var builder = new FrameBufferBuilder(WIDTH, HEIGHT);
        builder.addBasicColorTextureAttachment(Format.RGBA8888);
        buffer = builder.build();
        uiTexture = buffer.getColorBufferTexture();

        // Events.
        EventManager.instance.subscribe(this, Event.SHOW_VR_UI, Event.VR_CONTROLLER_INFO);
    }

    @Override
    public void doneLoading(AssetManager assetManager) {

    }

    public void build(Skin skin) {
        Table content = new Table(skin);
        content.setBackground("table-border");
        Table t = new Table(skin);
        t.setBackground("table-bg");

        // Focus interface.
        focusInfoInterface = new FocusInfoInterface(skin);

        // Main content.
        float width = 500;
        OwnLabel title = new OwnLabel("HELLO FROM VR", skin, "header-large");

        Button button = new OwnTextButton("Button", skin, "toggle-big");
        button.setWidth(width);
        button.pad(15);
        Button buttonChecked = new OwnTextButton("Button (checked)", skin, "toggle-big");
        buttonChecked.setWidth(width);
        buttonChecked.setChecked(true);
        buttonChecked.pad(15);
        Slider slider = new OwnSliderPlus("Slider", 0, 100, 1, skin);
        slider.setWidth(width);
        slider.setValue(80);
        OwnCheckBox checkBox = new OwnCheckBox(" Checked", skin);
        checkBox.setChecked(true);
        OwnCheckBox checkBoxNo = new OwnCheckBox(" Unchecked", skin);
        checkBoxNo.setChecked(false);
        SelectBox<String> selectBox = new OwnSelectBox<>(skin, "big");
        selectBox.setItems("First item", "Second item", "Third item");

        t.pad(20);
        t.add(title).padBottom(60).row();
        t.add(button).left().top().padBottom(10).row();
        t.add(buttonChecked).left().top().padBottom(10).row();
        t.add(slider).left().top().padBottom(10).row();
        t.add(checkBox).left().top().padBottom(10).row();
        t.add(checkBoxNo).left().top().padBottom(10).row();
        t.add(selectBox).left().top().padBottom(10).row();

        content.pad(15);
        content.add(focusInfoInterface).left().top().padRight(20);
        content.add(t).left().top();

        content.setFillParent(true);
        content.left().top();

        stage.clear();
        stage.addActor(content);
    }

    Vector3d point = new Vector3d();
    Vector3d normal = new Vector3d();
    Vector3d intersection = new Vector3d();
    Plane plane3D = new Plane(Vector3D.PLUS_J, 0.1 * Constants.M_TO_U);
    Line line3D = new Line(Vector3D.PLUS_I, Vector3D.MINUS_I, 0.1 * Constants.M_TO_U);
    Matrix4d transform = new Matrix4d();
    Matrix4d inverse = new Matrix4d();

    public void update(double dt) {
        // Compute screen-space vertex coordinates for the current camera.
        if (entity != null) {
            var model = Mapper.model.get(entity);

            if (!vrControllers.isEmpty()) {
                transform.set(model.model.instance.transform);
                inverse.set(transform).inv();
                // Original plane in object space.
                point.set(0, 0, 0);
                normal.set(0, 1, 0);
                // Move to world space by applying transform.
                point.mul(transform);
                normal.mul(transform);
                // Characterize plane.
                var p3d = new Vector3D(point.x, point.y, point.z);
                var n3d = new Vector3D(normal.x, normal.y, normal.z);
                plane3D.reset(p3d, n3d);
                // Check intersection with each controller.
                int i = 0;
                for (var device : vrControllers) {
                    if (device.device.isInitialized() && device.device.isConnected()) {
                        line3D.reset(new Vector3D(device.beamP0.x, device.beamP0.y, device.beamP0.z), new Vector3D(device.beamP1.x, device.beamP1.y, device.beamP1.z));
                        var intersection3D = plane3D.intersection(line3D);
                        if (intersection3D != null) {
                            // Intersect!
                            if (i < pointer.length) {
                                // Use inverse transform to position on ZX plane.
                                intersection.set(intersection3D.getX(), intersection3D.getY(), intersection3D.getZ());
                                intersection.mul(inverse);
                                double x3d = MathUtilsDouble.clamp(intersection.x, -0.28125, 0.28125);
                                double z3d = MathUtilsDouble.clamp(intersection.z, -0.5, 0.5);
                                double u = z3d + 0.5;
                                double v = (x3d + 0.28125) * 1 / 0.5625;

                                // Update hit.
                                device.hitUI = u > 0 && u < 1 && v > 0 && v < 1;

                                if (device.hitUI) {
                                    int x = (int) (u * Gdx.graphics.getWidth());
                                    int y = (int) (v * Gdx.graphics.getHeight());

                                    pointer[i].x = x;
                                    pointer[i].y = Gdx.graphics.getHeight() - y;

                                    stage.mouseMoved(x, y);
                                } else {
                                    pointer[i].x = Float.NaN;
                                    pointer[i].y = Float.NaN;
                                }

                            }
                        }
                        i++;
                    }
                }
            }
        }

        stage.act((float) dt);
    }

    /**
     * Checks whether the quadrilateral or polygon defined by points contains the point [x,y].
     *
     * @param x      The coordinate X of the point to test.
     * @param y      The coordinate Y of the point to test.
     * @param points The points defining the polygon.
     * @return Whether the point is in the polygon.
     */
    public boolean contains(int x, int y, Vector3[] points) {
        int i;
        int j;
        boolean result = false;
        for (i = 0, j = points.length - 1; i < points.length; j = i++) {
            if ((points[i].y > y) != (points[j].y > y) && (x < (points[j].x - points[i].x) * (y - points[i].y) / (points[j].y - points[i].y) + points[i].x)) {
                result = !result;
            }
        }
        return result;
    }

    @Override
    public void render(int rw, int rh) {
        buffer.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        stage.draw();

        if (pointer != null) {
            shapeRenderer.begin(ShapeType.Filled);
            for (int i = 0; i < pointer.length; i++) {
                var pi = pointer[i];
                if (Float.isFinite(pi.x) && Float.isFinite(pi.y)) {
                    shapeRenderer.setColor(1f, (float) i / (pointer.length - 1f), 0f, 1f);
                    shapeRenderer.circle(pi.x, pi.y, 10);
                }
            }
            shapeRenderer.end();
        }
        buffer.end();
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void resizeImmediate(int width, int height) {
    }

    @Override
    public boolean cancelTouchFocus() {
        return false;
    }

    @Override
    public Stage getGuiStage() {
        return stage;
    }

    @Override
    public void setVisibilityToggles(ComponentType[] entities, ComponentTypes visible) {
    }

    @Override
    public Actor findActor(String name) {
        return null;
    }

    @Override
    public void sethOffset(int hOffset) {
    }

    @Override
    public void setVr(boolean vr) {
    }

    @Override
    public boolean mustDraw() {
        return true;
    }

    @Override
    public boolean updateUnitsPerPixel(float upp) {
        return false;
    }

    @Override
    public void dispose() {
        if (buffer != null) {
            buffer.dispose();
        }
        if (stage != null) {
            stage.dispose();
        }
    }

    @Override
    public void notify(Event event, Object source, Object... data) {
        if (event == Event.SHOW_VR_UI) {

            GaiaSky.postRunnable(() -> {
                if (entity == null) {
                    Archetype at = scene.archetypes().get("gaiasky.scenegraph.ModelBody");
                    Entity entity = at.createEntity();

                    // Add tags.
                    entity.add(new TagNoClosest());

                    var base = Mapper.base.get(entity);
                    base.setName("VRUI");
                    base.archetype = at;
                    base.ct = new ComponentTypes(ComponentType.Others.ordinal());

                    var body = Mapper.body.get(entity);
                    body.setColor(new float[] { 1, 1, 1, 1 });
                    body.setLabelColor(new float[] { 0, 0, 0, 0 });
                    body.setSizeKm(100.0);

                    var affine = Mapper.affine.get(entity);
                    affine.initialize();

                    var coord = Mapper.coordinates.get(entity);
                    var staticCoordinates = new StaticCoordinates();
                    staticCoordinates.setPosition(body.pos);
                    coord.coordinates = staticCoordinates;

                    var graph = Mapper.graph.get(entity);
                    graph.setParent(Scene.ROOT_NAME);

                    Map<String, Object> params = new HashMap<>();
                    params.put("divisionsu", 1L);
                    params.put("divisionsv", 1L);
                    params.put("width", 1.0);
                    params.put("height", (double) HEIGHT / (double) WIDTH);
                    params.put("flip", false);

                    var model = Mapper.model.get(entity);
                    model.model = new ModelComponent();
                    model.model.type = "surface";
                    model.modelSize = 1;
                    model.model.setPrimitiveType(GL20.GL_TRIANGLES);
                    model.model.setParams(params);
                    model.model.setStaticLight(1.0);
                    model.model.setBlendMode(BlendMode.ALPHA);

                    var rt = Mapper.renderType.get(entity);
                    rt.renderGroup = RenderGroup.MODEL_PIX_TRANSPARENT;

                    // Initialize shape.
                    scene.initializeEntity(entity);
                    scene.setUpEntity(entity);

                    // Set texture.
                    var mat = model.model.instance.materials.get(0);
                    if (mat != null) {
                        mat.set(TextureAttribute.createDiffuse(buffer.getColorBufferTexture()));
                    }
                    this.entity = entity;

                    // Add to scene.
                    EventManager.publish(Event.SCENE_ADD_OBJECT_NO_POST_CMD, this, entity, true);
                }

                Runnable r = () -> {
                    // Set position and orientation
                    // 10 meters in front of the camera, on the equatorial plane.
                    ICamera camera = GaiaSky.instance.getICamera();
                    var dir = camera.getDirection().cpy();
                    dir.y = 0;
                    dir.nor();
                    var body = Mapper.body.get(entity);
                    body.pos.set(camera.getPos().cpy().add(dir.scl(190 * Constants.KM_TO_U)));
                    body.pos.add(0, 80.0 * Constants.KM_TO_U, 0);

                    // Set new static coordinates.
                    var coord = Mapper.coordinates.get(entity);
                    ((StaticCoordinates) coord.coordinates).setPosition(body.pos);

                    // Set rotation.
                    var affine = Mapper.affine.get(entity);
                    affine.transformations.clear();
                    dir.nor();
                    var angle = dir.anglePrecise(new Vector3d(0, 0, 1));
                    if (dir.x < 0) {
                        angle = -angle;
                    }
                    affine.setQuaternion(new double[] { 0, 1, 0 }, angle);
                    affine.setQuaternion(new double[] { 1, 0, 0 }, 90);
                    affine.setQuaternion(new double[] { 0, 1, 0 }, -90);
                };

                r.run();
            });

        } else if (event == Event.VR_CONTROLLER_INFO) {
            vrControllers.add((VRDevice) data[0]);
        }
    }

}
