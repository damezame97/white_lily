#version 150

uniform float NoiseTime;
uniform vec2 FieldSize;
uniform float Opacity;
in vec2 fieldUv;
out vec4 fragColor;

// 【密度/亮度调试】灰白密粒为主体；白碎边与黑孔洞沿用描述文件的视觉语言。
const float FINE_CELL = 2.15;
const float FINE_DENSITY = 0.76;
const float FINE_LIGHT = 0.30;
const float SOFT_LIGHT = 0.12;
const float EDGE_LIGHT = 0.70;
// 【轮廓调试】台阶尺寸与虚化距离只控制不规则块域，不会画固定格子或扫描线。
const float TERRACE_SIZE = 4.6;
const float DOMAIN_SCALE = 128.0;
const float DOMAIN_RATE = 2.7;
const float EDGE_FEATHER = 0.045;
const float CENTER_QUIET = 0.43;

float hash21(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec3 hash23(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yxz + 33.33);
    return fract((q.xxy + q.yzz) * q.zyx);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 s = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), s.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0)), s.x), s.y);
}

// 时间只切换随机形态，不向某一方向推进坐标；两层不同速率避免全屏同步跳动。
float changingDomain(vec2 p, float time, float seed) {
    float frame = floor(time);
    float f = fract(time);
    f = f * f * (3.0 - 2.0 * f);
    float a = valueNoise(p + vec2(frame * 17.137 + seed, seed * 7.7));
    float b = valueNoise(p + vec2((frame + 1.0) * 17.137 + seed, seed * 7.7));
    return mix(a, b, f);
}

// 每粒拥有独立双轴速度/相位和亮灭频率；没有整段平移、粗扫描或可识别的字形。
float particles(vec2 p, float cellSize, float seed, float density, float blur) {
    vec2 grid = p / cellSize;
    vec2 cell = floor(grid);
    vec3 r = hash23(cell + vec2(seed, 37.0));
    vec2 motion = vec2(sin(NoiseTime * (1.7 + r.x * 7.3) + r.y * 6.28318),
                       sin(NoiseTime * (2.1 + r.z * 8.1) + r.x * 6.28318)) * 0.16;
    vec2 q = (fract(grid) - vec2(0.5) - motion) * cellSize;
    float size = cellSize * (0.055 + r.z * 0.14);
    float point = 1.0 - smoothstep(size - blur, size + blur, max(abs(q.x), abs(q.y)));
    float pulse = 0.5 + 0.5 * sin(NoiseTime * (9.0 + r.z * 24.0) + r.x * 6.28318);
    return point * step(1.0 - density, r.y) * (0.13 + pulse * pulse * 0.87);
}

void main() {
    vec2 uv = fieldUv;
    float aspect = FieldSize.x / max(1.0, FieldSize.y);
    float referenceHeight = max(540.0, FieldSize.y);
    vec2 p = uv * vec2(referenceHeight * aspect, referenceHeight);
    vec2 terrace = floor(p / TERRACE_SIZE) * TERRACE_SIZE;
    float broad = changingDomain(terrace / DOMAIN_SCALE, NoiseTime * DOMAIN_RATE, 17.31);
    float middle = changingDomain(terrace / (DOMAIN_SCALE * 0.47), NoiseTime * 3.9, 61.7);
    float domain = broad * 0.72 + middle * 0.28;
    float litArea = smoothstep(0.34, 0.59, domain);
    float edgeDistance = abs(domain - 0.48);
    float edge = exp(-edgeDistance / EDGE_FEATHER);
    float blackHole = 1.0 - smoothstep(0.32, 0.41, domain);

    float fine = particles(p, FINE_CELL, 7.1, FINE_DENSITY, 0.26);
    float far = particles(p + vec2(0.9, -1.7), 5.7, 39.4, 0.69, 0.95);
    float chips = particles(p + vec2(3.1, 4.7), 3.35, 91.8, 0.38, 0.48);
    float edgeCells = hash21(floor(p / 2.1) + vec2(floor(NoiseTime * 27.0), 91.0));
    float edgeChips = step(0.60, edgeCells) * edge * (0.18 + edgeCells * 0.82);
    float mist = edge * 0.018;
    float light = (fine * FINE_LIGHT + far * SOFT_LIGHT + chips * 0.16) * litArea;
    light += edgeChips * EDGE_LIGHT + mist;
    light *= 1.0 - blackHole * 0.97;

    // 中央按钮/标题后面的白噪略暗；这是柔和亮度分布，不添加任何HUD或可见面板。
    vec2 menu = (uv - vec2(0.5, 0.56)) / vec2(0.39, 0.48);
    float quiet = 1.0 - exp(-dot(menu, menu) * 1.1) * CENTER_QUIET;
    float grain = hash21(floor(gl_FragCoord.xy) + vec2(floor(NoiseTime * 31.0), 17.0));
    light = light * quiet + grain * grain * 0.008 * litArea;
    float fade = clamp(Opacity, 0.0, 1.0);
    fade = fade * fade * (3.0 - 2.0 * fade);
    // 黑底和黑色孔洞都必须真正不透明；Opacity 只控制白粒渐入，不能透出原全景。
    fragColor = vec4(vec3(clamp(light * fade, 0.0, 1.0)), 1.0);
}
