#version 330 core

#include shader/lib_logdepthbuff.glsl

uniform float u_alpha;
uniform float u_zfar;
uniform float u_k;
uniform float u_coordPos;

in vec4 v_col;
in float v_coord;

layout (location = 0) out vec4 fragColor;

#ifdef velocityBufferFlag
#include shader/lib_velbuffer.frag.glsl
#endif

void main() {
    float trail = v_coord - u_coordPos;
    if (trail < 0.0) {
        trail += 1.0;
    }
    fragColor = vec4(v_col.rgb * u_alpha * trail, 1.0);
    gl_FragDepth = getDepthValue(u_zfar, u_k);

    #ifdef velocityBufferFlag
    velocityBuffer();
    #endif
}
