#version 330 core

#include <shader/lib/logdepthbuff.glsl>

uniform float u_alpha;
uniform float u_zfar;
uniform float u_k;
uniform float u_coordEnabled;
uniform float u_trailMap;
uniform float u_trailMinOpacity;
uniform float u_coordPos;
uniform float u_period;
uniform float u_lineWidth;
uniform float u_blendFactor;

in vec4 v_col;
in float v_coord;
in vec2 v_lineCenter;

// We use the location of the layer buffer (1).
layout (location = 1) out vec4 layerBuffer;

#ifdef ssrFlag
#include <shader/lib/ssr.frag.glsl>
#endif// ssrFlag

void main() {
    float trail;
    if (u_coordEnabled > 0.0) {
        if (u_period > 0.0) {
            trail = v_coord - u_coordPos;
            if (trail < 0.0) {
                trail += 1.0;
            }
            trail = min(trail + u_trailMinOpacity, 1.0);
        } else if (v_coord <= u_coordPos) {
            // Non-periodic lines, before the object.
            trail = min(v_coord / u_coordPos + u_trailMinOpacity, 1.0);
        } else {
            // We are past the object in non-periodic orbits.
            trail = 0.0;
        }
        if (u_trailMap >= 1.0) {
            // We map to zero, always.
            trail = 0.0;
        } else {
            trail = (trail - u_trailMap) / (1.0 - u_trailMap);
        }
    } else {
        // We assume a periodic orbit.
        trail = 1.0;
    }

    if (u_alpha <= 0.0 || trail <= 0.0) {
        discard;
    }

    vec4 col = v_col;
    if (u_lineWidth > 0.0) {
        // We do aliasing here!
        float d = length(v_lineCenter - gl_FragCoord.xy);
        float w = u_lineWidth;
        if (d > w) {
            discard;
        } else {
            col.rgb *= pow((w - d) / w, u_blendFactor);
        }
    }

    layerBuffer = vec4(col.rgb * u_alpha * trail, 1.0);
    gl_FragDepth = getDepthValue(u_zfar, u_k);

    #ifdef ssrFlag
    ssrBuffers();
    #endif// ssrFlag
}
