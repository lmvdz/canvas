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

package grondag.canvas.material.property;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;

import grondag.canvas.CanvasMod;
import grondag.canvas.render.CanvasTextureState;
import grondag.canvas.texture.MaterialIndexProvider;
import grondag.canvas.texture.SpriteIndex;
import grondag.canvas.texture.TextureData;
import grondag.canvas.varia.GFX;

public class MaterialTextureState {
	public final int index;
	public final Identifier id;

	private AbstractTexture texture;
	private boolean isAtlas;
	private SpriteFinder spriteFinder;
	private SpriteIndex atlasInfo;
	private MaterialIndexProvider donglenator;

	private MaterialTextureState(int index, Identifier id) {
		this.index = index;
		this.id = id;
	}

	private void retreiveTexture() {
		if (texture == null) {
			final TextureManager tm = MinecraftClient.getInstance().getTextureManager();
			// forces registration
			tm.bindTexture(id);
			texture = tm.getTexture(id);
			isAtlas = texture != null && texture instanceof SpriteAtlasTexture;

			if (isAtlas) {
				atlasInfo = SpriteIndex.getOrCreate(id);
				donglenator = MaterialIndexProvider.getOrCreateForAtlas(id);
				spriteFinder = SpriteFinder.get((SpriteAtlasTexture) texture);
			} else {
				atlasInfo = null;
				donglenator = MaterialIndexProvider.GENERIC;
			}
		}
	}

	public SpriteFinder spriteFinder() {
		retreiveTexture();
		return spriteFinder;
	}

	public MaterialIndexProvider materialIndexProvider() {
		retreiveTexture();
		return donglenator;
	}

	public AbstractTexture texture() {
		retreiveTexture();
		return texture;
	}

	public boolean isAtlas() {
		retreiveTexture();
		return isAtlas;
	}

	public SpriteIndex atlasInfo() {
		retreiveTexture();
		return atlasInfo;
	}

	public void enable(boolean bilinear) {
		// Note we don't call texture enable anywhere here - it only applies to fixed function pipeline

		if (activeState == this) {
			if (bilinear != activeIsBilinearFilter) {
				CanvasTextureState.activeTextureUnit(TextureData.MC_SPRITE_ATLAS);
				CanvasTextureState.bindTexture(texture().getGlId());
				setFilter(bilinear);
				activeIsBilinearFilter = bilinear;
			}
		} else {
			if (this == MaterialTextureState.NO_TEXTURE) {
				CanvasTextureState.activeTextureUnit(TextureData.MC_SPRITE_ATLAS);
				CanvasTextureState.bindTexture(0);
			} else {
				CanvasTextureState.activeTextureUnit(TextureData.MC_SPRITE_ATLAS);
				CanvasTextureState.bindTexture(texture().getGlId());
				setFilter(bilinear);

				activeIsBilinearFilter = bilinear;
				activeState = this;
			}
		}
	}

	/**
	 * Vanilla logic for texture filtering works like this:
	 *
	 * <p><pre>
	 * 	int minFilter;
	 * 	int magFilter;
	 *
	 * 	if (bilinear) {
	 * 		minFilter = mipmap ? GL21.GL_LINEAR_MIPMAP_LINEAR : GL21.GL_LINEAR;
	 * 		magFilter = GL21.GL_LINEAR;
	 * 	} else {
	 * 		minFilter = mipmap ? GL21.GL_NEAREST_MIPMAP_LINEAR : GL21.GL_NEAREST;
	 * 		magFilter = GL21.GL_NEAREST;
	 * 	}
	 *
	 * 	GlStateManager.texParameter(GL21.GL_TEXTURE_2D, GL21.GL_TEXTURE_MIN_FILTER, minFilter);
	 * 	GlStateManager.texParameter(GL21.GL_TEXTURE_2D, GL21.GL_TEXTURE_MAG_FILTER, magFilter);
	 * </pre>
	 *
	 * <p>In vanilla,"bilinear" enables blur on magnification, makes minification blur LOD-linear.
	 * It seems to only be used only be used for enchantment glint.
	 *
	 * <p>The vanilla mipmap parameter enables minification blur (linear with nearest LOD).
	 * Vanilla does not use GL_LINEAR_MIPMAP_LINEAR for most rendering because atlas textures
	 * cause artifacts when sampled across LODS levels, especially for randomized rotated sprites
	 * like grass.
	 *
	 * <p>Canvas controlled mipmap in shader, so we always set that to GL_NEAREST_MIPMAP_LINEAR unless
	 * bilinear filtering is needed.
	 */
	private static void setFilter(boolean bilinear) {
		if (bilinear) {
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MIN_FILTER, GFX.GL_LINEAR_MIPMAP_LINEAR);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MAG_FILTER, GFX.GL_LINEAR);
		} else {
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MIN_FILTER, GFX.GL_NEAREST_MIPMAP_LINEAR);
			GFX.texParameter(GFX.GL_TEXTURE_2D, GFX.GL_TEXTURE_MAG_FILTER, GFX.GL_NEAREST);
		}
	}

	public static final int MAX_TEXTURE_STATES = 4096;
	private static int nextIndex = 1;
	private static final MaterialTextureState[] STATES = new MaterialTextureState[MAX_TEXTURE_STATES];
	private static final Object2ObjectOpenHashMap<Identifier, MaterialTextureState> MAP = new Object2ObjectOpenHashMap<>(256, Hash.VERY_FAST_LOAD_FACTOR);

	public static final MaterialTextureState NO_TEXTURE = new MaterialTextureState(0, TextureManager.MISSING_IDENTIFIER) {
		@Override
		public void enable(boolean bilinear) {
			if (activeState != this) {
				activeState = this;
			}
		}
	};

	public static final MaterialTextureState MISSING;

	private static MaterialTextureState activeState = NO_TEXTURE;
	private static boolean activeIsBilinearFilter = false;

	public static void disable() {
		if (activeState != null) {
			activeState = null;
		}
	}

	static {
		STATES[0] = NO_TEXTURE;
		MAP.defaultReturnValue(NO_TEXTURE);
		MISSING = fromId(TextureManager.MISSING_IDENTIFIER);
	}

	public static MaterialTextureState fromIndex(int index) {
		return STATES[index];
	}

	private static boolean shouldWarn = true;

	// PERF: use cow or other method to avoid synch
	public static synchronized MaterialTextureState fromId(Identifier id) {
		MaterialTextureState state = MAP.get(id);

		if (state == NO_TEXTURE) {
			if (nextIndex >= MAX_TEXTURE_STATES) {
				if (shouldWarn) {
					shouldWarn = false;
					CanvasMod.LOG.warn(String.format("Maximum unique textures (%d) exceeded when attempting to add %s.  Missing texture will be used.",
							MAX_TEXTURE_STATES, id.toString()));
					CanvasMod.LOG.warn("Previously encountered textures are listed below. Subsequent warnings are suppressed.");

					for (final MaterialTextureState extant : STATES) {
						CanvasMod.LOG.info(extant == null ? "Null (this is a bug)" : extant.id.toString());
					}
				}

				return MISSING;
			}

			final int index = nextIndex++;
			state = new MaterialTextureState(index, id);
			MAP.put(id, state);
			STATES[index] = state;
		}

		return state;
	}

	public static void reload() {
		MAP.values().forEach(t -> {
			t.texture = null;
		});
	}
}
