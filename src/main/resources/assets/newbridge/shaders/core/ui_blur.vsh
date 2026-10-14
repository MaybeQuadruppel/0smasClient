#version 330
#extension GL_ARB_separate_shader_objects : require

// newbridge ClickGUI blur: full-screen quads given directly in NDC (no projection needed).
// Params = (unused, unused, offset, mode 0 = down / 1 = up), Out = (1/outW, 1/outH, alpha, unused)

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Params;
layout(location = 2) in vec4 Out;

layout(location = 0) out vec4 vParams;
layout(location = 1) out vec4 vOut;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vParams = Params;
    vOut = Out;
}
