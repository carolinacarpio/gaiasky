/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.system.render.draw;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.RenderGroup;
import gaiasky.render.api.IRenderable;
import gaiasky.scene.Mapper;
import gaiasky.scene.api.IParticleRecord;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.component.Render;
import gaiasky.scene.entity.ParticleUtils;
import gaiasky.scene.system.render.SceneRenderer;
import gaiasky.util.Constants;
import gaiasky.util.Settings;
import gaiasky.util.Settings.SceneSettings.StarSettings;
import gaiasky.util.color.Colormap;
import gaiasky.util.gdx.shader.ExtShaderProgram;
import gaiasky.util.math.StdRandom;
import org.lwjgl.opengl.GL30;

import java.util.Random;

public class ParticleSetPointRenderer extends PointCloudRenderer implements IObserver {
    private final Vector3 aux1;
    private final Random rand;
    private final Colormap cmap;
    private final ParticleUtils utils;
    private int additionalOffset;
    private ICamera camera;
    private boolean stereoHalfWidth;

    public ParticleSetPointRenderer(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtShaderProgram[] shaders) {
        super(sceneRenderer, rg, alphas, shaders);
        utils = new ParticleUtils();
        cmap = new Colormap();

        rand = new Random(123);
        aux1 = new Vector3();
        EventManager.instance.subscribe(this, Event.GPU_DISPOSE_PARTICLE_GROUP);
    }

    @Override
    protected void initShaderProgram() {
        Gdx.gl.glEnable(GL30.GL_POINT_SPRITE);
        Gdx.gl.glEnable(GL30.GL_VERTEX_PROGRAM_POINT_SIZE);
    }

    protected void offsets(MeshData curr) {
        curr.colorOffset = curr.mesh.getVertexAttribute(Usage.ColorPacked) != null ? curr.mesh.getVertexAttribute(Usage.ColorPacked).offset / 4 : 0;
        additionalOffset = curr.mesh.getVertexAttribute(OwnUsage.Additional) != null ? curr.mesh.getVertexAttribute(OwnUsage.Additional).offset / 4 : 0;
    }

    protected void addVertexAttributes(Array<VertexAttribute> attributes) {
        attributes.add(new VertexAttribute(Usage.Position, 3, ExtShaderProgram.POSITION_ATTRIBUTE));
        attributes.add(new VertexAttribute(Usage.ColorPacked, 4, ExtShaderProgram.COLOR_ATTRIBUTE));
        attributes.add(new VertexAttribute(OwnUsage.Additional, 2, "a_additional"));
    }

    protected void preRenderObjects(ExtShaderProgram shaderProgram, ICamera camera) {
        stereoHalfWidth = Settings.settings.program.modeStereo.isStereoHalfWidth();
        this.camera = camera;

        shaderProgram.setUniformMatrix("u_projView", camera.getCamera().combined);
        shaderProgram.setUniformf("u_ar", stereoHalfWidth ? 2f : 1f);
        shaderProgram.setUniformf("u_camPos", camera.getCurrent().getPos().put(aux1));
        shaderProgram.setUniformf("u_camDir", camera.getCurrent().getCamera().direction);
        shaderProgram.setUniformi("u_cubemap", Settings.settings.program.modeCubemap.active ? 1 : 0);
        addEffectsUniforms(shaderProgram, camera);
    }

    @Override
    protected void renderObject(ExtShaderProgram shaderProgram, IRenderable renderable) {
        final Render render = (Render) renderable;
        var base = Mapper.base.get(render.entity);
        var body = Mapper.body.get(render.entity);
        var set = Mapper.particleSet.get(render.entity);
        var hl = Mapper.highlight.get(render.entity);
        var desc = Mapper.datasetDescription.get(render.entity);

        float sizeFactor = utils.getDatasetSizeFactor(render.entity, hl, desc);

        if (!set.disposed) {
            boolean hlCmap = hl.isHighlighted() && !hl.isHlplain();
            if (!inGpu(render)) {
                int offset = addMeshData(set.pointData.size());
                setOffset(render, offset);
                curr = meshes.get(offset);

                float[] c = utils.getColor(body, hl);
                float[] colorMin = set.getColorMin();
                float[] colorMax = set.getColorMax();
                double minDistance = set.getMinDistance();
                double maxDistance = set.getMaxDistance();

                ensureTempVertsSize(set.pointData.size() * curr.vertexSize);
                int n = set.pointData.size();
                int numAdded = 0;
                for (int i = 0; i < n; i++) {
                    if (utils.filter(i, set, desc) && set.isVisible(i)) {
                        IParticleRecord pb = set.get(i);
                        double[] p = pb.rawDoubleData();
                        // COLOR
                        if (hl.isHighlighted()) {
                            if (hlCmap) {
                                // Color map
                                double[] color = cmap.colormap(hl.getHlcmi(), hl.getHlcma().get(pb), hl.getHlcmmin(), hl.getHlcmmax());
                                tempVerts[curr.vertexIdx + curr.colorOffset] = Color.toFloatBits((float) color[0], (float) color[1], (float) color[2], 1.0f);
                            } else {
                                // Plain
                                tempVerts[curr.vertexIdx + curr.colorOffset] = Color.toFloatBits(c[0], c[1], c[2], c[3]);
                            }
                        } else {
                            if (colorMin != null && colorMax != null) {
                                double dist = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                                // fac = 0 -> colorMin,  fac = 1 -> colorMax
                                double fac = (dist - minDistance) / (maxDistance - minDistance);
                                interpolateColor(colorMin, colorMax, c, fac);
                            }
                            float r = 0, g = 0, b = 0;
                            if (set.colorNoise != 0) {
                                r = (float) ((StdRandom.uniform() - 0.5) * 2.0 * set.colorNoise);
                                g = (float) ((StdRandom.uniform() - 0.5) * 2.0 * set.colorNoise);
                                b = (float) ((StdRandom.uniform() - 0.5) * 2.0 * set.colorNoise);
                            }
                            tempVerts[curr.vertexIdx + curr.colorOffset] = Color.toFloatBits(MathUtils.clamp(c[0] + r, 0, 1), MathUtils.clamp(c[1] + g, 0, 1), MathUtils.clamp(c[2] + b, 0, 1), MathUtils.clamp(c[3], 0, 1));
                        }

                        // SIZE, CMAP_VALUE
                        tempVerts[curr.vertexIdx + additionalOffset] = (body.size + (float) (rand.nextGaussian() * body.size / 5d)) * sizeFactor * (float) Constants.DISTANCE_SCALE_FACTOR;

                        // POSITION
                        final int idx = curr.vertexIdx;
                        tempVerts[idx] = (float) p[0];
                        tempVerts[idx + 1] = (float) p[1];
                        tempVerts[idx + 2] = (float) p[2];

                        curr.vertexIdx += curr.vertexSize;
                        numAdded++;
                    }
                }
                int count = numAdded * curr.vertexSize;
                setCount(render, count);
                curr.mesh.setVertices(tempVerts, 0, count);

                setInGpu(render, true);
            }

            curr = meshes.get(getOffset(render));
            if (curr != null) {
                float meanDist = (float) (set.getMeanDistance());

                shaderProgram.setUniformf("u_alpha", alphas[base.ct.getFirstOrdinal()] * base.opacity);
                shaderProgram.setUniformf("u_falloff", set.profileDecay);
                shaderProgram.setUniformf("u_sizeFactor", (float) ((((stereoHalfWidth ? 2.0 : 1.0) * rc.scaleFactor * StarSettings.getStarPointSize() * 0.5)) * sizeFactor * meanDist / (camera.getFovFactor() * Constants.DISTANCE_SCALE_FACTOR)));
                shaderProgram.setUniformf("u_sizeLimits", (float) (set.particleSizeLimitsPoint[0] / camera.getFovFactor()), (float) (set.particleSizeLimitsPoint[1] / camera.getFovFactor()));

                curr.mesh.render(shaderProgram, ShapeType.Point.getGlType());

            }
        }
    }

    private void interpolateColor(float[] c0, float[] c1, float[] result, double factor) {
        float f = (float) factor;
        result[0] = (1 - f) * c0[0] + f * c1[0];
        result[1] = (1 - f) * c0[1] + f * c1[1];
        result[2] = (1 - f) * c0[2] + f * c1[2];
        result[3] = (1 - f) * c0[3] + f * c1[3];
    }

    protected void setInGpu(IRenderable renderable, boolean state) {
        if (inGpu != null) {
            if (inGpu.contains(renderable) && !state) {
                EventManager.publish(Event.GPU_DISPOSE_PARTICLE_GROUP, renderable);
            }
            if (state) {
                inGpu.add(renderable);
            } else {
                inGpu.remove(renderable);
            }
        }
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        if (event == Event.GPU_DISPOSE_PARTICLE_GROUP) {
            IRenderable renderable = (IRenderable) source;
            int offset = getOffset(renderable);
            clearMeshData(offset);
            inGpu.remove(renderable);
        }
    }

}
