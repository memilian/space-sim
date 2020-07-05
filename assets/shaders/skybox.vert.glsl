#version 140
in vec4 POSITION_ATTRIBUTE;
in vec2 TEXCOORD_ATTRIBUTE0;
uniform mat4 WORLD;
uniform mat4 VIEW_PROJECTION;

out vec2 uvs;

void main() {
    mat4 mvp = VIEW_PROJECTION * WORLD;
    uvs = TEXCOORD_ATTRIBUTE0;
    gl_Position = mvp * POSITION_ATTRIBUTE;
}