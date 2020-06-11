//from https://www.iquilezles.org/www/articles/intersectors/intersectors.htm


struct Ray{
    vec3 o;// origin
    vec3 d;// direction
};

struct Plane{
    vec3 o;// origin
    vec3 n;// normal
};


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

// cylinder defined by extremes pa and pb, and radius ra
// return vec4 dist nx ny nz
vec4 cylIntersect(in Ray ray, in vec3 pa, in vec3 pb, float ra)
{
    vec3 ca = pb-pa;
    vec3 oc = ray.o-pa;
    float caca = dot(ca, ca);
    float card = dot(ca, ray.d);
    float caoc = dot(ca, oc);
    float a = caca - card*card;
    float b = caca*dot(oc, ray.d) - caoc*card;
    float c = caca*dot(oc, oc) - caoc*caoc - ra*ra*caca;
    float h = b*b - a*c;
    if (h<0.0) return vec4(-1.0);//no intersection
    h = sqrt(h);
    float t = (-b-h)/a;
    // body
    float y = caoc + t*card;
    if (y>0.0 && y<caca) return vec4(t, (oc+t*ray.d-ca*y/caca)/ra);
    // caps
    t = (((y<0.0)?0.0:caca) - caoc)/card;
    if (abs(b+a*t)<h) return vec4(t, ca*sign(y)/caca);
    return vec4(-1.0);//no intersection
}

// pos bottomCap topCap radius
float sdCylinder(vec3 p, vec3 a, vec3 b, float r)
{
    vec3  ba = b - a;
    vec3  pa = p - a;
    float baba = dot(ba, ba);
    float paba = dot(pa, ba);
    float x = length(pa*baba-ba*paba) - r*baba;
    float y = abs(paba-baba*0.5)-baba*0.5;
    float x2 = x*x;
    float y2 = y*y*baba;

    float d = (max(x, y)<0.0)?-min(x2, y2):(((x>0.0)?x2:0.0)+((y>0.0)?y2:0.0));

    return sign(d)*sqrt(abs(d))/baba;
}


vec3 isecPlane(Ray ray, Plane pl, out float dist) {
    float denom = dot(ray.d, pl.n);
    dist = dot(pl.o - ray.o, pl.n) / denom;
    return ray.o + ray.d * dist;
}

float distToLine(vec3 point, vec3 from, vec3 to){
    vec3 dir = normalize(from - to);
    vec3 ptf = from - point;
    float d = length(ptf - dot(dir, ptf) * dir);
    return d;
}

float distToLine(vec2 point, vec2 from, vec2 to){
    vec2 dir = normalize(from - to);
    vec2 ptf = from - point;
    float d = length(ptf - dot(dir, ptf) * dir);
    return d;
}

vec3 project(Plane pl, vec3 point){
    return point - dot(pl.n, point - pl.o) * pl.n;
    //    return vec3(
    //    dot(point - pl.o, pl.x),
    //    dot(point - pl.o, cross(pl.n, pl.x)),
    //    0
    //    );
}
