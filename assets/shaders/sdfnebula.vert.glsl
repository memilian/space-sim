#version 140
in vec4 POSITION_ATTRIBUTE;
in vec2 TEXCOORD_ATTRIBUTE0;
uniform mat4 WORLD;
uniform mat4 VIEW;
uniform mat4 PROJECTION;
uniform mat4 VIEW_PROJECTION;
uniform mat4 ROTATION;
uniform vec3 CAM_POS;
uniform vec3 CAM_DIR;

out vec4 near;
out vec4 far;
out mat4 mvp;
out vec4 vertexPos;
out vec2 uv;

void main() {
    uv = TEXCOORD_ATTRIBUTE0;
    vertexPos = POSITION_ATTRIBUTE;
    mvp  = VIEW_PROJECTION * WORLD;
    gl_Position = POSITION_ATTRIBUTE;

    mat4 mvpi = inverse(mvp);
    //2D projection of vertex in NDC
    vec2 pos = gl_Position.xy / gl_Position.w;
    //Ray start and end
    near =  mvpi * vec4(pos, -1.0, 1.0);
    far = mvpi * vec4(pos, 1.0, 1.0);
}
