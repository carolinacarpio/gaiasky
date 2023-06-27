/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.contrib.postprocess.effects;

import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.filters.XBRZUpscale;
import gaiasky.util.gdx.contrib.utils.GaiaSkyFrameBuffer;

public class XBRZ extends PostProcessorEffect {

    private final XBRZUpscale filter;

    public XBRZ() {
        super();
        filter = new XBRZUpscale();
        disposables.add(filter);
    }

    public void setInputSize(int w, int h) {
        filter.setInputSize(w, h);
    }
    public void setOutputSize(int w, int h) {
        filter.setOutputSize(w, h);
    }

    @Override
    public void rebind() {
        if (filter != null) {
            filter.rebind();
        }
    }

    @Override
    public void render(FrameBuffer src, FrameBuffer dest, GaiaSkyFrameBuffer main) {
        restoreViewport(dest);
        // Set input, output and render
        filter.setInput(src).setOutput(dest).render();
    }
}

