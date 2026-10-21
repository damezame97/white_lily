import com.damezame97.white_lily.client.SpecialWindowTree;
import com.damezame97.white_lily.client.NoiseCursorTrail;
import com.damezame97.white_lily.client.NoiseDocumentMotion;
import com.damezame97.white_lily.client.NoiseTransition;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Standalone shader verification with a permanently hidden GLFW context and an
 * OpenGL framebuffer. Never shows a window, sends desktop input, or changes settings.
 * Uses only cached LWJGL dependencies. Run with -Djava.awt.headless=true.
 */
public final class NoiseShaderPreview {
    private static final int STRIDE = 9;
    private static final String PREVIEW_VERSION = "v5";
    private static final List<Float> VERTICES = new ArrayList<>();
    private static final float FPS = 18.0F;
    private static final float GUI_SCALE = 2.0F;
    private static int width = 960;
    private static int height = 600;
    private static int vao;
    private static int vbo;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        System.setProperty("java.awt.headless", "true");
        boolean probe = args.length > 0 && args[0].equals("--probe");
        Path project = Path.of(args.length > 0 && !probe ? args[0] : ".").toAbsolutePath().normalize();
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 72;
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        callback.set();
        long context = 0;
        try {
            if (!glfwInit()) throw new IllegalStateException("GLFW initialization failed; no desktop fallback is attempted.");
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE);
            glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
            context = glfwCreateWindow(1, 1, "Hidden shader verification", 0, 0);
            if (context == 0) throw new IllegalStateException("Hidden OpenGL context creation failed; no visible window is attempted.");
            if (glfwGetWindowAttrib(context, GLFW_VISIBLE) != GLFW_FALSE)
                throw new IllegalStateException("Refusing to continue because the context is visible.");
            glfwMakeContextCurrent(context);
            GL.createCapabilities();
            System.out.println("Hidden OpenGL context: " + glGetString(GL_VERSION));
            System.out.println("Renderer: " + glGetString(GL_RENDERER));
            if (probe) return;

            Path shaders = project.resolve("src/main/resources/assets/white_lily/shaders/core");
            int background = program(shaders, "noise_field");
            int tree = program(shaders, "noise_tree");
            int tooltip = program(shaders, "noise_tooltip");
            int framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Offscreen framebuffer is incomplete.");
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glViewport(0, 0, width, height);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            Path output = project.resolve("build/noise-preview-" + PREVIEW_VERSION);
            Files.createDirectories(output);
            Path checks = output.resolve("checks");
            Files.createDirectories(checks);
            verifyTree(tree, checks);
            grainAndMotionPreviews(tree, checks);
            System.out.println("Actual cursor radius: " + NoiseCursorTrail.RADIUS_GUI_PIXELS
                    + " GUI pixels; history=" + NoiseCursorTrail.DURATION_SECONDS + " seconds.");
            SpecialWindowTree.Definition definition = SpecialWindowTree.defaultDefinition();
            SpecialWindowTree.Selection selection = new SpecialWindowTree.Selection(definition);
            Map<String, NoiseTransition> transitions = new LinkedHashMap<>();
            for (SpecialWindowTree.NodeSpec n : definition.nodes())
                transitions.put(n.id(), new NoiseTransition(0, 1.0F));
            NoiseCursorTrail trail = new NoiseCursorTrail();
            boolean opened = false;
            boolean closed = false;
            int peakTrailSamples = 0;
            for (int i = 0; i < frames; i++) {
                float time = i / FPS;
                if (!opened && time >= 0.4F) {
                    selection.setRootEnabled(true);
                    if (!selection.toggle("east_main")) throw new IllegalStateException("Cannot enable east_main.");
                    opened = true;
                }
                if (!closed && time >= 2.2F) {
                    if (!selection.toggle("east_main")) throw new IllegalStateException("Cannot disable east_main.");
                    closed = true;
                }
                Map<String, Float> activation = new LinkedHashMap<>();
                for (SpecialWindowTree.NodeSpec n : definition.nodes()) {
                    NoiseTransition transition = transitions.get(n.id());
                    transition.target(selection.isEnabled(n.id()) ? 1 : 0, time);
                    activation.put(n.id(), transition.value(time));
                }
                float guiWidth = width / GUI_SCALE, guiHeight = height / GUI_SCALE;
                boolean mouseActive = time < 2.0F;
                float mx = guiWidth * (0.10F + Math.min(time / 2.0F, 1) * 0.80F);
                float my = guiHeight * (0.50F + (float) Math.sin(time * 8.0F) * 0.25F);
                trail.sample(mx, my, time, mouseActive);
                List<NoiseCursorTrail.Sample> samples = trail.samples(time);
                peakTrailSamples = Math.max(peakTrailSamples, samples.size());
                if (!mouseActive) mx = my = -1000;
                clear(0.005F, 0.012F, 0.03F);
                background(background, time, mx, my, mouseActive ? 0.43F : 0.20F, samples);
                if (i == 0) save(output.resolve("background.png"));

                glUseProgram(tree);
                matrices(tree);
                uniform(tree, "NoiseTime", time);
                uniform(tree, "Activity", 0.25F);
                uniform(tree, "Opacity", 1);
                VERTICES.clear();
                float worldMouseX = (mx - guiWidth / 2) / 0.62F;
                float worldMouseY = (my - guiHeight / 2) / 0.62F;
                buildTree(definition, activation, worldMouseX, worldMouseY);
                draw(tree);
                glFinish();
                int error = glGetError();
                if (error != GL_NO_ERROR) throw new IllegalStateException("OpenGL error: 0x" + Integer.toHexString(error));
                Path image = output.resolve(String.format("frame-%03d.png", i));
                save(image);
                if (i % 18 == 0 || i == frames - 1)
                    System.out.println("Frame " + i + ": root=" + activation.get(definition.rootId())
                            + ", east=" + activation.get("east_main") + ", trail=" + samples.size());
            }
            if (!selection.isEnabled(definition.rootId()) || selection.isEnabled("east_main"))
                throw new IllegalStateException("The preview ended with an incorrect logical state.");
            if (!trail.samples(frames / FPS).isEmpty())
                throw new IllegalStateException("Mouse trail did not expire after its 1.3 second lifetime.");
            System.out.println("Real cursor samples: peak=" + peakTrailSamples + ", final=0; 1.3s fade verified.");
            Path preview = project.resolve("docs/special-window-preview-" + PREVIEW_VERSION + ".png");
            Files.createDirectories(preview.getParent());
            Files.copy(output.resolve(String.format("frame-%03d.png", Math.min(frames - 1, 32))),
                    preview, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Preview: " + preview);
            documentFrames(background, tooltip, output.resolve("document"), frames);
            // The portal is rendered at the same 2x GUI scale against a neutral
            // gray backing. This is an offscreen shader crop, not a desktop capture.
            glClearColor(0.15F, 0.15F, 0.15F, 1);
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(background);
            matrices(background);
            uniform(background, "NoiseTime", 3.0F);
            uniform2(background, "FieldSize", 66, 47);
            uniform2(background, "Cursor", 0.65F, 0.65F);
            uniform(background, "Activity", 0.35F);
            uniform(background, "Opacity", 1);
            uniform(background, "Portal", 1);
            uploadTrail(background, List.of());
            VERTICES.clear();
            quad(width-132,height-174,width,height-80,0,0,1,1,1,1,1,1);
            draw(background);
            glFinish();
            save(output.resolve("portal-full.png"));
            Path portalPreview = project.resolve("docs/special-window-portal-preview-" + PREVIEW_VERSION + ".png");
            saveRegion(portalPreview,width-240,height-220,240,220);
            System.out.println("Portal shader crop: " + portalPreview);
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
            glDeleteTextures(texture);
            glDeleteFramebuffers(framebuffer);
            glDeleteProgram(background);
            glDeleteProgram(tree);
            glDeleteProgram(tooltip);
        } finally {
            if (context != 0) glfwDestroyWindow(context);
            glfwTerminate();
            callback.free();
        }
    }

    private static void clear(float r, float g, float b) {
        glClearColor(r, g, b, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private static void background(int program, float time, float mouseX, float mouseY,
                                   float activity, List<NoiseCursorTrail.Sample> samples) {
        glUseProgram(program);
        matrices(program);
        uniform(program, "NoiseTime", time);
        uniform2(program, "FieldSize", width / GUI_SCALE, height / GUI_SCALE);
        uniform2(program, "Cursor", mouseX / (width / GUI_SCALE), mouseY / (height / GUI_SCALE));
        uniform(program, "Activity", activity);
        uniform(program, "Opacity", 1);
        uniform(program, "Portal", 0);
        uploadTrail(program, samples);
        VERTICES.clear();
        quad(0, 0, width, height, 0, 0, 1, 1, 1, 1, 1, 1);
        draw(program);
    }

    /** 与 NoiseFieldRenderer 上传相同的标量80数组和 GUI 坐标范围。 */
    private static void uploadTrail(int program, List<NoiseCursorTrail.Sample> samples) {
        float[] values = new float[NoiseCursorTrail.MAX_SAMPLES * 4];
        int count = Math.min(samples.size(), NoiseCursorTrail.MAX_SAMPLES);
        int start = samples.size() - count;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float guiWidth = width / GUI_SCALE, guiHeight = height / GUI_SCALE;
        for (int i = 0; i < count; i++) {
            NoiseCursorTrail.Sample sample = samples.get(start + i);
            float x = sample.x() / guiWidth, y = sample.y() / guiHeight;
            values[i * 4] = x;
            values[i * 4 + 1] = y;
            values[i * 4 + 2] = Math.max(0, Math.min(1, sample.strength()));
            values[i * 4 + 3] = sample.connected() && i > 0 ? 1 : 0;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        int points = glGetUniformLocation(program, "TrailPoints");
        if (points >= 0) glUniform1fv(points, values);
        int size = glGetUniformLocation(program, "TrailCount");
        if (size >= 0) glUniform1i(size, count);
        uniform(program, "TrailRadius", NoiseCursorTrail.RADIUS_GUI_PIXELS);
        int bounds = glGetUniformLocation(program, "TrailBounds");
        if (bounds >= 0) {
            if (count == 0) glUniform4f(bounds, 0, 0, 0, 0);
            else glUniform4f(bounds, minX - NoiseCursorTrail.RADIUS_GUI_PIXELS / guiWidth,
                    minY - NoiseCursorTrail.RADIUS_GUI_PIXELS / guiHeight,
                    maxX + NoiseCursorTrail.RADIUS_GUI_PIXELS / guiWidth,
                    maxY + NoiseCursorTrail.RADIUS_GUI_PIXELS / guiHeight);
        }
    }

    private static void treeUniforms(int program, float time, float activity) {
        glUseProgram(program);
        matrices(program);
        uniform(program, "NoiseTime", time);
        uniform(program, "Activity", activity);
        uniform(program, "Opacity", 1);
    }

    /** 实际shader的像素验证：稳定线不受时间/悬停/Activity影响，节点端点确实蓝/红。 */
    private static void verifyTree(int program, Path output) throws IOException {
        clear(0, 0, 0);
        treeUniforms(program, 0.13F, 0);
        VERTICES.clear();
        link(-100, 0, 100, 0, 31 / 255.0F, 0, 1);
        draw(program);
        byte[] stableA = pixels(width / 2 - 140, height / 2 - 12, 280, 24);
        save(output.resolve("stable-line.png"));
        saveRegion(output.resolve("stable-line-halo.png"), width / 2 - 160, height / 2 - 20, 320, 40);
        clear(0, 0, 0);
        treeUniforms(program, 73.91F, 1);
        VERTICES.clear();
        link(-100, 0, 100, 0, 31 / 255.0F, 1, 1);
        draw(program);
        byte[] stableB = pixels(width / 2 - 140, height / 2 - 12, 280, 24);
        if (!Arrays.equals(stableA, stableB))
            throw new IllegalStateException("Stable line changed when time, hover and Activity changed.");
        long lit = 0;
        for (int p = 0; p < stableA.length; p += 4) {
            int r = stableA[p] & 255, g = stableA[p + 1] & 255, b = stableA[p + 2] & 255;
            if (r != g || r != b) throw new IllegalStateException("Stable line is not white.");
            if (r > 0) lit++;
        }
        if (lit < 100) throw new IllegalStateException("Static line test rendered too few visible pixels.");
        System.out.println("PASS static white line: identical 280x24 RGBA ROI across time/hover/Activity; lit=" + lit);
        verifyThinHalo(stableA, 280, 24, output);

        clear(0, 0, 0);
        treeUniforms(program, 0.13F, 0);
        VERTICES.clear();
        link(-100, 0, 100, 0, 31 / 255.0F, 0, 0);
        draw(program);
        byte[] looseA = pixels(width / 2 - 140, height / 2 - 30, 280, 60);
        clear(0, 0, 0);
        treeUniforms(program, 1.79F, 0);
        VERTICES.clear();
        link(-100, 0, 100, 0, 31 / 255.0F, 0, 0);
        draw(program);
        byte[] looseB = pixels(width / 2 - 140, height / 2 - 30, 280, 60);
        if (Arrays.equals(looseA, looseB)) throw new IllegalStateException("Loose white noise did not animate.");

        ColorStats[] stats = new ColorStats[3];
        float[] amounts = {0, 0.5F, 1};
        String[] names = {"node-blue.png", "node-transition.png", "node-red.png"};
        for (int i = 0; i < amounts.length; i++) {
            clear(0, 0, 0);
            treeUniforms(program, 0.71F, 0.25F);
            VERTICES.clear();
            node(0, 0, 97 / 255.0F, false, amounts[i], 0);
            draw(program);
            byte[] data = pixels(width / 2 - 80, height / 2 - 80, 160, 160);
            stats[i] = colorStats(data);
            save(output.resolve(names[i]));
        }
        if (stats[0].blue <= stats[0].red * 1.8 || stats[2].red <= stats[2].blue * 1.8)
            throw new IllegalStateException("Node endpoint palettes are not clearly blue and red.");
        if (stats[1].redPixels < 20 || stats[1].bluePixels < 20)
            throw new IllegalStateException("Activation midpoint did not retain spatially separate red and blue particles.");
        System.out.println("PASS node palettes: blue B/R=" + stats[0].blue / stats[0].red
                + ", red R/B=" + stats[2].red / stats[2].blue
                + "; midpoint red/blue pixels=" + stats[1].redPixels + "/" + stats[1].bluePixels);
    }

    /**
     * Measure the actual rendered cross-section, not a shader constant: a bright
     * thin core must have dimmer visible shoulders outside its antialiasing edge.
     * The ROI used above also proves that this halo is independent of time/input.
     */
    private static void verifyThinHalo(byte[] rgba, int regionWidth, int regionHeight, Path output) throws IOException {
        double center = (regionHeight - 1) / 2.0;
        double peak = 0, halo = 0, extent = 0;
        double[] profile = new double[regionHeight];
        StringBuilder report = new StringBuilder("distance_physical_pixels mean_white_intensity_0_255\n");
        for (int y = 0; y < regionHeight; y++) {
            double total = 0;
            for (int x = 40; x < regionWidth - 40; x++) total += rgba[(y * regionWidth + x) * 4] & 255;
            profile[y] = total / (regionWidth - 80);
            peak = Math.max(peak, profile[y]);
        }
        for (int y = 0; y < regionHeight; y++) {
            double distance = Math.abs(y - center);
            if (distance >= 2.5 && distance <= 6.5) halo = Math.max(halo, profile[y]);
            if (profile[y] >= 1) extent = Math.max(extent, distance);
            report.append(String.format(Locale.ROOT, "%+.1f %.3f%n", y - center, profile[y]));
        }
        Files.writeString(output.resolve("stable-line-profile.txt"), report);
        if (peak < 120) throw new IllegalStateException("Stable line has no sufficiently bright core: " + peak);
        if (halo < 1) throw new IllegalStateException("Stable line has no visible halo outside its antialiasing edge.");
        if (halo >= peak * 0.50) throw new IllegalStateException("Stable line halo is as bright as its core.");
        if (extent > 8.5) throw new IllegalStateException("Stable line halo is too broad for a thin halo: " + extent);
        System.out.println("PASS stable line halo: core=" + peak + ", dim shoulder=" + halo
                + ", >=1/255 extent=" + extent + " physical pixels (GUI scale 2, zoom 0.62).");
    }

    /**
     * Actual shader close-ups for artistic review. These deliberately make no
     * brittle assertion about particle size/density or a subjective motion style.
     * Node cells show successive times; loose-line strips should mutate locally,
     * without a whole envelope or repeated pattern marching in one direction.
     */
    private static void grainAndMotionPreviews(int program, Path output) throws IOException {
        BufferedImage nodeSheet = new BufferedImage(480, 480, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 9; i++) {
            clear(0, 0, 0);
            treeUniforms(program, 0.13F + i * 0.073F, 0.25F);
            VERTICES.clear();
            node(0, 0, 97 / 255.0F, false, 0, 0);
            draw(program);
            BufferedImage crop = imageRegion(width / 2 - 80, height / 2 - 80, 160, 160);
            nodeSheet.getRaster().setRect((i % 3) * 160, (i / 3) * 160, crop.getRaster());
        }
        ImageIO.write(nodeSheet, "PNG", output.resolve("node-grain-contact-sheet.png").toFile());
        BufferedImage lineSheet = new BufferedImage(340, 720, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 10; i++) {
            clear(0, 0, 0);
            treeUniforms(program, 0.13F + i * 0.037F, 0.25F);
            VERTICES.clear();
            link(-120, 0, 120, 0, 31 / 255.0F, 0, 0);
            draw(program);
            BufferedImage crop = imageRegion(width / 2 - 170, height / 2 - 36, 340, 72);
            lineSheet.getRaster().setRect(0, i * 72, crop.getRaster());
        }
        ImageIO.write(lineSheet, "PNG", output.resolve("coarse-line-time-stack.png").toFile());
        System.out.println("Review close-ups: node-grain-contact-sheet.png (row-major times, 73ms apart), "
                + "coarse-line-time-stack.png (top-to-bottom times, 37ms apart).");
    }

    private record ColorStats(double red, double blue, int redPixels, int bluePixels) { }

    private static ColorStats colorStats(byte[] data) {
        double red = 0, blue = 0;
        int redPixels = 0, bluePixels = 0;
        for (int p = 0; p < data.length; p += 4) {
            int r = data[p] & 255, b = data[p + 2] & 255;
            red += r; blue += b;
            if (r > 25 && r > b * 1.5) redPixels++;
            if (b > 25 && b > r * 1.5) bluePixels++;
        }
        return new ColorStats(red, blue, redPixels, bluePixels);
    }

    private static byte[] pixels(int left, int top, int regionWidth, int regionHeight) {
        glFinish();
        ByteBuffer buffer = BufferUtils.createByteBuffer(regionWidth * regionHeight * 4);
        glReadPixels(left, height - top - regionHeight, regionWidth, regionHeight,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        byte[] result = new byte[buffer.capacity()];
        buffer.get(result);
        return result;
    }

    /** 只画真实黑白噪边shader，不伪造描述正文；字体由实际游戏界面负责。 */
    private static void documentFrames(int background, int tooltip, Path output, int frames) throws IOException {
        Files.createDirectories(output);
        NoiseDocumentMotion motion = new NoiseDocumentMotion(0.42F, 0.18F);
        float peak = 0;
        for (int i = 0; i < frames; i++) {
            float time = i / FPS;
            String hovered = time >= 0.7F && time < 1.7F ? "east_main" : null;
            motion.update(hovered, i == 0 ? 0 : 1 / FPS, 130, 85);
            peak = Math.max(peak, motion.amount());
            clear(0.005F, 0.012F, 0.03F);
            background(background, time, -1000, -1000, 0.20F, List.of());
            glUseProgram(tooltip);
            matrices(tooltip);
            uniform(tooltip, "NoiseTime", time);
            uniform2(tooltip, "FieldSize", 240, 120);
            uniform(tooltip, "Reveal", motion.amount());
            VERTICES.clear();
            quad(width / 2.0F - 240, height / 2.0F - 120,
                    width / 2.0F + 240, height / 2.0F + 120, 0, 0, 1, 1, 1, 1, 1, 1);
            draw(tooltip);
            save(output.resolve(String.format("frame-%03d.png", i)));
            int error = glGetError();
            if (error != GL_NO_ERROR) throw new IllegalStateException("Document OpenGL error 0x" + Integer.toHexString(error));
        }
        if (peak < 0.999F || motion.amount() != 0 || motion.visibleId() != null)
            throw new IllegalStateException("The real document motion did not fully open and close.");
        System.out.println("PASS document motion: opened with 0.42s, closed with 0.18s; no body text synthesized.");
    }

    private static int program(Path shaders, String name) throws IOException {
        int vertex = shader(GL_VERTEX_SHADER, Files.readString(shaders.resolve(name + ".vsh")), name + ".vsh");
        int fragment = shader(GL_FRAGMENT_SHADER, Files.readString(shaders.resolve(name + ".fsh")), name + ".fsh");
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException(name + " link failed:\n" + glGetProgramInfoLog(program));
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        System.out.println("Compiled and linked actual project shader: " + name);
        return program;
    }

    private static int shader(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
            throw new IllegalStateException(name + " compilation failed:\n" + glGetShaderInfoLog(shader));
        String log = glGetShaderInfoLog(shader);
        if (!log.isBlank()) System.out.println(name + ": " + log);
        return shader;
    }

    private static void matrices(int program) {
        float[] identity = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
        float[] projection = {2.0F/width,0,0,0, 0,-2.0F/height,0,0, 0,0,-1,0, -1,1,0,1};
        glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false, identity);
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false, projection);
    }

    private static void uniform(int program, String name, float value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform1f(location, value);
    }

    private static void uniform2(int program, String name, float a, float b) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform2f(location, a, b);
    }

    private static void draw(int program) {
        FloatBuffer data = BufferUtils.createFloatBuffer(VERTICES.size());
        for (float value : VERTICES) data.put(value);
        data.flip();
        glBufferData(GL_ARRAY_BUFFER, data, GL_STREAM_DRAW);
        attribute(program, "Position", 3, 0);
        attribute(program, "UV0", 2, 3);
        attribute(program, "Color", 4, 5);
        glDrawArrays(GL_TRIANGLES, 0, VERTICES.size() / STRIDE);
    }

    private static void attribute(int program, String name, int size, int offset) {
        int location = glGetAttribLocation(program, name);
        if (location < 0) return;
        glEnableVertexAttribArray(location);
        glVertexAttribPointer(location, size, GL_FLOAT, false, STRIDE * Float.BYTES, (long) offset * Float.BYTES);
    }

    private static void quad(float x1, float y1, float x2, float y2, float u1, float v1,
                             float u2, float v2, float r, float g, float b, float a) {
        vertex(x1,y1,u1,v1,r,g,b,a); vertex(x1,y2,u1,v2,r,g,b,a); vertex(x2,y2,u2,v2,r,g,b,a);
        vertex(x1,y1,u1,v1,r,g,b,a); vertex(x2,y2,u2,v2,r,g,b,a); vertex(x2,y1,u2,v1,r,g,b,a);
    }

    private static void vertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        for (float value : new float[]{x,y,0,u,v,r,g,b,a}) VERTICES.add(value);
    }

    /**
     * 直接读取纯 Java 图定义，不解析 Screen 源码、不复制另一份拓扑。
     * 与 Screen.buildBranches 相同：Connection 从节点中心出发，经 bends，抵达后继中心。
     */
    private static void buildTree(SpecialWindowTree.Definition definition, Map<String, Float> activation,
                                  float mouseX, float mouseY) {
        int index = 0;
        int routeIndex = 0;
        for (SpecialWindowTree.Connection route : definition.connections()) {
            SpecialWindowTree.NodeSpec from = definition.node(route.fromId());
            SpecialWindowTree.NodeSpec to = definition.node(route.toId());
            float stability = Math.min(activation.get(route.fromId()), activation.get(route.toId()));
            SpecialWindowTree.Point previous = new SpecialWindowTree.Point(from.x(), from.y());
            List<SpecialWindowTree.Point> path = new ArrayList<>(route.bends());
            path.add(new SpecialWindowTree.Point(to.x(), to.y()));
            for (SpecialWindowTree.Point next : path) {
                float dx = next.x() - previous.x(), dy = next.y() - previous.y();
                if (Math.hypot(dx, dy) > 0.01) {
                    int seed = (index++ * 73 + routeIndex * 37
                            + (int) (previous.x() * 3 + previous.y() * 7)) & 255;
                    float along = Math.max(0, Math.min(1,
                            ((mouseX - previous.x()) * dx + (mouseY - previous.y()) * dy)
                                    / Math.max(0.01F, dx * dx + dy * dy)));
                    float distance = (float) Math.hypot(mouseX - previous.x() - dx * along,
                            mouseY - previous.y() - dy * along);
                    float hover = Math.round(Math.max(0, 1 - distance / 54) * 255) / 255.0F;
                    link(previous.x(), previous.y(), next.x(), next.y(), seed / 255.0F, hover, stability);
                }
                previous = next;
            }
            routeIndex++;
        }
        for (SpecialWindowTree.NodeSpec n : definition.nodes()) {
            float hover = Math.round(Math.max(0, 1 - (float) Math.hypot(mouseX - n.x(), mouseY - n.y()) / 54)
                    * 255) / 255.0F;
            node(n.x(), n.y(), (n.visualSeed() & 255) / 255.0F,
                    n.id().equals(definition.rootId()), activation.get(n.id()), hover);
        }
    }

    private static void link(float x1, float y1, float x2, float y2, float seed, float hover, float stability) {
        float dx = x2-x1, dy = y2-y1;
        float length = (float) Math.hypot(dx,dy);
        float cos = dx/length, sin = dy/length;
        float[][] points = {{-18,-28},{-18,28},{length+18,28},{length+18,-28}};
        float lengthCode = Math.max(1,Math.min(255,Math.round(length/4)))/255.0F;
        float stabilityCode = Math.round(Math.max(0, Math.min(1, stability)) * 127) / 255.0F;
        for (int i : new int[]{0,1,2,0,2,3}) {
            float u=points[i][0],v=points[i][1];
            vertex(sx(x1+u*cos-v*sin),sy(y1+u*sin+v*cos),u,v,seed,lengthCode,stabilityCode,hover);
        }
    }

    private static void node(float x, float y, float seed, boolean origin, float activation, float hover) {
        // 与产品顶点编码相同：g 的高位=origin，低7位=activation；b255 表示节点。
        float flags = ((origin ? 128 : 0) + Math.round(Math.max(0, Math.min(1, activation)) * 127)) / 255.0F;
        quad(sx(x-62),sy(y-62),sx(x+62),sy(y+62),-62,-62,62,62,seed,flags,1,hover);
    }

    private static float sx(float x) { return width/2.0F+x*0.62F*GUI_SCALE; }
    private static float sy(float y) { return height/2.0F+y*0.62F*GUI_SCALE; }

    private static void save(Path path) throws IOException {
        saveRegion(path,0,0,width,height);
    }

    private static void saveRegion(Path path,int left,int top,int regionWidth,int regionHeight) throws IOException {
        ImageIO.write(imageRegion(left, top, regionWidth, regionHeight), "PNG", path.toFile());
    }

    private static BufferedImage imageRegion(int left, int top, int regionWidth, int regionHeight) {
        byte[] pixels = pixels(left, top, regionWidth, regionHeight);
        BufferedImage image = new BufferedImage(regionWidth,regionHeight,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<regionHeight;y++) for(int x=0;x<regionWidth;x++) {
            int p=((regionHeight-1-y)*regionWidth+x)*4;
            int r=pixels[p]&255,g=pixels[p+1]&255,b=pixels[p+2]&255;
            image.setRGB(x,y,(r<<16)|(g<<8)|b);
        }
        return image;
    }
}
