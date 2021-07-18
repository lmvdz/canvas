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

package grondag.canvas.render.region.vbo;

import grondag.canvas.buffer.StaticDrawBuffer;
import grondag.canvas.buffer.TransferBuffer;
import grondag.canvas.buffer.TransferBuffers;
import grondag.canvas.buffer.format.CanvasVertexFormats;
import grondag.canvas.buffer.input.VertexCollectorList;
import grondag.canvas.render.region.DrawableRegion;
import grondag.canvas.render.region.UploadableRegion;

public class VboUploadableRegion implements UploadableRegion {
	protected final StaticDrawBuffer vboBuffer;
	protected final DrawableRegion drawable;

	public VboUploadableRegion(VertexCollectorList collectorList, boolean sorted, int bytes, long packedOriginBlockPos) {
		TransferBuffer buffer = TransferBuffers.claim(bytes);
		vboBuffer = new StaticDrawBuffer(CanvasVertexFormats.STANDARD_MATERIAL_FORMAT, buffer);
		assert vboBuffer.capacityBytes() >= buffer.sizeBytes();
		drawable = VboDrawableRegion.pack(collectorList, buffer, vboBuffer, sorted, bytes, packedOriginBlockPos);
	}

	@Override
	public DrawableRegion produceDrawable() {
		vboBuffer.upload();
		return drawable;
	}
}
