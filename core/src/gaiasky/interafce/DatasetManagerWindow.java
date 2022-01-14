/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.interafce;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.InputEvent.Type;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import gaiasky.GaiaSky;
import gaiasky.desktop.GaiaSkyDesktop;
import gaiasky.desktop.util.SysUtils;
import gaiasky.event.EventManager;
import gaiasky.event.Events;
import gaiasky.util.*;
import gaiasky.util.Logger.Log;
import gaiasky.util.color.ColorUtils;
import gaiasky.util.datadesc.DataDescriptor;
import gaiasky.util.datadesc.DataDescriptorUtils;
import gaiasky.util.datadesc.DatasetDesc;
import gaiasky.util.datadesc.DatasetType;
import gaiasky.util.format.INumberFormat;
import gaiasky.util.format.NumberFormatFactory;
import gaiasky.util.io.FileInfoInputStream;
import gaiasky.util.scene2d.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dataset manager. It gets a descriptor file from the server containing all
 * available datasets, detects them in the current system and offers and manages
 * their downloads.
 */
public class DatasetManagerWindow extends GenericDialog {
    private static final Log logger = Logger.getLogger(DatasetManagerWindow.class);

    private static final Map<String, String> iconMap;

    static {
        iconMap = new HashMap<>();
        iconMap.put("other", "icon-elem-others");
        iconMap.put("data-pack", "icon-elem-others");
        iconMap.put("catalog-lod", "icon-elem-stars");
        iconMap.put("catalog-gaia", "icon-elem-stars");
        iconMap.put("catalog-star", "icon-elem-stars");
        iconMap.put("catalog-gal", "icon-elem-galaxies");
        iconMap.put("catalog-cluster", "icon-elem-clusters");
        iconMap.put("catalog-other", "icon-elem-others");
        iconMap.put("mesh", "icon-elem-meshes");
        iconMap.put("texture-pack", "icon-elem-moons");
    }

    public static int getTypeWeight(String type) {
        return switch (type) {
            case "catalog-lod" -> 0;
            case "catalog-gaia" -> 1;
            case "catalog-star" -> 2;
            case "catalog-gal" -> 3;
            case "catalog-cluster" -> 4;
            case "catalog-other" -> 5;
            case "mesh" -> 6;
            case "other" -> 8;
            default -> 10;
        };
    }

    public static String getIcon(String type) {
        if (type != null && iconMap.containsKey(type))
            return iconMap.get(type);
        return "icon-elem-others";
    }

    private DataDescriptor serverDd, localDd;
    private OwnScrollPane leftScroll;
    private DatasetDesc[] selectedDataset;
    private float[][] scroll;
    private static int selectedTab = 0;

    private Map<String, Button>[] buttonMap;

    private final Color highlight;

    // Whether to show the data location chooser
    private final boolean dataLocation;

    private final INumberFormat nf;

    private final Set<DatasetWatcher> watchers;
    private DatasetWatcher rightPaneWatcher;

    private AtomicBoolean initialized;

    public DatasetManagerWindow(Stage stage, Skin skin, DataDescriptor dd) {
        this(stage, skin, dd, true, I18n.txt("gui.close"));
    }

    public DatasetManagerWindow(Stage stage, Skin skin, DataDescriptor dd, boolean dataLocation, String acceptText) {
        super(I18n.txt("gui.download.title") + (dd != null && dd.updatesAvailable ? " - " + I18n.txt("gui.download.updates", dd.numUpdates) : ""), skin, stage);
        this.nf = NumberFormatFactory.getFormatter("##0.0");
        this.serverDd = dd;
        this.highlight = ColorUtils.gYellowC;
        this.watchers = new HashSet<>();
        this.scroll = new float[][] { { 0f, 0f }, { 0f, 0f } };
        this.selectedDataset = new DatasetDesc[2];
        this.initialized = new AtomicBoolean(false);
        this.buttonMap = new HashMap[2];
        this.buttonMap[0] = new HashMap<>();
        this.buttonMap[1] = new HashMap<>();

        this.dataLocation = dataLocation;

        setAcceptText(acceptText);

        // Build
        buildSuper();
    }

    @Override
    protected void build() {
        initialized.set(false);
        me.acceptButton.setDisabled(false);
        float tabWidth = 500f;
        float width = 1800f;

        try {
            Files.createDirectories(SysUtils.getLocalDataDir());
        } catch (FileAlreadyExistsException e) {
            // Good
        } catch (IOException e) {
            logger.error(e);
            return;
        }

        // Tabs
        HorizontalGroup tabGroup = new HorizontalGroup();
        tabGroup.align(Align.center);

        String updateCount = "";
        if (serverDd != null && serverDd.updatesAvailable) {
            updateCount = " (" + serverDd.numUpdates + ")";
        }

        final OwnTextButton tabAvail = new OwnTextButton(I18n.txt("gui.download.tab.available"), skin, "toggle-big");
        tabAvail.pad(pad5);
        tabAvail.setWidth(tabWidth);
        final OwnTextButton tabInstalled = new OwnTextButton(I18n.txt("gui.download.tab.installed") + updateCount, skin, "toggle-big");
        tabInstalled.pad(pad5);
        tabInstalled.setWidth(tabWidth);

        tabGroup.addActor(tabAvail);
        tabGroup.addActor(tabInstalled);

        content.add(tabGroup).center().expandX().row();

        // Create the tab content. Just using images here for simplicity.
        Stack tabContent = new Stack();

        // Content
        final Table contentAvail = new Table(skin);
        contentAvail.align(Align.top);
        contentAvail.pad(pad10);

        final Table contentInstalled = new Table(skin);
        contentInstalled.align(Align.top);
        contentInstalled.pad(pad10);

        /* ADD ALL CONTENT */
        tabContent.addActor(contentAvail);
        tabContent.addActor(contentInstalled);

        content.add(tabContent).expand().fill().padBottom(pad20).row();

        // Listen to changes in the tab button checked states
        // Set visibility of the tab content to match the checked state
        ChangeListener tabListener = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (tabAvail.isChecked()) {
                    if (initialized.get())
                        selectedTab = 0;
                    reloadAvailable(contentAvail, width);
                }
                if (tabInstalled.isChecked()) {
                    if (initialized.get())
                        selectedTab = 1;
                    reloadInstalled(contentInstalled, width);
                }
                contentAvail.setVisible(tabAvail.isChecked());
                contentInstalled.setVisible(tabInstalled.isChecked());

                content.pack();
                setKeyboardFocus();
            }
        };
        tabAvail.addListener(tabListener);
        tabInstalled.addListener(tabListener);

        // Let only one tab button be checked at a time
        ButtonGroup<Button> tabs = new ButtonGroup<>();
        tabs.setMinCheckCount(1);
        tabs.setMaxCheckCount(1);
        tabs.add(tabAvail);
        tabs.add(tabInstalled);

        // Check
        if (serverDd.updatesAvailable)
            selectedTab = 1;
        tabs.setChecked(selectedTab == 0 ? tabAvail.getText().toString() : tabInstalled.getText().toString());

        // Data location
        if (dataLocation)
            addDataLocation(content);

        initialized.set(true);
    }

    private void addDataLocation(Table content) {
        float buttonPad = 1.6f;
        String catLoc = Settings.settings.data.location;

        Table dataLocTable = new Table(skin);

        OwnLabel catalogsLocLabel = new OwnLabel(I18n.txt("gui.download.location"), skin);
        OwnImageButton catalogsLocTooltip = new OwnImageButton(skin, "tooltip");
        catalogsLocTooltip.addListener(new OwnTextTooltip(I18n.txt("gui.download.location.info"), skin));
        HorizontalGroup catalogsLocGroup = new HorizontalGroup();
        catalogsLocGroup.space(pad10);
        catalogsLocGroup.addActor(catalogsLocLabel);
        catalogsLocGroup.addActor(catalogsLocTooltip);

        OwnTextButton dataLocationButton = new OwnTextButton(catLoc, skin);
        dataLocationButton.pad(buttonPad * 4f);
        dataLocationButton.setMinWidth(800f);

        dataLocTable.add(catalogsLocGroup).left().padBottom(pad10);
        dataLocTable.add(dataLocationButton).left().padLeft(pad5).padBottom(pad10).row();
        Cell<Actor> notice = dataLocTable.add((Actor) null).colspan(2).padBottom(pad10);
        notice.row();

        content.add(dataLocTable).center().row();

        dataLocationButton.addListener((event) -> {
            if (event instanceof ChangeEvent) {
                FileChooser fc = new FileChooser(I18n.txt("gui.download.pickloc"), skin, stage, Path.of(Settings.settings.data.location), FileChooser.FileChooserTarget.DIRECTORIES);
                fc.setShowHidden(Settings.settings.program.fileChooser.showHidden);
                fc.setShowHiddenConsumer((showHidden) -> Settings.settings.program.fileChooser.showHidden = showHidden);
                fc.setResultListener((success, result) -> {
                    if (success) {
                        if (Files.isReadable(result) && Files.isWritable(result)) {
                            // Set data location
                            dataLocationButton.setText(result.toAbsolutePath().toString());
                            // Change data location
                            Settings.settings.data.location = result.toAbsolutePath().toString().replaceAll("\\\\", "/");
                            // Create temp dir
                            SysUtils.mkdir(SysUtils.getTempDir(Settings.settings.data.location));
                            me.pack();
                            GaiaSky.postRunnable(() -> {
                                // Reset datasets
                                Settings.settings.data.catalogFiles.clear();
                                reloadAll();
                                GaiaSky.instance.getGlobalResources().reloadDataFiles();
                            });
                        } else {
                            Label warn = new OwnLabel(I18n.txt("gui.download.pickloc.permissions"), skin);
                            warn.setColor(ColorUtils.gRedC);
                            notice.setActor(warn);
                            return false;
                        }
                    }
                    notice.clearActor();
                    return true;
                });
                fc.show(stage);

                return true;
            }
            return false;
        });
    }

    private void reloadAvailable(Table content, float width) {
        reloadPane(content, width, serverDd, DatasetMode.AVAILABLE);
    }

    private void reloadInstalled(Table content, float width) {
        this.localDd = reloadLocalCatalogs(this.serverDd);
        reloadPane(content, width, localDd, DatasetMode.INSTALLED);
    }

    private enum DatasetMode {
        AVAILABLE,
        INSTALLED
    }

    private void reloadPane(Table content, float width, DataDescriptor dd, DatasetMode mode) {
        content.clear();
        if (dd == null || dd.datasets.isEmpty()) {
            if (mode == DatasetMode.AVAILABLE) {
                content.add(new OwnLabel(I18n.txt("gui.download.noconnection.title"), skin)).center().padTop(pad20 * 2f).padBottom(pad10).row();
            }
            content.add(new OwnLabel(I18n.txt("gui.dschooser.nodatasets"), skin)).center().padTop(mode == DatasetMode.AVAILABLE ? 0 : pad20 * 2f).row();
        } else {
            Cell left = content.add().top().left().padRight(pad20);
            Cell right = content.add().top().left();

            Table leftTable = new Table(skin);
            leftTable.align(Align.topRight);
            leftScroll = new OwnScrollPane(leftTable, skin, "minimalist-nobg");
            leftScroll.setScrollingDisabled(true, false);
            leftScroll.setForceScroll(false, true);
            leftScroll.setSmoothScrolling(false);
            leftScroll.setFadeScrollBars(false);
            leftScroll.addListener(event -> {
                if (event instanceof InputEvent) {
                    InputEvent ie = (InputEvent) event;
                    if (ie.getType() == Type.scrolled) {
                        // Save scroll position
                        scroll[mode.ordinal()][0] = leftScroll.getScrollX();
                        scroll[mode.ordinal()][1] = leftScroll.getScrollY();
                    }
                }
                return false;
            });

            // Current selected datasets
            java.util.List<String> currentSetting = Settings.settings.data.catalogFiles;

            for (DatasetType type : dd.types) {
                List<DatasetDesc> datasets = type.datasets;
                List<DatasetDesc> filtered = datasets.stream().filter(d -> mode == DatasetMode.AVAILABLE ? !d.exists : true).collect(Collectors.toList());
                if (!filtered.isEmpty()) {
                    OwnLabel dsType = new OwnLabel(I18n.txt("gui.download.type." + type.typeStr), skin, "hud-header");
                    leftTable.add(dsType).left().padTop(pad10).row();

                    for (DatasetDesc dataset : filtered) {
                        Table t = new Table(skin);
                        t.pad(pad10, pad10, 0, pad10);

                        String tooltipText = dataset.shortDescription;

                        // Type icon
                        Image typeImage = new OwnImage(skin.getDrawable(getIcon(dataset.type)));
                        float scl = 0.7f;
                        float iw = typeImage.getWidth();
                        float ih = typeImage.getHeight();
                        typeImage.setSize(iw * scl, ih * scl);
                        typeImage.addListener(new OwnTextTooltip(dataset.type, skin, 10));

                        // Title
                        String titleString = mode == DatasetMode.AVAILABLE ? dataset.shortDescription : dataset.name;
                        OwnLabel title = new OwnLabel(TextUtils.capString(titleString, 60), skin, "ui-23");
                        title.setWidth(width * 0.41f);
                        if (dataset.outdated) {
                            title.setColor(highlight);
                            tooltipText = I18n.txt("gui.download.version.new", Integer.toString(dataset.serverVersion), Integer.toString(dataset.myVersion));
                        }

                        // Install, update or enable/disable
                        Actor installOrSelect;
                        float installOrSelectSize = 40f;
                        if (mode == DatasetMode.AVAILABLE || dataset.outdated) {
                            OwnTextIconButton install = new OwnTextIconButton("", skin, "install");
                            install.setContentAlign(Align.center);
                            install.addListener(new OwnTextTooltip(I18n.txt(dataset.outdated ? "gui.download.update" : "gui.download.install"), skin));
                            install.addListener((event) -> {
                                if (event instanceof ChangeEvent) {
                                    if (dataset.outdated) {
                                        actionUpdateDataset(dataset);
                                    } else {
                                        actionDownloadDataset(dataset);
                                    }
                                }
                                return false;
                            });
                            install.setSize(installOrSelectSize, installOrSelectSize);
                            installOrSelect = install;
                        } else {
                            OwnCheckBox select = new OwnCheckBox("", skin, 0f);
                            installOrSelect = select;
                            if (dataset.baseData) {
                                select.setChecked(true);
                                select.setDisabled(true);
                            } else if (dataset.minGsVersion > GaiaSkyDesktop.SOURCE_VERSION) {
                                select.setChecked(false);
                                select.setDisabled(true);
                                title.setColor(ColorUtils.gRedC);
                                tooltipText = I18n.txt("gui.download.version.gs.mismatch", Integer.toString(GaiaSkyDesktop.SOURCE_VERSION), Integer.toString(dataset.minGsVersion));
                                select.getStyle().disabledFontColor = ColorUtils.gRedC;

                                // Remove from selected, if it is
                                String filePath = dataset.catalogFile.path();
                                if (Settings.settings.data.catalogFiles.contains(filePath)) {
                                    Settings.settings.data.catalogFiles.remove(filePath);
                                    logger.info(I18n.txt("gui.download.deselected.version", dataset.name, Integer.toString(dataset.minGsVersion), Integer.toString(GaiaSkyDesktop.SOURCE_VERSION)));
                                }
                            } else {
                                select.setChecked(TextUtils.contains(dataset.catalogFile.path(), currentSetting));
                                select.addListener(new OwnTextTooltip(dataset.path.toString(), skin));
                            }
                            select.setSize(installOrSelectSize, installOrSelectSize);
                            select.setHeight(40f);
                            select.addListener(new ChangeListener() {
                                @Override
                                public void changed(ChangeEvent event, Actor actor) {
                                    if (select.isChecked()) {
                                        actionEnableDataset(dataset);
                                    } else {
                                        actionDisableDataset(dataset);
                                    }
                                    updateDatasetInfoPane(right, dataset, mode);
                                }
                            });
                        }

                        // Size
                        OwnLabel size = new OwnLabel(dataset.size, skin, "grey-large");
                        size.addListener(new OwnTextTooltip(I18n.txt("gui.download.size.tooltip"), skin, 10));
                        size.setWidth(88f);

                        // Version
                        OwnLabel version = null;
                        if (mode == DatasetMode.AVAILABLE) {
                            version = new OwnLabel(I18n.txt("gui.download.version.server", dataset.serverVersion), skin, "grey-large");
                        } else if (mode == DatasetMode.INSTALLED) {
                            if (dataset.outdated) {
                                version = new OwnLabel(I18n.txt("gui.download.version.new", Integer.toString(dataset.serverVersion), Integer.toString(dataset.myVersion)), skin, "grey-large");
                                version.setColor(highlight);
                            } else {
                                version = new OwnLabel(I18n.txt("gui.download.version.local", dataset.myVersion), skin, "grey-large");
                            }
                        }
                        HorizontalGroup versionSize = new HorizontalGroup();
                        versionSize.space(pad20 * 2f);
                        versionSize.addActor(version);
                        versionSize.addActor(size);

                        // Progress
                        OwnProgressBar progress = new OwnProgressBar(0f, 100f, 0.1f, false, skin, "tiny-horizontal");
                        progress.setPrefWidth(850f);
                        progress.setValue(60f);
                        progress.setVisible(false);

                        t.add(typeImage).left().padRight(pad10);
                        t.add(title).left().padRight(pad10);
                        t.add(installOrSelect).right().row();
                        t.add();
                        t.add(versionSize).colspan(2).left().padRight(pad10).padBottom(pad5).row();
                        t.add(progress).colspan(3).expandX();
                        t.pack();
                        OwnButton button = new OwnButton(t, skin, "dataset", false);
                        button.setWidth(width * 0.52f);
                        title.addListener(new OwnTextTooltip(tooltipText, skin, 10));
                        buttonMap[mode.ordinal()].put(dataset.key, button);

                        // Clicks
                        button.addListener(new InputListener() {
                            @Override
                            public boolean handle(Event event) {
                                if (event != null && event instanceof InputEvent) {
                                    InputEvent ie = (InputEvent) event;
                                    InputEvent.Type type = ie.getType();
                                    if (type == InputEvent.Type.touchDown) {
                                        if (ie.getButton() == Input.Buttons.LEFT) {
                                            updateDatasetInfoPane(right, dataset, mode);
                                            selectedDataset[mode.ordinal()] = dataset;
                                        } else if (ie.getButton() == Input.Buttons.RIGHT) {
                                            GaiaSky.postRunnable(() -> {
                                                // Context menu
                                                ContextMenu datasetContext = new ContextMenu(skin, "default");
                                                if (mode == DatasetMode.INSTALLED) {
                                                    if (dataset.outdated) {
                                                        // Update
                                                        MenuItem update = new MenuItem(I18n.txt("gui.download.update"), skin, "default");
                                                        update.addListener(new ChangeListener() {
                                                            @Override
                                                            public void changed(ChangeEvent event, Actor actor) {
                                                                actionUpdateDataset(dataset);
                                                            }
                                                        });
                                                        datasetContext.addItem(update);
                                                    }
                                                    if (!dataset.baseData && dataset.minGsVersion <= GaiaSkyDesktop.SOURCE_VERSION) {
                                                        boolean enabled = TextUtils.contains(dataset.catalogFile.path(), currentSetting);
                                                        if (enabled) {
                                                            // Disable
                                                            MenuItem disable = new MenuItem(I18n.txt("gui.download.disable"), skin, "default");
                                                            disable.addListener(new ChangeListener() {
                                                                @Override
                                                                public void changed(ChangeEvent event, Actor actor) {
                                                                    actionDisableDataset(dataset);
                                                                    if (installOrSelect instanceof OwnCheckBox) {
                                                                        OwnCheckBox cb = (OwnCheckBox) installOrSelect;
                                                                        cb.setProgrammaticChangeEvents(false);
                                                                        cb.setChecked(false);
                                                                        cb.setProgrammaticChangeEvents(true);
                                                                    }
                                                                    updateDatasetInfoPane(right, dataset, mode);
                                                                }
                                                            });
                                                            datasetContext.addItem(disable);
                                                        } else {
                                                            // Enable
                                                            MenuItem enable = new MenuItem(I18n.txt("gui.download.enable"), skin, "default");
                                                            enable.addListener(new ChangeListener() {
                                                                @Override
                                                                public void changed(ChangeEvent event, Actor actor) {
                                                                    actionEnableDataset(dataset);
                                                                    if (installOrSelect instanceof OwnCheckBox) {
                                                                        OwnCheckBox cb = (OwnCheckBox) installOrSelect;
                                                                        cb.setProgrammaticChangeEvents(false);
                                                                        cb.setChecked(true);
                                                                        cb.setProgrammaticChangeEvents(true);
                                                                    }
                                                                    updateDatasetInfoPane(right, dataset, mode);
                                                                }
                                                            });
                                                            datasetContext.addItem(enable);
                                                        }
                                                        datasetContext.addSeparator();
                                                    }
                                                    // Delete
                                                    MenuItem delete = new MenuItem(I18n.txt("gui.download.delete"), skin, "default");
                                                    delete.addListener(new ClickListener() {
                                                        @Override
                                                        public void clicked(InputEvent event, float x, float y) {
                                                            actionDeleteDataset(dataset);
                                                            super.clicked(event, x, y);
                                                        }
                                                    });
                                                    datasetContext.addItem(delete);
                                                } else if (mode == DatasetMode.AVAILABLE) {
                                                    // Install
                                                    MenuItem install = new MenuItem(I18n.txt("gui.download.install"), skin, "default");
                                                    install.addListener(new ChangeListener() {
                                                        @Override
                                                        public void changed(ChangeEvent event, Actor actor) {
                                                            actionDownloadDataset(dataset);
                                                        }
                                                    });
                                                    datasetContext.addItem(install);
                                                }
                                                datasetContext.showMenu(stage, Gdx.input.getX(ie.getPointer()) / Settings.settings.program.ui.scale, stage.getHeight() - Gdx.input.getY(ie.getPointer()) / Settings.settings.program.ui.scale);
                                            });
                                        }
                                    }
                                }
                                return super.handle(event);
                            }
                        });

                        leftTable.add(button).left().row();

                        if (selectedDataset[mode.ordinal()] == null)
                            selectedDataset[mode.ordinal()] = dataset;

                        // Create watcher
                        watchers.add(new DatasetWatcher(dataset, progress, installOrSelect instanceof OwnTextIconButton ? (OwnTextIconButton) installOrSelect : null, null));
                    }
                }
            }
            leftScroll.setWidth(width * 0.52f);
            leftScroll.setHeight(Math.min(stage.getHeight() * 0.5f, 1500f));
            leftScroll.layout();
            leftScroll.setScrollX(scroll[mode.ordinal()][0]);
            leftScroll.setScrollY(scroll[mode.ordinal()][1]);
            left.setActor(leftScroll);

            updateDatasetInfoPane(right, selectedDataset[mode.ordinal()], mode);

        }
        me.pack();
    }

    private void updateDatasetInfoPane(Cell cell, DatasetDesc dataset, DatasetMode mode) {
        if (rightPaneWatcher != null) {
            rightPaneWatcher.dispose();
            watchers.remove(rightPaneWatcher);
            rightPaneWatcher = null;
        }
        cell.clearActor();
        Table t = new Table(skin);

        // Type icon
        Image typeImage = new OwnImage(skin.getDrawable(getIcon(dataset.type)));
        float scl = 0.7f;
        float iw = typeImage.getWidth();
        float ih = typeImage.getHeight();
        typeImage.setSize(iw * scl, ih * scl);
        typeImage.addListener(new OwnTextTooltip(dataset.type, skin, 10));

        // Title
        String titleString = mode == DatasetMode.AVAILABLE ? dataset.shortDescription : dataset.name;
        OwnLabel title = new OwnLabel(TextUtils.breakCharacters(titleString, 45), skin, "hud-header");
        title.addListener(new OwnTextTooltip(dataset.shortDescription, skin, 10));

        // Title group
        HorizontalGroup titleGroup = new HorizontalGroup();
        titleGroup.space(pad10);
        titleGroup.addActor(typeImage);
        titleGroup.addActor(title);

        // Status
        OwnLabel status = null;
        if (mode == DatasetMode.AVAILABLE) {
            status = new OwnLabel(I18n.txt("gui.download.available"), skin, "mono");
        } else if (mode == DatasetMode.INSTALLED) {
            if (dataset.baseData) {
                // Always enabled
                status = new OwnLabel(I18n.txt("gui.download.enabled"), skin, "mono");
            } else if (dataset.minGsVersion > GaiaSkyDesktop.SOURCE_VERSION) {
                // Notify version mismatch
                status = new OwnLabel(I18n.txt("gui.download.version.gs.mismatch.short", Integer.toString(GaiaSkyDesktop.SOURCE_VERSION), Integer.toString(dataset.minGsVersion)), skin, "mono");
                status.setColor(ColorUtils.gRedC);
            } else {
                // Notify status
                java.util.List<String> currentSetting = Settings.settings.data.catalogFiles;
                boolean enabled = TextUtils.contains(dataset.catalogFile.path(), currentSetting);
                status = new OwnLabel(I18n.txt(enabled ? "gui.download.enabled" : "gui.download.disabled"), skin, "mono");
            }
        }

        // Type
        OwnLabel type = new OwnLabel(I18n.txt("gui.download.type", dataset.type), skin, "grey-large");

        // Version
        OwnLabel version = null;
        if (mode == DatasetMode.AVAILABLE) {
            version = new OwnLabel(I18n.txt("gui.download.version.server", dataset.serverVersion), skin, "grey-large");
        } else if (mode == DatasetMode.INSTALLED) {
            if (dataset.outdated) {
                version = new OwnLabel(I18n.txt("gui.download.version.new", Integer.toString(dataset.serverVersion), Integer.toString(dataset.myVersion)), skin, "grey-large");
                version.setColor(highlight);
            } else {
                version = new OwnLabel(I18n.txt("gui.download.version.local", dataset.myVersion), skin, "grey-large");
            }
        }

        // Key
        OwnLabel key = new OwnLabel(I18n.txt("gui.download.name", dataset.name), skin, "grey-large");

        // Size
        OwnLabel size = new OwnLabel(I18n.txt("gui.download.size", dataset.size), skin, "grey-large");
        size.addListener(new

                OwnTextTooltip(I18n.txt("gui.download.size.tooltip"), skin, 10));

        // Num objects
        String nObjStr = dataset.nObjects > 0 ? I18n.txt("gui.dataset.nobjects", (int) dataset.nObjects) : I18n.txt("gui.dataset.nobjects.none");
        OwnLabel nObjects = new OwnLabel(nObjStr, skin, "grey-large");
        nObjects.addListener(new

                OwnTextTooltip(I18n.txt("gui.download.nobjects.tooltip") + ": " + dataset.nObjectsStr, skin, 10));

        // Link
        Link link = null;
        if (dataset.link != null && !dataset.link.isEmpty()) {
            String linkStr = dataset.link.replace("@mirror-url@", Settings.settings.program.url.dataMirror);
            link = new Link(linkStr, skin, dataset.link);
        }

        // Description
        String descriptionString = mode == DatasetMode.AVAILABLE ? dataset.description.substring(dataset.description.indexOf(" - ") + 3) : dataset.description;
        OwnLabel desc = new OwnLabel(TextUtils.breakCharacters(descriptionString, 80), skin);
        desc.setWidth(1000f);

        t.add(titleGroup).top().left().padBottom(pad5).padTop(pad20).row();
        t.add(status).top().left().padLeft(pad10 * 3f).padBottom(pad20).row();
        t.add(type).top().left().padBottom(pad5).row();
        t.add(version).top().left().padBottom(pad5).row();
        t.add(key).top().left().padBottom(pad5).row();
        t.add(size).top().left().padBottom(pad5).row();
        t.add(nObjects).top().left().padBottom(pad10).row();
        t.add(link).top().left().padBottom(pad20 * 2f).row();
        t.add(desc).top().left();
        // Scroll
        OwnScrollPane scrollPane = new OwnScrollPane(t, skin, "minimalist-nobg");
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setSmoothScrolling(false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setWidth(1050f);
        scrollPane.setHeight(Math.min(stage.getHeight() * 0.5f, 1500f));

        cell.setActor(t);
        cell.top().

                left();
        cell.getTable().

                pack();

        // Create watcher
        rightPaneWatcher = new

                DatasetWatcher(dataset, null, null, status);
        watchers.add(rightPaneWatcher);
    }

    public void refresh() {
        content.clear();
        build();
    }

    private void downloadDataset(DatasetDesc dataset) {
        downloadDataset(dataset, null);
    }

    private void downloadDataset(DatasetDesc dataset, Runnable successRunnable) {
        GaiaSky.postRunnable(() -> EventManager.instance.post(Events.DATASET_DOWNLOAD_START_INFO, dataset.key));

        String name = dataset.name;
        String url = dataset.file.replace("@mirror-url@", Settings.settings.program.url.dataMirror);

        String filename = FilenameUtils.getName(url);
        FileHandle tempDownload = Gdx.files.absolute(SysUtils.getTempDir(Settings.settings.data.location) + "/" + filename + ".part");

        ProgressRunnable progressDownload = (read, total, progress, speed) -> {
            double readMb = (double) read / 1e6d;
            double totalMb = (double) total / 1e6d;
            final String progressString = progress >= 100 ? I18n.txt("gui.done") : I18n.txt("gui.download.downloading", nf.format(progress));
            double mbPerSecond = speed / 1000d;
            final String speedString = nf.format(readMb) + "/" + nf.format(totalMb) + " MB (" + nf.format(mbPerSecond) + " MB/s)";
            // Since we are downloading on a background thread, post a runnable to touch UI
            GaiaSky.postRunnable(() -> {
                EventManager.instance.post(Events.DATASET_DOWNLOAD_PROGRESS_INFO, dataset.key, (float) progress, progressString, speedString);
            });
        };
        ProgressRunnable progressHashResume = (read, total, progress, speed) -> {
            double readMb = (double) read / 1e6d;
            double totalMb = (double) total / 1e6d;
            final String progressString = progress >= 100 ? I18n.txt("gui.done") : I18n.txt("gui.download.checksumming", nf.format(progress));
            double mbPerSecond = speed / 1000d;
            final String speedString = nf.format(readMb) + "/" + nf.format(totalMb) + " MB (" + nf.format(mbPerSecond) + " MB/s)";
            // Since we are downloading on a background thread, post a runnable to touch UI
            GaiaSky.postRunnable(() -> {
                EventManager.instance.post(Events.DATASET_DOWNLOAD_PROGRESS_INFO, dataset.key, (float) progress, progressString, speedString);
            });
        };

        ChecksumRunnable finish = (digest) -> {
            // Unpack
            int errors = 0;
            logger.info("Extracting: " + tempDownload.path());
            String dataLocation = Settings.settings.data.location + File.separatorChar;
            // Checksum
            if (digest != null && dataset.sha256 != null) {
                String serverDigest = dataset.sha256;
                try {
                    boolean ok = serverDigest.equals(digest);
                    if (ok) {
                        logger.info("SHA256 ok: " + name);
                    } else {
                        logger.error("SHA256 check failed: " + name);
                        errors++;
                    }
                } catch (Exception e) {
                    logger.info("Error checking SHA256: " + name);
                    errors++;
                }
            } else {
                logger.info("No digest found for dataset: " + name);
            }

            if (errors == 0) {
                try {
                    // Extract
                    decompress(tempDownload.path(), new File(dataLocation), dataset);
                    // Remove archive
                    cleanupTempFile(tempDownload.path());
                } catch (Exception e) {
                    logger.error(e, "Error decompressing: " + name);
                    errors++;
                }
            }

            final int numErrors = errors;
            // Done
            GaiaSky.postRunnable(() -> {
                me.acceptButton.setDisabled(false);

                if (numErrors == 0) {
                    // Ok message
                    EventManager.instance.post(Events.DATASET_DOWNLOAD_FINISH_INFO, dataset.key, 0);
                    dataset.exists = true;
                    if (successRunnable != null) {
                        successRunnable.run();
                    }
                    resetSelectedDataset();
                    reloadAll();
                } else {
                    logger.info("Error getting dataset: " + name);
                    setStatusError(dataset);
                }
            });

        };

        Runnable fail = () -> {
            logger.error("Download failed: " + name);
            setStatusError(dataset);
            me.acceptButton.setDisabled(false);
        };

        Runnable cancel = () -> {
            logger.error(I18n.txt("gui.download.cancelled", name));
            setStatusCancelled(dataset);
            me.acceptButton.setDisabled(false);
        };

        // Download
        me.acceptButton.setDisabled(true);
        final Net.HttpRequest request = DownloadHelper.downloadFile(url, tempDownload, progressDownload, progressHashResume, finish, fail, cancel);

        // Cancel button
        OwnTextButton cancelDownloadButton = new OwnTextButton(I18n.txt("gui.download.cancel"), skin);
        cancelDownloadButton.pad(14.4f);
        cancelDownloadButton.getLabel().setColor(1, 0, 0, 1);
        cancelDownloadButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (request != null) {
                    GaiaSky.postRunnable(() -> Gdx.net.cancelHttpRequest(request));
                }
            }
        });
    }

    private void decompress(String in, File out, DatasetDesc dataset) throws Exception {
        FileInfoInputStream fIs = new FileInfoInputStream(in);
        GzipCompressorInputStream gzIs = new GzipCompressorInputStream(fIs);
        TarArchiveInputStream tarIs = new TarArchiveInputStream(gzIs);
        double sizeKb = fileSize(in) / 1000d;
        String sizeKbStr = nf.format(sizeKb);
        TarArchiveEntry entry;
        long last = 0;
        while ((entry = tarIs.getNextTarEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            File curFile = new File(out, entry.getName());
            File parent = curFile.getParentFile();
            if (!parent.exists())
                parent.mkdirs();

            IOUtils.copy(tarIs, new FileOutputStream(curFile));

            // Every 250 ms we update the view
            long current = System.currentTimeMillis();
            long elapsed = current - last;
            if (elapsed > 250) {
                GaiaSky.postRunnable(() -> {
                    float val = (float) ((fIs.getBytesRead() / 1000d) / sizeKb) * 100f;
                    String progressString = I18n.txt("gui.download.extracting", nf.format(fIs.getBytesRead() / 1000d) + "/" + sizeKbStr + " Kb");
                    EventManager.instance.post(Events.DATASET_DOWNLOAD_PROGRESS_INFO, dataset.key, val, progressString, null);
                });
                last = current;
            }

        }
    }

    /**
     * Returns the file size
     *
     * @param inputFilePath A file
     *
     * @return The size in bytes
     */
    private long fileSize(String inputFilePath) {
        return new File(inputFilePath).length();
    }

    /**
     * Returns the GZ uncompressed size
     *
     * @param inputFilePath A gzipped file
     *
     * @return The uncompressed size in bytes
     *
     * @throws IOException If the file failed to read
     */
    private long fileSizeGZUncompressed(String inputFilePath) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(inputFilePath, "r");
        raf.seek(raf.length() - 4);
        byte[] bytes = new byte[4];
        raf.read(bytes);
        long fileSize = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).getLong();
        if (fileSize < 0)
            fileSize += (1L << 32);
        raf.close();
        return fileSize;
    }

    /**
     * We should never need to call this, as the main {@link GaiaSky#dispose()} method
     * already cleans up the temp directory.
     * This way, we allow download resumes within the same session.
     */
    private void cleanupTempFiles() {
        cleanupTempFiles(true, false);
    }

    private void cleanupTempFile(String file) {
        deleteFile(Path.of(file));
    }

    private void cleanupTempFiles(final boolean dataDownloads, final boolean dataDescriptor) {
        if (dataDownloads) {
            final Path tempDir = SysUtils.getTempDir(Settings.settings.data.location);
            // Clean up partial downloads
            try {
                final Stream<Path> stream = java.nio.file.Files.find(tempDir, 2, (path, basicFileAttributes) -> {
                    final File file = path.toFile();
                    return !file.isDirectory() && file.getName().endsWith("tar.gz.part");
                });
                stream.forEach(this::deleteFile);
            } catch (IOException e) {
                logger.error(e);
            }
        }

        if (dataDescriptor) {
            // Clean up data descriptor
            Path gsDownload = SysUtils.getTempDir(Settings.settings.data.location).resolve("gaiasky-data.json");
            deleteFile(gsDownload);
        }
    }

    private void deleteFile(Path p) {
        if (java.nio.file.Files.exists(p)) {
            try {
                java.nio.file.Files.delete(p);
            } catch (IOException e) {
                logger.error(e, "Failed cleaning up file: " + p.toString());
            }
        }
    }

    private void setStatusOutdated(DatasetDesc ds, OwnLabel label) {
        label.setText(I18n.txt("gui.download.status.outdated"));
        label.setColor(highlight);
        if (ds.releaseNotes != null && !ds.releaseNotes.isEmpty()) {
            label.setText(label.getText());
            label.addListener(new OwnTextTooltip(I18n.txt("gui.download.releasenotes", ds.releaseNotes), skin, 10));
        }
    }

    private void setStatusFound(DatasetDesc ds, OwnLabel label) {
        label.setText(I18n.txt("gui.download.status.found"));
        label.setColor(ColorUtils.gGreenC);
    }

    private void setStatusNotFound(DatasetDesc ds) {
        EventManager.instance.post(Events.DATASET_DOWNLOAD_FINISH_INFO, ds.key, 3);
    }

    private void setStatusError(DatasetDesc ds) {
        EventManager.instance.post(Events.DATASET_DOWNLOAD_FINISH_INFO, ds.key, 1);
    }

    private void setStatusCancelled(DatasetDesc ds) {
        EventManager.instance.post(Events.DATASET_DOWNLOAD_FINISH_INFO, ds.key, 2);
    }

    @Override
    protected void accept() {
    }

    @Override
    protected void cancel() {
    }

    @Override
    public void dispose() {

    }

    private void backupScrollValues() {
        if (leftScroll != null) {
            this.scroll[0][0] = leftScroll.getScrollX();
            this.scroll[0][1] = leftScroll.getScrollY();
        }
    }

    private void restoreScrollValues() {
        if (leftScroll != null) {
            GaiaSky.postRunnable(() -> {
                leftScroll.setScrollX(scroll[0][0]);
                leftScroll.setScrollY(scroll[0][1]);
            });
        }
    }

    @Override
    public void setKeyboardFocus() {
        if (stage != null && selectedDataset[selectedTab] != null) {
            Button button = buttonMap[selectedTab].get(selectedDataset[selectedTab].key);
            if (button != null) {
                stage.setKeyboardFocus(button);
            }
        }
    }

    private void cleanWatchers() {
        for (DatasetWatcher watcher : watchers) {
            watcher.dispose();
        }
        watchers.clear();
        rightPaneWatcher = null;
    }

    /**
     * Drops the current view and regenerates all window content
     */
    private void reloadAll() {
        cleanWatchers();

        serverDd = DataDescriptorUtils.instance().buildDatasetsDescriptor(null);
        backupScrollValues();
        leftScroll = null;
        content.clear();
        build();
        pack();
        restoreScrollValues();
    }

    private void actionDownloadDataset(DatasetDesc dataset) {
        downloadDataset(dataset);
    }

    private void actionUpdateDataset(DatasetDesc dataset) {
        downloadDataset(dataset, () -> {
            // Success!
            dataset.outdated = false;
            if (dataset.server != null) {
                dataset.server.outdated = false;
                dataset.server.myVersion = dataset.server.serverVersion;
                dataset.myVersion = dataset.server.serverVersion;
                if (serverDd != null) {
                    serverDd.numUpdates = Math.max(serverDd.numUpdates - 1, 0);
                    serverDd.updatesAvailable = serverDd.numUpdates > 0;
                }
            } else {
                dataset.myVersion = dataset.serverVersion;
            }
        });
    }

    private void actionEnableDataset(DatasetDesc dataset) {
        String filePath = dataset.catalogFile.path();
        if (!Settings.settings.data.catalogFiles.contains(filePath)) {
            Settings.settings.data.catalogFiles.add(filePath);
        }
    }

    private void actionDisableDataset(DatasetDesc dataset) {
        String filePath = dataset.catalogFile.path();
        Settings.settings.data.catalogFiles.remove(filePath);
    }

    private void actionDeleteDataset(DatasetDesc dataset) {
        GenericDialog question = new GenericDialog(I18n.txt("gui.download.delete.title"), skin, stage) {

            @Override
            protected void build() {
                content.clear();
                String title = dataset.exists ? dataset.name : dataset.shortDescription;
                content.add(new OwnLabel(I18n.txt("gui.download.delete.text"), skin)).left().padBottom(pad10 * 2f).row();
                content.add(new OwnLabel(title, skin, "warp")).center().padBottom(pad10 * 2f).row();
            }

            @Override
            protected void accept() {
                DatasetDesc serverDataset = serverDd != null ? serverDd.findDatasetByKey(dataset.key) : null;
                boolean deleted = false;
                // Delete
                if (dataset.filesToDelete != null) {
                    for (String fileToDelete : dataset.filesToDelete) {
                        try {
                            if (fileToDelete.endsWith("/")) {
                                fileToDelete = fileToDelete.substring(0, fileToDelete.length() - 1);
                            }
                            // Expand possible wildcards
                            String basePath = "";
                            String baseName = fileToDelete;
                            if (fileToDelete.contains("/")) {
                                basePath = fileToDelete.substring(0, fileToDelete.lastIndexOf('/'));
                                baseName = fileToDelete.substring(fileToDelete.lastIndexOf('/') + 1);
                            }
                            Path dataPath = Paths.get(Settings.settings.data.location);
                            dataPath = dataPath.toRealPath();
                            File directory = dataPath.resolve(basePath).toFile();
                            Collection<File> files = FileUtils.listFilesAndDirs(directory, new WildcardFileFilter(baseName), new WildcardFileFilter(baseName));
                            for (File file : files) {
                                if (!file.equals(directory) && file.exists()) {
                                    FileUtils.forceDelete(file);
                                }
                            }
                            deleted = true;
                        } catch (Exception e) {
                            logger.error(e);
                        }
                    }
                } else if (dataset.check != null) {
                    // Only remove "check"
                    try {
                        FileUtils.forceDelete(dataset.check.toFile());
                        deleted = true;
                    } catch (IOException e) {
                        logger.error(e);
                    }
                } else if (dataset.path != null) {
                    // Only remove "path"
                    try {
                        FileUtils.forceDelete(dataset.path.toFile());
                        deleted = true;
                    } catch (IOException e) {
                        logger.error(e);
                    }
                }
                // Update server dataset status and selected
                if (deleted && serverDataset != null) {
                    serverDataset.exists = false;
                    actionEnableDataset(dataset);
                    resetSelectedDataset();

                }
                // RELOAD DATASETS VIEW
                GaiaSky.postRunnable(() -> reloadAll());
            }

            @Override
            protected void cancel() {
                // Nothing
            }

            @Override
            public void dispose() {
                // Nothing
            }
        };
        question.setAcceptText(I18n.txt("gui.yes"));
        question.setCancelText(I18n.txt("gui.no"));
        question.buildSuper();
        question.show(stage);
    }

    private void resetSelectedDataset() {
        selectedDataset[0] = null;
        selectedDataset[1] = null;
        scroll[0][0] = 0f;
        scroll[0][1] = 0f;
        scroll[1][0] = 0f;
        scroll[1][1] = 0f;
    }

    /**
     * Constructs a list of local catalogs found in the current data location.
     *
     * @param server The server catalog descriptors, for combining with the local catalogs.
     *
     * @return The data descriptor containing the local catalogs.
     */
    public static synchronized DataDescriptor reloadLocalCatalogs(DataDescriptor server) {
        // Discover data sets, add as buttons
        Array<FileHandle> catalogLocations = new Array<>();
        catalogLocations.add(Gdx.files.absolute(Settings.settings.data.location));

        Array<FileHandle> catalogFiles = new Array<>();

        for (FileHandle catalogLocation : catalogLocations) {
            FileHandle[] cfs = catalogLocation.list(pathname -> (pathname.getName().startsWith("catalog-") || pathname.getName().startsWith("data-main")) && pathname.getName().endsWith(".json"));
            catalogFiles.addAll(cfs);
        }

        JsonReader reader = new JsonReader();
        Map<String, DatasetType> typeMap = new HashMap<>();
        List<DatasetType> types = new ArrayList<>();
        List<DatasetDesc> datasets = new ArrayList<>();
        for (FileHandle catalogFile : catalogFiles) {
            JsonValue val = reader.parse(catalogFile);
            DatasetDesc dd = new DatasetDesc(reader, val, catalogFile.path().endsWith("data-main.json"));
            dd.path = Path.of(catalogFile.path());
            dd.catalogFile = catalogFile;
            dd.exists = true;
            dd.status = DatasetDesc.DatasetStatus.INSTALLED;

            DatasetType dt;
            if (typeMap.containsKey(dd.type)) {
                dt = typeMap.get(dd.type);
            } else {
                dt = new DatasetType(dd.type);
                typeMap.put(dd.type, dt);
                types.add(dt);
            }

            dt.datasets.add(dd);
            datasets.add(dd);
        }

        Comparator<DatasetType> byType = Comparator.comparing(datasetType -> DatasetManagerWindow.getTypeWeight(datasetType.typeStr));
        types.sort(byType);

        if (server != null && server.datasets != null) {
            // Combine server with local datasets
            for (DatasetDesc local : datasets) {
                for (DatasetDesc remote : server.datasets) {
                    if (remote.check.getFileName().toString().equals(local.path.getFileName().toString())) {
                        // Match, update local with some server info
                        if(local.name.equals(remote.name)) {
                            // Update name, as ours is the remote key
                            local.name = remote.shortDescription;
                            local.shortDescription = remote.shortDescription;
                            local.description = remote.description.substring(remote.description.indexOf(" - ") + 3);
                        }
                        local.check = remote.check;
                        local.key = remote.key;
                        local.filesToDelete = remote.filesToDelete;
                        local.file = remote.file;
                        local.serverVersion = remote.serverVersion;
                        local.outdated = remote.outdated;
                        if (local.releaseNotes == null) {
                            local.releaseNotes = remote.releaseNotes;
                        }
                        if (local.datasetType == null) {
                            local.datasetType = remote.datasetType;
                        }
                        if (local.link == null) {
                            local.link = remote.link;
                        }
                        if (local.description == null) {
                            local.description = remote.description;
                        }
                        if (local.shortDescription == null) {
                            local.shortDescription = remote.shortDescription;
                        }
                        local.server = remote;
                    }
                }
            }

            // Add remotes on disk that are not catalogs
            for (DatasetDesc remote : server.datasets) {
                if (remote.exists) {
                    boolean found = false;
                    for (DatasetDesc local : datasets) {
                        if (remote.check.getFileName().toString().equals(local.path.getFileName().toString())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // Add to local datasets
                        DatasetDesc copy = remote.copy();
                        copy.name = copy.shortDescription;
                        copy.description = copy.description.substring(copy.description.indexOf(" - ") + 3);
                        copy.catalogFile = Gdx.files.absolute(copy.check.toAbsolutePath().toString());
                        copy.path = copy.check.toAbsolutePath();
                        datasets.add(copy);
                        // Add to local types
                        DatasetType remoteType = copy.datasetType;
                        found = false;
                        for (DatasetType localType : types) {
                            if (localType.equals(remoteType)) {
                                localType.datasets.add(copy);
                                copy.datasetType = localType;
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            // No type! create it
                            DatasetType newType = new DatasetType(remoteType.typeStr);
                            newType.addDataset(copy);
                            copy.datasetType = newType;
                            types.add(newType);
                        }
                    }
                }
            }
        }

        // Default values in case this is a totally offline dataset
        for (DatasetDesc dd : datasets) {
            if (dd.description == null)
                dd.description = dd.path.toString();
            if (dd.shortDescription == null)
                dd.shortDescription = dd.description;
            if (dd.name == null)
                dd.name = dd.catalogFile.nameWithoutExtension();
        }

        return new DataDescriptor(types, datasets);
    }

}
