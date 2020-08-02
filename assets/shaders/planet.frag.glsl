#version 140
uniform float FACTOR;
uniform float FREQUENCY;
uniform float AMPLITUDE;
uniform float SEED;
uniform vec2 RESOLUTION;
uniform vec3 CAM_DIR;
uniform vec3 CAM_POS;
uniform mat4 VIEW_PROJECTION;
uniform sampler2D SPECTRUM;

uniform int TIME;

in vec4 vertexPos;
in vec3 normal;
in vec2 uv;
out vec4 fragColor;

#include "shaders/libs/noise.glsl"
#include "shaders/libs/utils.glsl"

void main()
{
    float timeScale = 0.55;
    vec4 color = vec4(0.5);
    float d1 = fbm(vec4(vertexPos.xyz * 1.0 + SEED * 1.1, 1.0), 1.1);
    float d2 = abs(voronoise(vec4(vertexPos.xyz * 5.0 + SEED * 5.0, 1.0), 0.5));
    float d3 = 0.5 + 0.5 * voronoise(vec4(vertexPos.xzy * 20.0 + SEED * 20, 1.0), 0.5);

    float h = abs(d1*  2.6 + d2 * 0.2) * d3;

    h = clamp(h, 0, 1);
    vec3 col = vec3(h);
    vec3 water = vec3(0.2, 0.2, 1.0);
    vec3 sand = vec3(1.0, 1.0, 0.2);
    vec3 grass = vec3(0.3, 1, 0.3);
    vec3 hills = vec3(0.5, 0.5, 0.5);
    vec3 mountains = vec3(0.2, 0.2, 0.2);
    vec3 snow = vec3(0.8, 0.8, 0.8);

    col = mix(water, sand, h);
    if (h < 0.3){
        col = water;
    } else if (h < 0.4){
        col = sand;
    } else if (h < 0.6){
        col = grass;
    } else if (h < 0.8){
        col = hills;
    } else if (h < 0.9999){
        col = mountains;
    } else {
        col = snow;
    }
    fragColor.rgb = col * 0.9;
    fragColor.a = 1.0;
}
