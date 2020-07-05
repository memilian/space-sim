#version 140

in vec2 uvs;
out vec4 fragColor;
uniform sampler2D FACE_TEXTURE;

#include "shaders/libs/utils.glsl"

void main()
{
    fragColor = vec4(texture2D(FACE_TEXTURE, uvs).rgb, 1.0);
}
