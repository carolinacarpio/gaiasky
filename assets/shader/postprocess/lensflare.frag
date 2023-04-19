#version 330 core
// Simple lens flare implementation by Toni Sagrista

uniform sampler2D u_texture0;

// Viewport dimensions along X and Y
uniform vec2 u_viewport;
uniform float u_intensity;
uniform vec2 u_lightPosition;
uniform vec3 u_color;

in vec2 v_texCoords;
layout (location = 0) out vec4 fragColor;

vec3 lensflare(vec2 uv, vec2 pos, float intensity) {
    vec2 main = uv - pos;
    vec2 uvd = uv * (length(uv));

    float dist = length(main);
    dist = pow(dist, .1);

    float f1 = max(0.01-pow(length(uv+1.2*pos), 1.9), .0)*7.0;

    float f2 = max(1.0/(1.0+32.0*pow(length(uvd+0.8*pos), 2.0)), .0)*00.1;
    float f22 = max(1.0/(1.0+32.0*pow(length(uvd+0.85*pos), 2.0)), .0)*00.08;
    float f23 = max(1.0/(1.0+32.0*pow(length(uvd+0.9*pos), 2.0)), .0)*00.06;

    vec2 uvx = mix(uv, uvd, -0.5);

    float f4 = max(0.01-pow(length(uvx+0.4*pos), 2.4), .0)*6.0;
    float f42 = max(0.01-pow(length(uvx+0.47*pos), 2.4), .0)*5.0;
    float f43 = max(0.01-pow(length(uvx+0.54*pos), 2.4), .0)*3.0;

    uvx = mix(uv, uvd, -.4);

    float f5 = max(0.01-pow(length(uvx+0.2*pos), 5.5), .0)*2.0;
    float f52 = max(0.01-pow(length(uvx+0.4*pos), 5.5), .0)*2.0;
    float f53 = max(0.01-pow(length(uvx+0.6*pos), 5.5), .0)*2.0;

    uvx = mix(uv, uvd, -0.5);

    float f6 = max(0.01-pow(length(uvx-0.3*pos), 1.6), .0)*6.0;
    float f62 = max(0.01-pow(length(uvx-0.325*pos), 1.6), .0)*3.0;
    float f63 = max(0.01-pow(length(uvx-0.35*pos), 1.6), .0)*5.0;

    vec3 c = vec3(.0);

    c.r += f2+f4+f5+f6;
    c.g += f22+f42+f52+f62;
    c.b += f23+f43+f53+f63;
    c = c*1.3 - vec3(length(uvd)*.05);

    return c * intensity;
}

vec3 cc(vec3 color, float factor, float factor2) {
    float w = color.x+color.y+color.z;
    return mix(color, vec3(w)*factor, w*factor2);
}

float fx(float t, float a) {
    return a * t * cos(t);
}

float fy(float t, float a) {
    return a * t * sin(t);
}

#define N_SAMPLES 5
void main(void) {
    if (u_intensity > 0.0) {
        vec2 uv = v_texCoords - 0.5;
        float ar = u_viewport.x / u_viewport.y;
        uv.x *= ar;
        vec2 lpos = u_lightPosition;

        // Compute intensity of light.
        float t = 0;
        float a = 6.0e-4;
        float dt = 3.0 * 3.14159 / N_SAMPLES;
        float lum = 0.0;
        for (int idx = 0; idx < N_SAMPLES; idx++){
            vec2 curr_coord = clamp(lpos + vec2(fx(t, a) / ar, fy(t, a)), 0.0, 1.0);
            lum += (clamp(texture(u_texture0, curr_coord), 0.0, 1.0)).r;
            t += dt;
        }
        lum /= N_SAMPLES;

        if (u_intensity * lum > 0.0) {
            vec3 color = u_color * lensflare(uv, lpos - 0.5, u_intensity * lum);
            color = cc(color, .5, .1) + texture(u_texture0, v_texCoords).rgb;
            fragColor = vec4(color, 1.0);
        } else {
            fragColor = texture(u_texture0, v_texCoords);
        }
    } else {
        fragColor = texture(u_texture0, v_texCoords);
    }
}