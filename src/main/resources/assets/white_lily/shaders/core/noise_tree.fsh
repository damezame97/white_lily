#version 150

uniform float NoiseTime;
uniform float Activity;
uniform float Opacity;

in vec2 localPixel;
in vec4 fragmentData;
out vec4 fragColor;

// 【颜色调试】连接只有白色；节点使用两套独立 palette，开启态压低蓝通道以免发紫。
const vec3 LINK_WHITE = vec3(1.0);
const vec3 BLUE_HALO = vec3(0.010, 0.10, 0.37);
const vec3 BLUE_MASS = vec3(0.026, 0.25, 1.02);
const vec3 BLUE_CHIP = vec3(0.15, 0.58, 1.25);
const vec3 BLUE_EXPOSURE = vec3(0.65, 1.00, 1.35);
const vec3 RED_HALO = vec3(0.37, 0.009, 0.004);
const vec3 RED_MASS = vec3(1.08, 0.028, 0.012);
const vec3 RED_CHIP = vec3(1.32, 0.13, 0.050);
const vec3 RED_EXPOSURE = vec3(1.45, 0.59, 0.38);

// 【连线粗细/噪声调试】0 为密集白点构成的粗带；1 为无闪烁、无位移的细白线。
// 动画时间由 Screen 提供的 stability 控制，这里不另起时钟，反向下降会自然散开。
const float LINK_LOOSE_HALF_WIDTH = 15.0;
const float LINK_STABLE_HALF_WIDTH = 0.56;
const float LINK_STABLE_BLUR = 0.42;
// 细白光晕只使用静态局部距离；不读取时间、悬停或 Activity，不会闪动或呼吸。
const float LINK_STABLE_HALO_RADIUS = 1.8;
const float LINK_STABLE_HALO_ALPHA = 0.16;
const float LINK_STABLE_HALO_CUTOFF = 5.0;
const float LINK_NOISE_CELL = 1.65;
const float LINK_NOISE_SPEED = 1.0;
const float LINK_LOCAL_MOTION = 0.19;

// 【节点粒子/光晕调试】所有粒子使用等边方粒或圆点，不生成横向白条或扫描线。
const float NODE_RADIUS = 20.0;
const float ORIGIN_RADIUS = 25.0;
const float NODE_HALO_SPREAD = 1.65;
const float NODE_HALO_ALPHA = 0.085;
// 细密度调试：cell 越小，相同面积中的粒子越密；粒径同步缩小，避免又变成疏散大块。
// 本轮 point 间距 2.5->1.25、细粒 1.3->0.78、模糊团块 8.6->3.2 世界像素。
const float NODE_POINT_CELL = 1.25;
const float NODE_FINE_CELL = 0.78;
// 轮廓仍保留模糊团块，但单块约为旧版的四成大小，更多块重叠成细密活动噪团。
const float NODE_BLOCK_CELL = 3.2;
const float NODE_BLOCK_BLUR = 0.95;
const float NODE_BLOCK_RATE = 3.4;
const float NODE_BURST_SPREAD = 0.72;

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash23(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yzz) * p3.zyx);
}

float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), u.x),
               mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0)), u.x), u.y);
}

float squareOrPoint(vec2 p, float halfSize, float roundness, float blur) {
    float d = mix(max(abs(p.x), abs(p.y)), length(p), roundness) - halfSize;
    return 1.0 - smoothstep(-blur, blur, d);
}

// 每粒各自拥有双轴频率、相位与方向，速度也不同；没有整段位移或沿线/逐列波浪。
vec2 localParticleMotion(vec3 random, float time, float amount) {
    return vec2(sin(time * (1.7 + random.x * 7.3) + random.y * 6.28318),
                sin(time * (2.1 + random.z * 8.1) + random.x * 6.28318)) * amount;
}

float blurredBlock(vec2 p, vec2 halfSize, float blur) {
    vec2 d = abs(p) - halfSize;
    float sd = length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0);
    return 1.0 - smoothstep(-blur, blur, sd);
}

float redParticle(float threshold, float activation) {
    if (activation <= 0.0) return 0.0;
    if (activation >= 1.0) return 1.0;
    return step(threshold, activation);
}

float staticLine(vec2 p, float segmentLength) {
    float side = 1.0 - smoothstep(LINK_STABLE_HALF_WIDTH,
                                 LINK_STABLE_HALF_WIDTH + LINK_STABLE_BLUR, abs(p.y));
    // 长度经过 Color.g 量化到 4 像素；两端多覆盖 2 像素保证折点细线不断开。
    float end = 1.0 - smoothstep(-0.25, 0.4, max(-p.x - 2.0, p.x - segmentLength - 2.0));
    float halo = exp(-p.y * p.y / (2.0 * LINK_STABLE_HALO_RADIUS * LINK_STABLE_HALO_RADIUS))
               * LINK_STABLE_HALO_ALPHA;
    halo *= 1.0 - smoothstep(LINK_STABLE_HALO_CUTOFF - 1.0,
                             LINK_STABLE_HALO_CUTOFF, abs(p.y));
    return (side + halo * (1.0 - side)) * end;
}

vec4 branch(vec2 p, float seed, float segmentLength, float hover, float stability) {
    float stable = clamp(stability, 0.0, 1.0);
    // 此分支在读取 NoiseTime/Activity/hover 之前返回；完全开启后是真正静止的细白线。
    if (stable >= 0.999) return vec4(LINK_WHITE, staticLine(p, segmentLength) * 0.96);

    float gather = stable * stable * (3.0 - 2.0 * stable);
    float loose = 1.0 - gather;
    float t = NoiseTime;
    float width = mix(LINK_LOOSE_HALF_WIDTH, LINK_STABLE_HALF_WIDTH, gather);
    // 包络不随时间沿线或按列摆动；破碎轮廓由每粒独立运动及亮灭自然形成。
    float distance = abs(p.y) / max(0.5, width);
    float envelope = exp(-pow(distance, 2.45) * 1.65);
    envelope *= 0.76 + hash21(floor(p / 3.7) + seed) * 0.24;
    float tips = 1.0 - smoothstep(0.0, 8.0 * loose + 0.6,
                                 max(-p.x, p.x - segmentLength));

    // 小白点的亮灭互相独立，边缘只有稀疏散点；没有连续实心灰白矩形遮住背景。
    vec2 grid = (p + vec2(seed * 0.37, 0.0)) / LINK_NOISE_CELL;
    vec2 cell = floor(grid);
    vec3 r = hash23(cell + vec2(seed, 17.0));
    float speed = LINK_NOISE_SPEED * (1.0 + hover * 0.12 + Activity * 0.09);
    vec2 center = vec2(0.5) + localParticleMotion(r, t * speed, LINK_LOCAL_MOTION * loose);
    vec2 local = (fract(grid) - center) * LINK_NOISE_CELL;
    float dots = squareOrPoint(local, 0.18 + r.z * 0.33, step(0.52, r.x), 0.23);
    float pulse = 0.5 + 0.5 * sin(t * (7.0 + r.z * 21.0) + r.y * 6.28318);
    float life = 0.14 + pulse * pulse * (0.32 + r.z * 0.54);
    float noiseAlpha = envelope * (0.022 + dots * life * 0.85);
    float haze = exp(-distance * distance * 0.56) * 0.020;
    float noise = (noiseAlpha + haze) * loose * tips;
    float spine = staticLine(p, segmentLength) * gather * gather * 0.96;
    return vec4(LINK_WHITE, clamp(noise + spine, 0.0, 0.97));
}

// 方粒、曝光点和不规则团块各自保留独立色彩。activation 中段爆散，端点重新收聚。
// 蓝红采用逐粒阈值置换，过渡帧会在空间中并存，不对整团做静态紫色线性混色。
vec4 node(vec2 p, float seed, float origin, float activation, float hover) {
    float a = clamp(activation, 0.0, 1.0);
    float burst = 4.0 * a * (1.0 - a);
    float t = NoiseTime;
    float energy = Activity * 0.17 + hover * 0.40 + burst * 0.66;
    float radius = mix(NODE_RADIUS, ORIGIN_RADIUS, origin);
    float spread = 1.0 + burst * NODE_BURST_SPREAD;
    // 不旋转整个节点：宏观块体分别扩张、坍缩和重排，不是一个刚性圆球。
    vec2 q = p / spread;
    vec2 drift = vec2(valueNoise(q * 0.075 + vec2(t * 0.7, seed)),
                      valueNoise(q * 0.075 + vec2(seed, -t * 0.6))) - 0.5;
    q += drift * (1.2 + energy * 2.4);
    float coarse = valueNoise(q * 0.10 + vec2(seed * 1.7, t * 0.48));
    float detail = valueNoise(q * 0.23 + vec2(-t * 0.52, seed));
    float radiusNoise = 0.75 + coarse * 0.48;
    float radial = length(q) / (radius * radiusNoise);
    float soft = exp(-radial * radial * 1.6);
    float halo = exp(-dot(p / (radius * NODE_HALO_SPREAD * spread),
                         p / (radius * NODE_HALO_SPREAD * spread)) * 1.9);
    vec2 blockGrid = q / NODE_BLOCK_CELL;
    vec2 blockCell = floor(blockGrid);
    vec3 blockRand = hash23(blockCell + vec2(seed, floor(t * NODE_BLOCK_RATE)));
    vec2 blockLocal = (fract(blockGrid) - vec2(0.23) - blockRand.xy * 0.54) * NODE_BLOCK_CELL;
    float blockSize = 0.50 + blockRand.z * 1.00;
    vec2 blockHalfSize = vec2(blockSize) * (vec2(0.83) + blockRand.xy * 0.34);
    float blocks = blurredBlock(blockLocal, blockHalfSize, NODE_BLOCK_BLUR)
                 * step(0.08, blockRand.y);
    float mass = soft * (0.12 + blocks * (0.45 + coarse * 0.27))
               * (0.72 + detail * 0.28);
    float exposure = mix(0.95, 1.22, a) + origin * 0.08 + hover * 0.19;

    vec2 grid = q / NODE_POINT_CELL;
    vec2 cell = floor(grid);
    vec3 r = hash23(cell + vec2(seed * 2.1, 17.0));
    vec2 center = vec2(0.5) + localParticleMotion(r, t, 0.17);
    vec2 pointLocal = (fract(grid) - center) * NODE_POINT_CELL;
    float point = squareOrPoint(pointLocal, 0.08 + r.z * 0.24,
                                step(0.57, r.x), 0.17);
    float flicker = hash21(cell + vec2(seed + floor(t * (13.0 + r.x * 23.0)), r.y * 67.0));
    float points = point * step(0.12, r.z) * soft * (0.28 + flicker * 0.72);

    // 第二层更小的曝光像素也保持等边，避免重新出现横向白色条纹。
    vec2 fineGrid = q / NODE_FINE_CELL;
    vec2 fineCell = floor(fineGrid);
    vec3 f = hash23(fineCell + vec2(seed * 3.3, 73.0));
    vec2 fineCenter = vec2(0.5) + localParticleMotion(f, t * 1.3, 0.19);
    vec2 fineLocal = (fract(fineGrid) - fineCenter) * NODE_FINE_CELL;
    float finePulse = 0.35 + 0.65 * hash21(fineCell + floor(t * (17.0 + f.y * 29.0)));
    float glints = squareOrPoint(fineLocal, 0.06 + f.z * 0.11, 0.0, 0.12)
                 * step(0.88, f.z) * soft * finePulse;
    float burstRing = exp(-pow((length(p) - radius * (1.0 + burst * 0.72))
                              / max(radius * 0.24, 1.0), 2.0)) * burst;
    float debris = point * burstRing * step(0.45, r.y) * 0.38;

    float patchRed = redParticle(hash21(blockCell + vec2(seed, 51.0)), a);
    float pointRed = redParticle(r.y, a);
    float glintRed = redParticle(f.x, a);
    vec3 light = halo * mix(BLUE_HALO, RED_HALO, patchRed) * 0.30;
    light += mass * mix(BLUE_MASS, RED_MASS, patchRed);
    light += (points * 0.30 + debris * 0.58) * mix(BLUE_CHIP, RED_CHIP, pointRed);
    light += glints * mix(BLUE_EXPOSURE, RED_EXPOSURE, glintRed) * 1.15;
    light *= exposure;
    float alpha = clamp(halo * NODE_HALO_ALPHA + mass * 0.65
                      + points * 0.28 + glints * 0.78 + debris * 0.53, 0.0, 0.97);
    float edgeFade = 1.0 - smoothstep(52.0, 61.0, max(abs(p.x), abs(p.y)));
    return vec4(light / max(alpha, 0.015), alpha * edgeFade);
}

void main() {
    float seed = floor(fragmentData.r * 255.0 + 0.5);
    float hover = fragmentData.a;
    vec4 pixel;
    if (fragmentData.b < 0.75) {
        // 线段：Color.b byte 0..127 为 stability；g byte 为 length/4。
        float stability = clamp(fragmentData.b * 255.0 / 127.0, 0.0, 1.0);
        pixel = branch(localPixel, seed, fragmentData.g * 1020.0, hover, stability);
    } else {
        // 节点：Color.b 固定 255；g byte 位7=origin，低7位=activation 的127级进度。
        float flags = floor(fragmentData.g * 255.0 + 0.5);
        float origin = floor(flags / 128.0);
        float activation = mod(flags, 128.0) / 127.0;
        pixel = node(localPixel, seed, origin, activation, hover);
    }
    fragColor = vec4(pixel.rgb, pixel.a * Opacity);
}
