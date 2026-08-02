#version 110

attribute vec2 a_position;

attribute vec2 a_uv;

uniform vec2 u_offset;

varying vec2 v_uv;

void main() {
    v_uv = a_uv;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(a_position + u_offset, 0.0, 1.0);
}
