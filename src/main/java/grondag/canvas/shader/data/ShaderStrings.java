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

package grondag.canvas.shader.data;

import net.minecraft.util.Identifier;

public final class ShaderStrings {
	public static final Identifier DEFAULT_VERTEX_SOURCE = new Identifier("canvas:shaders/material/default.vert");
	public static final Identifier DEFAULT_FRAGMENT_SOURCE = new Identifier("canvas:shaders/material/default.frag");

	public static final Identifier MATERIAL_MAIN_VERTEX = new Identifier("canvas:shaders/internal/material_main.vert");
	public static final Identifier MATERIAL_MAIN_FRAGMENT = new Identifier("canvas:shaders/internal/material_main.frag");

	public static final Identifier DEPTH_MAIN_VERTEX = new Identifier("canvas:shaders/internal/shadow_main.vert");
	public static final Identifier DEPTH_MAIN_FRAGMENT = new Identifier("canvas:shaders/internal/shadow_main.frag");

	public static final String API_TARGET = "#include canvas:apitarget";
	public static final String FRAGMENT_START = "#include canvas:startfragment";
	public static final String VERTEX_START = "#include canvas:startvertex";

	private ShaderStrings() { }
}
