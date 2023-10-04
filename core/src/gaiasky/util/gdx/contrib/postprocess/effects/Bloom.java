/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.contrib.postprocess.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import gaiasky.util.gdx.contrib.postprocess.PostProcessor;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.filters.Blur;
import gaiasky.util.gdx.contrib.postprocess.filters.Blur.BlurType;
import gaiasky.util.gdx.contrib.postprocess.filters.Combine;
import gaiasky.util.gdx.contrib.postprocess.filters.Threshold;
import gaiasky.util.gdx.contrib.postprocess.utils.PingPongBuffer;
import gaiasky.util.gdx.contrib.utils.GaiaSkyFrameBuffer;

public final class Bloom extends PostProcessorEffect {
    private final PingPongBuffer pingPongBuffer;
    private final Blur blur;
    private final Threshold threshold;
    private final Combine combine;
    private boolean blending = false;
    private int sFactor, dFactor;

    public Bloom(int fboWidth, int fboHeight) {
        pingPongBuffer = PostProcessor.newPingPongBuffer(fboWidth, fboHeight, PostProcessor.getFramebufferFormat(), false, false, false, false, false);

        blur = new Blur(fboWidth, fboHeight);
        threshold = new Threshold();
        combine = new Combine();
        disposables.addAll(blur, threshold, combine, pingPongBuffer);

        blur.setAmount(0);
        blur.setPasses(3);
        blur.setType(BlurType.Gaussian5x5b);
        setThreshold(0.3f);
        setBaseIntensity(1f);
        setBaseSaturation(0.85f);
        setBloomIntesnity(1.1f);
        setBloomSaturation(0.85f);

    }

    public void setBaseIntensity(float intensity) {
        combine.setSource1Intensity(intensity);
    }

    public void setBaseSaturation(float saturation) {
        combine.setSource1Saturation(saturation);
    }

    public void setBloomIntesnity(float intensity) {
        combine.setSource2Intensity(intensity);
    }

    public void setBloomSaturation(float saturation) {
        combine.setSource2Saturation(saturation);
    }

    public void enableBlending(int sfactor, int dfactor) {
        this.blending = true;
        this.sFactor = sfactor;
        this.dFactor = dfactor;
    }

    public void disableBlending() {
        this.blending = false;
    }

    public float getThreshold() {
        return threshold.getThreshold();
    }

    public void setThreshold(float threshold) {
        this.threshold.setThreshold(threshold);
    }

    public BlurType getBlurType() {
        return blur.getType();
    }

    public void setBlurType(BlurType type) {
        blur.setType(type);
    }


    public int getBlurPasses() {
        return blur.getPasses();
    }

    public void setBlurPasses(int passes) {
        blur.setPasses(passes);
    }

    public float getBlurAmount() {
        return blur.getAmount();
    }

    public void setBlurAmount(float amount) {
        blur.setAmount(amount);
    }

    @Override
    public void render(final FrameBuffer src, final FrameBuffer dest, GaiaSkyFrameBuffer main) {
        Texture texsrc = src.getColorBufferTexture();

        boolean blendingWasEnabled = PostProcessor.isStateEnabled(GL20.GL_BLEND);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        pingPongBuffer.begin();
        {
            // threshold / high-pass filterp
            // only areas with pixels >= threshold are blit to smaller fbo
            threshold.setInput(texsrc).setOutput(pingPongBuffer.getSourceBuffer()).render();

            // blur pass
            blur.render(pingPongBuffer);
        }
        pingPongBuffer.end();

        if (blending || blendingWasEnabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
        }

        if (blending) {
            //Gdx.gl.glBlendFuncSeparate(sfactor, dfactor, GL30.GL_ONE, GL30.GL_ONE);
            Gdx.gl.glBlendFunc(sFactor, dFactor);
        }

        restoreViewport(dest);

        // mix original scene and blurred threshold, modulate via
        // set(Base|Bloom)(Saturation|Intensity)
        combine.setOutput(dest).setInput(texsrc, pingPongBuffer.getResultTexture()).render();
        //copy.setInput(pingPongBuffer.getResultTexture()).setOutput(dest).render();

    }

    @Override
    public void rebind() {
        blur.rebind();
        threshold.rebind();
        combine.rebind();
        pingPongBuffer.rebind();
    }

    public static class Settings {
        public final String name;

        public final BlurType blurType;
        public final int blurPasses; // simple blur
        public final float blurAmount; // normal blur (1 pass)
        public final float bloomThreshold;

        public final float bloomIntensity;
        public final float bloomSaturation;
        public final float baseIntensity;
        public final float baseSaturation;

        public Settings(String name, BlurType blurType, int blurPasses, float blurAmount, float bloomThreshold, float baseIntensity, float baseSaturation, float bloomIntensity, float bloomSaturation) {
            this.name = name;
            this.blurType = blurType;
            this.blurPasses = blurPasses;
            this.blurAmount = blurAmount;

            this.bloomThreshold = bloomThreshold;
            this.baseIntensity = baseIntensity;
            this.baseSaturation = baseSaturation;
            this.bloomIntensity = bloomIntensity;
            this.bloomSaturation = bloomSaturation;
        }

        // simple blur
        public Settings(String name, int blurPasses, float bloomThreshold, float baseIntensity, float baseSaturation, float bloomIntensity, float bloomSaturation) {
            this(name, BlurType.Gaussian5x5, blurPasses, 0, bloomThreshold, baseIntensity, baseSaturation, bloomIntensity, bloomSaturation);
        }

        public Settings(Settings other) {
            this.name = other.name;
            this.blurType = other.blurType;
            this.blurPasses = other.blurPasses;
            this.blurAmount = other.blurAmount;

            this.bloomThreshold = other.bloomThreshold;
            this.baseIntensity = other.baseIntensity;
            this.baseSaturation = other.baseSaturation;
            this.bloomIntensity = other.bloomIntensity;
            this.bloomSaturation = other.bloomSaturation;
        }
    }
}
