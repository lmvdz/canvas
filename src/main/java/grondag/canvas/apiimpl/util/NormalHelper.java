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

package grondag.canvas.apiimpl.util;

import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3f;
import net.minecraft.util.math.Vec3i;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;

/**
 * Static routines of general utility for renderer implementations.
 * Renderers are not required to use these helpers, but they were
 * designed to be usable without the default renderer.
 */
public abstract class NormalHelper {
	private NormalHelper() { }

	public static int packNormal(float x, float y, float z) {
		x = MathHelper.clamp(x, -1, 1);
		y = MathHelper.clamp(y, -1, 1);
		z = MathHelper.clamp(z, -1, 1);

		return ((int) (x * 127) & 255) | (((int) (y * 127) & 255) << 8) | (((int) (z * 127) & 255) << 16);
	}

	/**
	 * Version of {@link #packNormal(float, float, float)} that accepts a vector type.
	 */
	public static int packNormal(Vec3f normal) {
		return packNormal(normal.getX(), normal.getY(), normal.getZ());
	}

	/**
	 * Retrieves values packed by {@link #packNormal(float, float, float, float)}.
	 *
	 * <p>Components are x, y, z - zero based.
	 */
	public static float getPackedNormalComponent(int packedNormal, int component) {
		return ((byte) ((packedNormal >>> (8 * component)) & 0xFF)) / 127f;
	}

	/**
	 * This format is easier to convert to a float vector in GLSL when received as a 32-bit int.
	 */
	public static int shaderPackedNormal(int packedNormal) {
		final int x = ((byte) (packedNormal & 0xFF)) + 127;
		final int y = ((byte) ((packedNormal >>> 8) & 0xFF)) + 127;
		final int z = ((byte) ((packedNormal >>> 16) & 0xFF)) + 127;
		return x | (y << 8) | (z << 16);
	}

	/**
	 * Computes the face normal of the given quad and saves it in the provided non-null vector.
	 * If {@link QuadView#nominalFace()} is set will optimize by confirming quad is parallel to that
	 * face and, if so, use the standard normal for that face direction.
	 *
	 * <p>Will work with triangles also. Assumes counter-clockwise winding order, which is the norm.
	 * Expects convex quads with all points co-planar.
	 */
	public static void computeFaceNormal(Vec3f saveTo, QuadView q) {
		final Direction nominalFace = q.nominalFace();

		if (GeometryHelper.isQuadParallelToFace(nominalFace, q)) {
			final Vec3i vec = nominalFace.getVector();
			saveTo.set(vec.getX(), vec.getY(), vec.getZ());
			return;
		}

		final float x0 = q.x(0);
		final float y0 = q.y(0);
		final float z0 = q.z(0);
		final float x1 = q.x(1);
		final float y1 = q.y(1);
		final float z1 = q.z(1);
		final float x2 = q.x(2);
		final float y2 = q.y(2);
		final float z2 = q.z(2);
		final float x3 = q.x(3);
		final float y3 = q.y(3);
		final float z3 = q.z(3);

		final float dx0 = x2 - x0;
		final float dy0 = y2 - y0;
		final float dz0 = z2 - z0;
		final float dx1 = x3 - x1;
		final float dy1 = y3 - y1;
		final float dz1 = z3 - z1;

		float normX = dy0 * dz1 - dz0 * dy1;
		float normY = dz0 * dx1 - dx0 * dz1;
		float normZ = dx0 * dy1 - dy0 * dx1;

		final float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

		if (l != 0) {
			normX /= l;
			normY /= l;
			normZ /= l;
		}

		saveTo.set(normX, normY, normZ);
	}
}
