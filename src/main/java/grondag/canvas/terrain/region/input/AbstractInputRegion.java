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

package grondag.canvas.terrain.region.input;

import static grondag.canvas.terrain.util.RenderRegionStateIndexer.regionIndex;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Tries to prevent InputRegion from being unreadably big. Fails.
 */
abstract class AbstractInputRegion {
	// larger than needed to speed up indexing
	protected final WorldChunk[] chunks = new WorldChunk[16];
	protected int originX;
	protected int originY;
	protected int originZ;
	protected int chunkBaseX;
	/** Section index of region below this one, -1 if this is the bottom-most region in a chunk. */
	protected int baseSectionIndex;
	protected int chunkBaseZ;
	protected World world;

	final boolean isInMainChunk(int x, int y, int z) {
		return originX == (x & 0xFFFFFFF0) && originY == (y & 0xFFFFFFF0) && originZ == (z & 0xFFFFFFF0);
	}

	final boolean isInMainChunk(BlockPos pos) {
		return isInMainChunk(pos.getX(), pos.getY(), pos.getZ());
	}

	final int blockIndex(int x, int y, int z) {
		return regionIndex(x - originX, y - originY, z - originZ);
	}

	protected ChunkSection getSection(int x, int y, int z) {
		final int index = y + baseSectionIndex;

		if (index < 0) {
			return null;
		}

		final ChunkSection[] sections = chunks[x | (z << 2)].getSectionArray();
		return index >= sections.length ? null : sections[index];
	}

	protected WorldChunk getChunk(int cx, int cz) {
		final int chunkBaseX = this.chunkBaseX;
		final int chunkBaseZ = this.chunkBaseZ;

		if (cx < chunkBaseX || cx > (chunkBaseZ + 2) || cz < chunkBaseZ || cz > (chunkBaseZ + 2)) {
			return world.getChunk(cx, cz);
		} else {
			return chunks[(cx - chunkBaseX) | ((cz - chunkBaseZ) << 2)];
		}
	}
}
