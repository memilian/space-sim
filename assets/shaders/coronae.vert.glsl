#version 140
in vec4 POSITION_ATTRIBUTE;
in vec3 NORMAL_ATTRIBUTE;
in vec2 TEXCOORD_ATTRIBUTE0;

uniform mat4 WORLD;
uniform mat4 VIEW_PROJECTION;
uniform mat4 PROJECTION;
uniform mat4 VIEW;
uniform vec3 CENTER;

uniform vec3 CAM_RIGHT;
uniform vec3 CAM_UP;
uniform vec3 CAM_INV_DIR;

uniform vec2 SIZE;
uniform vec2 RESOLUTION;

out vec4 vertexPos;
out vec4 basePos;

void main() {
    vertexPos = POSITION_ATTRIBUTE * 1.0;

    vec4 view_pos = VIEW * WORLD * vec4(CENTER, 1.0);
    vec2 size = SIZE * 10.0;
    // size *= -view_pos.z; // Fixed size on screen
    gl_Position = PROJECTION * (view_pos + vec4(vertexPos.xy * size, 0, 0));
    basePos = gl_Position;
}
