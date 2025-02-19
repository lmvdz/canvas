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

package grondag.canvas.terrain.occlusion.camera;

import grondag.canvas.terrain.occlusion.base.AbstractRegionVisibility;
import grondag.canvas.terrain.region.RenderRegion;

public class CameraRegionVisibility extends AbstractRegionVisibility<CameraVisibility, CameraRegionVisibility> {
	public CameraRegionVisibility(CameraVisibility visibility, RenderRegion region) {
		super(visibility, region);
	}

	/**
	 * Accepts the squared chunk distance of the region from which this region was reached.
	 * If this region's distance is less than the input distance, it will not be added.
	 *
	 * <p>This prevents the addition of invisible regions that "backtrack" during camera iteration.
	 * We know such regions must be invisible because camera terrain iteration always proceeds in
	 * near-to-far order and if the region was visible from a nearer region, then that region
	 * would have already been added and checked.
	 *
	 * <p>If we are going backwards, then this region is not visible from a nearer region,
	 * which means all nearer regions must fully occlude it, and we are "wrapping around"
	 * from a more distance region.
	 */
	public void addIfValid(int fromSquaredDistance) {
		if (region.origin.squaredCameraChunkDistance() >= fromSquaredDistance) {
			this.addIfValid();
		}
	}

	@Override
	public void addIfValid() {
		if (region.origin.isPotentiallyVisibleFromCamera() && !region.isClosed() && region.isNearOrHasLoadedNeighbors()) {
			addVisitedIfNotPresent();
		}
	}
}
