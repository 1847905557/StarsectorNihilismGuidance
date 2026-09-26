#version 120

attribute vec2 a_position;

uniform vec2 u_center;
uniform float u_facing;
varying vec2 v_local;

void main() {
    v_local = a_position;
    float angle = radians(u_facing);
    float cosine = cos(angle);
    float sine = sin(angle);
    vec2 rotated = vec2(
            cosine * a_position.x - sine * a_position.y,
            sine * a_position.x + cosine * a_position.y);
    vec2 world = u_center + rotated;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(world, 0.0, 1.0);
}
