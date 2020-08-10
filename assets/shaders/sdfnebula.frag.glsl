#version 410
uniform float SIZE;
uniform float FACTOR;
uniform float FREQUENCY;
uniform float AMPLITUDE;
uniform float ZONE_RADIUS;
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
out vec4 fragColor;

float freq = FREQUENCY;

#include "shaders/libs/intersectors.glsl"
#include "shaders/libs/noise.glsl"
#include "shaders/libs/utils.glsl"


struct DensityInfo{
    float density;
    float material;
};

float fbm(in vec3 p)
{
    return fbm(p, FREQUENCY);
}

    #define STEPS 10
    #define FAR 25
    #define DELTA_STEP 2.5
DensityInfo accumulate(vec3 rd, vec3 pos, float steps, float deltaStep){
    DensityInfo result = DensityInfo(0.0, 0.0);
    vec3 cur = vec3(pos);
    float i = 0.0;
    float cd = 0.0;

    for (i = 0.0; i < steps; i++){
        cd =  fbm(cur);
        cd -=  pow(voronoise(cur, 4.0 + noise(cur * 0.008) * 0.1), 10.2) * 1.0;
        cd -=  noise((cur + vec3(pos * 0.7)) * 0.010) * 0.965;
        result.density += cd;
        result.material += noise((cur + vec3(pos * 0.77)) * 0.0565);
        cur +=  rd * deltaStep;
    }
    result.density /= (max(1, i));
    result.material /= (max(1, i));

    return result;
}

DensityInfo accumulate(vec3 rd, vec3 p){
    return accumulate(rd, p, STEPS, DELTA_STEP);
}

float accumulateVor(vec3 rd, vec3 pos, float scale){
    /*  float d = 0.0;
      vec3 cur = vec3(pos);
      float t = 0.0;
      for (int i = 0; i < 10; i++){
          cur = pos + rd * t;
          d += voronoise(cur, scale) / (10);
          t += 4.52521;
          if (d > 1.0 || t > 50) break;
      }*/// FIXME

    return 0.5;
}

vec4 tracePatch(Ray ray, Plane pl, float size){
    float dist = 0.0;
    vec3 isec = isecPlane(ray, pl, dist);
    //    float toCenter = length(isec - pl.o);
    float toCenter = distToLine(vec3(isec.xy, 0), project(pl, vec3(0, 0.0, 0.0)), project(pl, vec3(1, 1, 0)));
    float radius = size + fbm(isec, 0.5) * 13;
    vec4 color = vec4(0.0);
    if (toCenter < radius &&  dist > 0){
        vec3 point = isec + ray.d * toCenter;
        float warpFreq = 0.005;
        float warpAmount = 5.;
        vec3 warp = vec3(warpAmount * fbm(isec, warpFreq), warpAmount * fbm(isec * 7.0, warpFreq), warpAmount * fbm(isec * -3, warpFreq));
        float density = accumulate(ray.d, point * warp).density;

        float vor = voronoise(point * warp * 0.5, 30);
        float value = clamp(pow(density * (vor * vor + 0.3), 1 - vor) - fbm(point + 113, 0.1) * 0.5, 0.0, 1.0);

        color = vec4(value);
        // fade near border
        color.a *= smoothstep(0, size * 0.25, radius - toCenter);
        //fade near camera
        float toCam = length(ray.o - isec);
        color.a *= smoothstep(0, size * 0.5, toCam);
    }
    return color;
}

vec4 traceSphere(Ray ray, vec3 sc, float r, float fade){
    float b = dot(ray.o, ray.d);
    float c = dot(ray.o, ray.o) - r * r;
    float h = b*b - c;
    if (h<0.0 || (b+h) < 0.) return vec4(0.);// no intersections
    h = sqrt(h);
    vec3 isec = ray.o + ray.d * (-b+h);
    vec3 isecIn = ray.o + ray.d * (-b-h);
    vec4 color = vec4(accumulate(ray.d, isec).density);
    float distTraversed = length(isec - isecIn);
    color.a *= smoothstep(r * 0.5, r + fbm(ray.d + 0.001 * TIME, FACTOR) * fade * 4, distTraversed);
    //fade near camera
    color.a *= smoothstep(0, r, length(ray.o - isec));
    return color;
}

void traceScene(Ray ray)
{
    float topLimit = 10.0;
    vec3 center = vec3(0.0, 0.0, topLimit);
    vec3 norm = normalize(vec3(0.0, 0.0, 1.0));
    Plane plane = Plane(center, normalize(norm));

    float planeDist = 0.0;
    vec3 start = isecPlane(ray, plane, planeDist);

    vec4 col = vec4(0.0);

    float dist = 0.0;
    //switch view when camera origin is below the plane
    float viewSwitch = 1.0 - smoothstep(0.0, 0.0, (start + ray.d * planeDist).z);
    dist = abs(planeDist) * viewSwitch;
    //if (viewSwitch == .0) discard;

    vec3 usedPos = vec3(0.0);

    for (int i = 0; i < 256; i++){

        vec3 pos = ray.o + dist * ray.d;
        float t = sdCylinder(pos, vec3(0, 0, -10), vec3(0, 0, topLimit), ZONE_RADIUS);
        if (t < 1.0){
            float toCenter = length(pos);
            if (ray.o.z < topLimit){
                pos += smoothstep(0.0, 15.0, topLimit - ray.o.z)* 45.0 * ray.d;
            }
            usedPos = pos;

            DensityInfo di = accumulate(ray.d, pos, 25.0, 1.0);

            //float vor = accumulateVor(ray.d, pos, 10.0);
            float density = pow(di.density * 1.0, 0.5);

            /*
            float warp = fbm(pos * 17, 0.000010 * fbm(pos * 0.5, 0.0137));
            pos *= warp;
            pos += vec3(cos(TIME * 0.0003));
            float d = accumulate(ray.d, pos, 250.0, 20.0, 2.75);
            //float vor = accumulateVor(ray.d, pos, 10.0);
            float density = pow(d * 0.7, 0.5);
            density += fbm(usedPos * 7, 0.01) * 0.5;
            float neg = fbm(pos + vec3(7770.0), 0.05);
            density = smoothstep(0.0, 0.9, neg) * density;
*/
            col = vec4(density);
            /*
            vec3 variation1 = vec3(0.);
            variation1.r = noise(usedPos.xyz * 0.0051);
            variation1.g = noise(usedPos.zyx * 0.0051);
            variation1.b = noise(usedPos.yxz * 0.0051);
            vec3 variation = .70 * variation1;
            col.rgb *= mix(1.6 * PRIMARY_COLOR.rgb * variation, 1.6 * SECONDARY_COLOR.rgb * variation.gbr, min(max(0.0, di.material), 1.0));
            */
            col.rgb *= mix(PRIMARY_COLOR.rgb, SECONDARY_COLOR.rgb, min(max(0.0, di.material), 1.0));

            col.a *= 1 - smoothstep(ZONE_RADIUS - 100.0, ZONE_RADIUS, toCenter);

            break;
        }
        dist += t;
    }

    //    vec4 col = 1.3 * traceSphere(ray, vec3(0.), 20., 15.0);

    fragColor = col * 1.3;
}


void main()
{
    vec3 ro = CAM_POS;//ray's origin
    vec3 f = far.xyz/far.w;
    vec3 rd = f - ro;
    rd = normalize(rd);//ray's direction
    Ray ray = Ray(ro, rd);
    traceScene(ray);
}
