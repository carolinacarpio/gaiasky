/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.system;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.render.IRenderable;
import gaiasky.render.SceneGraphRenderer.RenderGroup;
import gaiasky.render.system.ImmediateModeRenderSystem.MeshData;
import gaiasky.render.system.ImmediateModeRenderSystem.OwnUsage;
import gaiasky.scenegraph.StarGroup;
import gaiasky.scenegraph.camera.CameraManager;
import gaiasky.scenegraph.camera.FovCamera;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.scenegraph.particle.IParticleRecord;
import gaiasky.util.Constants;
import gaiasky.util.Settings;
import gaiasky.util.color.Colormap;
import gaiasky.util.coord.AstroUtils;
import gaiasky.util.gdx.shader.ExtShaderProgram;

/**
 * Renders star groups using regular arrays via billboards with geometry (quads as two triangles).
 */
public class StarGroupRenderSystem extends PointCloudTriRenderSystem implements IObserver {
    private final Vector3 aux1;
    private int sizeOffset, pmOffset, uvOffset, starPosOffset;
    private final Colormap cmap;

    private StarGroupTriComponent triComponent;

    public StarGroupRenderSystem(RenderGroup rg, float[] alphas, ExtShaderProgram[] shaders) {
        super(rg, alphas, shaders);
        this.aux1 = new Vector3();
        cmap = new Colormap();
        triComponent.setStarTexture(Settings.settings.scene.star.getStarTexture());

        EventManager.instance.subscribe(this, Events.STAR_BRIGHTNESS_CMD, Events.STAR_BRIGHTNESS_POW_CMD, Events.STAR_POINT_SIZE_CMD, Events.STAR_MIN_OPACITY_CMD, Events.DISPOSE_STAR_GROUP_GPU_MESH, Events.STAR_TEXTURE_IDX_CMD);
    }

    protected void addVertexAttributes(Array<VertexAttribute> attributes) {
        attributes.add(new VertexAttribute(Usage.Position, 2, ExtShaderProgram.POSITION_ATTRIBUTE));
        attributes.add(new VertexAttribute(Usage.TextureCoordinates, 2, ExtShaderProgram.TEXCOORD_ATTRIBUTE));
        attributes.add(new VertexAttribute(Usage.ColorPacked, 4, ExtShaderProgram.COLOR_ATTRIBUTE));
        attributes.add(new VertexAttribute(OwnUsage.ObjectPosition, 3, "a_starPos"));
        attributes.add(new VertexAttribute(OwnUsage.ProperMotion, 3, "a_pm"));
        attributes.add(new VertexAttribute(OwnUsage.Size, 1, "a_size"));
    }

    protected void offsets(MeshData curr) {
        curr.colorOffset = curr.mesh.getVertexAttribute(Usage.ColorPacked) != null ? curr.mesh.getVertexAttribute(Usage.ColorPacked).offset / 4 : 0;
        uvOffset = curr.mesh.getVertexAttribute(Usage.TextureCoordinates) != null ? curr.mesh.getVertexAttribute(Usage.TextureCoordinates).offset / 4 : 0;
        pmOffset = curr.mesh.getVertexAttribute(OwnUsage.ProperMotion) != null ? curr.mesh.getVertexAttribute(OwnUsage.ProperMotion).offset / 4 : 0;
        sizeOffset = curr.mesh.getVertexAttribute(OwnUsage.Size) != null ? curr.mesh.getVertexAttribute(OwnUsage.Size).offset / 4 : 0;
        starPosOffset = curr.mesh.getVertexAttribute(OwnUsage.ObjectPosition) != null ? curr.mesh.getVertexAttribute(OwnUsage.ObjectPosition).offset / 4 : 0;
    }

    @Override
    protected void initShaderProgram() {
        triComponent = new StarGroupTriComponent();
        triComponent.initShaderProgram(getShaderProgram());
    }

    protected void preRenderObjects(ExtShaderProgram shaderProgram, ICamera camera) {
        shaderProgram.setUniformMatrix("u_projView", camera.getCamera().combined);
        shaderProgram.setUniformf("u_camPos", camera.getPos().put(aux1));
        addEffectsUniforms(shaderProgram, camera);
        // Update projection if fovMode is 3
        triComponent.fovMode = camera.getMode().getGaiaFovMode();
        if (triComponent.fovMode == 3) {
            // Cam is Fov1 & Fov2
            FovCamera cam = ((CameraManager) camera).fovCamera;
            // Update combined
            PerspectiveCamera[] cams = camera.getFrontCameras();
            shaderProgram.setUniformMatrix("u_projView", cams[cam.dirIndex].combined);
        }
    }

    protected void renderObject(ExtShaderProgram shaderProgram, IRenderable renderable) {
        final StarGroup starGroup = (StarGroup) renderable;
        synchronized (starGroup) {
            if (!starGroup.disposed) {
                boolean hlCmap = starGroup.isHighlighted() && !starGroup.isHlplain();
                if (!starGroup.inGpu()) {
                    int n = starGroup.size();
                    starGroup.offset = addMeshData(n * 4, n * 6);
                    // Get mesh, reset indices
                    curr = meshes.get(starGroup.offset);
                    ensureTempVertsSize(n * 4 * curr.vertexSize);
                    ensureTempIndicesSize(n * 6);
                    int numVerticesAdded = 0;
                    int numStarsAdded = 0;

                    for (int i = 0; i < n; i++) {
                        if (starGroup.filter(i) && starGroup.isVisible(i)) {
                            IParticleRecord particle = starGroup.data().get(i);
                            if (!Double.isFinite(particle.size())) {
                                logger.debug("Star " + particle.id() + " has a non-finite size");
                                continue;
                            }
                            // 4 vertices per star
                            for (int vert = 0; vert < 4; vert++) {
                                // Vertex POSITION
                                tempVerts[curr.vertexIdx] = vertPos[vert].getFirst();
                                tempVerts[curr.vertexIdx + 1] = vertPos[vert].getSecond();

                                // UV coordinates
                                tempVerts[curr.vertexIdx + uvOffset] = vertUV[vert].getFirst();
                                tempVerts[curr.vertexIdx + uvOffset + 1] = vertUV[vert].getSecond();

                                // COLOR
                                if (hlCmap) {
                                    // Color map
                                    double[] color = cmap.colormap(starGroup.getHlcmi(), starGroup.getHlcma().get(particle), starGroup.getHlcmmin(), starGroup.getHlcmmax());
                                    tempVerts[curr.vertexIdx + curr.colorOffset] = Color.toFloatBits((float) color[0], (float) color[1], (float) color[2], 1.0f);
                                } else {
                                    // Plain
                                    tempVerts[curr.vertexIdx + curr.colorOffset] = starGroup.getColor(i);
                                }

                                // SIZE
                                if (starGroup.isHlAllVisible() && starGroup.isHighlighted()) {
                                    tempVerts[curr.vertexIdx + sizeOffset] = Math.max(10f, (float) (particle.size() * Constants.STAR_SIZE_FACTOR) * starGroup.highlightedSizeFactor());
                                } else {
                                    tempVerts[curr.vertexIdx + sizeOffset] = (float) (particle.size() * Constants.STAR_SIZE_FACTOR) * starGroup.highlightedSizeFactor();
                                }

                                // PROPER MOTION [u/yr]
                                tempVerts[curr.vertexIdx + pmOffset] = (float) particle.pmx();
                                tempVerts[curr.vertexIdx + pmOffset + 1] = (float) particle.pmy();
                                tempVerts[curr.vertexIdx + pmOffset + 2] = (float) particle.pmz();

                                // STAR POSITION [u]
                                tempVerts[curr.vertexIdx + starPosOffset] = (float) particle.x();
                                tempVerts[curr.vertexIdx + starPosOffset + 1] = (float) particle.y();
                                tempVerts[curr.vertexIdx + starPosOffset + 2] = (float) particle.z();

                                curr.vertexIdx += curr.vertexSize;
                                curr.numVertices++;
                                numVerticesAdded++;
                            }
                            // Indices
                            quadIndices(curr);
                            numStarsAdded++;
                        }
                    }
                    starGroup.count = numVerticesAdded * curr.vertexSize;
                    curr.mesh.setVertices(tempVerts, 0, starGroup.count);
                    curr.mesh.setIndices(tempIndices, 0, numStarsAdded * 6);

                    starGroup.inGpu(true);
                }

                /*
                 * RENDER
                 */
                curr = meshes.get(starGroup.offset);
                if (curr != null) {
                    if (triComponent.starTex != null) {
                        triComponent.starTex.bind(0);
                        shaderProgram.setUniformi("u_starTex", 0);
                    }

                    triComponent.alphaSizeBr[0] = starGroup.opacity * alphas[starGroup.ct.getFirstOrdinal()];
                    triComponent.alphaSizeBr[1] = (triComponent.fovMode == 0 ? 1f : 10f) * triComponent.starPointSize * 1e6f * starGroup.highlightedSizeFactor();
                    shaderProgram.setUniform3fv("u_alphaSizeBr", triComponent.alphaSizeBr, 0, 3);

                    // Days since epoch
                    // Emulate double with floats, for compatibility
                    double curRt = AstroUtils.getDaysSince(GaiaSky.instance.time.getTime(), starGroup.getEpoch());
                    float curRt2 = (float) (curRt - (double) ((float) curRt));
                    shaderProgram.setUniformf("u_t", (float) curRt, curRt2);

                    try {
                        curr.mesh.render(shaderProgram, GL20.GL_TRIANGLES);
                    } catch (IllegalArgumentException e) {
                        logger.error(e, "Render exception");
                    }
                }
            }
        }
    }

    @Override
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case STAR_MIN_OPACITY_CMD -> {
            triComponent.updateStarOpacityLimits((float) data[0], Settings.settings.scene.star.opacity[1]);
            triComponent.touchStarParameters(getShaderProgram());
        }
        case STAR_BRIGHTNESS_CMD -> {
            triComponent.updateStarBrightness((float) data[0]);
            triComponent.touchStarParameters(getShaderProgram());
        }
        case STAR_BRIGHTNESS_POW_CMD -> {
            triComponent.updateBrightnessPower((float) data[0]);
            triComponent.touchStarParameters(getShaderProgram());
        }
        case STAR_POINT_SIZE_CMD -> {
            triComponent.updateStarPointSize((float) data[0]);
            triComponent.touchStarParameters(getShaderProgram());
        }
        case DISPOSE_STAR_GROUP_GPU_MESH -> {
            Integer meshIdx = (Integer) data[0];
            clearMeshData(meshIdx);
        }
        case STAR_TEXTURE_IDX_CMD -> GaiaSky.postRunnable(() -> triComponent.setStarTexture(Settings.settings.scene.star.getStarTexture()));
        default -> {
        }
        }
    }

}
