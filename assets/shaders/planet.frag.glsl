#version 140
uniform float FACTOR;
uniform float FREQUENCY;
uniform float AMPLITUDE;
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
    float d1 = (0.5 + (0.5 * fbm(vec4(vertexPos.xyz * 10.0, TIME * timeScale *  0.0003), 2)));
    float d2 = 0.5 + 0.5 * voronoise(vec4(vertexPos.xyz * 10.0, TIME * timeScale *  0.0005), 0.5);
    float d3 = 0.5 + 0.5 * voronoise(vec4(vertexPos.xzy * 40.0, TIME * timeScale *  0.0005), 0.5);

    //higher scale details
    // TODO instead of adding noise layers, scale existing layers depending on the size of the star
    //    float d4 = 0.5 + 0.5 * voronoise(vec4(vertexPos.xyz * 0.01, TIME * timeScale *  0.00001), 0.02);
    //    float d5 = 0.8 * (0.5 + (fbm(vec4(vertexPos.xyz * 1000.0, TIME * timeScale *  3), 0.0001)));
    //    float d6 = 0.8 * (0.5 + (fbm(vec4(vertexPos.xyz * 1000.0, TIME * timeScale *  0.6), 0.001)));

    fragColor.rgb =  color.rgb * (d1 + 0.25) * 0.5 * d3 * d2+ (d2*d3*d1) * 0.1;
    fragColor.a = 1.0;
}
