#ifndef GLSL_LIB_SHADOWMAP_FRAG
#define GLSL_LIB_SHADOWMAP_FRAG

#ifdef shadowMapFlag
#define bias 0.03
uniform sampler2D u_shadowTexture;
#ifdef shadowMapGlobalFlag
uniform sampler2D u_shadowTextureGlobal;
#endif //shadowMapGlobalFlag
uniform float u_shadowPCFOffset;

float getShadowness(sampler2D sampler, vec2 uv, vec2 offset, float compare) {
    const vec4 bitShifts = vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0);
    return step(compare, dot(texture(sampler, uv.xy + offset), bitShifts) + bias); // (1.0/255.0)
}

#ifdef numCSM
// CASCADED SHADOW MAPS.
uniform mat4 u_csmTransforms[numCSM];
uniform sampler2D u_csmSamplers[numCSM];
uniform float u_csmClip[numCSM];
uniform float u_csmPCF;
int layer = -1;

float getCSMShadow(sampler2D sampler, vec3 uv, float pcf) {
    return (
    getShadowness(sampler, uv.xy, vec2(pcf,pcf), uv.z) +
    getShadowness(sampler, uv.xy, vec2(-pcf,pcf), uv.z) +
    getShadowness(sampler, uv.xy, vec2(pcf,-pcf), uv.z) +
    getShadowness(sampler, uv.xy, vec2(-pcf,-pcf), uv.z) ) * 0.25;
}

float getShadow(vec3 shadowMapUv, vec3 lightSpacePos[numCSM], float fragDepth) {
    layer = -1;
    for (int i = 0; i < numCSM; ++i) {
        // Compare depth value with layer boundary.
        if (fragDepth < u_csmClip[i]) {
            layer = i;
            break;
        }
    }
    if (layer == -1) {
        return 1.0;
    } else {
        // Projected coordinates.
        return getCSMShadow(u_csmSamplers[layer], lightSpacePos[layer], u_csmPCF);
    }

    //for(int i = 0; i < numCSM; i++){
    //    vec2 pcfClip = u_csmPCFClip[i];
    //    float pcf = pcfClip.x;
    //    float clip = pcf * 3.0;
    //    vec3 uv = csmUVs[i];
    //    if(uv.x >= clip && uv.x <= 1.0 - clip &&
    //        uv.y >= clip && uv.y <= 1.0 - clip &&
    //        uv.z >= 0.0 && uv.z <= 1.0){

    //        #if numCSM > 0
    //        if(i == 0) return getCSMShadow(u_csmSamplers[0], uv, pcf);
    //        #endif
    //        #if numCSM > 1
    //        if(i == 1) return getCSMShadow(u_csmSamplers[1], uv, pcf);
    //        #endif
    //        #if numCSM > 2
    //        if(i == 2) return getCSMShadow(u_csmSamplers[2], uv, pcf);
    //        #endif
    //        #if numCSM > 3
    //        if(i == 3) return getCSMShadow(u_csmSamplers[3], uv, pcf);
    //        #endif
    //        #if numCSM > 4
    //        if(i == 4) return getCSMShadow(u_csmSamplers[4], uv, pcf);
    //        #endif
    //        #if numCSM > 5
    //        if(i == 5) return getCSMShadow(u_csmSamplers[5], uv, pcf);
    //        #endif
    //        #if numCSM > 6
    //        if(i == 6) return getCSMShadow(u_csmSamplers[6], uv, pcf);
    //        #endif
    //        #if numCSM > 7
    //        if(i == 7) return getCSMShadow(u_csmSamplers[7], uv, pcf);
    //        #endif

    //    }
    //}
    //// Use default map as a last resort.
    //return getCSMShadow(u_shadowTexture, shadowMapUv, u_shadowPCFOffset);
}

#else
// REGULAR SHADOW MAPS.

float textureShadowLerp(sampler2D sampler, vec2 size, vec2 uv, float compare){
    vec2 texelSize = vec2(1.0) / size;
    vec2 f = fract(uv * size + 0.5);
    vec2 centroidUV = floor(uv * size + 0.5) / size;

    float lb = getShadowness(sampler, centroidUV, texelSize * vec2(0.0, 0.0), compare);
    float lt = getShadowness(sampler, centroidUV, texelSize * vec2(0.0, 1.0), compare);
    float rb = getShadowness(sampler, centroidUV, texelSize * vec2(1.0, 0.0), compare);
    float rt = getShadowness(sampler, centroidUV, texelSize * vec2(1.0, 1.0), compare);
    float a = mix(lb, lt, f.y);
    float b = mix(rb, rt, f.y);
    float c = mix(a, b, f.x);
    return c;
}

float getShadowSingular(sampler2D shadowMap, vec3 shadowMapUv) {
    // Complex lookup: PCF + interpolation (see http://codeflow.org/entries/2013/feb/15/soft-shadow-mapping/)
    vec2 size = vec2(1.0 / (2.0 * u_shadowPCFOffset));
    float result = 0.0;
    for(int x=-2; x<=2; x++) {
        for(int y=-2; y<=2; y++) {
            vec2 offset = vec2(float(x), float(y)) / size;
            result += textureShadowLerp(shadowMap, size, shadowMapUv.xy + offset, shadowMapUv.z);
        }
    }
    return result / 25.0;

    // Simple lookup
    //return getShadowness(u_shadowTexture, shadowMapUv.xy, vec2(0.0), shadowMapUv.z);
}

#ifdef shadowMapGlobalFlag
// With global shadow map
float getShadow(vec3 shadowMapUv, vec3 shadowMapUvGlobal) {
    float individual = getShadowSingular(u_shadowTexture, shadowMapUv);
    float global = getShadowSingular(u_shadowTextureGlobal, shadowMapUvGlobal);
    return individual * global;

    // Simple lookup
    //return getShadowness(u_shadowTexture, shadowMapUv.xy, vec2(0.0), shadowMapUv.z);
}
#else
float getShadow(vec3 shadowMapUv) {
    return getShadowSingular(u_shadowTexture, shadowMapUv);

    // Simple lookup
    //return getShadowness(u_shadowTexture, shadowMapUv.xy, vec2(0.0), shadowMapUv.z);
}
#endif // shadowMapGlobalFlag

#endif // numCSM

#endif // shadowMapFlag
#endif // GLSL_LIB_SHADOWMAP_FRAG