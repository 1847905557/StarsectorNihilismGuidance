#version 430

layout (location = 0) in vec2 a_position;
layout (location = 1) in vec2 a_uv;

layout (std140) uniform BUtilGlobalData
{
    mat4 gameViewport;
    vec4 gameScreenBorder;
};

uniform mat4 modelMatrix;
uniform vec2 size;

out vec2 fragUV;

void main() {
    fragUV = a_uv;
    vec4 local = vec4(a_position * size * 0.5, 0.0, 1.0);
    gl_Position = gameViewport * vec4((modelMatrix * local).xy, 0.0, 1.0);
}
