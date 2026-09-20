#version 430

in vec2 fragUV;

uniform sampler2D tex;
uniform float time;
uniform vec4 coreColor;
uniform vec4 fringeColor;

out vec4 fragColor;

const float DISCARD_ALPHA = 0.001;

void main() {
    vec2 uv = vec2(fragUV.x, fract(fragUV.y + time * 0.25));
    vec4 texSample = texture(tex, uv);
    vec3 color = mix(fringeColor.rgb, coreColor.rgb, texSample.r);
    float alpha = texSample.a;
    if (alpha <= DISCARD_ALPHA) discard;
    fragColor = vec4(color, alpha);
}
