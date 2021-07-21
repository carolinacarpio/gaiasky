/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.system;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import gaiasky.GaiaSky;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.render.IRenderable;
import gaiasky.render.PostProcessorFactory;
import gaiasky.render.system.AbstractRenderSystem.RenderSystemRunnable;
import gaiasky.scenegraph.Star;
import gaiasky.scenegraph.camera.ICamera;
import gaiasky.util.GlobalConf;
import gaiasky.util.GlobalConf.SceneConf.GraphicsQuality;
import gaiasky.util.GlobalResources;
import gaiasky.util.gravwaves.RelativisticEffectsManager;
import gaiasky.util.math.Vector3d;

import java.util.Arrays;

public class LightPositionUpdater implements RenderSystemRunnable, IObserver {

    private final Object lock;
    private int nLights;
    private float[] positions;
    private float[] viewAngles;
    private float[] colors;
    private final Vector3 auxV;
    private final Vector3d auxD;

    private Texture glowTex;

    public LightPositionUpdater() {
        this.lock = new Object();

        reinitialize(GlobalConf.scene.GRAPHICS_QUALITY.getGlowNLights());

        this.auxV = new Vector3();
        this.auxD = new Vector3d();

        EventManager.instance.subscribe(this, Events.GRAPHICS_QUALITY_UPDATED);
    }

    public void reinitialize(int nLights) {
        synchronized (lock) {
            this.nLights = nLights;
            this.positions = initializeList(null, nLights * 2);
            this.viewAngles = initializeList(null, nLights);
            this.colors = initializeList(null, nLights * 3);
        }
    }

    public float[] initializeList(final float[] list, int size) {
        if (list == null) {
            return new float[size];
        } else {
            if (list.length == size) {
                return list;
            } else {
                synchronized (list) {
                    return Arrays.copyOf(list, size);
                }
            }
        }
    }

    /**
     * Sets the occlusion texture to use for the glow effect
     *
     * @param tex The texture
     */
    public void setGlowTexture(Texture tex) {
        this.glowTex = tex;
    }

    @Override
    public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
        synchronized (lock) {
            int size = renderables.size;
            if (PostProcessorFactory.instance.getPostProcessor().isLightScatterEnabled()) {
                // Compute light positions for light scattering or light
                // glow
                int lightIndex = 0;
                float angleEdgeDeg = camera.getAngleEdge() * MathUtils.radDeg;
                for (int i = size - 1; i >= 0; i--) {
                    IRenderable s = renderables.get(i);
                    if (s instanceof Star) {
                        Star p = (Star) s;
                        if (lightIndex < nLights && (GlobalConf.program.CUBEMAP_MODE || GlobalConf.runtime.OPENVR || GaiaSky.instance.cameraManager.getDirection().angle(p.translation) < angleEdgeDeg)) {
                            Vector3d pos3d = p.translation.put(auxD);

                            // Aberration
                            GlobalResources.applyRelativisticAberration(pos3d, camera);
                            // GravWaves
                            RelativisticEffectsManager.getInstance().gravitationalWavePos(pos3d);
                            Vector3 pos3 = pos3d.put(auxV);

                            float w = GlobalConf.screen.SCREEN_WIDTH;
                            float h = GlobalConf.screen.SCREEN_HEIGHT;

                            camera.getCamera().project(pos3, 0, 0, w, h);
                            // Here we **need** to use
                            // Gdx.graphics.getWidth/Height() because we use
                            // camera.project() which uses screen
                            // coordinates only
                            positions[lightIndex * 2] = auxV.x / w;
                            positions[lightIndex * 2 + 1] = auxV.y / h;
                            viewAngles[lightIndex] = (float) p.viewAngleApparent;
                            colors[lightIndex * 3] = p.cc[0];
                            colors[lightIndex * 3 + 1] = p.cc[1];
                            colors[lightIndex * 3 + 2] = p.cc[2];
                            lightIndex++;
                        }
                    }
                }
                EventManager.instance.post(Events.LIGHT_POS_2D_UPDATE, lightIndex, positions, viewAngles, colors, glowTex);
            } else {
                EventManager.instance.post(Events.LIGHT_POS_2D_UPDATE, 0, positions, viewAngles, colors, glowTex);
            }
        }

    }

    @Override
    public void notify(final Events event, final Object... data) {
        switch (event) {
        case GRAPHICS_QUALITY_UPDATED:
            // Update graphics quality
            GraphicsQuality gq = (GraphicsQuality) data[0];
            reinitialize(gq.getGlowNLights());
            break;
        default:
            break;

        }
    }
}
