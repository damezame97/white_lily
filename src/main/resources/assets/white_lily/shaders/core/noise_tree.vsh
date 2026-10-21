#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localPixel;
// r=随机种子；节点 g=origin*128+round(activation*127)，b=255；
// 线段 g=长度/4，b=round(stability*127)；a 始终是悬停强度。
// 这四个通道传输的是数据，真实颜色和透明度由片段着色器计算。
out vec4 fragmentData;

void main() {
    localPixel = UV0;
    fragmentData = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
