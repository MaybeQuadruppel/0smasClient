#version 330
#extension GL_ARB_separate_shader_objects : require

// newbridge ClickGUI: glyphs from our own FreeType atlas (coverage in the alpha channel).

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).a;
    vec4 color = vec4(vertexColor.rgb, vertexColor.a * coverage) * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
