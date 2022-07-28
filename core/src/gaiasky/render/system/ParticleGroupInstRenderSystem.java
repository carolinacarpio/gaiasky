/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.system;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.render.api.IRenderable;
import gaiasky.render.RenderGroup;
import gaiasky.scene.system.render.SceneRenderer;
import gaiasky.scenegraph.ParticleGroup;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.util.Constants;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings.SceneSettings.StarSettings;
import gaiasky.util.color.Colormap;
import gaiasky.util.comp.DistToCameraComparator;
import gaiasky.util.gdx.shader.ExtShaderProgram;
import gaiasky.util.math.StdRandom;

import java.util.Random;

/**
 * Renders particle groups using instancing via billboards with geometry (quads as two triangles).
 */
public class ParticleGroupInstRenderSystem extends InstancedRenderSystem implements IObserver {
    protected static final Log logger = Logger.getLogger(ParticleGroupInstRenderSystem.class);

    private final Vector3 aux1;
    private int sizeOffset, particlePosOffset;
    private final Random rand;
    private final Colormap cmap;

    public ParticleGroupInstRenderSystem(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtShaderProgram[] shaders) {
        super(sceneRenderer, rg, alphas, shaders);
        comp = new DistToCameraComparator<>();
        rand = new Random(123);
        aux1 = new Vector3();
        cmap = new Colormap();
        EventManager.instance.subscribe(this, Event.GPU_DISPOSE_PARTICLE_GROUP);
    }

    @Override
    protected void addAttributesDivisor0(Array<VertexAttribute> attributes) {
        // Vertex position and texture coordinates are global
        attributes.add(new VertexAttribute(Usage.Position, 2, ExtShaderProgram.POSITION_ATTRIBUTE));
        attributes.add(new VertexAttribute(Usage.TextureCoordinates, 2, ExtShaderProgram.TEXCOORD_ATTRIBUTE));
    }

    @Override
    protected void addAttributesDivisor1(Array<VertexAttribute> attributes) {
        // Color, object position, proper motion and size are per instance
        attributes.add(new VertexAttribute(Usage.ColorPacked, 4, ExtShaderProgram.COLOR_ATTRIBUTE));
        attributes.add(new VertexAttribute(OwnUsage.ObjectPosition, 3, "a_particlePos"));
        attributes.add(new VertexAttribute(OwnUsage.Size, 1, "a_size"));
    }

    @Override
    protected void offsets0(MeshData curr) {
        // Not needed
    }

    @Override
    protected void offsets1(MeshData curr) {
        curr.colorOffset = curr.mesh.getInstancedAttribute(Usage.ColorPacked) != null ? curr.mesh.getInstancedAttribute(Usage.ColorPacked).offset / 4 : 0;
        sizeOffset = curr.mesh.getInstancedAttribute(OwnUsage.Size) != null ? curr.mesh.getInstancedAttribute(OwnUsage.Size).offset / 4 : 0;
        particlePosOffset = curr.mesh.getInstancedAttribute(OwnUsage.ObjectPosition) != null ? curr.mesh.getInstancedAttribute(OwnUsage.ObjectPosition).offset / 4 : 0;
    }

    @Override
    protected void initShaderProgram() {
        // Empty
    }

    protected void preRenderObjects(ExtShaderProgram shaderProgram, ICamera camera) {
        shaderProgram.setUniformMatrix("u_projView", camera.getCamera().combined);
        shaderProgram.setUniformf("u_camPos", camera.getPos().put(aux1));
        addEffectsUniforms(shaderProgram, camera);
    }

    @Override
    protected void renderObject(ExtShaderProgram shaderProgram, IRenderable renderable) {
        final ParticleGroup particleGroup = (ParticleGroup) renderable;
        synchronized (particleGroup) {
            if (!particleGroup.disposed) {
                boolean hlCmap = particleGroup.isHighlighted() && !particleGroup.isHlplain();
                int n = particleGroup.size();
                if (!inGpu(particleGroup)) {
                    int offset = addMeshData(6, n);
                    setOffset(particleGroup, offset);
                    curr = meshes.get(offset);
                    ensureInstanceAttribsSize(n * curr.instanceSize);

                    float[] c = particleGroup.getColor();
                    float[] colorMin = particleGroup.getColorMin();
                    float[] colorMax = particleGroup.getColorMax();
                    double minDistance = particleGroup.getMinDistance();
                    double maxDistance = particleGroup.getMaxDistance();

                    int numParticlesAdded = 0;
                    for (int i = 0; i < n; i++) {
                        if (particleGroup.filter(i) && particleGroup.isVisible(i)) {
                            IParticleRecord particle = particleGroup.get(i);
                            double[] p = particle.rawDoubleData();

                            // COLOR
                            if (particleGroup.isHighlighted()) {
                                if (hlCmap) {
                                    // Color map
                                    double[] color = cmap.colormap(particleGroup.getHlcmi(), particleGroup.getHlcma().get(particle), particleGroup.getHlcmmin(), particleGroup.getHlcmmax());
                                    tempInstanceAttribs[curr.instanceIdx + curr.colorOffset] = Color.toFloatBits((float) color[0], (float) color[1], (float) color[2], 1.0f);
                                } else {
                                    // Plain
                                    tempInstanceAttribs[curr.instanceIdx + curr.colorOffset] = Color.toFloatBits(c[0], c[1], c[2], c[3]);
                                }
                            } else {
                                if (colorMin != null && colorMax != null) {
                                    double dist = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                                    // fac = 0 -> colorMin,  fac = 1 -> colorMax
                                    double fac = (dist - minDistance) / (maxDistance - minDistance);
                                    interpolateColor(colorMin, colorMax, c, fac);
                                }
                                float r = 0, g = 0, b = 0;
                                if (particleGroup.colorNoise != 0) {
                                    r = (float) ((StdRandom.uniform() - 0.5) * 2.0 * particleGroup.colorNoise);
                                    g = (float) ((StdRandom.uniform() - 0.5) * 2.0 * particleGroup.colorNoise);
                                    b = (float) ((StdRandom.uniform() - 0.5) * 2.0 * particleGroup.colorNoise);
                                }
                                tempInstanceAttribs[curr.instanceIdx + curr.colorOffset] = Color.toFloatBits(MathUtils.clamp(c[0] + r, 0, 1), MathUtils.clamp(c[1] + g, 0, 1), MathUtils.clamp(c[2] + b, 0, 1), MathUtils.clamp(c[3], 0, 1));
                            }

                            // SIZE
                            tempInstanceAttribs[curr.instanceIdx + sizeOffset] = (particleGroup.size + (float) (rand.nextGaussian() * particleGroup.size / 5d)) * particleGroup.highlightedSizeFactor();

                            // PARTICLE POSITION
                            tempInstanceAttribs[curr.instanceIdx + particlePosOffset] = (float) p[0];
                            tempInstanceAttribs[curr.instanceIdx + particlePosOffset + 1] = (float) p[1];
                            tempInstanceAttribs[curr.instanceIdx + particlePosOffset + 2] = (float) p[2];

                            curr.instanceIdx += curr.instanceSize;
                            curr.numVertices++;
                            numParticlesAdded++;
                        }
                    }
                    // Global (divisor=0) vertices (position, uv)
                    curr.mesh.setVertices(tempVerts, 0, 24);
                    // Per instance (divisor=1) vertices
                    int count = numParticlesAdded * curr.instanceSize;
                    setCount(particleGroup, count);
                    curr.mesh.setInstanceAttribs(tempInstanceAttribs, 0, count);

                    setInGpu(particleGroup, true);
                }

                /*
                 * RENDER
                 */
                curr = meshes.get(getOffset(particleGroup));
                if (curr != null) {
                    float meanDist = (float) (particleGroup.getMeanDistance());

                    double s = .3e-4f;
                    shaderProgram.setUniformf("u_alpha", alphas[particleGroup.ct.getFirstOrdinal()] * particleGroup.getOpacity());
                    shaderProgram.setUniformf("u_falloff", particleGroup.profileDecay);
                    shaderProgram.setUniformf("u_sizeFactor", (float) (((StarSettings.getStarPointSize() * s)) * particleGroup.highlightedSizeFactor() * meanDist / Constants.DISTANCE_SCALE_FACTOR));
                    shaderProgram.setUniformf("u_sizeLimits", (float) (particleGroup.particleSizeLimits[0] * particleGroup.highlightedSizeFactor()), (float) (particleGroup.particleSizeLimits[1] * particleGroup.highlightedSizeFactor()));

                    try {
                        curr.mesh.render(shaderProgram, GL20.GL_TRIANGLES, 0, 6, n);
                    } catch (IllegalArgumentException e) {
                        logger.error(e, "Render exception");
                    }
                }
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
            if (inGpu != null)
                inGpu.remove(renderable);
        }
    }

}
