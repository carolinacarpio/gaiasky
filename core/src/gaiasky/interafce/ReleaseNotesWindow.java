package gaiasky.interafce;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import gaiasky.desktop.util.SysUtils;
import gaiasky.util.I18n;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import gaiasky.util.scene2d.OwnLabel;
import gaiasky.util.scene2d.OwnScrollPane;
import gaiasky.util.scene2d.OwnTextArea;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReleaseNotesWindow extends GenericDialog {
    private static final Log logger = Logger.getLogger(ReleaseNotesWindow.class);

    private final Path releaseNotesFile;

    public ReleaseNotesWindow(Stage stage, Skin skin, Path file) {
        super(I18n.txt("gui.releasenotes.title"), skin, stage);

        this.releaseNotesFile = file;
        this.setResizable(true);
        setAcceptText(I18n.txt("gui.ok"));

        // Build
        buildSuper();
    }

    @Override
    protected void build() {

        int taWidth = 1400;
        int taHeight = 900;
        int lines = 25;

        try {
            String releaseNotes = Files.readString(releaseNotesFile);

            OwnLabel title = new OwnLabel(Settings.getApplicationTitle(false) + "   " + Settings.settings.version.version, skin, "header");
            content.add(title).left().pad(pad10).padBottom(pad20).row();

            OwnTextArea releaseNotesText = new OwnTextArea(releaseNotes, skin, "monospace-txt");
            releaseNotesText.setDisabled(true);
            releaseNotesText.setPrefRows(lines);
            releaseNotesText.clearListeners();

            OwnScrollPane scroll = new OwnScrollPane(releaseNotesText, skin, "default-nobg");
            scroll.setWidth(taWidth);
            scroll.setHeight(taHeight);
            scroll.setForceScroll(false, true);
            scroll.setSmoothScrolling(true);
            scroll.setFadeScrollBars(false);
            content.add(scroll).center().pad(pad10);
        } catch (IOException e) {
            // Show error
            OwnLabel error = new OwnLabel(I18n.txt("error.file.read", releaseNotesFile.toString()), skin);
            content.add(error).center();
        }
    }

    @Override
    protected void accept() {
        // Write current version to $WORKDIR/.releasenotes.rev
        Path releaseNotesRev = SysUtils.getReleaseNotesRevisionFile();
        try {
            if (Files.exists(releaseNotesRev))
                Files.delete(releaseNotesRev);
            Files.writeString(releaseNotesRev, Integer.toString(Settings.settings.version.versionNumber));
        } catch (IOException e) {
            logger.error(e);
        }
    }

    @Override
    protected void cancel() {

    }

    @Override
    public void dispose() {

    }
}
