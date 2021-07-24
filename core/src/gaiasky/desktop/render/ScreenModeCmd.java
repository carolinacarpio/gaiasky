/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.desktop.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Graphics.Monitor;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.event.IObserver;
import gaiasky.util.I18n;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;

/**
 * Manages screen mode changes (fullscreen, windowed)
 */
public class ScreenModeCmd implements IObserver {
    private static final Log logger = Logger.getLogger(ScreenModeCmd.class);
    
    public static ScreenModeCmd instance;

    public static void initialize() {
        ScreenModeCmd.instance = new ScreenModeCmd();
    }

    private ScreenModeCmd() {
        EventManager.instance.subscribe(this, Events.SCREEN_MODE_CMD);
    }

    @Override
    public void notify(final Events event, final Object... data) {
        if (event == Events.SCREEN_MODE_CMD) {
            boolean toFullScreen = Settings.settings.graphics.fullScreen.active;
            if (toFullScreen) {
                // TODO hack
                Monitor m = Gdx.graphics.getPrimaryMonitor();
                // Available modes for this monitor
                DisplayMode[] modes = Gdx.graphics.getDisplayModes(m);
                // Find best mode
                DisplayMode myMode = null;
                for (DisplayMode mode : modes) {
                    if (mode.height == Settings.settings.graphics.fullScreen.resolution[1] && mode.width == Settings.settings.graphics.fullScreen.resolution[0]) {
                        myMode = mode;
                        break;
                    }
                }
                // If no mode found, get default
                if (myMode == null) {
                    myMode = Gdx.graphics.getDisplayMode(m);
                    Settings.settings.graphics.fullScreen.resolution[0] = myMode.width;
                    Settings.settings.graphics.fullScreen.resolution[1] = myMode.height;
                }

                // set the window to full screen mode
                boolean good = Gdx.graphics.setFullscreenMode(myMode);
                if (!good) {
                    logger.error(I18n.txt("notif.error", I18n.txt("gui.fullscreen")));
                }

            } else {
                int width = Settings.settings.graphics.resolution[0];
                int height = Settings.settings.graphics.resolution[1];

                boolean good = Gdx.graphics.setWindowedMode(width, height);
                if (!good) {
                    logger.error(I18n.txt("notif.error", I18n.txt("gui.windowed")));
                }

            }
            Gdx.graphics.setVSync(Settings.settings.graphics.vsync);
        }
    }
}
