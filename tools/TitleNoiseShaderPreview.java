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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL32C.*;

/** Actual noise_title shader verification in an invisible GLFW context and FBO. */
public final class TitleNoiseShaderPreview {
    private static final int WIDTH = 960;
    private static final int HEIGHT = 600;
    private static final float FPS = 18.0F;
    private static final float GUI_SCALE = 2.0F;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Locale.setDefault(Locale.ROOT);
        Path project = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        int frames = args.length > 1 ? Integer.parseInt(args[1]) : 36;
        if (frames < 2) throw new IllegalArgumentException("At least two frames are required.");
        Path output = project.resolve("build/title-noise-preview");
        Files.createDirectories(output);
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        callback.set();
        long context = 0;
        boolean capabilitiesReady = false;
        int program = 0, framebuffer = 0, texture = 0, vao = 0, vbo = 0;
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
            context = glfwCreateWindow(1, 1, "Hidden title noise verification", 0, 0);
            if (context == 0) throw new IllegalStateException("Hidden context creation failed; no visible window is attempted.");
            if (glfwGetWindowAttrib(context, GLFW_VISIBLE) != GLFW_FALSE)
                throw new IllegalStateException("The verification context must remain invisible.");
            glfwMakeContextCurrent(context);
            GL.createCapabilities();
            capabilitiesReady = true;
            System.out.println("Hidden OpenGL context: " + glGetString(GL_VERSION));
            System.out.println("Renderer: " + glGetString(GL_RENDERER));
            Path shaders = project.resolve("src/main/resources/assets/white_lily/shaders/core");
            program = program(shaders, "noise_title");

            framebuffer = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Title preview framebuffer is incomplete.");

            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            FloatBuffer vertices = BufferUtils.createFloatBuffer(30);
            vertices.put(new float[]{
                    0, 0, 0, 0, 0, 0, HEIGHT, 0, 0, 1, WIDTH, HEIGHT, 0, 1, 1,
                    0, 0, 0, 0, 0, WIDTH, HEIGHT, 0, 1, 1, WIDTH, 0, 0, 1, 0
            }).flip();
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            attribute(program, "Position", 3, 0);
            attribute(program, "UV0", 2, 3);
            glViewport(0, 0, WIDTH, HEIGHT);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_DITHER);
            // Read the shader's actual alpha, with no opaque clear or blending to hide a defect.
            glDisable(GL_BLEND);
            glUseProgram(program);
            matrices(program);
            uniform2(program, "FieldSize", WIDTH / GUI_SCALE, HEIGHT / GUI_SCALE);

            for (float opacity : new float[]{0, 0.35F, 1}) {
                render(program, 0.71F, opacity);
                validate(pixels(), "opacity=" + opacity);
            }
            byte[] previous = null;
            int changedPairs = 0;
            Set<Integer> hashes = new HashSet<>();
            long luminanceSum = 0;
            int minGray = 255, maxGray = 0;
            for (int frame = 0; frame < frames; frame++) {
                float time = frame / FPS;
                render(program, time, 1);
                byte[] rgba = pixels();
                validate(rgba, "frame=" + frame);
                if (previous != null && !Arrays.equals(previous, rgba)) changedPairs++;
                hashes.add(Arrays.hashCode(rgba));
                for (int p = 0; p < rgba.length; p += 4) {
                    int gray = rgba[p] & 255;
                    luminanceSum += gray;
                    minGray = Math.min(minGray, gray);
                    maxGray = Math.max(maxGray, gray);
                }
                save(output.resolve(String.format("frame-%03d.png", frame)), rgba);
                previous = rgba;
            }
            if (changedPairs == 0 || hashes.size() < 2)
                throw new IllegalStateException("The title noise shader did not animate across the exported frames.");
            Path preview = project.resolve("docs/title-noise-preview.png");
            Files.createDirectories(preview.getParent());
            Files.copy(output.resolve(String.format("frame-%03d.png", Math.min(frames - 1, 18))),
                    preview, StandardCopyOption.REPLACE_EXISTING);
            double mean = luminanceSum / (double) (WIDTH * HEIGHT * frames);
            String report = "Actual noise_title.vsh/fsh compiled and linked.\n"
                    + "Context: " + glGetString(GL_VERSION) + "\nRenderer: " + glGetString(GL_RENDERER) + "\n"
                    + "Frames: " + frames + "; fps=" + FPS + "; size=" + WIDTH + "x" + HEIGHT + "\n"
                    + "Changed adjacent pairs: " + changedPairs + "/" + (frames - 1) + "\n"
                    + "Distinct frame hashes: " + hashes.size() + "\n"
                    + "Grayscale: every pixel R=G=B; alpha=255 at opacity 0/0.35/1 and every frame.\n"
                    + String.format("Gray range: %d..%d; mean=%.3f/255%n", minGray, maxGray, mean)
                    + "No visible window, desktop input, panorama, menu, or synthetic label was used.\n";
            Files.writeString(output.resolve("verification.txt"), report);
            System.out.print(report);
            System.out.println("Preview: " + preview);
        } finally {
            if (context != 0 && capabilitiesReady) {
                if (vbo != 0) glDeleteBuffers(vbo);
                if (vao != 0) glDeleteVertexArrays(vao);
                if (texture != 0) glDeleteTextures(texture);
                if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
                if (program != 0) glDeleteProgram(program);
            }
            if (context != 0) glfwDestroyWindow(context);
            glfwTerminate();
            callback.free();
        }
    }

    private static void render(int program, float time, float opacity) {
        // Magenta + transparent is a sentinel: any uncovered pixel must fail validation.
        glClearColor(1, 0, 1, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        uniform(program, "NoiseTime", time);
        uniform(program, "Opacity", opacity);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glFinish();
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("Title shader OpenGL error: 0x" + Integer.toHexString(error));
    }

    private static void validate(byte[] rgba, String stage) {
        for (int p = 0; p < rgba.length; p += 4) {
            int r = rgba[p] & 255, g = rgba[p + 1] & 255, b = rgba[p + 2] & 255;
            if (r != g || r != b) throw new IllegalStateException("Colored title pixel at " + stage + ": " + r + "," + g + "," + b);
            if ((rgba[p + 3] & 255) != 255)
                throw new IllegalStateException("Transparent title background at " + stage + "; alpha=" + (rgba[p + 3] & 255));
        }
    }

    private static byte[] pixels() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        byte[] result = new byte[buffer.capacity()];
        buffer.get(result);
        return result;
    }

    private static void save(Path path, byte[] rgba) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            int p = ((HEIGHT - 1 - y) * WIDTH + x) * 4;
            image.setRGB(x, y, (rgba[p + 3] & 255) << 24 | (rgba[p] & 255) << 16
                    | (rgba[p + 1] & 255) << 8 | (rgba[p + 2] & 255));
        }
        if (!ImageIO.write(image, "PNG", path.toFile())) throw new IOException("PNG writer unavailable.");
    }

    private static int program(Path directory, String name) throws IOException {
        int vertex = shader(GL_VERTEX_SHADER, Files.readString(directory.resolve(name + ".vsh")), name + ".vsh");
        int fragment = shader(GL_FRAGMENT_SHADER, Files.readString(directory.resolve(name + ".fsh")), name + ".fsh");
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new IllegalStateException("noise_title link failed:\n" + glGetProgramInfoLog(program));
        glDeleteShader(vertex);
        glDeleteShader(fragment);
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
        glUniformMatrix4fv(glGetUniformLocation(program, "ModelViewMat"), false,
                new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1});
        glUniformMatrix4fv(glGetUniformLocation(program, "ProjMat"), false,
                new float[]{2.0F / WIDTH, 0, 0, 0, 0, -2.0F / HEIGHT, 0, 0, 0, 0, -1, 0, -1, 1, 0, 1});
    }

    private static void uniform(int program, String name, float value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform1f(location, value);
    }

    private static void uniform2(int program, String name, float x, float y) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform2f(location, x, y);
    }

    private static void attribute(int program, String name, int count, int offset) {
        int location = glGetAttribLocation(program, name);
        if (location < 0) throw new IllegalStateException("Missing required title vertex attribute: " + name);
        glEnableVertexAttribArray(location);
        glVertexAttribPointer(location, count, GL_FLOAT, false, Float.BYTES * 5, (long) Float.BYTES * offset);
    }
}
