/*******************************************************************************
 * Copyright 2012 tsagrista
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

package gaiasky.util.gdx.contrib.postprocess.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import gaiasky.GaiaSky;
import gaiasky.util.Constants;
import gaiasky.util.Settings;
import gaiasky.util.gdx.contrib.postprocess.PostProcessorEffect;
import gaiasky.util.gdx.contrib.postprocess.filters.Copy;
import gaiasky.util.gdx.contrib.postprocess.filters.LevelsFilter;
import gaiasky.util.gdx.contrib.postprocess.filters.Luma;
import gaiasky.util.gdx.contrib.utils.GaiaSkyFrameBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implements brightness, contrast, hue and saturation levels, plus
 * auto-tone mapping HDR and gamma correction.
 */
public final class Levels extends PostProcessorEffect {
    private static final int LUMA_SIZE = 500;
    private int lumaLodLevels;
    private LevelsFilter levels;
    private final Luma luma;

    private float lumaMax = 0.9f, lumaAvg = 0.09f;
    private float currLumaMax = -1f, currLumaAvg = -1f;
    private final FrameBuffer lumaBuffer;

    /**
     * Creates the effect
     */
    public Levels() {
        levels = new LevelsFilter();
        luma = new Luma();

        // Compute number of lod levels based on LUMA_SIZE
        int size = LUMA_SIZE;
        lumaLodLevels = 1;
        while (size > 1) {
            size = (int) Math.floor(size / 2f);
            lumaLodLevels++;
        }

        GLFrameBuffer.FrameBufferBuilder builder = new GLFrameBuffer.FrameBufferBuilder(LUMA_SIZE, LUMA_SIZE);
        builder.addColorTextureAttachment(Settings.settings.graphics.useSRGB ? GL30.GL_SRGB8_ALPHA8 : GL30.GL_RGB16F, GL30.GL_RGB, GL30.GL_FLOAT);
        lumaBuffer = new GaiaSkyFrameBuffer(builder, 0);
        lumaBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear);

        luma.setImageSize(LUMA_SIZE, LUMA_SIZE);
        luma.setTexelSize(1f / LUMA_SIZE, 1f / LUMA_SIZE);
    }

    public Luma getLuma() {
        return luma;
    }

    public FrameBuffer getLumaBuffer() {
        return lumaBuffer;
    }

    /**
     * Set the brightness
     *
     * @param value The brightness value in [-1..1]
     */
    public void setBrightness(float value) {
        levels.setBrightness(value);
    }

    /**
     * Set the saturation
     *
     * @param value The saturation value in [0..2]
     */
    public void setSaturation(float value) {
        levels.setSaturation(value);
    }

    /**
     * Set the hue
     *
     * @param value The hue value in [0..2]
     */
    public void setHue(float value) {
        levels.setHue(value);
    }

    /**
     * Set the contrast
     *
     * @param value The contrast value in [0..2]
     */
    public void setContrast(float value) {
        levels.setContrast(value);
    }

    /**
     * Sets the gamma correction value
     *
     * @param value The gamma value in [0..3]
     */
    public void setGamma(float value) {
        levels.setGamma(value);
    }

    /**
     * Sets the exposure tone mapping value
     *
     * @param value The exposure value in [0..n]
     */
    public void setExposure(float value) {
        levels.setExposure(value);
    }

    public void enableToneMappingExposure() {
        levels.enableToneMappingExposure();
    }

    public void enableToneMappingAuto() {
        levels.enableToneMappingAuto();
    }

    public void enableToneMappingACES() {
        levels.enableToneMappingACES();
    }

    public void enableToneMappingUncharted() {
        levels.enableToneMappingUncharted();
    }

    public void enableToneMappingFilmic() {
        levels.enableToneMappingFilmic();
    }

    public void disableToneMapping() {
        levels.disableToneMapping();
    }

    @Override
    public void dispose() {
        if (levels != null) {
            levels.dispose();
            levels = null;
        }
    }

    @Override
    public void rebind() {
        levels.rebind();
    }

    FloatBuffer pixels = BufferUtils.createFloatBuffer(LUMA_SIZE * LUMA_SIZE * 3);

    @Override
    public void render(FrameBuffer src, FrameBuffer dest, GaiaSkyFrameBuffer main) {
        restoreViewport(dest);

        if (levels.isToneMappingAuto()) {
            // Compute luminance texture and use it to work out max and avg luminance
            computeLumaValuesCPU(src);
        }
        // Actual levels
        levels.setInput(src).setOutput(dest).render();

    }

    private void lowPassFilter(float baseLuma, float topLuma) {
        // Slowly move towards target luma values
        if (currLumaAvg < 0) {
            currLumaAvg = lumaAvg;
            currLumaMax = lumaMax;
        } else {
            float dt = Gdx.graphics.getDeltaTime();
            // Low pass filter
            float smoothingAvg = .2f;
            float smoothingMax = .2f;
            currLumaAvg += dt * (lumaAvg - currLumaAvg) / smoothingAvg;
            currLumaMax += dt * (lumaMax - currLumaMax) / smoothingMax;
            // Run in main thread
            GaiaSky.postRunnable(() -> levels.setAvgMaxLuma(Math.max(currLumaAvg, baseLuma), Math.min(currLumaMax, topLuma)));
        }
    }

    /** Is the compute max/avg process running? **/
    private final AtomicBoolean processRunning = new AtomicBoolean(false);
    private long lastFrame = -Long.MAX_VALUE;

    private void computeLumaValuesCPU(FrameBuffer src) {
        // Launch process every 4th frame
        if (!processRunning.get() && GaiaSky.instance.frames - lastFrame >= 4) {
            processRunning.set(true);
            // Render as is
            luma.enableProgramLuma();
            luma.setInput(src).setOutput(lumaBuffer).render();
            lumaBuffer.getColorBufferTexture().bind();

            // Get texture as is and compute avg/max in CPU - use with reinhardToneMapping()
            //GL30.glGenerateMipmap(lumaBuffer.getColorBufferTexture().glTarget);
            //GL30.glGetTexImage(lumaBuffer.getColorBufferTexture().glTarget, Math.min(3, lumaLodLevels - 1), GL30.GL_RGB, GL30.GL_FLOAT, pixels);
            GL30.glGetTexImage(lumaBuffer.getColorBufferTexture().glTarget, 0, GL30.GL_RGB, GL30.GL_FLOAT, pixels);

            // Launch asynchronously.
            GaiaSky.instance.getExecutorService().execute(() -> {
                computeMaxAvg(pixels);
                if (!Double.isNaN(lumaAvg) && !Double.isNaN(lumaMax)) {
                    lowPassFilter(0.0f, 2.0f);
                }
                processRunning.set(false);
            });

            // Generate mip-map to get average - use with autoExposureToneMapping()
            //GL30.glGenerateMipmap(lumaBuffer.getColorBufferTexture().glTarget);
            //GL30.glGetTexImage(lumaBuffer.getColorBufferTexture().glTarget, lumaLodLevels - 1, GL30.GL_RGB, GL30.GL_FLOAT, pixels);
            //lumaAvg = pixels.get(0);
            //// Ugly hack, but works
            //lumaMax = GaiaSky.instance.getICamera().getPos().lend() * Constants.U_TO_PC > 10000 ? lumaAvg * 5000f : lumaAvg * 30f;
            //if (!Double.isNaN(lumaAvg) && !Double.isNaN(lumaMax)) {
            //    lowPassFilter(0.08f, 1.0f);
            //}
            lastFrame = GaiaSky.instance.frames;
        }
    }

    private void computeMaxAvg(FloatBuffer buff) {
        buff.rewind();
        double avg = 0;
        double max = -Double.MAX_VALUE;
        int i = 1;
        while (buff.hasRemaining()) {
            double v = buff.get();

            // Skip g, b, a
            buff.get();
            buff.get();
            buff.get();

            if (!Double.isNaN(v)) {
                avg = avg + (v - avg) / (i + 1);
                max = v > max ? v : max;
                i++;
            }
        }

        // Avoid very bright images by setting a minimum maximum luminosity
        lumaMax = (float) max;
        lumaAvg = (float) avg;
        buff.clear();
    }

    private void computeLumaValuesMipMap(FrameBuffer src) {
        // Average using mipmap
        luma.enableProgramAvg();
        lumaAvg = renderLumaMipMap(src);

        // Max using mipmap
        luma.enableProgramMax();
        lumaMax = renderLumaMipMap(src);

        lowPassFilter(0.08f, 1.0f);
    }

    private float renderLumaMipMap(FrameBuffer src) {
        int mipLevel = 1;
        float lodLevel = 0;
        int size = (int) Math.floor(LUMA_SIZE / 2f);
        while (size >= 1f) {
            luma.setImageSize(size, size);
            luma.setTexelSize(1f / size, 1f / size);
            luma.setLodLevel(lodLevel);

            // Draw to lumaBuffer, copy texture to mipmap level later
            luma.setInput(src).setOutput(lumaBuffer).render();

            // Copy framebuffer to specified mipmap level in texture
            GL30.glCopyTexImage2D(GL30.GL_TEXTURE_2D, mipLevel, GL30.GL_RGBA, 0, 0, size, size, 0);

            mipLevel++;
            lodLevel++;
            size = (int) Math.floor(size / 2f);
        }

        // For HDR, we need the max and average luminance. At this point, a 1x1 rendering was made to the frame buffer.
        lumaBuffer.begin();
        GL30.glReadPixels(0, 0, 1, 1, GL30.GL_RGBA, GL30.GL_FLOAT, pixels);
        lumaBuffer.end();

        pixels.rewind();
        return pixels.get();
    }

}
