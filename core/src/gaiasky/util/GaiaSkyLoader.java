package gaiasky.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Timer;
import gaiasky.GaiaSky;
import gaiasky.gui.BookmarksManager;
import gaiasky.render.ComponentTypes;
import gaiasky.render.MainPostProcessor;
import gaiasky.script.EventScriptingInterface;
import gaiasky.script.HiddenHelperUser;
import gaiasky.script.ScriptingServer;
import gaiasky.util.GaiaSkyLoader.GaiaSkyLoaderParameters;
import gaiasky.util.gravwaves.RelativisticEffectsManager;
import gaiasky.util.samp.SAMPClient;
import gaiasky.util.svt.SVTManager;

public class GaiaSkyLoader extends AsynchronousAssetLoader<GaiaSkyAssets, GaiaSkyLoaderParameters> {

    private GaiaSkyAssets assets;

    public GaiaSkyLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, GaiaSkyLoaderParameters parameter) {
        assets = new GaiaSkyAssets();

        if(parameter.firstStage) {
            // First stage async.

            // Tooltip to 1s
            TooltipManager.getInstance().initialTime = 1f;

            // Initialise hidden helper user
            HiddenHelperUser.initialize();

            // Initialise gravitational waves helper
            RelativisticEffectsManager.initialize(parameter.gaiaSky.time);

            // Location log
            LocationLogManager.initialize();

            // Init timer thread
            Timer.instance();

            // Scripting server.
            if (!parameter.noScripting) {
                assets.scriptingInterface = new EventScriptingInterface(parameter.gaiaSky.assetManager, parameter.gaiaSky.getCatalogManager());
                ScriptingServer.initialize(assets.scriptingInterface);
            }

            // Bookmarks manager.
            assets.bookmarksManager = new BookmarksManager();

            // SAMP client.
            assets.sampClient = new SAMPClient(parameter.gaiaSky.getCatalogManager());
            assets.sampClient.initialize(parameter.gaiaSky.getGlobalResources().getSkin());

            // SVT.
            assets.svtManager = new SVTManager();

            // Post processor.
            assets.postProcessor = new MainPostProcessor(null);
            assets.postProcessor.initialize(manager);
        } else {
            // Second stage async.

        }
    }

    @Override
    public GaiaSkyAssets loadSync(AssetManager manager, String fileName, FileHandle file, GaiaSkyLoaderParameters parameter) {
        if(parameter.firstStage) {
            // First stage sync.
            assets.svtManager.doneLoading(manager);
            assets.postProcessor.doneLoading(manager);
        } else {
            // Second stage sync.

        }
        return assets;
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, GaiaSkyLoaderParameters parameter) {
        return null;
    }

    static public class GaiaSkyLoaderParameters extends AssetLoaderParameters<GaiaSkyAssets> {
        public boolean firstStage;
        public boolean noScripting;
        public GaiaSky gaiaSky;
        public GaiaSkyLoaderParameters(GaiaSky gaiaSky, boolean noScripting, boolean firstStage) {
            this.gaiaSky = gaiaSky;
            this.noScripting = noScripting;
            this.firstStage = firstStage;
        }
        public GaiaSkyLoaderParameters(GaiaSky gaiaSky, boolean noScripting) {
            this(gaiaSky, noScripting, true);
        }
    }
}
