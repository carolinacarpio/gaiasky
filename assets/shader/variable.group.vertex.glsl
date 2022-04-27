#version 330 core

#define N_VECS 5

#include shader/lib_math.glsl
#include shader/lib_geometry.glsl
#include shader/lib_doublefloat.glsl

// UNIFORMS
// time in julian days since epoch, as a 64-bit double encoded with two floats
uniform vec2 u_t;
// time in julian days since variablity epoch
uniform float u_s;
uniform mat4 u_projView;
uniform vec3 u_camPos;
uniform vec3 u_camDir;
uniform int u_cubemap;
uniform vec2 u_opacityLimits;
uniform vec2 u_thAnglePoint;
uniform float u_brightnessPower;
// VR scale factor
uniform float u_vrScale;
// x - alpha
// y - point size/fov factor
// z - star brightness
// w - rc primitive scale factor
uniform vec4 u_alphaSizeBrRc;

// INPUT
layout (location=0) in vec3 a_position;
layout (location=1) in vec3 a_pm;
layout (location=2) in vec4 a_color;
layout (location=3) in float a_nVari;
// Magnitudes
layout (location=4) in vec4 a_vmags1;
layout (location=5) in vec4 a_vmags2;
layout (location=6) in vec4 a_vmags3;
layout (location=7) in vec4 a_vmags4;
layout (location=8) in vec4 a_vmags5;
// Times
layout (location=9) in vec4 a_vtimes1;
layout (location=10) in vec4 a_vtimes2;
layout (location=11) in vec4 a_vtimes3;
layout (location=12) in vec4 a_vtimes4;
layout (location=13) in vec4 a_vtimes5;

// OUTPUT
out vec4 v_col;

#ifdef relativisticEffects
#include shader/lib_relativity.glsl
#endif // relativisticEffects

#ifdef gravitationalWaves
#include shader/lib_gravwaves.glsl
#endif // gravitationalWaves

#define LEN0 20000.0
#define DAY_TO_YEAR 1.0 / 365.25

#ifdef velocityBufferFlag
#include shader/lib_velbuffer.vert.glsl
#endif

float idx(vec4[N_VECS] v, int i) {
    int a = int(i / 4);
    int b = i - a * 4;
    return v[a][b];
}

void main() {
	// Lengths
	float l0 = LEN0 * u_vrScale;
	float l1 = l0 * 1e3;

    vec3 pos = a_position - u_camPos;

    // Proper motion using 64-bit emulated arithmetics:
    // pm = a_pm * t * DAY_TO_YEAR
    // pos = pos + pm
    vec2 t_yr = ds_mul(u_t, ds_set(DAY_TO_YEAR));
    vec2 pmx = ds_mul(ds_set(a_pm.x), t_yr);
    vec2 pmy = ds_mul(ds_set(a_pm.y), t_yr);
    vec2 pmz = ds_mul(ds_set(a_pm.z), t_yr);
    pos.x = ds_add(ds_set(pos.x), pmx).x;
    pos.y = ds_add(ds_set(pos.y), pmy).x;
    pos.z = ds_add(ds_set(pos.z), pmz).x;
    // Pm for use downstream
    vec3 pm = vec3(pmx.x, pmy.x, pmz.x);

    // Distance to star
    float dist = length(pos);

    float cubemapFactor = 1.0;
    if (u_cubemap == 1) {
        // Cosine of angle between star position and camera direction
        // Correct point primitive size error due to perspective projection
        float cosphi = pow(dot(u_camDir, pos) / dist, 2.0);
        cubemapFactor = 1.0 - cosphi * 0.65;
    }

    #ifdef relativisticEffects
    	pos = computeRelativisticAberration(pos, dist, u_velDir, u_vc);
    #endif // relativisticEffects

    #ifdef gravitationalWaves
        pos = computeGravitationalWaves(pos, u_gw, u_gwmat3, u_ts, u_omgw, u_hterms);
    #endif // gravitationalWaves

    vec4[N_VECS] mags;
    mags[0] = a_vmags1;
    mags[1] = a_vmags2;
    mags[2] = a_vmags3;
    mags[3] = a_vmags4;
    mags[4] = a_vmags5;
    vec4[N_VECS] times;
    times[0] = a_vtimes1;
    times[1] = a_vtimes2;
    times[2] = a_vtimes3;
    times[3] = a_vtimes4;
    times[4] = a_vtimes5;

    // Linear interpolation of time in light curve
    int nVari = int(a_nVari);
    float t0 = idx(times, 0);
    float t1 = idx(times, nVari - 1);
    float period = t1 - t0;
    float t = mod(u_s, period);
    float size = idx(mags, 0);
    for (int i = 0; i < nVari - 1; i++) {
        float x0 = idx(times, i) - t0;
        float x1 = idx(times, i+1) - t0;
        if (t >= x0 && t <= x1) {
            size = lint(t, x0, x1, idx(mags, i), idx(mags, i+1));
            break;
        } else {
            // Next
        }
    }

    float solidAngle = atan((size * u_alphaSizeBrRc.z) / dist);
    float opacity = lint(solidAngle, u_thAnglePoint.x, u_thAnglePoint.y, u_opacityLimits.x, u_opacityLimits.y);
    float boundaryFade = smoothstep(l0, l1, dist);
    v_col = vec4(a_color.rgb, clamp(opacity * u_alphaSizeBrRc.x * boundaryFade, 0.0, 1.0));

    vec4 gpos = u_projView * vec4(pos, 1.0);
    gl_Position = gpos;
    gl_PointSize = max(3.3 * u_alphaSizeBrRc.w, pow(solidAngle * .5e8, u_brightnessPower) * u_alphaSizeBrRc.y * cubemapFactor);

    #ifdef velocityBufferFlag
    velocityBuffer(gpos, a_position, dist, pm, vec2(500.0, 3000.0), 1.0);
    #endif

    if (dist < l0) {
        // The pixels of this star will be discarded in the fragment shader
        v_col = vec4(0.0, 0.0, 0.0, 0.0);
    }
}
