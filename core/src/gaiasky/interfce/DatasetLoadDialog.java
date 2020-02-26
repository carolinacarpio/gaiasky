/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interfce;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import gaiasky.data.group.DatasetOptions;
import gaiasky.render.ComponentTypes.ComponentType;
import gaiasky.util.GlobalConf;
import gaiasky.util.GlobalResources;
import gaiasky.util.GuiUtils;
import gaiasky.util.I18n;
import gaiasky.util.scene2d.*;
import gaiasky.util.validator.FloatValidator;
import gaiasky.util.validator.IValidator;
import gaiasky.util.validator.TextFieldComparatorValidator;

public class DatasetLoadDialog extends GenericDialog {

    public OwnCheckBox particles, stars, fadeIn, fadeOut;
    public OwnTextField colorNoise, particleSize, profileDecay, magnitudeScale, fadeInMin, fadeInMax, fadeOutMin, fadeOutMax;
    public ColorPicker particleColor, labelColor;
    public OwnSelectBox<ComponentType> componentType;

    private float pad5, pad10, pad15;

    public DatasetLoadDialog(String title, Skin skin, Stage ui) {
        super(title, skin, ui);

        pad5 = 5f * GlobalConf.UI_SCALE_FACTOR;
        pad10 = 10f * GlobalConf.UI_SCALE_FACTOR;
        pad15 = 15f * GlobalConf.UI_SCALE_FACTOR;

        setAcceptText(I18n.txt("gui.ok"));
        setCancelText(I18n.txt("gui.cancel"));

        buildSuper();
    }

    public DatasetLoadDialog(Skin skin, Stage ui) {
        this(I18n.txt("gui.dsload.title"), skin, ui);
    }

    @Override
    protected void build() {
        content.clear();

        OwnLabel info = new OwnLabel(I18n.txt("gui.dsload.info"), skin, "hud-subheader");
        content.add(info).left().padBottom(pad15).row();

        // Table containing the actual widget
        Table container = new Table(skin);
        Container<Table> cont = new Container<>(container);

        // Radio buttons
        stars = new OwnCheckBox(I18n.txt("gui.dsload.stars"), skin, "radio", pad5);
        stars.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (stars.isChecked()) {
                    container.clear();
                    addStarsWidget(container);
                    pack();
                }
                return true;
            }
            return false;
        });
        stars.setChecked(true);
        content.add(stars).left().padBottom(pad10).row();

        particles = new OwnCheckBox(I18n.txt("gui.dsload.particles"), skin, "radio", pad5);
        particles.addListener(event -> {
            if (event instanceof ChangeEvent) {
                if (particles.isChecked()) {
                    container.clear();
                    addParticlesWidget(container);
                    pack();
                }
                return true;
            }
            return false;
        });
        particles.setChecked(false);
        content.add(particles).left().padBottom(pad10 * 2f).row();

        new ButtonGroup<>(particles, stars);

        content.add(cont).left();
    }

    private void addStarsWidget(Table container) {
        float cpsize = 20f * GlobalConf.UI_SCALE_FACTOR;

        OwnLabel starProps = new OwnLabel(I18n.txt("gui.dsload.stars.properties"), skin, "hud-subheader");
        container.add(starProps).colspan(2).left().padTop(pad15).padBottom(pad10).row();

        // Magnitude multiplier
        FloatValidator sclValidator = new FloatValidator(0.1f, 100f);
        magnitudeScale = new OwnTextField("1.0", skin, sclValidator);
        container.add(new OwnLabel(I18n.txt("gui.dsload.magnitude.scale"), skin)).left().padRight(pad10).padBottom(pad10);
        container.add(GuiUtils.tooltipHg(magnitudeScale, "gui.dsload.magnitude.scale.tooltip", skin)).left().padBottom(pad10).row();

        // Label color
        labelColor = new ColorPicker(new float[] { 0.8f, 0.7f, 0.2f, 1f }, stage, skin);
        container.add(new OwnLabel(I18n.txt("gui.dsload.color.label"), skin)).left().padRight(pad10).padBottom(pad5);
        Table lc = new Table(skin);
        lc.add(labelColor).size(cpsize);
        container.add(GuiUtils.tooltipHg(lc, "gui.dsload.color.label.tooltip", skin)).left().padBottom(pad5).row();

        // Fade
        addFadeAttributes(container);
    }

    private void addParticlesWidget(Table container) {
        float cpsize = 20f * GlobalConf.UI_SCALE_FACTOR;

        OwnLabel particleProps = new OwnLabel(I18n.txt("gui.dsload.particles.properties"), skin, "hud-subheader");
        container.add(particleProps).colspan(2).left().padTop(pad15).padBottom(pad10).row();

        // Color noise
        FloatValidator decayValidator = new FloatValidator(1e-3f, 50f);
        profileDecay = new OwnTextField("1.0", skin, decayValidator);
        container.add(new OwnLabel(I18n.txt("gui.dsload.profiledecay"), skin)).left().padRight(pad10).padBottom(pad10);
        container.add(GuiUtils.tooltipHg(profileDecay, "gui.dsload.profiledecay.tooltip", skin)).left().padBottom(pad10).row();

        // Particle color
        particleColor = new ColorPicker(new float[] { 0.5f, 0f, 1f, 1f }, stage, skin);
        container.add(new OwnLabel(I18n.txt("gui.dsload.color"), skin)).left().padRight(pad10).padBottom(pad5);
        container.add(particleColor).size(cpsize).left().padBottom(pad5).row();

        // Color noise
        FloatValidator zeroOneValidator = new FloatValidator(0f, 1f);
        colorNoise = new OwnTextField("0.0", skin, zeroOneValidator);
        container.add(new OwnLabel(I18n.txt("gui.dsload.color.noise"), skin)).left().padRight(pad10).padBottom(pad10);
        container.add(GuiUtils.tooltipHg(colorNoise, "gui.dsload.color.noise.tooltip", skin)).left().padBottom(pad10).row();

        // Label color
        labelColor = new ColorPicker(new float[] { 0.8f, 0.7f, 0.2f, 1f }, stage, skin);
        container.add(new OwnLabel(I18n.txt("gui.dsload.color.label"), skin)).left().padRight(pad10).padBottom(pad5);
        Table lc = new Table(skin);
        lc.add(labelColor).size(cpsize);
        container.add(GuiUtils.tooltipHg(lc, "gui.dsload.color.label.tooltip", skin)).left().padBottom(pad5).row();

        // Particle size
        FloatValidator sizeValidator = new FloatValidator(0.5f, 50f);
        particleSize = new OwnTextField("3.0", skin, sizeValidator);
        container.add(new OwnLabel(I18n.txt("gui.dsload.size"), skin)).left().padRight(pad10).padBottom(pad5);
        container.add(particleSize).left().padBottom(pad5).row();

        // Component type
        ComponentType[] componentTypes = new ComponentType[] { ComponentType.Others, ComponentType.Galaxies, ComponentType.Stars };
        componentType = new OwnSelectBox(skin);
        componentType.setItems(componentTypes);
        container.add(new OwnLabel(I18n.txt("gui.dsload.ct"), skin)).left().padRight(pad10).padBottom(pad5);
        container.add(componentType).left().padBottom(pad5).row();

        // Fade
        addFadeAttributes(container);
    }

    private void addFadeAttributes(Table container) {
        final float tawidth = 500 * GlobalConf.UI_SCALE_FACTOR;

        OwnLabel fadeLabel = new OwnLabel(I18n.txt("gui.dsload.fade"), skin, "hud-subheader");
        container.add(fadeLabel).colspan(2).left().padTop(pad15).padBottom(pad10).row();

        // Info
        String ssInfoStr = I18n.txt("gui.dsload.fade.info") + '\n';
        int ssLines = GlobalResources.countOccurrences(ssInfoStr, '\n');
        TextArea fadeInfo = new OwnTextArea(ssInfoStr, skin, "info");
        fadeInfo.setDisabled(true);
        fadeInfo.setPrefRows(ssLines + 1);
        fadeInfo.setWidth(tawidth);
        fadeInfo.clearListeners();

        container.add(fadeInfo).colspan(2).left().padTop(pad5).padBottom(pad10).row();

        // Fade in
        fadeIn = new OwnCheckBox(I18n.txt("gui.dsload.fade.in"), skin, pad5);
        container.add(fadeIn).left().padRight(pad10).padBottom(pad5);

        HorizontalGroup fadeInGroup = new HorizontalGroup();
        fadeInGroup.space(pad5);
        fadeInMin = new OwnTextField("0", skin);
        fadeInMax = new OwnTextField("10", skin);
        fadeInGroup.addActor(new OwnLabel("[", skin));
        fadeInGroup.addActor(fadeInMin);
        fadeInGroup.addActor(new OwnLabel(", ", skin));
        fadeInGroup.addActor(fadeInMax);
        fadeInGroup.addActor(new OwnLabel("] pc", skin));
        fadeIn.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                boolean disable = !fadeIn.isChecked();

                for (Actor child : fadeInGroup.getChildren()) {
                    if (child instanceof OwnLabel) {
                        ((OwnLabel) child).setDisabled(disable);
                    } else if (child instanceof OwnTextField) {
                        ((OwnTextField) child).setDisabled(disable);
                    }
                }
                return true;
            }
            return false;
        });
        fadeIn.setChecked(true);
        fadeIn.setProgrammaticChangeEvents(true);
        fadeIn.setChecked(false);

        container.add(fadeInGroup).left().padBottom(pad5).row();

        // Fade out
        fadeOut = new OwnCheckBox(I18n.txt("gui.dsload.fade.out"), skin, pad5);
        container.add(fadeOut).left().padRight(pad10).padBottom(pad5);

        HorizontalGroup fadeOutGroup = new HorizontalGroup();
        fadeOutGroup.space(pad5);
        fadeOutMin = new OwnTextField("3000", skin);
        fadeOutMax = new OwnTextField("6000", skin);
        fadeOutGroup.addActor(new OwnLabel("[", skin));
        fadeOutGroup.addActor(fadeOutMin);
        fadeOutGroup.addActor(new OwnLabel(", ", skin));
        fadeOutGroup.addActor(fadeOutMax);
        fadeOutGroup.addActor(new OwnLabel("] pc", skin));
        fadeOut.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                boolean disable = !fadeOut.isChecked();

                for (Actor child : fadeOutGroup.getChildren()) {
                    if (child instanceof OwnLabel) {
                        ((OwnLabel) child).setDisabled(disable);
                    } else if (child instanceof OwnTextField) {
                        ((OwnTextField) child).setDisabled(disable);
                    }
                }
                return true;
            }
            return false;
        });
        fadeOut.setChecked(true);
        fadeOut.setProgrammaticChangeEvents(true);
        fadeOut.setChecked(false);

        // Validators
        FloatValidator fadeVal = new FloatValidator(0f, 1e10f);
        IValidator fadeInMinVal = new TextFieldComparatorValidator(fadeVal, new OwnTextField[] { fadeInMax, fadeOutMin, fadeOutMax }, null);
        IValidator fadeInMaxVal = new TextFieldComparatorValidator(fadeVal, new OwnTextField[] { fadeOutMin, fadeOutMax }, new OwnTextField[] { fadeInMin });
        IValidator fadeOutMinVal = new TextFieldComparatorValidator(fadeVal, new OwnTextField[] { fadeOutMax }, new OwnTextField[] { fadeInMin, fadeInMax });
        IValidator fadeOutMaxVal = new TextFieldComparatorValidator(fadeVal, null, new OwnTextField[] { fadeInMin, fadeInMax, fadeOutMin });

        // Set them
        fadeInMin.setValidator(fadeInMinVal);
        fadeInMax.setValidator(fadeInMaxVal);
        fadeOutMin.setValidator(fadeOutMinVal);
        fadeOutMax.setValidator(fadeOutMaxVal);

        container.add(fadeOutGroup).left().padBottom(pad5).row();
    }

    public DatasetOptions generateDatasetOptions() {
        DatasetOptions dops = new DatasetOptions();

        if (stars.isChecked()) {
            dops.type = DatasetOptions.DatasetLoadType.STARS;
            dops.magnitudeScale = magnitudeScale.getDoubleValue(1);
        } else {
            dops.type = DatasetOptions.DatasetLoadType.PARTICLES;
            dops.ct = componentType.getSelected();
            dops.profileDecay = profileDecay.getDoubleValue(1);
            dops.particleColor = particleColor.getPickedColorDouble();
            dops.particleColorNoise = colorNoise.getDoubleValue(0);
            dops.particleSize = particleSize.getDoubleValue(3);
        }
        dops.labelColor = labelColor.getPickedColorDouble();
        addFadeInfo(dops);

        return dops;
    }

    private void addFadeInfo(DatasetOptions dops) {
        if (fadeIn.isChecked()) {
            dops.fadeIn = new double[] { fadeInMin.getDoubleValue(0f), fadeInMax.getDoubleValue(10f) };
        }
        if (fadeOut.isChecked()) {
            dops.fadeOut = new double[] { fadeInMin.getDoubleValue(0f), fadeInMax.getDoubleValue(10f) };
        }
    }

    @Override
    protected void accept() {

    }

    @Override
    protected void cancel() {

    }
}
