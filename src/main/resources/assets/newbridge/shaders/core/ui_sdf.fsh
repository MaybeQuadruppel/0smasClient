#version 330

// newbridge ClickGUI: signed-distance rounded rects in physical pixels.
// Shape = (local.x, local.y, mode, param), Rect = (halfW, halfH, radius, unused).
// mode 0 = fill, 1 = outline (param = thickness), 2 = glow (param = glow radius).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vColor;
in vec4 vShape;
in vec4 vRect;

out vec4 fragColor;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 hs = vRect.xy;
    float r = min(vRect.z, min(hs.x, hs.y));
    float d = roundBox(vShape.xy, hs, r);
    float mode = vShape.z;
    float param = vShape.w;

    float a;
    if (mode < 0.5) {
        a = clamp(0.5 - d, 0.0, 1.0);
    } else if (mode < 1.5) {
        a = clamp(0.5 - (abs(d + param * 0.5) - param * 0.5), 0.0, 1.0);
    } else {
        float t = clamp(max(d, 0.0) / max(param, 0.001), 0.0, 1.0);
        float f = 1.0 - t;
        a = f * f * (3.0 - 2.0 * f) * f;
    }

    vec4 color = vec4(vColor.rgb, vColor.a * a) * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
