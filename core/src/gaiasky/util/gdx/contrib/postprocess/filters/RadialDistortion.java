/*******************************************************************************
 * Copyright 2012 bmanuel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package gaiasky.util.gdx.contrib.postprocess.filters;

import gaiasky.util.gdx.contrib.utils.ShaderLoader;

public final class RadialDistortion extends Filter<RadialDistortion> {
    private float zoom, distortion;

    public RadialDistortion() {
        super(ShaderLoader.fromFile("screenspace", "radial-distortion"));
        rebind();
        setDistortion(0.3f);
        setZoom(1f);
    }

    public float getDistortion() {
        return distortion;
    }

    public void setDistortion(float distortion) {
        this.distortion = distortion;
        setParam(Param.Distortion, this.distortion);
    }

    public float getZoom() {
        return zoom;
    }

    public void setZoom(float zoom) {
        this.zoom = zoom;
        setParam(Param.Zoom, this.zoom);
    }

    @Override
    protected void onBeforeRender() {
        inputTexture.bind(u_texture0);
    }

    @Override
    public void rebind() {
        setParams(Param.Texture0, u_texture0);
        setParams(Param.Distortion, distortion);
        setParams(Param.Zoom, zoom);

        endParams();
    }

    public enum Param implements Parameter {
        // @formatter:off
        Texture0("u_texture0", 0),
        Distortion("distortion", 0),
        Zoom("zoom", 0);
        // @formatter:on

        private final String mnemonic;
        private final int elementSize;

        Param(String m, int elementSize) {
            this.mnemonic = m;
            this.elementSize = elementSize;
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
}
