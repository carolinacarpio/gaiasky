/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import gaiasky.GaiaSky;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.gui.vr.FixedScreenViewport;
import gaiasky.util.LoadingTextGenerator;
import gaiasky.util.Settings;
import gaiasky.util.TipsGenerator;
import gaiasky.util.gdx.loader.OwnTextureLoader;
import gaiasky.util.i18n.I18n;
import gaiasky.util.math.StdRandom;
import gaiasky.util.scene2d.OwnLabel;
import gaiasky.util.scene2d.OwnTextIconButton;
import gaiasky.util.scene2d.OwnTextTooltip;

public class LoadingGui extends AbstractGui {
    private static final long tipTime = 3500;
    public NotificationsInterface notificationsInterface;
    protected Table center, topLeft, bottomMiddle, screenMode;
    private TipsGenerator tipGenerator;
    private LoadingTextGenerator loadingTextGenerator;
    private OwnLabel spin;
    private HorizontalGroup tip;
    private long lastFunnyTime;
    private long lastTipTime;
    private long funnyTextTime = 1400;

    public LoadingGui(final Skin skin, final Graphics graphics, final Float unitsPerPixel, final Boolean vr) {
        super(graphics, unitsPerPixel);
        this.vr = vr;
        this.skin = skin;
    }

    @Override
    public void initialize(AssetManager assetManager, SpriteBatch sb) {
        interfaces = new Array<>();
        float pad30 = 48f;
        float pad10 = 16f;
        final Settings settings = Settings.settings;
        // User interface
        Viewport vp;
        if (vr) {
            vp = new FixedScreenViewport(getBackBufferWidth(), getBackBufferHeight());
        } else {
            vp = new ScreenViewport();
            ((ScreenViewport) vp).setUnitsPerPixel(unitsPerPixel);
        }
        stage = new Stage(vp, sb);
        if (vr) {
            vp.update(settings.graphics.backBufferResolution[0], settings.graphics.backBufferResolution[1], true);
        } else {
            vp.update(GaiaSky.instance.graphics.getWidth(), GaiaSky.instance.graphics.getHeight(), true);
        }

        center = new Table(skin);
        if (!vr) {
            Texture tex = new Texture(OwnTextureLoader.Factory.loadFromFile(Gdx.files.internal("img/splash/splash.jpg"), false));
            Drawable bg = new SpriteDrawable(new Sprite(tex));
            center.setBackground(bg);
        }
        center.setFillParent(true);
        center.center();

        // Logo.
        FileHandle gsIcon = Gdx.files.internal("icon/gs_icon.png");
        Texture iconTex = new Texture(gsIcon);
        iconTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        Image logo = new Image(iconTex);
        logo.setOrigin(Align.center);

        // Title.
        HorizontalGroup titleGroup = new HorizontalGroup();
        titleGroup.space(pad30 * 2f);
        OwnLabel gaiaSky = new OwnLabel(Settings.getApplicationTitle(settings.runtime.openXr), skin, "main-title");
        OwnLabel version = new OwnLabel(Settings.settings.version.version, skin, "main-title");
        version.setColor(skin.getColor("theme"));
        titleGroup.addActor(gaiaSky);
        titleGroup.addActor(version);

        // Funny text.
        loadingTextGenerator = new LoadingTextGenerator();
        lastFunnyTime = 0;
        spin = new OwnLabel("0", skin, "main-title-xs");
        spin.setColor(skin.getColor("theme"));

        center.add(logo).center().padBottom(pad10).row();
        center.add(titleGroup).center().padBottom(pad10 * 2f).row();
        center.add(spin).padBottom(pad30).row();

        if (vr) {
            bottomMiddle = new VersionLineTable(skin, true);
            bottomMiddle.center().bottom();
        } else {
            // Tips
            tipGenerator = new TipsGenerator(skin);
            tip = new HorizontalGroup();
            tip.space(pad10);
            tip.pad(10, 30, 10, 30);
            Container<HorizontalGroup> tipContainer = new Container<>(tip);
            tipContainer.setBackground(skin.getDrawable("table-bg"));
            bottomMiddle = new Table(skin);
            bottomMiddle.setFillParent(true);
            bottomMiddle.center().bottom();
            bottomMiddle.padLeft(pad30).padBottom(pad10);
            bottomMiddle.add(tipContainer);
        }

        // Version and build
        topLeft = new VersionLineTable(skin);

        // SCREEN MODE BUTTON - TOP RIGHT
        if (!vr) {
            screenMode = new Table(skin);
            screenMode.setFillParent(true);
            screenMode.top().right();
            screenMode.pad(pad10);
            OwnTextIconButton screenModeButton = new OwnTextIconButton("", skin, "screen-mode");
            screenModeButton.addListener(new OwnTextTooltip(I18n.msg("gui.fullscreen"), skin, 10));
            screenModeButton.addListener(event -> {
                if (event instanceof ChangeEvent) {
                    settings.graphics.fullScreen.active = !settings.graphics.fullScreen.active;
                    EventManager.publish(Event.SCREEN_MODE_CMD, screenModeButton);
                    return true;
                }
                return false;
            });
            screenMode.add(screenModeButton);
        }

        // MESSAGE INTERFACE - BOTTOM
        notificationsInterface = new NotificationsInterface(skin, lock, false, false, false);
        center.add(notificationsInterface);

        interfaces.add(notificationsInterface);

        rebuildGui();

    }

    @Override
    public void update(double dt) {
        super.update(dt);
        long currTime = System.currentTimeMillis();
        if (currTime - lastFunnyTime > funnyTextTime) {
            randomFunnyText();
            lastFunnyTime = currTime;
            funnyTextTime = StdRandom.uniform(1500, 3000);
        }
        if (!vr) {
            if (currTime - lastTipTime > tipTime) {
                tipGenerator.newTip(tip);
                lastTipTime = currTime;
            }
        }
    }

    private void randomFunnyText() {
        if (Settings.settings.runtime.openXr) {
            spin.setText(I18n.msg("gui.loading"));
        } else {
            try {
                spin.setText(loadingTextGenerator.next());
            } catch (Exception e) {
                spin.setText(I18n.msg("gui.loading"));
            }
        }
    }

    @Override
    public void doneLoading(AssetManager assetManager) {
    }

    public void rebuildGui() {
        if (stage != null) {
            stage.clear();
            stage.addActor(center);
            stage.addActor(bottomMiddle);
            if (!vr) {
                stage.addActor(screenMode);
                stage.addActor(topLeft);
            }
        }
    }

}
