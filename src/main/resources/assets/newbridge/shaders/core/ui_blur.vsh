#version 330

// newbridge ClickGUI blur: full-screen quads given directly in NDC (no projection needed).
// Params = (unused, unused, offset, mode 0 = down / 1 = up), Out = (1/outW, 1/outH, alpha, unused)

in vec3 Position;
in vec4 Params;
in vec4 Out;

out vec4 vParams;
out vec4 vOut;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vParams = Params;
    vOut = Out;
}
