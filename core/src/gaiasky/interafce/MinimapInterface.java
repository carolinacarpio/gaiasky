/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interafce;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import gaiasky.util.I18n;
import gaiasky.util.scene2d.OwnLabel;

public class MinimapInterface extends TableGuiInterface {
    private final MinimapWidget minimap;
    private final OwnLabel mapName;

    public MinimapInterface(final Skin skin, final ShaderProgram shapeShader, final ShaderProgram spriteShader) {
        super(skin);
        float pad = 5f;
        minimap = new MinimapWidget(skin, shapeShader, spriteShader);

        Table side = new Table(skin);
        side.setBackground("table-bg");
        side.add(minimap.getSideProjection());
        Table top = new Table(skin);
        top.setBackground("table-bg");
        top.add(minimap.getTopProjection());

        mapName = new OwnLabel("", skin, "header");
        OwnLabel sideLabel = new OwnLabel(I18n.txt("gui.minimap.vert.side"), skin, "header");
        Table sideLabelTable = new Table(skin);
        sideLabelTable.setBackground("table-bg");
        sideLabelTable.add(sideLabel).pad(pad);
        OwnLabel topLabel = new OwnLabel(I18n.txt("gui.minimap.vert.top"), skin, "header");
        Table topLabelTable = new Table(skin);
        topLabelTable.setBackground("table-bg");
        topLabelTable.add(topLabel).pad(pad);

        add(mapName).right().colspan(2).padBottom(pad * 2f).row();
        add(sideLabelTable).top().padBottom(pad);
        add(side).padBottom(pad).row();
        add(topLabelTable).top().padBottom(pad);
        add(top).padBottom(pad).row();

        pack();

    }

    private void updateMapName(String name) {
        if (this.mapName != null)
            mapName.setText(name);
    }

    public void update() {
        if (minimap != null) {
            minimap.update();
            String mapName = minimap.getCurrentName();
            if (mapName != null && !mapName.equals(this.mapName.getName())) {
                updateMapName(mapName);
            }
        }
    }

    @Override
    public void dispose() {
        minimap.dispose();
    }
}
