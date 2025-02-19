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

package grondag.canvas.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.Shader;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;

import grondag.canvas.material.state.MojangShaderData;
import grondag.canvas.mixinterface.ShaderExt;

@Mixin(Shader.class)
public class MixinShader implements ShaderExt {
	private MojangShaderData canvas_shaderData;

	@Inject(at = @At("RETURN"), method = "<init>*")
	private void onNew(ResourceFactory resourceFactory, String string, VertexFormat vertexFormat, CallbackInfo ci) {
		canvas_shaderData = MojangShaderData.get(string);
	}

	@Override
	public MojangShaderData canvas_shaderData() {
		return canvas_shaderData;
	}
}
