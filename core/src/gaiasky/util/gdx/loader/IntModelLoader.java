/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.gdx.loader;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import gaiasky.util.gdx.model.IntModel;
import gaiasky.util.gdx.model.data.IntModelData;

import java.util.Iterator;

public abstract class IntModelLoader<P extends IntModelLoader.IntModelParameters> extends AsynchronousAssetLoader<IntModel, P> {
    protected Array<ObjectMap.Entry<String, IntModelData>> items = new Array<>();
    protected IntModelParameters defaultParameters = new IntModelParameters();
    public IntModelLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    /** Directly load the raw model data on the calling thread. */
    public abstract IntModelData loadModelData(final FileHandle fileHandle, P parameters);

    /** Directly load the raw model data on the calling thread. */
    public IntModelData loadModelData(final FileHandle fileHandle) {
        return loadModelData(fileHandle, null);
    }

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    public IntModel loadModel(final FileHandle fileHandle, TextureProvider textureProvider, P parameters) {
        final IntModelData data = loadModelData(fileHandle, parameters);
        return data == null ? null : new IntModel(data, textureProvider);
    }

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    public IntModel loadModel(final FileHandle fileHandle, P parameters) {
        return loadModel(fileHandle, new TextureProvider.FileTextureProvider(), parameters);
    }

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    public IntModel loadModel(final FileHandle fileHandle, TextureProvider textureProvider) {
        return loadModel(fileHandle, textureProvider, null);
    }

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    public IntModel loadModel(final FileHandle fileHandle) {
        return loadModel(fileHandle, new TextureProvider.FileTextureProvider(), null);
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, P parameters) {
        final Array<AssetDescriptor> deps = new Array<>();
        IntModelData data = loadModelData(file, parameters);
        if (data == null)
            return deps;

        ObjectMap.Entry<String, IntModelData> item = new ObjectMap.Entry<>();
        item.key = fileName;
        item.value = data;
        synchronized (items) {
            items.add(item);
        }

        OwnTextureLoader.OwnTextureParameter textureParameter = (parameters != null)
                ? parameters.textureParameter
                : defaultParameters.textureParameter;

        for (final ModelMaterial modelMaterial : data.materials) {
            if (modelMaterial.textures != null) {
                for (final ModelTexture modelTexture : modelMaterial.textures)
                    deps.add(new AssetDescriptor(modelTexture.fileName, Texture.class, textureParameter));
            }
        }
        return deps;
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, P parameters) {
    }

    @Override
    public IntModel loadSync(AssetManager manager, String fileName, FileHandle file, P parameters) {
        IntModelData data = null;
        synchronized (items) {
            for (int i = 0; i < items.size; i++) {
                if (items.get(i).key.equals(fileName)) {
                    data = items.get(i).value;
                    items.removeIndex(i);
                }
            }
        }
        if (data == null)
            return null;
        final IntModel result = new IntModel(data, new TextureProvider.AssetTextureProvider(manager));
        // need to remove the textures from the managed disposables, or else ref counting
        // doesn't work!
        Iterator<Disposable> disposables = result.getManagedDisposables().iterator();
        while (disposables.hasNext()) {
            Disposable disposable = disposables.next();
            if (disposable instanceof Texture) {
                disposables.remove();
            }
        }
        data = null;
        return result;
    }

    static public class IntModelParameters extends AssetLoaderParameters<IntModel> {
        public OwnTextureLoader.OwnTextureParameter textureParameter;

        public IntModelParameters() {
            textureParameter = new OwnTextureLoader.OwnTextureParameter();
            textureParameter.minFilter = textureParameter.magFilter = Texture.TextureFilter.Linear;
            textureParameter.wrapU = textureParameter.wrapV = Texture.TextureWrap.Repeat;
        }
    }
}
