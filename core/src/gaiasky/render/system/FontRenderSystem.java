/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.render.system;

import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.RenderGroup;
import gaiasky.render.api.I3DTextRenderable;
import gaiasky.render.api.IAnnotationsRenderable;
import gaiasky.render.api.IRenderable;
import gaiasky.scene.camera.ICamera;
import gaiasky.scene.system.render.SceneRenderer;
import gaiasky.util.gdx.g2d.BitmapFont;
import gaiasky.util.gdx.g2d.ExtSpriteBatch;
import gaiasky.util.gdx.shader.ExtShaderProgram;

import java.util.List;

public class FontRenderSystem extends AbstractRenderSystem {

    public BitmapFont fontDistanceField, font2d, fontTitles;
    private ExtSpriteBatch batch;

    protected FontRenderSystem(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtShaderProgram[] programs) {
        super(sceneRenderer, rg, alphas, programs);
    }

    public FontRenderSystem(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtSpriteBatch batch, ExtShaderProgram program) {
        super(sceneRenderer, rg, alphas, new ExtShaderProgram[] { program });
        this.batch = batch;
    }

    public FontRenderSystem(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtSpriteBatch batch, ExtShaderProgram program, BitmapFont fontDistanceField, BitmapFont font2d, BitmapFont fontTitles) {
        this(sceneRenderer, rg, alphas, batch, program);

        this.font2d = font2d;
        this.fontTitles = fontTitles;
        if (fontDistanceField != null) {
            this.fontDistanceField = fontDistanceField;
            this.fontDistanceField.getData().setScale(0.6f);
        }
    }

    @Override
    public void renderStud(List<IRenderable> renderables, ICamera camera, double t) {
        batch.begin();

        int size = renderables.size();
        ExtShaderProgram program = programs[0];
        if (program == null) {
            for (IRenderable renderable : renderables) {
                IAnnotationsRenderable s = (IAnnotationsRenderable) renderable;
                // Render sprite
                s.render(batch, camera, font2d, getAlpha(s));
            }
        } else {
            renderFont3D(renderables, program, camera, alphas[ComponentType.Labels.ordinal()]);
        }
        batch.end();
    }

    private void renderFont3D(List<IRenderable> renderables, ExtShaderProgram program, ICamera camera, float alpha) {

        renderables.forEach(r -> {
            I3DTextRenderable lr = (I3DTextRenderable) r;

            // Label color
            program.setUniform4fv("u_color", lr.textColour(), 0, 4);
            // Component alpha
            program.setUniformf("u_componentAlpha", getAlpha(lr) * (!lr.isLabel() ? 1 : alpha));
            // Font opacity multiplier, take into account element opacity
            program.setUniformf("u_opacity", 0.75f * lr.getTextOpacity());
            // z-far and k
            addDepthBufferUniforms(program, camera);

            lr.render(batch, program, this, rc, camera);
        });
    }

    @Override
    public void resize(int w, int h) {
        super.resize(w, h);
        updateBatchSize(w, h);
    }

    @Override
    public void updateBatchSize(int w, int h) {
        batch.setProjectionMatrix(batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h));
    }

}
