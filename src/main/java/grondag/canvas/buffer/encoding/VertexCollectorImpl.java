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

package grondag.canvas.buffer.encoding;

import static grondag.canvas.buffer.format.CanvasVertexFormats.MATERIAL_QUAD_STRIDE;

import java.nio.IntBuffer;

import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.util.math.MathHelper;

import grondag.canvas.buffer.format.CanvasVertexFormats;
import grondag.canvas.material.state.RenderMaterialImpl;

public class VertexCollectorImpl extends AbstractVertexCollector {
	float[] perQuadDistance = new float[512];

	public VertexCollectorImpl prepare(RenderMaterialImpl materialState) {
		clear();
		this.materialState = materialState;
		vertexState(materialState);
		return this;
	}

	public void clear() {
		currentVertexIndex = 0;
		integerSize = 0;
		didPopulateNormal = false;
	}

	public int integerSize() {
		return integerSize;
	}

	public int byteSize() {
		return integerSize * 4;
	}

	public boolean isEmpty() {
		return integerSize == 0;
	}

	public RenderMaterialImpl materialState() {
		return materialState;
	}

	public int vertexCount() {
		return integerSize / CanvasVertexFormats.MATERIAL_VERTEX_STRIDE;
	}

	public int quadCount() {
		return vertexCount() / 4;
	}

	@Override
	public VertexCollectorImpl clone() {
		throw new UnsupportedOperationException();
	}

	public void sortQuads(float x, float y, float z) {
		final int quadCount = vertexCount() / 4;

		if (perQuadDistance.length < quadCount) {
			perQuadDistance = new float[MathHelper.smallestEncompassingPowerOfTwo(quadCount)];
		}

		for (int j = 0; j < quadCount; ++j) {
			perQuadDistance[j] = getDistanceSq(x, y, z, CanvasVertexFormats.MATERIAL_VERTEX_STRIDE, j);
		}

		// sort the indexes by distance - farthest first
		// mergesort is important here - quicksort causes problems
		// PERF: consider sorting primitive packed long array with distance in high bits
		// and then use result to reorder the array. Will need to copy vertex data.
		it.unimi.dsi.fastutil.Arrays.mergeSort(0, quadCount, comparator, swapper);
	}

	private final IntComparator comparator = new IntComparator() {
		@Override
		public int compare(int a, int b) {
			return Float.compare(perQuadDistance[b], perQuadDistance[a]);
		}
	};

	private final int[] swapData = new int[MATERIAL_QUAD_STRIDE * 2];

	private final Swapper swapper = new Swapper() {
		@Override
		public void swap(int a, int b) {
			final float distSwap = perQuadDistance[a];
			perQuadDistance[a] = perQuadDistance[b];
			perQuadDistance[b] = distSwap;

			final int aIndex = a * MATERIAL_QUAD_STRIDE;
			final int bIndex = b * MATERIAL_QUAD_STRIDE;

			System.arraycopy(vertexData, aIndex, swapData, 0, MATERIAL_QUAD_STRIDE);
			System.arraycopy(vertexData, bIndex, swapData, MATERIAL_QUAD_STRIDE, MATERIAL_QUAD_STRIDE);
			System.arraycopy(swapData, 0, vertexData, bIndex, MATERIAL_QUAD_STRIDE);
			System.arraycopy(swapData, MATERIAL_QUAD_STRIDE, vertexData, aIndex, MATERIAL_QUAD_STRIDE);
		}
	};

	private float getDistanceSq(float x, float y, float z, int integerStride, int vertexIndex) {
		// unpack vertex coordinates
		int i = vertexIndex * integerStride * 4;
		final float x0 = Float.intBitsToFloat(vertexData[i]);
		final float y0 = Float.intBitsToFloat(vertexData[i + 1]);
		final float z0 = Float.intBitsToFloat(vertexData[i + 2]);

		i += integerStride;
		final float x1 = Float.intBitsToFloat(vertexData[i]);
		final float y1 = Float.intBitsToFloat(vertexData[i + 1]);
		final float z1 = Float.intBitsToFloat(vertexData[i + 2]);

		i += integerStride;
		final float x2 = Float.intBitsToFloat(vertexData[i]);
		final float y2 = Float.intBitsToFloat(vertexData[i + 1]);
		final float z2 = Float.intBitsToFloat(vertexData[i + 2]);

		i += integerStride;
		final float x3 = Float.intBitsToFloat(vertexData[i]);
		final float y3 = Float.intBitsToFloat(vertexData[i + 1]);
		final float z3 = Float.intBitsToFloat(vertexData[i + 2]);

		// compute average distance by component
		final float dx = (x0 + x1 + x2 + x3) * 0.25f - x;
		final float dy = (y0 + y1 + y2 + y3) * 0.25f - y;
		final float dz = (z0 + z1 + z2 + z3) * 0.25f - z;

		return dx * dx + dy * dy + dz * dz;
	}

	public int[] saveState(int[] priorState) {
		if (integerSize == 0) {
			return null;
		}

		int[] result = priorState;

		if (result == null || result.length != integerSize) {
			result = new int[integerSize];
		}

		if (integerSize > 0) {
			System.arraycopy(vertexData, 0, result, 0, integerSize);
		}

		return result;
	}

	public VertexCollectorImpl loadState(RenderMaterialImpl state, int[] stateData) {
		if (stateData == null) {
			clear();
			return this;
		}

		materialState = state;
		final int newSize = stateData.length;
		integerSize = 0;

		if (newSize > 0) {
			grow(newSize);
			integerSize = newSize;
			System.arraycopy(stateData, 0, vertexData, 0, newSize);
		}

		return this;
	}

	public void toBuffer(IntBuffer intBuffer) {
		intBuffer.put(vertexData, 0, integerSize);
	}

	public void draw(boolean clear) {
		if (!isEmpty()) {
			drawSingle();

			if (clear) {
				clear();
			}
		}
	}

	void sortIfNeeded() {
		if (materialState.sorted) {
			sortQuads(0, 0, 0);
		}
	}

	/** Avoid: slow. */
	public void drawSingle() {
		// PERF: allocation - or eliminate this
		final ObjectArrayList<VertexCollectorImpl> drawList = new ObjectArrayList<>();
		drawList.add(this);
		draw(drawList);
	}

	/**
	 * Single-buffer draw, minimizes state changes.
	 * Assumes all collectors are non-empty.
	 */
	public static void draw(ObjectArrayList<VertexCollectorImpl> drawList) {
		final DrawableBuffer buffer = new DrawableBuffer(drawList);
		buffer.draw(false);
		buffer.close();
	}

	@Override
	protected void emitQuad() {
		if (conditionActive) {
			final int newSize = integerSize + CanvasVertexFormats.MATERIAL_QUAD_STRIDE;
			grow(newSize + CanvasVertexFormats.MATERIAL_QUAD_STRIDE);
			currentVertexIndex = newSize;
			integerSize = newSize;
		} else {
			currentVertexIndex = integerSize;
		}
	}

	@Override
	public void append(int val) {
		final int oldSize = integerSize;
		vertexData[oldSize] = val;

		if ((oldSize & FULL_BLOCK_MASK) == FULL_BLOCK_MASK) {
			grow();
		}

		integerSize = oldSize + 1;
	}

	public static String debugReport() {
		return String.format("Vertex Collectors - count;%d,   MB allocated:%f", collectorCount.get(), collectorBytes.get() / 1048576f);
	}
}
