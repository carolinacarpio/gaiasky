/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

/*******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package gaiasky.util.gdx.contrib.postprocess.filters;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import gaiasky.util.gdx.contrib.utils.ShaderLoader;

/**
 * Raymarching filter.
 *
 * @author Toni Sagrista
 */
public final class RaymarchingFilter extends Filter3<RaymarchingFilter> {
    private Vector2 viewport;
    private Matrix4 frustumCorners;
    private Matrix4 camInvView;

    public enum Param implements Parameter {
        // @formatter:off
        Texture("u_texture0", 0),
        Viewport("u_viewport", 2),
        CamInvView("u_camInvViewTransform", 16),
        FrustumCorners("u_frustumCorners", 16);
        // @formatter:on

        private String mnemonic;
        private int elementSize;

        Param(String mnemonic, int arrayElementSize) {
            this.mnemonic = mnemonic;
            this.elementSize = arrayElementSize;
        }

        @Override
        public String mnemonic() {
            return this.mnemonic;
        }

        @Override
        public int arrayElementSize() {
            return this.elementSize;
        }
    }


    /**
     * Creates a filter with the given viewport size
     *
     * @param viewportWidth  The viewport width in pixels.
     * @param viewportHeight The viewport height in pixels.
     */
    public RaymarchingFilter(int viewportWidth, int viewportHeight) {
        this(new Vector2((float) viewportWidth, (float) viewportHeight));
    }

    /**
     * Creates a filter with the given viewport size.
     *
     * @param viewportSize The viewport size in pixels.
     */
    public RaymarchingFilter(Vector2 viewportSize) {
        super(ShaderLoader.fromFile("raymarching/screenspace", "raymarching/blackhole"));
        this.viewport = viewportSize;
        this.frustumCorners = new Matrix4();
        this.camInvView = new Matrix4();
        rebind();
    }

    public void setFrustumCorners(Matrix4 fc){
        this.frustumCorners = fc;
        setParam(Param.FrustumCorners, this.frustumCorners);
    }

    public void setCaminvView(Matrix4 civ){
        this.camInvView.set(civ);
        setParam(Param.CamInvView, this.camInvView);
    }

    public void setViewportSize(float width, float height) {
        this.viewport.set(width, height);
        setParam(Param.Viewport, this.viewport);
    }

    public Vector2 getViewportSize() {
        return viewport;
    }

    @Override
    public void rebind() {
        // reimplement super to batch every parameter
        setParams(Param.Texture, u_texture0);
        setParams(Param.Viewport, viewport);
        setParams(Param.FrustumCorners, frustumCorners);
        setParams(Param.CamInvView, camInvView);
        endParams();
    }

    @Override
    protected void onBeforeRender() {
        inputTexture.bind(u_texture0);
    }
}
