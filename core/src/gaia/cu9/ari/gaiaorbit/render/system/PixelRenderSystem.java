package gaia.cu9.ari.gaiaorbit.render.system;

import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.render.IRenderable;
import gaia.cu9.ari.gaiaorbit.scenegraph.CelestialBody;
import gaia.cu9.ari.gaiaorbit.scenegraph.ICamera;
import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode.RenderGroup;

import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

public class PixelRenderSystem extends AbstractRenderSystem implements IObserver {

    boolean initialized = false;
    boolean starColorTransit = false;
    Vector3 aux;
    ShaderProgram pointShader;

    public int vertexIdx;
    public final Mesh mesh;
    private final int vertexSize;
    private final int colorOffset;
    public final float[] vertices;

    public PixelRenderSystem(RenderGroup rg, int priority, float[] alphas) {
	super(rg, priority, alphas);

	// Initialise renderer
	pointShader = new ShaderProgram(Gdx.files.internal("shader/point.vertex.glsl"), Gdx.files.internal("shader/point.fragment.glsl"));
	if (!pointShader.isCompiled()) {
	    Gdx.app.error(this.getClass().getName(), "Point shader compilation failed:\n" + pointShader.getLog());
	}

	aux = new Vector3();

	/** Init renderer **/
	int maxVertices = 3000000;

	VertexAttribute[] attribs = buildVertexAttributes();
	mesh = new Mesh(false, maxVertices, 0, attribs);

	vertices = new float[maxVertices * (mesh.getVertexAttributes().vertexSize / 4)];
	vertexSize = mesh.getVertexAttributes().vertexSize / 4;
	colorOffset = mesh.getVertexAttribute(Usage.ColorPacked) != null ? mesh.getVertexAttribute(Usage.ColorPacked).offset / 4
		: 0;

	EventManager.instance.subscribe(this, Events.TRANSIT_COLOUR_CMD);
    }

    @Override
    public void renderStud(List<IRenderable> renderables, ICamera camera) {

	int size = renderables.size();
	for (int i = 0; i < size; i++) {
	    /**
	    IRenderable s = renderables.get(i);
	    s.render(renderer, alphas[s.getComponentType().ordinal()], starColorTransit);
	    **/

	    // 2 FPS gain
	    CelestialBody cb = (CelestialBody) renderables.get(i);
	    float[] col = starColorTransit ? cb.ccTransit : cb.cc;
	    // COLOR
	    vertices[vertexIdx + colorOffset] = Color.toFloatBits(col[0], col[1], col[2], cb.opacity * alphas[cb.ct.ordinal()]);

	    // VERTEX
	    aux.set((float) cb.pos.x, (float) cb.pos.y, (float) cb.pos.z);
	    //cb.transform.getTranslationf(aux);
	    final int idx = vertexIdx;
	    vertices[idx] = aux.x;
	    vertices[idx + 1] = aux.y;
	    vertices[idx + 2] = aux.z;

	    vertexIdx += vertexSize;
	}

	pointShader.begin();
	pointShader.setUniformMatrix("u_projModelView", camera.getCamera().combined);
	mesh.setVertices(vertices, 0, vertexIdx);
	mesh.render(pointShader, ShapeType.Point.getGlType());
	pointShader.end();

	vertexIdx = 0;

    }

    private VertexAttribute[] buildVertexAttributes() {
	Array<VertexAttribute> attribs = new Array<VertexAttribute>();
	attribs.add(new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
	attribs.add(new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
	attribs.add(new VertexAttribute(Usage.Generic, 4, "a_additional"));

	VertexAttribute[] array = new VertexAttribute[attribs.size];
	for (int i = 0; i < attribs.size; i++)
	    array[i] = attribs.get(i);
	return array;
    }

    @Override
    public void notify(Events event, Object... data) {
	if (event == Events.TRANSIT_COLOUR_CMD) {
	    starColorTransit = (boolean) data[1];
	}

    }

}
