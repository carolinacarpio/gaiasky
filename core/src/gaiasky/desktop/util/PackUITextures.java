/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.desktop.util;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class PackUITextures {
    public static void main(String[] args) {
        TexturePacker.Settings x1settings = new TexturePacker.Settings();
        x1settings.scale[0] = 1f;
        x1settings.jpegQuality = 0.9f;
        x1settings.paddingX = 4;
        x1settings.paddingY = 4;
        TexturePacker.Settings x2settings = new TexturePacker.Settings();
        x2settings.scale[0] = 1.6f;
        x2settings.jpegQuality = 0.9f;
        x2settings.paddingX = 4;
        x2settings.paddingY = 4;
        x2settings.filterMag = Texture.TextureFilter.Linear;
        x2settings.filterMin = Texture.TextureFilter.MipMapLinearLinear;

        // Use current path variable
        String gs = (new java.io.File("")).getAbsolutePath();

        // DARK-GREEN
        TexturePacker.process(x2settings, gs + "/assets/skins/raw/dark-green/", gs + "/assets/skins/dark-green/", "dark-green");

        // DARK-ORANGE
        TexturePacker.process(x2settings, gs + "/assets/skins/raw/dark-orange/", gs + "/assets/skins/dark-orange/", "dark-orange");

        // DARK-BLUE
        TexturePacker.process(x2settings, gs + "/assets/skins/raw/dark-blue/", gs + "/assets/skins/dark-blue/", "dark-blue");

        // NIGHT-RED
        TexturePacker.process(x2settings, gs + "/assets/skins/raw/night-red/", gs + "/assets/skins/night-red/", "night-red");
    }
}
