mat4 translate(vec3 pos)
{
    return mat4(1.0, 0.0, 0.0, 0.0,
    0.0, 1.0, 0.0, 0.0,
    0.0, 0.0, 1.0, 0.0,
    pos.x, pos.y, pos.z, 1.0);
}


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
    float indexMax=min(indexMin + 1.0, 6.0-1.0);
    highp int index = int(indexMin);
    highp int imax = int(indexMax);
    return mix(colors[index], colors[imax], ratio-indexMin);
}


vec4 grid(vec3 v, float eps){
    vec4 color = vec4(0.0);
    if (abs(fract(v.x * 10)) < eps){
        color=vec4(1.0, 0, 0, 1);
    }
    if (abs(fract(v.y * 10)) < eps){
        color=vec4(0, 1, 0, 1.0);
    }
    if (abs(fract(v.z * 10)) < eps){
        color=vec4(0, 0, 1, 1.0);
    }
    return color;
}

vec4 coords(vec3 v, float eps){
    vec4 color = vec4(0.0);
    if (abs(v.x) < eps){
        color=vec4(1.0, 0, 0, 1);
    }
    if (abs(v.y) < eps){
        color=vec4(0, 1, 0, 1.0);
    }
    if (abs(v.z) < eps){
        color=vec4(0, 0, 1, 1.0);
    }
    return color;
}
