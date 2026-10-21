#version 150
uniform float NoiseTime;
uniform vec2 FieldSize;
uniform float Reveal;
in vec2 fieldUv;
out vec4 fragColor;

// 【文件轮廓调试】单位为GUI像素：碎边尺度、白粒密度、轮廓软化，不是完整白边框。
const float EDGE_CELL = 3.5;
const float EDGE_SOFTNESS = 2.0;
const float FRAGMENT_RATE = 25.0;

float hash21(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

void main() {
    float r = clamp(Reveal, 0.0, 1.0);
    if (r <= 0.001) { fragColor = vec4(0.0); return; }
    float spread = r * r * (3.0 - 2.0 * r);
    vec2 p = (fieldUv - 0.5) * FieldSize;
    // q空间随spread扩展，白碎粒从中心真实外移；反向播放时向中心坍缩。
    vec2 q = p / max(0.025, spread);
    vec2 halfSize = max(vec2(2.0), FieldSize * 0.5 - vec2(5.0));
    float frame = floor(NoiseTime * FRAGMENT_RATE);
    float nx = hash21(vec2(floor(q.x / EDGE_CELL), frame));
    float ny = hash21(vec2(floor(q.y / EDGE_CELL), frame + 719.0));
    vec2 terrace = floor(vec2(nx, ny) * 3.0) - 1.0;
    vec2 delta = abs(q) - halfSize;
    float edge = max(delta.x + terrace.y * 1.5, delta.y + terrace.x * 1.5);
    float inside = 1.0 - smoothstep(-0.5, EDGE_SOFTNESS, edge);
    float whiteBand = exp(-abs(edge) / 2.6);
    vec2 cell = floor(q / vec2(1.7, 1.9));
    float random = hash21(cell + vec2(frame, 19.0));
    float neighbor = hash21(floor(q / 3.8) + vec2(frame, 39.0));
    float chips = step(0.69, random) * whiteBand;
    float feather = step(0.93, neighbor) * exp(-abs(edge) / 5.0);
    float mist = whiteBand * 0.055;
    // 过渡期附带稍快的中心碎粒；展开后正文区保持近黑，不用闪点遮挡白字。
    float breaking = 4.0 * r * (1.0 - r);
    float centerDebris = step(0.94, random)
                       * exp(-dot(p / max(vec2(4.0), halfSize * spread),
                                  p / max(vec2(4.0), halfSize * spread))) * breaking;
    float light = chips * (0.42 + random * 0.48) + feather * 0.32 + mist + centerDebris * 0.62;
    float alpha = max(inside * 0.985, clamp(light, 0.0, 0.98));
    alpha *= smoothstep(0.0, 0.08, r);
    // 黑白噪粒独立于下面的蓝红场；边界无锐利的连续矩形白线。
    fragColor = vec4(vec3(clamp(light / max(alpha, 0.01), 0.0, 1.0)), alpha);
}
