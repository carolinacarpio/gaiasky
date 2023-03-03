package gaiasky.gui.vr;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import gaiasky.gui.AbstractGui;
import gaiasky.gui.VersionLineTable;
import gaiasky.util.Settings;
import gaiasky.util.i18n.I18n;
import gaiasky.util.scene2d.OwnLabel;

public class WelcomeGuiVR extends AbstractGui {

    private Table center, bottom;

    public WelcomeGuiVR(final Skin skin, final Graphics graphics, final Float unitsPerPixel, final Boolean vr) {
        super(graphics, unitsPerPixel);
        this.skin = skin;
    }

    @Override
    public void initialize(AssetManager assetManager, SpriteBatch sb) {
        FixedScreenViewport vp = new FixedScreenViewport(getBackBufferWidth(), getBackBufferHeight());
        stage = new Stage(vp, sb);

        center = new Table();
        center.setFillParent(true);
        center.center();

        String textStyle = "header-raw";

        // Bottom info.
        bottom = new VersionLineTable(skin, true);
        bottom.bottom();

        // Logo.
        Image logo = new Image(new Texture(Gdx.files.internal("icon/gs_128.png")));
        center.add(logo).padBottom(20f).row();
        // Title.
        HorizontalGroup titleGroup = new HorizontalGroup();
        titleGroup.space(64f);
        OwnLabel gaiaSky = new OwnLabel(Settings.getApplicationTitle(Settings.settings.runtime.openVr), skin, "main-title");
        OwnLabel version = new OwnLabel(Settings.settings.version.version, skin, "main-title");
        version.setColor(skin.getColor("theme"));
        titleGroup.addActor(gaiaSky);
        titleGroup.addActor(version);
        center.add(titleGroup).padBottom(110f).row();

        // Check window!
        var w1 = new OwnLabel(I18n.msg("gui.vr.welcome.1"), skin, textStyle);
        w1.setAlignment(Align.center);
        var w2 = new OwnLabel(I18n.msg("gui.vr.welcome.2"), skin, textStyle);
        w2.setAlignment(Align.center);
        var w3 = new OwnLabel(I18n.msg("gui.vr.welcome.3"), skin, "header-blue");
        w3.setAlignment(Align.center);
        center.add(w1).padBottom(40f).row();
        center.add(w2).row();
        center.add(w3);

        rebuildGui();
    }

    @Override
    public void doneLoading(AssetManager assetManager) {

    }

    @Override
    protected void rebuildGui() {
        if (stage != null) {
            stage.clear();
            stage.addActor(center);
            stage.addActor(bottom);
        }
    }
}
