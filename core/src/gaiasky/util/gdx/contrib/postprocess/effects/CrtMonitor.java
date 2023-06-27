/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.contrib.postprocess.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Vector2;
import gaiasky.util.gdx.contrib.postprocess.PostProcessor;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.filters.Blur;
import gaiasky.util.gdx.contrib.postprocess.filters.Blur.BlurType;
import gaiasky.util.gdx.contrib.postprocess.filters.Combine;
import gaiasky.util.gdx.contrib.postprocess.filters.CrtScreen;
import gaiasky.util.gdx.contrib.postprocess.filters.CrtScreen.RgbMode;
import gaiasky.util.gdx.contrib.postprocess.utils.PingPongBuffer;
import gaiasky.util.gdx.contrib.utils.GaiaSkyFrameBuffer;

public final class CrtMonitor extends PostProcessorEffect {
    private final CrtScreen crt;
    private final Combine combine;
    private PingPongBuffer pingPongBuffer = null;
    private FrameBuffer buffer = null;
    private Blur blur;
    private boolean blending = false;
    private int sFactor, dFactor;

    // the effect is designed to work on the whole screen area, no small/mid size tricks!
    public CrtMonitor(int fboWidth, int fboHeight, boolean barrelDistortion, boolean performBlur, RgbMode mode, int effectsSupport) {

        if (performBlur) {
            pingPongBuffer = PostProcessor.newPingPongBuffer(fboWidth, fboHeight, PostProcessor.getFramebufferFormat(), false);
            blur = new Blur(fboWidth, fboHeight);
            blur.setPasses(1);
            blur.setAmount(1f);
            // blur.setType( BlurType.Gaussian3x3b ); // high defocus
            blur.setType(BlurType.Gaussian3x3); // modern machines defocus
            disposables.addAll(pingPongBuffer, blur);
        } else {
            buffer = new FrameBuffer(PostProcessor.getFramebufferFormat(), fboWidth, fboHeight, false);
            disposables.addAll(buffer);
        }

        combine = new Combine();
        crt = new CrtScreen(barrelDistortion, mode, effectsSupport);
        disposables.addAll(combine, crt);
    }

    public void enableBlending(int sFactor, int dFactor) {
        this.blending = true;
        this.sFactor = sFactor;
        this.dFactor = dFactor;
    }

    public void disableBlending() {
        this.blending = false;
    }

    // setters
    public void setTime(float elapsedSecs) {
        crt.setTime(elapsedSecs);
    }

    public void setColorOffset(float offset) {
        crt.setColorOffset(offset);
    }

    public void setChromaticDispersion(float redCyan, float blueYellow) {
        crt.setChromaticDispersion(redCyan, blueYellow);
    }

    public void setChromaticDispersionRC(float redCyan) {
        crt.setChromaticDispersionRC(redCyan);
    }

    public void setChromaticDispersionBY(float blueYellow) {
        crt.setChromaticDispersionBY(blueYellow);
    }

    public void setTint(float r, float g, float b) {
        crt.setTint(r, g, b);
    }

    public void setDistortion(float distortion) {
        crt.setDistortion(distortion);
    }

    // getters
    public Combine getCombinePass() {
        return combine;
    }

    public float getOffset() {
        return crt.getOffset();
    }

    public Vector2 getChromaticDispersion() {
        return crt.getChromaticDispersion();
    }

    public float getZoom() {
        return crt.getZoom();
    }

    public void setZoom(float zoom) {
        crt.setZoom(zoom);
    }

    public Color getTint() {
        return crt.getTint();
    }

    public void setTint(Color tint) {
        crt.setTint(tint);
    }

    public RgbMode getRgbMode() {
        return crt.getRgbMode();
    }

    public void setRgbMode(RgbMode mode) {
        crt.setRgbMode(mode);
    }

    @Override
    public void rebind() {
        crt.rebind();
    }

    @Override
    public void render(FrameBuffer src, FrameBuffer dest, GaiaSkyFrameBuffer main) {
        // the original scene
        Texture in = src.getColorBufferTexture();

        boolean blendingWasEnabled = PostProcessor.isStateEnabled(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        Texture out;

        if (blur != null) {

            pingPongBuffer.begin();
            {
                // crt pass
                crt.setInput(in).setOutput(pingPongBuffer.getSourceBuffer()).render();

                // blur pass
                blur.render(pingPongBuffer);
            }
            pingPongBuffer.end();

            out = pingPongBuffer.getResultTexture();
        } else {
            // crt pass
            crt.setInput(in).setOutput(buffer).render();

            out = buffer.getColorBufferTexture();
        }

        if (blending || blendingWasEnabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
        }

        if (blending) {
            Gdx.gl.glBlendFunc(sFactor, dFactor);
        }

        restoreViewport(dest);

        // do combine pass
        combine.setOutput(dest).setInput(in, out).render();
    }

}
