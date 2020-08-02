float mod289(float x){ return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x){ return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 perm(vec4 x){ return mod289(((x * 34.0) + 1.0) * x); }

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


float fbm(in vec3 p, float fre)
{
    float res = 0.0, amp = 0.1, div = 0.0;
    for (int i = 0; i < 5; ++i)
    {
        res += amp * noise((p + p * i) * fre);
        div += amp;
        amp *= 0.54;
        fre *= 1.7;
        p = p * 1.4;
    }
    res /= div;
    return res;
}

vec3 random3(vec3 p) {
    return fract(sin(vec3(dot(p, vec3(127.1, 311.7, 427.9)), dot(p, vec3(269.5, 183.3, 333.21)), dot(p, vec3(269.5, 183.3, 179.21))))*43758.5453);
}

float voronoise(vec3 point, float scale){
    vec3 a = floor(point / scale);
    vec3 b = fract(point / scale);

    float m_dist = 1.0;// minimun distance
    for (int y= -1; y <= 1; y++) {
        for (int x= -1; x <= 1; x++) {
            for (int z= -1; z <= 1; z++) {
                // Neighbor place in the grid
                vec3 neighbor = vec3(float(x), float(y), float(z));

                // Random position from current + neighbor place in the grid
                vec3 point = random3(a + neighbor);

                // Animate the point
                //point = 0.5 + 0.5*sin(TIME * 0.001 + 6.2831*point);

                // Vector between the pixel and the point
                vec3 diff = neighbor + point - b;

                // Distance to the point
                float dist = length(diff);

                // Keep the closer distance
                m_dist = min(m_dist, dist);
            }
        }
    }
    return m_dist;
}

vec4 random4(vec4 p) {
    return fract(sin(vec4(
    dot(p, vec4(127.1, 311.7, 427.9, 721.15)),
    dot(p, vec4(269.5, 183.3, 333.21, 777.23)),
    dot(p, vec4(469.5, 387.3, 444.72, 794.23)),
    dot(p, vec4(65.5, 813.3, 179.21, 312.94))))*43758.5453);
}

float voronoise(vec4 point, float scale){
    vec4 a = floor(point / scale);
    vec4 b = fract(point / scale);

    float m_dist = 1.0;// minimun distance
    for (int y= -1; y <= 1; y++) {
        for (int x= -1; x <= 1; x++) {
            for (int z= -1; z <= 1; z++) {
                for (int w= -1; w <= 1; w++) {
                    // Neighbor place in the grid
                    vec4 neighbor = vec4(float(x), float(y), float(z), float(w));

                    // Random position from current + neighbor place in the grid
                    vec4 point = random4(a + neighbor);

                    // Vector between the pixel and the point
                    vec4 diff = neighbor + point - b;

                    // Distance to the point
                    float dist = length(diff);

                    // Keep the closer distance
                    m_dist = min(m_dist, dist);
                }
            }
        }
    }
    return m_dist;
}


//	Simplex 4D Noise
//	by Ian McEwan, Ashima Arts
//
vec4 permute(vec4 x){ return mod(((x*34.0)+1.0)*x, 289.0); }
float permute(float x){ return floor(mod(((x*34.0)+1.0)*x, 289.0)); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314 * r; }
float taylorInvSqrt(float r){ return 1.79284291400159 - 0.85373472095314 * r; }

vec4 grad4(float j, vec4 ip){
    const vec4 ones = vec4(1.0, 1.0, 1.0, -1.0);
    vec4 p, s;

    p.xyz = floor(fract (vec3(j) * ip.xyz) * 7.0) * ip.z - 1.0;
    p.w = 1.5 - dot(abs(p.xyz), ones.xyz);
    s = vec4(lessThan(p, vec4(0.0)));
    p.xyz = p.xyz + (s.xyz*2.0 - 1.0) * s.www;

    return p;
}

float noise(vec4 v){
    const vec2  C = vec2(0.138196601125010504, // (5 - sqrt(5))/20  G4
    0.309016994374947451);// (sqrt(5) - 1)/4   F4
    // First corner
    vec4 i  = floor(v + dot(v, C.yyyy));
    vec4 x0 = v -   i + dot(i, C.xxxx);

    // Other corners

    // Rank sorting originally contributed by Bill Licea-Kane, AMD (formerly ATI)
    vec4 i0;

    vec3 isX = step(x0.yzw, x0.xxx);
    vec3 isYZ = step(x0.zww, x0.yyz);
    //  i0.x = dot( isX, vec3( 1.0 ) );
    i0.x = isX.x + isX.y + isX.z;
    i0.yzw = 1.0 - isX;

    //  i0.y += dot( isYZ.xy, vec2( 1.0 ) );
    i0.y += isYZ.x + isYZ.y;
    i0.zw += 1.0 - isYZ.xy;

    i0.z += isYZ.z;
    i0.w += 1.0 - isYZ.z;

    // i0 now contains the unique values 0,1,2,3 in each channel
    vec4 i3 = clamp(i0, 0.0, 1.0);
    vec4 i2 = clamp(i0-1.0, 0.0, 1.0);
    vec4 i1 = clamp(i0-2.0, 0.0, 1.0);

    //  x0 = x0 - 0.0 + 0.0 * C
    vec4 x1 = x0 - i1 + 1.0 * C.xxxx;
    vec4 x2 = x0 - i2 + 2.0 * C.xxxx;
    vec4 x3 = x0 - i3 + 3.0 * C.xxxx;
    vec4 x4 = x0 - 1.0 + 4.0 * C.xxxx;

    // Permutations
    i = mod(i, 289.0);
    float j0 = permute(permute(permute(permute(i.w) + i.z) + i.y) + i.x);
    vec4 j1 = permute(permute(permute(permute (
    i.w + vec4(i1.w, i2.w, i3.w, 1.0))
    + i.z + vec4(i1.z, i2.z, i3.z, 1.0))
    + i.y + vec4(i1.y, i2.y, i3.y, 1.0))
    + i.x + vec4(i1.x, i2.x, i3.x, 1.0));
    // Gradients
    // ( 7*7*6 points uniformly over a cube, mapped onto a 4-octahedron.)
    // 7*7*6 = 294, which is close to the ring size 17*17 = 289.

    vec4 ip = vec4(1.0/294.0, 1.0/49.0, 1.0/7.0, 0.0);

    vec4 p0 = grad4(j0, ip);
    vec4 p1 = grad4(j1.x, ip);
    vec4 p2 = grad4(j1.y, ip);
    vec4 p3 = grad4(j1.z, ip);
    vec4 p4 = grad4(j1.w, ip);

    // Normalise gradients
    vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
    p0 *= norm.x;
    p1 *= norm.y;
    p2 *= norm.z;
    p3 *= norm.w;
    p4 *= taylorInvSqrt(dot(p4, p4));

    // Mix contributions from the five corners
    vec3 m0 = max(0.6 - vec3(dot(x0, x0), dot(x1, x1), dot(x2, x2)), 0.0);
    vec2 m1 = max(0.6 - vec2(dot(x3, x3), dot(x4, x4)), 0.0);
    m0 = m0 * m0;
    m1 = m1 * m1;
    return 49.0 * (dot(m0*m0, vec3(dot(p0, x0), dot(p1, x1), dot(p2, x2)))
    + dot(m1*m1, vec2(dot(p3, x3), dot(p4, x4))));

}


float fbm(in vec4 p, float fre)
{
    float res = 0.0, amp = 0.1, div = 0.0;
    for (int i = 0; i < 5; ++i)
    {
        res += amp * noise((p + p * i) * fre);
        div += amp;
        amp *= 0.54;
        fre *= 1.7;
        p = p * 1.4;
    }
    res /= div;
    return res;
}


//no position shifting
float fbm2(in vec4 p, float fre)
{
    float res = 0.0, amp = 0.1, div = 0.0;
    for (int i = 0; i < 5; ++i)
    {
        res += amp * noise((p) * fre);
        div += amp;
        amp *= 0.54;
        fre *= 1.7;
        p = p * 1.4;
    }
    res /= div;
    return res;
}