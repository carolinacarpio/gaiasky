/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.scene.system.render.draw;

import com.badlogic.gdx.utils.Array;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.render.RenderGroup;
import gaiasky.render.api.IRenderable;
import gaiasky.render.system.FontRenderSystem;
import gaiasky.scene.Mapper;
import gaiasky.scene.component.Render;
import gaiasky.scene.system.render.SceneRenderer;
import gaiasky.scene.system.render.draw.text.AnnotationsEntityRenderSystem;
import gaiasky.scene.view.LabelView;
import gaiasky.scene.camera.ICamera;
import gaiasky.util.gdx.g2d.BitmapFont;
import gaiasky.util.gdx.g2d.ExtSpriteBatch;
import gaiasky.util.gdx.shader.ExtShaderProgram;

/**
 * Renders text (labels, annotations, titles, etc.) in two and three dimensional space.
 * TODO - extend AbstractRenderSystem instead of FontRenderSystem
 */
public class TextRenderer extends FontRenderSystem {

    private final ExtSpriteBatch batch;
    public BitmapFont fontDistanceField, font2d, fontTitles;

    private final AnnotationsEntityRenderSystem girdRenderer;

    private final LabelView view;

    public TextRenderer(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtSpriteBatch batch, ExtShaderProgram program) {
        super(sceneRenderer, rg, alphas, new ExtShaderProgram[] { program });
        this.batch = batch;

        this.girdRenderer = new AnnotationsEntityRenderSystem();
        this.view = new LabelView();
    }

    public TextRenderer(SceneRenderer sceneRenderer, RenderGroup rg, float[] alphas, ExtSpriteBatch batch, ExtShaderProgram program, BitmapFont fontDistanceField, BitmapFont font2d, BitmapFont fontTitles) {
        this(sceneRenderer, rg, alphas, batch, program);

        this.font2d = font2d;
        this.fontTitles = fontTitles;
        if (fontDistanceField != null) {
            this.fontDistanceField = fontDistanceField;
            this.fontDistanceField.getData().setScale(0.6f);
        }
    }

    @Override
    public void renderStud(Array<IRenderable> renderables, ICamera camera, double t) {
        batch.begin();

        int size = renderables.size;
        ExtShaderProgram program = programs[0];
        if (program == null) {
            for (int i = 0; i < size; i++) {
                Render render = (Render) renderables.get(i);
                girdRenderer.render(render, batch, camera, font2d, getAlpha(render.entity));
            }
        } else {
            renderFont3D(renderables, program, camera, alphas[ComponentType.Labels.ordinal()]);
        }
        batch.end();
    }

    private void renderFont3D(Array<IRenderable> renderables, ExtShaderProgram program, ICamera camera, float alpha) {
        renderables.forEach(r -> {
            Render render = (Render) r;
            var entity = render.entity;
            view.setEntity(entity);

            var body = Mapper.body.get(entity);

            // Label color
            program.setUniform4fv("u_color", body.labelColor, 0, 4);
            // Component alpha
            program.setUniformf("u_componentAlpha", getAlpha(entity) * (view.isLabel() ? alpha : 1));
            // Font opacity multiplier, take into account element opacity
            program.setUniformf("u_opacity", 0.75f * view.getTextOpacity());
            // z-far and k
            addDepthBufferUniforms(program, camera);

            view.render(batch, program, this, rc, camera);
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
