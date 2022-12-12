/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package gaiasky.util.gdx;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.Array;

/**
 * Responsible for sorting {@link IntRenderable} lists by whatever criteria (material, distance to camera, etc.)
 *
 * @author badlogic
 */
public interface IntRenderableSorter {
    /**
     * Sorts the array of {@link IntRenderable} instances based on some criteria, e.g. material, distance to camera etc.
     *
     * @param renderables the array of renderables to be sorted
     */
    void sort(Camera camera, Array<IntRenderable> renderables);
}
