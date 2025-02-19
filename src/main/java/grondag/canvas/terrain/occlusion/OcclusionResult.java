/*
 *  Copyright 2019, 2020 grondag
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License.  You may obtain a copy
 *  of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package grondag.canvas.terrain.occlusion;

public enum OcclusionResult {
	/** Not visited or classified in current version - visibility unknown. */
	UNDETERMINED,

	/** Visited but not classified in current version. Visibility testing still needed. */
	VISITED,

	/** No part of region is visible. */
	REGION_NOT_VISIBLE,

	/** Renderable content in region is visible. */
	REGION_VISIBLE,

	/** No renderable content visible, but some empty space is visible. */
	ENTITIES_VISIBLE
}
