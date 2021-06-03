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

package grondag.canvas.terrain.region;

import static grondag.canvas.terrain.util.RenderRegionAddressHelper.AIR;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.EXTERIOR_CACHE_SIZE;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.RENDER_REGION_INTERIOR_COUNT;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.interiorIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localCornerIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localXEdgeIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localXfaceIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localYEdgeIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localYfaceIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localZEdgeIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelper.localZfaceIndex;
import static grondag.canvas.terrain.util.RenderRegionAddressHelperNew.RENDER_REGION_STATE_COUNT;
import static grondag.canvas.terrain.util.RenderRegionAddressHelperNew.renderRegionIndex;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;

import grondag.canvas.perf.ChunkRebuildCounters;
import grondag.canvas.terrain.util.ChunkPaletteCopier;
import grondag.canvas.terrain.util.ChunkPaletteCopier.PaletteCopy;

public class ProtoRenderRegion extends AbstractRenderRegion {
	/**
	 * Signals that build was completed successfully, or has never been run. Nothing is scheduled.
	 */
	public static final ProtoRenderRegion IDLE = new DummyRegion();
	/**
	 * Signals that build is for resort only.
	 */
	public static final ProtoRenderRegion RESORT_ONLY = new DummyRegion();
	/**
	 * Signals that build has been cancelled or some other condition has made it unbuildable.
	 */
	public static final ProtoRenderRegion INVALID = new DummyRegion();
	/**
	 * Signals that build is for empty chunk.
	 */
	public static final ProtoRenderRegion EMPTY = new DummyRegion();
	private static final ArrayBlockingQueue<ProtoRenderRegion> POOL = new ArrayBlockingQueue<>(256);
	public final ObjectArrayList<BlockEntity> blockEntities = new ObjectArrayList<>();
	final BlockState[] exteriorStates = new BlockState[EXTERIOR_CACHE_SIZE];
	final ShortArrayList renderDataPos = new ShortArrayList();
	final ObjectArrayList<Object> renderData = new ObjectArrayList<>();
	final ShortArrayList blockEntityPos = new ShortArrayList();
	PaletteCopy mainSectionCopy;

	final BlockState[] states = new BlockState[RENDER_REGION_STATE_COUNT];

	public static ProtoRenderRegion claim(ClientWorld world, BlockPos origin) {
		final ProtoRenderRegion result = POOL.poll();
		return (result == null ? new ProtoRenderRegion() : result).prepare(world, origin);
	}

	private static void release(ProtoRenderRegion region) {
		POOL.offer(region);
	}

	public static void reload() {
		// ensure current AoFix rule or other config-dependent lambdas are used
		POOL.clear();
	}

	private ProtoRenderRegion prepare(ClientWorld world, BlockPos origin) {
		if (ChunkRebuildCounters.ENABLED) {
			ChunkRebuildCounters.startCopy();
		}

		this.world = world;

		final int originX = origin.getX();
		final int originY = origin.getY();
		final int originZ = origin.getZ();

		this.originX = originX;
		this.originY = originY;
		this.originZ = originZ;

		final int chunkBaseX = (originX >> 4) - 1;
		final int chunkBaseZ = (originZ >> 4) - 1;

		this.chunkBaseX = chunkBaseX;
		baseSectionIndex = ((originY - world.getBottomY()) >> 4) - 1;
		this.chunkBaseZ = chunkBaseZ;

		final WorldChunk mainChunk = world.getChunk(chunkBaseX + 1, chunkBaseZ + 1);
		mainSectionCopy = ChunkPaletteCopier.captureCopy(mainChunk, originY);

		final ProtoRenderRegion result;

		if (mainSectionCopy == ChunkPaletteCopier.AIR_COPY) {
			release();
			result = EMPTY;
		} else {
			captureBlockEntities(mainChunk);
			chunks[1 | (1 << 2)] = mainChunk;
			chunks[0 | (0 << 2)] = world.getChunk(chunkBaseX + 0, chunkBaseZ + 0);
			chunks[0 | (1 << 2)] = world.getChunk(chunkBaseX + 0, chunkBaseZ + 1);
			chunks[0 | (2 << 2)] = world.getChunk(chunkBaseX + 0, chunkBaseZ + 2);
			chunks[1 | (0 << 2)] = world.getChunk(chunkBaseX + 1, chunkBaseZ + 0);
			chunks[1 | (2 << 2)] = world.getChunk(chunkBaseX + 1, chunkBaseZ + 2);
			chunks[2 | (0 << 2)] = world.getChunk(chunkBaseX + 2, chunkBaseZ + 0);
			chunks[2 | (1 << 2)] = world.getChunk(chunkBaseX + 2, chunkBaseZ + 1);
			chunks[2 | (2 << 2)] = world.getChunk(chunkBaseX + 2, chunkBaseZ + 2);

			captureCorners();
			captureEdges();
			captureFaces();

			result = this;
		}

		if (ChunkRebuildCounters.ENABLED) {
			ChunkRebuildCounters.completeCopy();
		}

		return result;
	}

	PaletteCopy takePaletteCopy() {
		final PaletteCopy result = mainSectionCopy;
		mainSectionCopy = null;
		return result;
	}

	private void captureBlockEntities(WorldChunk mainChunk) {
		renderDataPos.clear();
		renderData.clear();
		blockEntityPos.clear();
		blockEntities.clear();
		final int yCheck = (originY >> 4);

		for (final Map.Entry<BlockPos, BlockEntity> entry : mainChunk.getBlockEntities().entrySet()) {
			final BlockPos pos = entry.getKey();

			// only those in this chunk
			if (pos.getY() >> 4 != yCheck) {
				continue;
			}

			final short key = (short) interiorIndex(pos);
			final BlockEntity be = entry.getValue();

			blockEntityPos.add(key);
			blockEntities.add(be);

			final Object rd = ((RenderAttachmentBlockEntity) be).getRenderAttachmentData();

			if (rd != null) {
				renderDataPos.add(key);
				renderData.add(rd);
			}
		}
	}

	private void captureFaces() {
		final ChunkSection lowX = getSection(0, 1, 1);
		final ChunkSection highX = getSection(2, 1, 1);
		final ChunkSection lowZ = getSection(1, 1, 0);
		final ChunkSection highZ = getSection(1, 1, 2);
		final ChunkSection lowY = getSection(1, 0, 1);
		final ChunkSection highY = getSection(1, 2, 1);

		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				exteriorStates[localXfaceIndex(false, i, j) - RENDER_REGION_INTERIOR_COUNT] = lowX == null ? AIR : lowX.getBlockState(15, i, j);
				exteriorStates[localXfaceIndex(true, i, j) - RENDER_REGION_INTERIOR_COUNT] = highX == null ? AIR : highX.getBlockState(0, i, j);

				exteriorStates[localZfaceIndex(i, j, false) - RENDER_REGION_INTERIOR_COUNT] = lowZ == null ? AIR : lowZ.getBlockState(i, j, 15);
				exteriorStates[localZfaceIndex(i, j, true) - RENDER_REGION_INTERIOR_COUNT] = highZ == null ? AIR : highZ.getBlockState(i, j, 0);

				exteriorStates[localYfaceIndex(i, false, j) - RENDER_REGION_INTERIOR_COUNT] = lowY == null ? AIR : lowY.getBlockState(i, 15, j);
				exteriorStates[localYfaceIndex(i, true, j) - RENDER_REGION_INTERIOR_COUNT] = highY == null ? AIR : highY.getBlockState(i, 0, j);

				///

				states[renderRegionIndex(-1, i, j)] = lowX == null ? AIR : lowX.getBlockState(15, i, j);
				states[renderRegionIndex(16, i, j)] = highX == null ? AIR : highX.getBlockState(0, i, j);

				states[renderRegionIndex(i, j, -1)] = lowZ == null ? AIR : lowZ.getBlockState(i, j, 15);
				states[renderRegionIndex(i, j, 16)] = highZ == null ? AIR : highZ.getBlockState(i, j, 0);

				states[renderRegionIndex(i, -1, j)] = lowY == null ? AIR : lowY.getBlockState(i, 15, j);
				states[renderRegionIndex(i, 16, j)] = highY == null ? AIR : highY.getBlockState(i, 0, j);
			}
		}
	}

	private void captureEdges() {
		final ChunkSection aaZ = getSection(0, 0, 1);
		final ChunkSection abZ = getSection(0, 2, 1);
		final ChunkSection baZ = getSection(2, 0, 1);
		final ChunkSection bbZ = getSection(2, 2, 1);

		final ChunkSection aYa = getSection(0, 1, 0);
		final ChunkSection aYb = getSection(0, 1, 2);
		final ChunkSection bYa = getSection(2, 1, 0);
		final ChunkSection bYb = getSection(2, 1, 2);

		final ChunkSection Xaa = getSection(1, 0, 0);
		final ChunkSection Xab = getSection(1, 0, 2);
		final ChunkSection Xba = getSection(1, 2, 0);
		final ChunkSection Xbb = getSection(1, 2, 2);

		for (int i = 0; i < 16; i++) {
			exteriorStates[localZEdgeIndex(false, false, i) - RENDER_REGION_INTERIOR_COUNT] = aaZ == null ? AIR : aaZ.getBlockState(15, 15, i);
			exteriorStates[localZEdgeIndex(false, true, i) - RENDER_REGION_INTERIOR_COUNT] = abZ == null ? AIR : abZ.getBlockState(15, 0, i);
			exteriorStates[localZEdgeIndex(true, false, i) - RENDER_REGION_INTERIOR_COUNT] = baZ == null ? AIR : baZ.getBlockState(0, 15, i);
			exteriorStates[localZEdgeIndex(true, true, i) - RENDER_REGION_INTERIOR_COUNT] = bbZ == null ? AIR : bbZ.getBlockState(0, 0, i);

			exteriorStates[localYEdgeIndex(false, i, false) - RENDER_REGION_INTERIOR_COUNT] = aYa == null ? AIR : aYa.getBlockState(15, i, 15);
			exteriorStates[localYEdgeIndex(false, i, true) - RENDER_REGION_INTERIOR_COUNT] = aYb == null ? AIR : aYb.getBlockState(15, i, 0);
			exteriorStates[localYEdgeIndex(true, i, false) - RENDER_REGION_INTERIOR_COUNT] = bYa == null ? AIR : bYa.getBlockState(0, i, 15);
			exteriorStates[localYEdgeIndex(true, i, true) - RENDER_REGION_INTERIOR_COUNT] = bYb == null ? AIR : bYb.getBlockState(0, i, 0);

			exteriorStates[localXEdgeIndex(i, false, false) - RENDER_REGION_INTERIOR_COUNT] = Xaa == null ? AIR : Xaa.getBlockState(i, 15, 15);
			exteriorStates[localXEdgeIndex(i, false, true) - RENDER_REGION_INTERIOR_COUNT] = Xab == null ? AIR : Xab.getBlockState(i, 15, 0);
			exteriorStates[localXEdgeIndex(i, true, false) - RENDER_REGION_INTERIOR_COUNT] = Xba == null ? AIR : Xba.getBlockState(i, 0, 15);
			exteriorStates[localXEdgeIndex(i, true, true) - RENDER_REGION_INTERIOR_COUNT] = Xbb == null ? AIR : Xbb.getBlockState(i, 0, 0);

			////

			states[renderRegionIndex(-1, -1, i)] = aaZ == null ? AIR : aaZ.getBlockState(15, 15, i);
			states[renderRegionIndex(-1, 16, i)] = abZ == null ? AIR : abZ.getBlockState(15, 0, i);
			states[renderRegionIndex(16, -1, i)] = baZ == null ? AIR : baZ.getBlockState(0, 15, i);
			states[renderRegionIndex(16, 16, i)] = bbZ == null ? AIR : bbZ.getBlockState(0, 0, i);

			states[renderRegionIndex(-1, i, -1)] = aYa == null ? AIR : aYa.getBlockState(15, i, 15);
			states[renderRegionIndex(-1, i, 16)] = aYb == null ? AIR : aYb.getBlockState(15, i, 0);
			states[renderRegionIndex(16, i, -1)] = bYa == null ? AIR : bYa.getBlockState(0, i, 15);
			states[renderRegionIndex(16, i, 16)] = bYb == null ? AIR : bYb.getBlockState(0, i, 0);

			states[renderRegionIndex(i, -1, -1)] = Xaa == null ? AIR : Xaa.getBlockState(i, 15, 15);
			states[renderRegionIndex(i, -1, 16)] = Xab == null ? AIR : Xab.getBlockState(i, 15, 0);
			states[renderRegionIndex(i, 16, -1)] = Xba == null ? AIR : Xba.getBlockState(i, 0, 15);
			states[renderRegionIndex(i, 16, 16)] = Xbb == null ? AIR : Xbb.getBlockState(i, 0, 0);
		}
	}

	private void captureCorners() {
		exteriorStates[localCornerIndex(false, false, false) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(0, 0, 0);
		exteriorStates[localCornerIndex(false, false, true) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(0, 0, 2);
		exteriorStates[localCornerIndex(false, true, false) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(0, 2, 0);
		exteriorStates[localCornerIndex(false, true, true) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(0, 2, 2);

		exteriorStates[localCornerIndex(true, false, false) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(2, 0, 0);
		exteriorStates[localCornerIndex(true, false, true) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(2, 0, 2);
		exteriorStates[localCornerIndex(true, true, false) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(2, 2, 0);
		exteriorStates[localCornerIndex(true, true, true) - RENDER_REGION_INTERIOR_COUNT] = captureCornerState(2, 2, 2);

		///

		states[renderRegionIndex(-1, -1, -1)] = captureCornerState(0, 0, 0);
		states[renderRegionIndex(-1, -1, 16)] = captureCornerState(0, 0, 2);
		states[renderRegionIndex(-1, 16, -1)] = captureCornerState(0, 2, 0);
		states[renderRegionIndex(-1, 16, 16)] = captureCornerState(0, 2, 2);

		states[renderRegionIndex(16, -1, -1)] = captureCornerState(2, 0, 0);
		states[renderRegionIndex(16, -1, 16)] = captureCornerState(2, 0, 2);
		states[renderRegionIndex(16, 16, -1)] = captureCornerState(2, 2, 0);
		states[renderRegionIndex(16, 16, 16)] = captureCornerState(2, 2, 2);
	}

	private BlockState captureCornerState(int x, int y, int z) {
		final ChunkSection section = getSection(x, y, z);
		return section == null ? AIR : section.getBlockState(x == 0 ? 15 : 0, y == 0 ? 15 : 0, z == 0 ? 15 : 0);
	}

	public void release() {
		if (mainSectionCopy != null) {
			mainSectionCopy.release();
			mainSectionCopy = null;
		}

		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				chunks[x | (z << 2)] = null;
			}
		}

		blockEntities.clear();
		renderData.clear();

		release(this);
	}

	private static class DummyRegion extends ProtoRenderRegion {
		@Override
		public void release() {
		}
	}
}
