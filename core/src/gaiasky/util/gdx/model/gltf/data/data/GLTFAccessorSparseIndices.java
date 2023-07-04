/*
 * This file is part of the gdx-gltf (https://github.com/mgsx-dev/gdx-gltf) library,
 * under the APACHE 2.0 license. We have possibly modified parts of this file so that
 * 32-bit integer indices are supported, instead of only 16-bit short ones.
 */

package gaiasky.util.gdx.model.gltf.data.data;

import gaiasky.util.gdx.model.gltf.data.GLTFObject;

public class GLTFAccessorSparseIndices extends GLTFObject {
	public int bufferView, byteOffset, componentType;
}
