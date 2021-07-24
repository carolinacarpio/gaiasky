/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interafce.beans;

import gaiasky.util.Logger;
import gaiasky.util.Settings;

import java.io.IOException;
import java.nio.file.Path;

public class MappingFileComboBoxBean extends FileComboBoxBean {
    public MappingFileComboBoxBean(Path file) {
        super(file);
        Path assets = Settings.assetsPath(".");
        try {
            String suffix = file.toRealPath().startsWith(assets.toRealPath()) ? " [internal]" : " [user]";
            this.name += suffix;
        } catch (IOException e) {
            Logger.getLogger(MappingFileComboBoxBean.class.getSimpleName()).error(e);
        }
    }
}
