/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.scene2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor.SystemCursor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputEvent.Type;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/**
 * ImageButton in which the cursor changes when the mouse rolls over.
 */
public class OwnImageButton extends ImageButton {
    OwnImageButton me;
    SystemCursor cursor;

    public OwnImageButton(Skin skin) {
        super(skin);
        this.me = this;
        initialize();
    }

    public OwnImageButton(Skin skin, String styleName) {
        super(skin, styleName);
        this.me = this;
        initialize();
    }

    public OwnImageButton(ImageButtonStyle style) {
        super(style);
        this.me = this;
        initialize();
    }

    public void setCheckedNoFire(boolean isChecked) {
        this.setProgrammaticChangeEvents(false);
        this.setChecked(isChecked);
        this.setProgrammaticChangeEvents(true);
    }

    private void initialize() {
        cursor = SystemCursor.Hand;
        this.addListener(event -> {
            if (event instanceof InputEvent) {
                Type type = ((InputEvent) event).getType();
                if (type == Type.enter) {
                    if (!me.isDisabled())
                        Gdx.graphics.setSystemCursor(cursor);
                    return true;
                } else if (type == Type.exit) {
                    Gdx.graphics.setSystemCursor(SystemCursor.Arrow);
                    return true;
                }
            }
            return false;
        });
    }

    protected void updateImage() {
        getImage().setDrawable(getImageDrawable());
        Color theme;
        try {
            theme = getSkin().getColor("highlight");
        } catch (Exception e) {
            theme = null;
        }

        if (isOver()) {
            if (theme != null)
                getImage().setColor(theme);
            else
                getImage().setColor(1, 1, 1, 1);
        } else {
            getImage().setColor(1, 1, 1, 1);
        }
    }

    public void draw(Batch batch, float parentAlpha) {
        updateImage();
        super.draw(batch, parentAlpha);
    }
}
