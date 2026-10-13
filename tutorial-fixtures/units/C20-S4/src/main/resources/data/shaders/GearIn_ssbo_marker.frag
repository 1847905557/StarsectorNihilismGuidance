#version 430 compatibility

uniform vec2 u_geometry;
uniform float u_viewportAlpha;
uniform float u_worldPerPixel;
in vec2 v_local;
in vec4 v_color;
out vec4 fragColor;

void main() {
    float ringDistance = abs(length(v_local) - u_geometry.x);
    vec2 nearestOnLine = vec2(clamp(v_local.x, 0.0, u_geometry.y), 0.0);
    float lineDistance = length(v_local - nearestOnLine);
    float edge = 0.5 * u_worldPerPixel;
    float ringHalfWidth = u_worldPerPixel;
    float lineHalfWidth = 1.5 * u_worldPerPixel;
    float ringCoverage = 1.0 - smoothstep(
            ringHalfWidth - edge, ringHalfWidth + edge, ringDistance);
    float lineCoverage = 1.0 - smoothstep(
            lineHalfWidth - edge, lineHalfWidth + edge, lineDistance);
    float coverage = max(ringCoverage, lineCoverage);
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(v_color.rgb, v_color.a * coverage * u_viewportAlpha);
}
