#version 140
uniform float SIZE;
uniform float FACTOR;
uniform float FREQUENCY;
uniform float AMPLITUDE;
uniform vec3 CENTER;
uniform vec2 RESOLUTION;
uniform vec3 CAM_POS;
uniform vec3 CAM_DIR;
uniform mat4 WORLD;
uniform mat4 VIEW;
uniform mat4 PROJECTION;
uniform mat4 VIEW_PROJECTION;
uniform mat4 ROTATION;
uniform int TIME;
uniform vec4 PRIMARY_COLOR;
uniform vec4 SECONDARY_COLOR;

in vec4 near;
in vec4 far;
in vec2 uv;
in mat4 mvp;
in vec4 vertexPos;
in vec4 vertexPosView;
out vec4 fragColor;

#include "shaders/libs/noise.glsl"
#include "shaders/libs/utils.glsl"


vec2 boxIntersection(in vec3 ro, in vec3 rd, in vec3 rad, out vec3 oN)
{
    vec3 m = 1.0/rd;
    vec3 n = m*ro;
    vec3 k = abs(m)*rad;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;

    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);

    if (tN>tF || tF<0.0) return vec2(-1.0);// no intersection

    oN = -sign(rd)*step(t1.yzx, t1.xyz)*step(t1.zxy, t1.xyz);

    return vec2(tN, tF);
}

// Calcs intersection and exit distances, normal, face and UVs
// row is the ray origin in world space
// rdw is the ray direction in world space
// txx is the world-to-box transformation
// txi is the box-to-world transformation
// ro and rd are in world space
// rad is the half-length of the box
//
// oT contains the entry and exit points
// oN is the normal in world space
// oU contains the UVs at the intersection point
// oF contains the index if the intersected face [0..5]
bool boxIntersect(in vec3 row, in vec3 rdw, in mat4 txx, in mat4 txi, in vec3 rad,
out vec2 oT, out vec3 oN, out vec2 oU, out int oF)
{
    // convert from world to box space
    vec3 rd = (txx*vec4(rdw, 0.0)).xyz;
    vec3 ro = (txx*vec4(row, 1.0)).xyz;


    // ray-box intersection in box space
    vec3 m = 1.0/rd;
    vec3 s = vec3((rd.x<0.0)?1.0:-1.0,
    (rd.y<0.0)?1.0:-1.0,
    (rd.z<0.0)?1.0:-1.0);
    vec3 t1 = m*(-ro + s*rad);
    vec3 t2 = m*(-ro - s*rad);

    float tN = max(max(t1.x, t1.y), t1.z);
    float tF = min(min(t2.x, t2.y), t2.z);

    if (tN>tF || tF<0.0) return false;

    // compute normal (in world space), face and UV
    if (t1.x>t1.y && t1.x>t1.z) {
        oN=txi[0].xyz*s.x; oU=ro.yz+rd.yz*t1.x; oF=(1+int(s.x))/2;
    }
    else if (t1.y>t1.z) {
        oN=txi[1].xyz*s.y; oU=ro.zx+rd.zx*t1.y; oF=(5+int(s.y))/2;
    }
    else {
        oN=txi[2].xyz*s.z; oU=ro.xy+rd.xy*t1.z; oF=(9+int(s.z))/2;
    }

    oT = vec2(tN, tF);

    return true;
}

vec2 sphereIntersect(in vec3 ro, in vec3 rd, in vec3 ce, float ra)
{
    vec3 oc = ro;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - ra*ra;
    float h = b*b - c;
    if (h<0.0) return vec2(-1.0);// no intersection
    h = sqrt(h);
    return vec2(-b-h, -b+h);
}

    #define STEPS 5
    #define FAR 20
float sphereTrace(vec3 ro, vec3 rd)
{

    float t = 0.05, maxD = 0.0, d = 1.0;
    float den = 0.0;
    float deltaStep = 1.0;
    vec3 cur = ro;
    int i = 0;
    for (i = 0; i < STEPS; i++){
        float cd =  fbm(cur, FREQUENCY);
        cd -=  pow(voronoise(cur, 4.0 + noise(cur * 0.008) * 0.1), 10.2) * 1.0;
        cd -=  noise((cur + vec3(ro * 0.7)) * 0.010) * 0.965;
        den += cd;
        cur +=  rd * deltaStep;
    }
    den /= (max(1, i));

    return den;
}

vec3 GetNormal(vec3 p, vec3 rd) {
    float d = sphereTrace(p, rd);
    vec2 e = vec2(.01, 0);

    vec3 n = d - vec3(
    sphereTrace(p-e.xyy, rd),
    sphereTrace(p-e.yxy, rd),
    sphereTrace(p-e.yyx, rd));

    return normalize(n);
}

//( in vec3 row, in vec3 rdw, in mat4 txx, in mat4 txi, in vec3 rad,
//                   out vec2 oT, out vec3 oN, out vec2 oU, out int oF )
void main()
{
    vec3 ro = near.xyz/near.w;//ray's origin
    vec3 f = far.xyz/far.w;
    vec3 rd = f - ro;
    rd = normalize(rd);//ray's direction
    vec2 isect = sphereIntersect(vertexPos.xyz, rd, CENTER, SIZE * 0.9);
    if (isect.x == -1 && isect.y == -1){
        discard;
    }
    vec3 iin = vertexPos.xyz + rd * isect.x;
    vec3 iout = vertexPos.xyz + rd * isect.y;

    float density = 0.0;
    density = sphereTrace(iout + ro, rd);
    density = clamp(density, 0.0, 1.0);
    //reduce luminosity
    density = pow(density, 2.5);
    fragColor = vec4(density);

    //attenuate at the edge of sphere

    vec3 diff = iin - iout;
    float disp = (sin(noise(vertexPos.xyz)) + 1.0) * 0.1;
    float din = clamp(length(diff * 0.6) / SIZE, 0.0, 1.0);
    //    din += noise(iin);
    fragColor.a = din * din;// clamp(din * density, 0.0, 1.0);ss
    //fade near camera
    float dist = length(CAM_POS - CENTER - vertexPos.xyz) - SIZE * 0.5;
    //    fragColor = heatMap(dist, 0.0, 1.0);
    fragColor.a *= smoothstep(0.0, SIZE, dist);
    //    fragColor.rgb *= 1 - (min(0.86, smoothstep(100.0, 500, dist)));
    //    fragColor.rgb = fragColor.rgb * 2 - 1.0;
    fragColor.rgb *= vec3(1.1, 0.7, noise(vertexPos.xyz) * 0.2 + 0.5);
}
