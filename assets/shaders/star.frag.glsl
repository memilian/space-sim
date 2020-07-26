#version 140
uniform float FACTOR;
uniform float FREQUENCY;
uniform float AMPLITUDE;
uniform float TEMPERATURE;
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
    vec4 color = texture2D(SPECTRUM, vec2(TEMPERATURE / 40000.0, 0.1));
    float d1 = (0.5 + (0.5 * fbm(vec4(vertexPos.xyz * 10.0, TIME * timeScale *  0.0003), 2)));
    float d2 = 0.5 + 0.5 * voronoise(vec4(vertexPos.xyz * 10.0, TIME * timeScale *  0.0005), 0.5);
    float d3 = 0.5 + 0.5 * voronoise(vec4(vertexPos.xzy * 40.0, TIME * timeScale *  0.0005), 0.5);
    fragColor.rgb =  color.rgb * (d1 + 0.25) * d3 * d2+ (d2*d3*d1) * 0.1;
    fragColor.rgb *= 1.0 +(1 - smoothstep(0, 40000, TEMPERATURE)) * 3;
    fragColor *= mix(1.2, 0.48, 1 - TEMPERATURE / 40000);
    fragColor.a = 1.0;
}
