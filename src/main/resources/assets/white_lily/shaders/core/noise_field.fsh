#version 150

uniform float NoiseTime;
uniform vec2 FieldSize;
uniform vec2 Cursor;
uniform float Activity;
uniform float Opacity;
uniform float Portal;
uniform float TrailPoints[80];
uniform int TrailCount;
uniform float TrailRadius;
uniform vec4 TrailBounds;

in vec2 fieldUv;
out vec4 fragColor;

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

vec4 trailPoint(int index) {
    int at = index * 4;
    return vec4(TrailPoints[at], TrailPoints[at + 1],
                TrailPoints[at + 2], TrailPoints[at + 3]);
}

// Each segment joins actual historical samples; a fast sweep never leaves gaps.
// The bounding rectangle skips the loop for fragments outside the whole trail.
float trailInfluence(vec2 uv) {
    if (TrailCount <= 0 || uv.x < TrailBounds.x || uv.y < TrailBounds.y
            || uv.x > TrailBounds.z || uv.y > TrailBounds.w) return 0.0;
    vec2 pixel = uv * FieldSize;
    float radiusSquared = max(1.0, TrailRadius * TrailRadius);
    float result = 0.0;
    vec4 previous = vec4(0.0);
    for (int i = 0; i < 20; ++i) {
        if (i >= TrailCount) break;
        vec4 current = trailPoint(i);
        vec2 center = current.xy * FieldSize;
        vec2 delta = pixel - center;
        float fade = max(0.0, 1.0 - dot(delta, delta) / radiusSquared);
        result = max(result, current.z * fade * fade);
        if (i > 0 && current.w > 0.5) {
            vec2 start = previous.xy * FieldSize;
            vec2 segment = center - start;
            float along = clamp(dot(pixel - start, segment) / max(0.0001, dot(segment, segment)), 0.0, 1.0);
            delta = pixel - start - segment * along;
            fade = max(0.0, 1.0 - dot(delta, delta) / radiusSquared);
            result = max(result, mix(previous.z, current.z, along) * fade * fade);
        }
        previous = current;
    }
    return clamp(result, 0.0, 1.0);
}

// A field of independently flickering tiny rectangles. Soft distant rectangles and
// sharp foreground slivers give depth without a liquid surface or readable text.
float particles(vec2 p, vec2 cellSize, float seed, float density, float blur, float rate) {
    vec2 grid = p / cellSize;
    vec2 cell = floor(grid);
    vec3 r = hash23(cell + vec2(seed, seed * 7.13));
    vec2 center = vec2(0.25) + r.yz * 0.50;
    vec2 size = vec2(0.035, 0.055) + r.xy * vec2(0.25, 0.34);
    float shape = max(abs(fract(grid).x - center.x) / size.x,
                      abs(fract(grid).y - center.y) / size.y);
    float rectangle = 1.0 - smoothstep(1.0 - blur, 1.0 + blur, shape);
    float life = hash21(cell + vec2(floor(NoiseTime * rate), seed * 31.7));
    float light = 0.12 + life * life * life * 0.88;
    return rectangle * step(1.0 - density, r.z) * light;
}

// Its geometry consists solely of horizontal/vertical terraces. The shape is not
// a smooth diagonal with a noise texture laid over it.
float portalCoverage(vec2 uv, float time, float energy) {
    float burst = energy * energy;
    float column = floor(uv.x * 23.0);
    float broad = hash21(vec2(floor(uv.x * 8.0), 7.43));
    float chip = hash21(vec2(column, floor(time * (2.0 + burst * 12.0))));
    float top = 0.54 - floor(uv.x * 8.0) * 0.036
              + (broad - 0.5) * 0.19 + (chip - 0.5) * (0.045 + burst * 0.23);
    top = floor(top * 23.0) / 23.0;
    float row = floor(uv.y * 19.0);
    float fracture = hash21(vec2(row + 83.0, floor(time * (3.0 + burst * 15.0))));
    float left = (1.0 - uv.y) * 0.28 + fracture * (0.08 + burst * 0.14);
    left = floor(left * 24.0) / 24.0;
    float body = step(top, uv.y) * step(left, uv.x);
    float nearEdge = 1.0 - smoothstep(0.025, 0.23, min(uv.y - top, uv.x - left));
    float rip = step(1.0 - burst * 0.30,
                     hash21(vec2(floor(uv.x * 16.0) + row * 2.0, floor(time * 21.0))));
    return body * (1.0 - rip * nearEdge);
}

void main() {
    vec2 uv = fieldUv;
    float t = NoiseTime;
    float aspect = FieldSize.x / max(FieldSize.y, 1.0);
    float energy = clamp(Activity, 0.0, 1.0);
    // GUI 背景的位移/增亮与红色轨迹共用小半径，避免红粒缩小后仍有一大片鼠标扰动。
    // 入口沿用其独立的局部悬停范围；调试背景范围只改 NoiseCursorTrail.RADIUS_GUI_PIXELS。
    float cursorRadius = Portal > 0.5 ? max(FieldSize.y / 2.5, 1.0) : max(TrailRadius, 1.0);
    float proximity = max(0.0, 1.0 - length((uv - Cursor) * FieldSize) / cursorRadius);
    float touch = proximity * proximity * energy;

    // Work in a reference pixel space so normal GUI scaling does not turn the
    // reference's fine granular field into a handful of enormous squares.
    float referenceHeight = mix(max(FieldSize.y, 540.0), max(FieldSize.y, 90.0), Portal);
    vec2 p = uv * vec2(referenceHeight * aspect, referenceHeight);
    float sliceRow = floor(p.y / 4.0);
    float sliceFrame = floor(t * (9.0 + energy * 13.0));
    float sliceRand = hash21(vec2(sliceRow, sliceFrame));
    float offset = (sliceRand - 0.5) * (3.0 + energy * 17.0 + touch * 13.0);
    offset *= step(0.72 - energy * 0.22, sliceRand);
    vec2 sliced = p + vec2(offset, 0.0);

    float farLayer = particles(p + vec2(t * 0.6, t * 0.9),
                              vec2(15.0, 21.0), 1.71, 0.77, 0.85, 7.0);
    float middleLayer = particles(sliced + vec2(t * 1.7, -t * 3.2),
                                 vec2(6.3, 9.3), 17.13, 0.67, 0.24, 15.0);
    float frontLayer = particles(sliced + vec2(-t * 4.1, t * 5.7),
                                vec2(4.8, 7.2), 61.9, 0.31, 0.055, 23.0);
    float slivers = particles(sliced + vec2(t * 11.0, t * 1.1),
                             vec2(34.0, 3.1), 128.2, 0.19, 0.13, 18.0);

    float redTrail = trailInfluence(uv);
    vec3 blueParticles = farLayer * vec3(0.020, 0.058, 0.139);
    blueParticles += middleLayer * vec3(0.028, 0.135, 0.43);
    blueParticles += frontLayer * vec3(0.085, 0.34, 0.93) * (0.78 + touch * 0.95);
    blueParticles += slivers * vec3(0.023, 0.12, 0.39) * (0.45 + energy * 0.55);
    vec3 redParticles = farLayer * vec3(0.17, 0.012, 0.025);
    redParticles += middleLayer * vec3(1.15, 0.035, 0.070);
    redParticles += frontLayer * vec3(2.3, 0.20, 0.28);
    redParticles += slivers * vec3(0.42, 0.013, 0.027);
    if (redTrail > 0.0001) {
        float hotSeed = hash21(floor(p / vec2(2.2, 2.8)) + vec2(floor(t * 29.0), 137.0));
        float hotDots = step(0.48, frontLayer) * step(0.87, hotSeed);
        redParticles += hotDots * vec3(1.2, 0.65, 0.70);
        // Larger defocused rectangular particles rearrange every ~0.3 seconds.
        // They remain separated chips, not a filled capsule of red illumination.
        float blockSeed = 341.2 + floor(t * 3.3) * 0.71;
        float exposureBlocks = particles(p + vec2(4.2, -2.8), vec2(39.0, 29.0),
                                         blockSeed, 0.72, 0.90, 3.3);
        redParticles += exposureBlocks * vec3(0.45, 0.008, 0.020);
    }
    // Only the existing particle masks acquire red exposure. The indigo base is
    // untouched, so the persistent influence cannot become a solid red circle.
    vec3 color = vec3(0.012, 0.032, 0.12) + mix(blueParticles, redParticles, redTrail);
    color += redTrail * farLayer * vec3(0.018, 0.001, 0.003);

    // Small separated horizontal failures, with no legible glyphs or large clouds.
    float scanRow = floor(p.y / 1.8);
    float scanSeed = hash21(vec2(scanRow, floor(t * 12.0)));
    float dash = hash21(vec2(floor((p.x + sliceFrame * 3.0) / 4.1), scanRow));
    float brokenScan = step(0.983 - energy * 0.013, scanSeed) * step(0.55, dash);
    color += brokenScan * mix(vec3(0.017, 0.079, 0.23), vec3(0.22, 0.009, 0.019), redTrail);
    float grain = hash21(floor(gl_FragCoord.xy) + vec2(floor(t * 24.0), floor(t * 19.0)));
    color += vec3(0.005, 0.013, 0.038) * (grain - 0.30);
    float edgeDarkness = smoothstep(0.15, 0.82, length((uv - 0.5) * vec2(1.0, 0.8)));
    color *= 1.0 - edgeDarkness * 0.18;

    float alpha = Opacity;
    if (Portal > 0.5) {
        float row = floor(uv.y * 24.0);
        float displace = (hash21(vec2(row, floor(t * 20.0))) - 0.5)
                       * energy * energy * 0.12;
        vec2 tornUv = uv + vec2(displace, 0.0);
        float body = portalCoverage(tornUv, t, energy);
        vec2 onePixel = vec2(0.85) / max(FieldSize, vec2(1.0));
        float neighbors = min(min(portalCoverage(tornUv + vec2(onePixel.x, 0.0), t, energy),
                                  portalCoverage(tornUv - vec2(onePixel.x, 0.0), t, energy)),
                              min(portalCoverage(tornUv + vec2(0.0, onePixel.y), t, energy),
                                  portalCoverage(tornUv - vec2(0.0, onePixel.y), t, energy)));
        float rim = body * (1.0 - neighbors);
        float debrisBand = 1.0 - smoothstep(0.0, 0.34, abs(uv.y - (0.54 - uv.x * 0.28)));
        float debris = step(0.13, frontLayer) * debrisBand * (1.0 - body)
                     * (0.14 + energy * energy * 0.86);
        color *= 1.15 + energy * 0.55;
        color += rim * vec3(0.055, 0.29, 0.75) * (0.7 + energy * 0.65);
        color += debris * vec3(0.055, 0.24, 0.68);
        alpha *= max(body * 0.98, debris * 0.87);
    }
    fragColor = vec4(max(color, vec3(0.0)), alpha);
}
