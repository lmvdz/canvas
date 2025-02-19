#include frex:shaders/api/context.glsl

/******************************************************
  canvas:shaders/internal/vertex.glsl
******************************************************/
#ifdef VERTEX_SHADER

// Same as default but region is looked up based on a vertex attribute.
// This avoid a uniform update per draw call.
#ifdef _CV_VERTEX_TERRAIN

uniform int[182] _cvu_sectors_int;

in ivec4 in_region;
in ivec4 in_blockpos;
in vec4 in_color;
in vec2 in_uv;
in vec2 in_lightmap;
in int in_material;
in vec3 in_normal;
in float in_ao;

vec3 in_vertex;

void _cv_prepareForVertex() {
	int packedSector = _cvu_sectors_int[in_region.x >> 1];
	packedSector = (in_region.x & 1) == 1 ? ((packedSector >> 16) & 0xFFFF) : (packedSector & 0xFFFF);

	// These are relative to the sector origin, which will be near the camera position
	vec3 origin = vec3(((packedSector & 0xF) - 5) * 128, ((packedSector >> 4) & 0xF) * 128 - 64, (((packedSector >> 8) & 0xF) - 5) * 128);

	// Add intra-sector block pos and fractional block pos
	in_vertex = origin + in_region.yzw / 65535.0 + in_blockpos.xyz - 63;
}
#endif

#ifdef _CV_VERTEX_DEFAULT
in vec3 in_vertex;
in vec4 in_color;
in vec2 in_uv;
in vec2 in_lightmap;
in int in_material;
in vec3 in_normal;
in float in_ao;

void _cv_prepareForVertex() { }
#endif

#endif
