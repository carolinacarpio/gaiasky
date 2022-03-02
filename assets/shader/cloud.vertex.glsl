#version 330 core

#define nop() {}

////////////////////////////////////////////////////////////////////////////////////
////////// POSITION ATTRIBUTE - VERTEX
////////////////////////////////////////////////////////////////////////////////////
#ifdef positionFlag
    in vec3 a_position;
#endif //positionFlag

out vec4 v_position;
#define pushPositionValue(value) v_position = value
#if defined(positionFlag)
    vec4 g_position = vec4(a_position, 1.0);
    #define passPositionValue(value) pushPositionValue(value)
#else
    vec4 g_position = vec4(0.0, 0.0, 0.0, 1.0);
    #define passPositionValue(value) nop()
#endif
#define passPosition() passPositionValue(g_position)
#define pushPosition() pushPositionValue(g_position)


////////////////////////////////////////////////////////////////////////////////////
////////// NORMAL ATTRIBUTE - VERTEX
///////////////////////////////////////////////////////////////////////////////////
#ifdef normalFlag
    in vec3 a_normal;
#endif //normalFlag

out vec3 v_normal;
#define pushNormalValue(value) v_normal = (value)
#if defined(normalFlag)
    vec3 g_normal = a_normal;
    #define passNormalValue(value) pushNormalValue(value)
#else
    vec3 g_normal = vec3(0.0, 0.0, 1.0);
    #define passNormalValue(value) nop()
#endif
#define passNormal() passNormalValue(g_normal)
#define pushNormal() pushNormalValue(g_normal)

////////////////////////////////////////////////////////////////////////////////////
////////// BINORMAL ATTRIBUTE - VERTEX
///////////////////////////////////////////////////////////////////////////////////
#ifdef binormalFlag
    in vec3 a_binormal;
#endif //binormalFlag

out vec3 v_binormal;
#define pushBinormalValue(value) v_binormal = (value)
#if defined(binormalFlag)
    vec3 g_binormal = a_binormal;
    #define passBinormalValue(value) pushBinormalValue(value)
#else
    vec3 g_binormal = vec3(0.0, 1.0, 0.0);
    #define passBinormalValue(value) nop()
#endif // binormalFlag
#define passBinormal() passBinormalValue(g_binormal)
#define pushBinormal() pushBinormalValue(g_binormal)

////////////////////////////////////////////////////////////////////////////////////
////////// TANGENT ATTRIBUTE - VERTEX
///////////////////////////////////////////////////////////////////////////////////
#ifdef tangentFlag
    in vec3 a_tangent;
#endif //tangentFlagvec3

out vec3 v_tangent;
#define pushTangentValue(value) v_tangent = (value)
#if defined(tangentFlag)
    vec3 g_tangent = a_tangent;
    #define passTangentValue(value) pushTangentValue(value)
#else
    vec3 g_tangent = vec3(1.0, 0.0, 0.0);
    #define passTangentValue(value) nop()
#endif // tangentFlag
#define passTangent() passTangentValue(g_tangent)
#define pushTangent() pushTangentValue(g_tangent)

////////////////////////////////////////////////////////////////////////////////////
////////// TEXCOORD0 ATTRIBUTE - VERTEX
///////////////////////////////////////////////////////////////////////////////////
#ifdef texCoord0Flag
    #ifndef texCoordsFlag
	#define texCoordsFlag
    #endif
    in vec2 a_texCoord0;
#endif

out vec2 v_texCoord0;
#define pushTexCoord0(value) v_texCoord0 = value

#if defined(texCoord0Flag)
    vec2 g_texCoord0 = a_texCoord0;
#else
    vec2 g_texCoord0 = vec2(0.0, 0.0);
#endif // texCoord0Flag


//////////////////////////////////////////////////////
////// SHADOW MAPPING
//////////////////////////////////////////////////////
#ifdef shadowMapFlag
uniform sampler2D u_shadowTexture;
uniform float u_shadowPCFOffset;
uniform mat4 u_shadowMapProjViewTrans;

out vec3 v_shadowMapUv;
#endif //shadowMapFlag

// GEOMETRY (QUATERNIONS)
#if defined(velocityBufferFlag) || defined(relativisticEffects)
#include shader/lib_geometry.glsl
#endif

////////////////////////////////////////////////////////////////////////////////////
//////////RELATIVISTIC EFFECTS - VERTEX
////////////////////////////////////////////////////////////////////////////////////
#ifdef relativisticEffects
    #include shader/lib_relativity.glsl
#endif // relativisticEffects


////////////////////////////////////////////////////////////////////////////////////
//////////GRAVITATIONAL WAVES - VERTEX
////////////////////////////////////////////////////////////////////////////////////
#ifdef gravitationalWaves
    #include shader/lib_gravwaves.glsl
#endif // gravitationalWaves

// Uniforms which are always available
uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
uniform vec4 u_cameraPosition;
uniform mat3 u_normalMatrix;
uniform float u_vrScale;

// Other uniforms
out float v_opacity;
#ifdef blendedFlag
    uniform float u_opacity;
#else
    const float u_opacity = 1.0;
#endif

out float v_alphaTest;
#ifdef alphaTestFlag
    uniform float u_alphaTest;
#else
    const float u_alphaTest = 0.0;
#endif

#ifdef diffuseTextureFlag
    uniform sampler2D u_diffuseTexture;
#endif

#ifdef normalTextureFlag
    uniform sampler2D u_normalTexture;
#endif



#if defined(diffuseTextureFlag) || defined(specularTextureFlag)
    #define textureFlag
#endif



#if defined(normalFlag) && defined(binormalFlag) && defined(tangentFlag)
    #define calculateTangentVectors() nop()
#elif defined(normalFlag) && defined(binormalFlag)
    #define calculateTangentVectors() g_tangent = cross(g_normal, g_binormal)
#elif defined(normalFlag) && defined(tangentFlag)
    #define calculateTangentVectors() g_binormal = cross(g_normal, g_tangent)
#elif defined(binormalFlag) && defined(tangentFlag)
    #define calculateTangentVectors() g_normal = cross(g_binormal, g_tangent)
#elif defined(normalFlag) || defined(binormalFlag) || defined(tangentFlag)
    #if defined(normalFlag)
        void calculateTangentVectors() {
            g_binormal = vec3(0, g_normal.z, -g_normal.y);
            g_tangent = normalize(cross(g_normal, g_binormal));
}
    #elif defined(binormalFlag)
        void calculateTangentVectors() {
            g_tangent = vec3(-g_binormal.z, 0, g_binormal.x);
            g_normal = normalize(cross(g_binormal, g_tangent));
        }
    #elif defined(tangentFlag)
        void calculateTangentVectors() {
            g_binormal = vec3(-g_tangent.z, 0, g_tangent.x);
            g_normal = normalize(cross(g_tangent, g_binormal));
        }
    #endif
#else
    #define calculateTangentVectors() nop()
#endif



//////////////////////////////////////////////////////
////// AMBIENT LIGHT
//////////////////////////////////////////////////////
out vec3 v_ambientLight;

#ifdef ambientLightFlag
    #ifndef ambientFlag
	#define ambientFlag
    #endif
    uniform vec3 u_ambientLight;
#endif

#ifdef ambientCubemapFlag
    uniform vec3 u_ambientCubemap[6];
#endif // ambientCubemapFlag 

//////////////////////////////////////////////////////
////// POINTS LIGHTS
//////////////////////////////////////////////////////
#ifdef lightingFlag
    #if defined(numPointLights) && (numPointLights > 0)
	#define pointLightsFlag
    #endif // numPointLights
#endif //lightingFlag

#ifdef pointLightsFlag
    struct PointLight
    {
	vec3 color;
	vec3 position;
	float intensity;
    };
    uniform PointLight u_pointLights[numPointLights];
#endif

//////////////////////////////////////////////////////
////// DIRECTIONAL LIGHTS
//////////////////////////////////////////////////////
#ifdef lightingFlag
#if defined(numDirectionalLights) && (numDirectionalLights > 0)
#define directionalLightsFlag
#endif // numDirectionalLights
#endif //lightingFlag

#ifdef directionalLightsFlag
struct DirectionalLight
{
    vec3 color;
    vec3 direction;
};
uniform DirectionalLight u_dirLights[numDirectionalLights];
#endif

out vec3 v_lightDir;
out vec3 v_lightCol;
out vec3 v_viewDir;

#ifdef velocityBufferFlag
#include shader/lib_velbuffer.vert.glsl
#endif

void main() {
    v_opacity = u_opacity;
    v_alphaTest = u_alphaTest;

    calculateTangentVectors();

    g_normal = normalize(u_normalMatrix * g_normal);
    g_binormal = normalize(u_normalMatrix * g_binormal);
    g_tangent = normalize(u_normalMatrix * g_tangent);

    vec4 pos = u_worldTrans * g_position;

    #ifdef relativisticEffects
        pos.xyz = computeRelativisticAberration(pos.xyz, length(pos.xyz), u_velDir, u_vc);
    #endif // relativisticEffects
    
    #ifdef gravitationalWaves
        pos.xyz = computeGravitationalWaves(pos.xyz, u_gw, u_gwmat3, u_ts, u_omgw, u_hterms);
    #endif // gravitationalWaves
    
    vec4 gpos = u_projViewTrans * pos;
    gl_Position = gpos;

    #ifdef velocityBufferFlag
    vec4 prevPos = pos + vec4(u_prevCamPos, 0.0);
    vec4 gprevpos = u_prevProjView * prevPos;
    v_vel = ((gpos.xy / gpos.w) - (gprevpos.xy / gprevpos.w));
    #endif// velocityBufferFlag

    #ifdef shadowMapFlag
	vec4 spos = u_shadowMapProjViewTrans * pos;
        
	v_shadowMapUv.xyz = (spos.xyz / spos.w) * 0.5 + 0.5;
	//v_shadowMapUv.z = min(spos.z * 0.5 + 0.5, 0.998);
    #endif //shadowMapFlag
    

    mat3 worldToTangent;
    worldToTangent[0] = g_tangent;
    worldToTangent[1] = g_binormal;
    worldToTangent[2] = g_normal;

    #ifdef ambientLightFlag
	v_ambientLight = u_ambientLight;
    #else
	v_ambientLight = vec3(0.0);
    #endif // ambientLightFlag
    
    #ifdef ambientCubemapFlag 		
	vec3 squaredNormal = g_normal * g_normal;
	vec3 isPositive = step(0.0, g_normal);
	v_ambientLight += squaredNormal.x * mix(u_ambientCubemap[0], u_ambientCubemap[1], isPositive.x) +
	squaredNormal.y * mix(u_ambientCubemap[2], u_ambientCubemap[3], isPositive.y) +
	squaredNormal.z * mix(u_ambientCubemap[4], u_ambientCubemap[5], isPositive.z);
    #endif // ambientCubemapFlag

    #ifdef directionalLightsFlag
        v_lightDir = normalize(-u_dirLights[0].direction * worldToTangent);
        v_lightCol = u_dirLights[0].color;
    #else
        v_lightDir = vec3(0.0, 0.0, 0.0);
        v_lightCol = vec3(0.0);
    #endif // directionalLightsFlag
    
    vec3 viewDir = (u_cameraPosition.xyz - pos.xyz);
    v_viewDir = normalize(viewDir * worldToTangent);
    #ifdef environmentCubemapFlag
	v_reflect = reflect(-viewDir, g_normal);
    #endif // environmentCubemapFlag

    pushTexCoord0(g_texCoord0);
}
