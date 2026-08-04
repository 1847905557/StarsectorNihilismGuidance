#version 110

varying vec2 v_uv;

void main() {
    gl_FragColor = vec4(v_uv.x, v_uv.y, 0.0, 1.0);
}
