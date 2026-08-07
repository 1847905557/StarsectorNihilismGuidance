#version 110

varying vec2 v_uv;

uniform float u_time;
uniform vec4 u_color;

void main() {
    float distanceToCenter = distance(v_uv, vec2(0.5, 0.5));
    float radius = fract(u_time * 0.35) * 0.5;
    float ring = 1.0 - smoothstep(0.0, 0.025, abs(distanceToCenter - radius));
    gl_FragColor = vec4(u_color.rgb, ring * u_color.a);
}
