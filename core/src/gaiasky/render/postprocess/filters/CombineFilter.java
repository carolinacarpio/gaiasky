/*
 * Copyright (c) 2023-2024 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.postprocess.filters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.render.util.ShaderLoader;

public final class CombineFilter extends Filter<CombineFilter> {

    private float s1i, s1s, s2i, s2s;
    private Texture inputTexture2 = null;

    public CombineFilter() {
        super(ShaderLoader.fromFile("screenspace", "combine"));
        s1i = 1f;
        s2i = 1f;
        s1s = 1f;
        s2s = 1f;

        rebind();
    }

    public CombineFilter setInput(FrameBuffer buffer1, FrameBuffer buffer2) {
        this.inputTexture = buffer1.getColorBufferTexture();
        this.inputTexture2 = buffer2.getColorBufferTexture();
        return this;
    }

    public CombineFilter setInput(Texture texture1, Texture texture2) {
        this.inputTexture = texture1;
        this.inputTexture2 = texture2;
        return this;
    }

    public float getSource1Intensity() {
        return s1i;
    }

    public void setSource1Intensity(float intensity) {
        s1i = intensity;
        setParam(CombineFilter.Param.Source1Intensity, intensity);
    }

    public float getSource2Intensity() {
        return s2i;
    }

    public void setSource2Intensity(float intensity) {
        s2i = intensity;
        setParam(CombineFilter.Param.Source2Intensity, intensity);
    }

    public float getSource1Saturation() {
        return s1s;
    }

    public void setSource1Saturation(float saturation) {
        s1s = saturation;
        setParam(CombineFilter.Param.Source1Saturation, saturation);
    }

    public float getSource2Saturation() {
        return s2s;
    }

    public void setSource2Saturation(float saturation) {
        s2s = saturation;
        setParam(CombineFilter.Param.Source2Saturation, saturation);
    }

    @Override
    public void rebind() {
        setParams(Param.Texture0, u_texture0);
        setParams(Param.Texture1, u_texture1);
        setParams(Param.Source1Intensity, s1i);
        setParams(Param.Source2Intensity, s2i);
        setParams(Param.Source1Saturation, s1s);
        setParams(Param.Source2Saturation, s2s);
        endParams();
    }

    @Override
    protected void onBeforeRender() {
        inputTexture.bind(u_texture0);
        inputTexture2.bind(u_texture1);
    }

    public enum Param implements Parameter {
        // @formatter:off
        Texture0("u_texture0", 0),
        Texture1("u_texture1", 0),
        Source1Intensity("u_src1Intensity", 0),
        Source1Saturation("u_src1Saturation", 0),
        Source2Intensity("u_src2Intensity", 0),
        Source2Saturation("u_src2Saturation", 0);
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
