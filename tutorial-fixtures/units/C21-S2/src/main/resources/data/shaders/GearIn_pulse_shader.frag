#version 110

uniform float u_time;

varying vec2 v_uv;

void main() {
    float d = distance(v_uv, vec2(0.5, 0.5));
    float ring = 1.0 - smoothstep(0.30, 0.45, d);
    float pulse = 0.5 + 0.5 * sin(u_time * 4.0 - d * 24.0);
    vec3 color = vec3(0.9, 0.3, 0.1) * ring * (0.3 + 0.7 * pulse);
    gl_FragColor = vec4(color, 1.0);
}
