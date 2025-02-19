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

package grondag.canvas.texture;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

public class ResourceCacheManager {
	public static final ObjectArrayList<ResourceCache<?>> CACHED = new ObjectArrayList<>(64);
	public static final SimpleSynchronousResourceReloadListener cacheReloader = new SimpleSynchronousResourceReloadListener() {
		private final Identifier ID = new Identifier("canvas:resource_cache_reloader");

		@Override
		public Identifier getFabricId() {
			return ID;
		}

		@Override
		public void reload(ResourceManager resourceManager) {
			MaterialIndexProvider.reload();
			CACHED.forEach(ResourceCache::invalidate);
		}
	};
}
