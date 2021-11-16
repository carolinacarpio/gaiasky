/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.system;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.utils.Array;
import gaiasky.event.IObserver;
import gaiasky.render.IRenderable;
import gaiasky.render.SceneGraphRenderer.RenderGroup;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.util.Pair;
import gaiasky.util.gdx.mesh.IntMesh;
import gaiasky.util.gdx.shader.ExtShaderProgram;
import org.lwjgl.system.CallbackI.V;

/**
 * Contains some common code to all point cloud renderers and some
 * scaffolding to make life easier. Should be used by point
 * clouds that render their particles as GL_POINTS.
 */
public abstract class PointCloudRenderSystem extends ImmediateRenderSystem implements IObserver {

    public PointCloudRenderSystem(RenderGroup rg, float[] alphas, ExtShaderProgram[] shaders) {
        super(rg, alphas, shaders);
    }

    @Override
    protected void initVertices() {
        meshes = new Array<>();
    }

    /**
     * Adds the required vertex attributes for this renderer to the given list
     * @param attributes The list of attributes
     */
    protected abstract void addVertexAttributes(Array<VertexAttribute> attributes);

    /**
     * Builds the vertex attributes array and returns it
     * @return The vertex attributes array
     */
    protected VertexAttribute[] buildVertexAttributes() {
        Array<VertexAttribute> attributes = new Array<>();
        addVertexAttributes(attributes);

        VertexAttribute[] array = new VertexAttribute[attributes.size];
        for (int i = 0; i < attributes.size; i++)
            array[i] = attributes.get(i);
        return array;
    }

    /**
     * Computes the offset for each vertex attribute. The offsets will be
     * used later in the render stage.
     * @param curr The current mesh data
     */
    protected abstract void offsets(MeshData curr);

    /**
     * Adds a new mesh data to the meshes list and increases the mesh data index
     *
     * @param maxVerts The max number of vertices this mesh data can hold
     *
     * @return The index of the new mesh data
     */
    protected int addMeshData(int maxVerts) {
        return addMeshData(maxVerts, 0);
    }

    /**
     * Adds a new mesh data to the meshes list and increases the mesh data index
     *
     * @param maxVerts   The max number of vertices this mesh data can hold
     * @param maxIndices The maximum number of indices this mesh data can hold
     *
     * @return The index of the new mesh data
     */
    protected int addMeshData(int maxVerts, int maxIndices) {
        int mdi = createMeshData();
        curr = meshes.get(mdi);

        VertexAttribute[] attributes = buildVertexAttributes();
        curr.mesh = new IntMesh(false, maxVerts, maxIndices, attributes);

        curr.vertexSize = curr.mesh.getVertexAttributes().vertexSize / 4;

        offsets(curr);

        return mdi;
    }

    protected abstract void globalUniforms(ExtShaderProgram shaderProgram, ICamera camera);

    protected abstract void renderObject(ExtShaderProgram shaderProgram, IRenderable renderable);

    @Override
    public void renderStud(Array<IRenderable> renderables, ICamera camera, double t) {
        if (renderables.size > 0) {
            ExtShaderProgram shaderProgram = getShaderProgram();

            shaderProgram.begin();
            // Global uniforms
            globalUniforms(shaderProgram, camera);
            // Render
            renderables.forEach((r) -> renderObject(shaderProgram, r));
            shaderProgram.end();
        }
    }


}
