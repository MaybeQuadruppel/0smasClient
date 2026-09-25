#version 330

// Dual-Kawase down / up sampling. Texture coordinates come from gl_FragCoord, so rendering into a texture
// and sampling it back is consistent on every backend (no Y-flip conventions involved).

uniform sampler2D Sampler0;

in vec4 vParams;
in vec4 vOut;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy * vOut.xy;
    vec2 hp = vOut.xy * 0.5 * vParams.z;
    vec3 sum;
    if (vParams.w < 0.5) {
        sum = texture(Sampler0, uv).rgb * 4.0;
        sum += texture(Sampler0, uv - hp).rgb;
        sum += texture(Sampler0, uv + hp).rgb;
        sum += texture(Sampler0, uv + vec2(hp.x, -hp.y)).rgb;
        sum += texture(Sampler0, uv - vec2(hp.x, -hp.y)).rgb;
        sum /= 8.0;
    } else {
        sum = texture(Sampler0, uv + vec2(-hp.x * 2.0, 0.0)).rgb;
        sum += texture(Sampler0, uv + vec2(-hp.x, hp.y)).rgb * 2.0;
        sum += texture(Sampler0, uv + vec2(0.0, hp.y * 2.0)).rgb;
        sum += texture(Sampler0, uv + vec2(hp.x, hp.y)).rgb * 2.0;
        sum += texture(Sampler0, uv + vec2(hp.x * 2.0, 0.0)).rgb;
        sum += texture(Sampler0, uv + vec2(hp.x, -hp.y)).rgb * 2.0;
        sum += texture(Sampler0, uv + vec2(0.0, -hp.y * 2.0)).rgb;
        sum += texture(Sampler0, uv + vec2(-hp.x, -hp.y)).rgb * 2.0;
        sum /= 12.0;
    }
    fragColor = vec4(sum, vOut.z);
}
