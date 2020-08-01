#version 140
uniform float FACTOR;
uniform float FREQUENCY;
uniform float AMPLITUDE;
uniform float SEED;
uniform float TEMPERATURE;
uniform vec2 RESOLUTION;
uniform vec2 SIZE;
uniform vec3 CAM_DIR;
uniform vec3 CAM_POS;
uniform mat4 VIEW_PROJECTION;
uniform vec3 ROTATION;
uniform sampler2D SPECTRUM;

uniform int TIME;

in vec4 vertexPos;
in vec4 basePos;
out vec4 fragColor;

#include "shaders/libs/noise.glsl"
#include "shaders/libs/utils.glsl"

void main()
{
    vec4 color = texture2D(SPECTRUM, vec2(TEMPERATURE / 40000.0, 0.1));

    vec4 pos = vertexPos;
    float dist = smoothstep(0.1, 0.0, length(pos.xyz));

    float d = length(pos.xyz);
    float x = dot(normalize(pos.xy), vec2(0.5, 0.0));
    float y = dot(normalize(pos.xy), vec2(0., 0.5));
    float timeScale = 0;
    float spike = voronoise(vec4(d + x * 4.0 * SEED + ROTATION.x, ROTATION.y, d + y * 4.0 + ROTATION.z, TIME * timeScale  *  0.00005 * (SEED * 10)), 0.5);
    //    spike = fbm(vec4(d + y * 4, d + x * 4.0, 1.0, TIME * timeScale * 0.0001), 0.3);
    spike            = smoothstep(-0.05, 1.595, spike);
    spike = mix(spike, (smoothstep(0., 2.0, dist) + 0.2 * fbm(vec4(pos.xy * 100, (TIME * timeScale * 0.001), 1.0), 0.50)), dist * 0.6);
    fragColor.rgba = vec4(spike * dist * mix(color, vec4(1.0), dist * 0.8));
}
