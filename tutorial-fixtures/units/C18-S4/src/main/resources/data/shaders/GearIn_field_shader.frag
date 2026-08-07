#version 430

in vec2 fragUV;

uniform sampler2D tex;
uniform float time;
uniform float intensity;
uniform vec4 coreColor;
uniform vec4 fringeColor;

out vec4 fragColor;

const float DISCARD_ALPHA = 0.001;

void main() {
    vec2 uv = vec2(fragUV.x, fract(fragUV.y + time * 0.15));
    vec4 texSample = texture(tex, uv);
    float d = distance(fragUV, vec2(0.5, 0.5));
    float edgeFade = 1.0 - smoothstep(0.38, 0.50, d);
    float pulse = 0.55 + 0.45 * sin(time * 2.0 - d * 24.0);
    vec3 color = mix(fringeColor.rgb, coreColor.rgb, texSample.r);
    float alpha = texSample.a * edgeFade * pulse * intensity;
    if (alpha <= DISCARD_ALPHA) discard;
    fragColor = vec4(color, alpha);
}
