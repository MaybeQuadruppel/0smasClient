#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec4 Shape;
layout(location = 3) in vec4 Rect;

layout(location = 0) out vec4 vColor;
layout(location = 1) out vec4 vShape;
layout(location = 2) out vec4 vRect;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vShape = Shape;
    vRect = Rect;
}
