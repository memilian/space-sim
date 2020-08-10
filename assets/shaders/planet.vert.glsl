#version 140
in vec4 POSITION_ATTRIBUTE;
in vec3 NORMAL_ATTRIBUTE;
in vec2 TEXCOORD_ATTRIBUTE0;

uniform mat4 WORLD;
uniform mat4 VIEW_PROJECTION;
uniform float FCOEF;

out vec4 vertexPos;
out vec3 normal;
out vec2 uv;

void main() {
    uv = TEXCOORD_ATTRIBUTE0;
    normal = NORMAL_ATTRIBUTE;
    vertexPos = POSITION_ATTRIBUTE * WORLD;
    mat4 mvp  = VIEW_PROJECTION * WORLD;
    gl_Position = mvp * POSITION_ATTRIBUTE;
    gl_Position.z = log2(max(1e-6, 1.0 + gl_Position.w)) * FCOEF - 1.0;
}
