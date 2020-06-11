package nebulae.rendering.shaders

val noiseFuncs = """
float mod289(float x){return x - floor(x * (1.0 / 289.0)) * 289.0;}
vec4 mod289(vec4 x){return x - floor(x * (1.0 / 289.0)) * 289.0;}
vec4 perm(vec4 x){return mod289(((x * 34.0) + 1.0) * x);}

float noise(vec3 p){
    vec3 a = floor(p);
    vec3 d = p - a;
    d = d * d * (3.0 - 2.0 * d);

    vec4 b = a.xxyy + vec4(0.0, 1.0, 0.0, 1.0);
    vec4 k1 = perm(b.xyxy);
    vec4 k2 = perm(k1.xyxy + b.zzww);

    vec4 c = k2 + a.zzzz;
    vec4 k3 = perm(c);
    vec4 k4 = perm(c + 1.0);

    vec4 o1 = fract(k3 * (1.0 / 41.0));
    vec4 o2 = fract(k4 * (1.0 / 41.0));

    vec4 o3 = o2 * d.z + o1 * (1.0 - d.z);
    vec2 o4 = o3.yw * d.x + o3.xz * (1.0 - d.x);

    return o4.y * d.y + o4.x * (1.0 - d.y);
}

float fbm( in vec3 p )
{
    float res = 0.0, fre = 3.50, amp = 1.0, div = 0.0;
    for( int i = 0; i < 5; ++i )
    {
        res += amp * noise( p * fre );
        div += amp;
        amp *= 0.7;
        fre *= 1.7;
    }
    res /= div;
    return res;
}

"""

val intersectionFuncs = """
vec2 boxIntersection( in vec3 ro, in vec3 rd, in vec3 rad, out vec3 oN )
{
    vec3 m = 1.0/rd;
    vec3 n = m*ro;
    vec3 k = abs(m)*rad;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;

    float tN = max( max( t1.x, t1.y ), t1.z );
    float tF = min( min( t2.x, t2.y ), t2.z );

    if( tN>tF || tF<0.0) return vec2(-1.0); // no intersection

    oN = -sign(rd)*step(t1.yzx,t1.xyz)*step(t1.zxy,t1.xyz);

    return vec2( tN, tF );
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
bool boxIntersect( in vec3 row, in vec3 rdw, in mat4 txx, in mat4 txi, in vec3 rad,
out vec2 oT, out vec3 oN, out vec2 oU, out int oF )
{
    // convert from world to box space
    vec3 rd = (txx*vec4(rdw,0.0)).xyz;
    vec3 ro = (txx*vec4(row,1.0)).xyz;


    // ray-box intersection in box space
    vec3 m = 1.0/rd;
    vec3 s = vec3((rd.x<0.0)?1.0:-1.0,
    (rd.y<0.0)?1.0:-1.0,
    (rd.z<0.0)?1.0:-1.0);
    vec3 t1 = m*(-ro + s*rad);
    vec3 t2 = m*(-ro - s*rad);

    float tN = max( max( t1.x, t1.y ), t1.z );
    float tF = min( min( t2.x, t2.y ), t2.z );

    if( tN>tF || tF<0.0) return false;

    // compute normal (in world space), face and UV
    if( t1.x>t1.y && t1.x>t1.z ) {
        oN=txi[0].xyz*s.x; oU=ro.yz+rd.yz*t1.x; oF=(1+int(s.x))/2;
    }
    else if( t1.y>t1.z) {
        oN=txi[1].xyz*s.y; oU=ro.zx+rd.zx*t1.y; oF=(5+int(s.y))/2;
    }
    else {
        oN=txi[2].xyz*s.z; oU=ro.xy+rd.xy*t1.z; oF=(9+int(s.z))/2;
    }

    oT = vec2(tN,tF);

    return true;
}

vec2 sphereIntersect( in vec3 ro, in vec3 rd, in vec3 ce, float ra )
{
    vec3 oc = ro;
    float b = dot( oc, rd );
    float c = dot( oc, oc ) - ra*ra;
    float h = b*b - c;
    if( h<0.0 ) return vec2(-1.0); // no intersection
    h = sqrt( h ); 
    return vec2( -b-h, -b+h );
}

"""

val utils = """
vec4 heatMap(float value, float minValue, float maxValue)
{
    vec4 colors[6] = vec4[6](
            vec4(0.32, 0.00, 0.32, 1.00),
            vec4(0.00, 0.00, 1.00, 1.00),
            vec4(0.00, 1.00, 0.00, 1.00),
            vec4(1.00, 1.00, 0.00, 1.00),
            vec4(1.00, 0.60, 0.00, 1.00),
            vec4(1.00, 0.00, 0.00, 1.00)
    );
    float ratio=(6.0-1.0) * clamp((value-minValue)/(maxValue-minValue), 0.0, 1.0);
    float indexMin=floor(ratio);
    float indexMax=min(indexMin + 1.0,6.0-1.0);
    highp int index = int(indexMin);
    highp int imax = int(indexMax);
    return mix(colors[index], colors[imax], ratio-indexMin);
}
"""