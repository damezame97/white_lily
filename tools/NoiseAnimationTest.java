import com.damezame97.white_lily.client.NoiseCursorTrail;
import com.damezame97.white_lily.client.NoiseCursorTrail.Sample;
import com.damezame97.white_lily.client.NoiseDocumentMotion;
import com.damezame97.white_lily.client.NoiseTransition;

import java.util.List;

/**
 * JDK 17 behavioral checks for visible timing, handoff and pointer history.
 * Does not require Minecraft, Forge, OpenGL, Gradle, desktop input, or -ea.
 */
public final class NoiseAnimationTest {
    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        run("one-second activation/deactivation", NoiseAnimationTest::transitionTiming);
        run("rapid reversal remains continuous", NoiseAnimationTest::transitionReversal);
        run("render frequency does not change timing", NoiseAnimationTest::transitionFrameRates);
        run("document opens and closes on schedule", NoiseAnimationTest::documentTiming);
        run("node change closes old document before replacing text", NoiseAnimationTest::documentHandoff);
        run("delayed frame preserves time after document handoff", NoiseAnimationTest::documentDelayedFrame);
        run("quick re-entry preserves amount and anchor", NoiseAnimationTest::documentReentry);
        run("pointer history keeps over a second at high frame rates", NoiseAnimationTest::trailCapacityAndSpan);
        run("moved-away trail fades gradually", NoiseAnimationTest::trailFade);
        run("stationary active pointer remains hot", NoiseAnimationTest::trailStationary);
        run("focus loss breaks pointer segments", NoiseAnimationTest::trailFocusBreak);
        run("clear starts a fresh pointer history", NoiseAnimationTest::trailClear);
        System.out.println("NoiseAnimationTest: " + checks + " checks; " + failures + " failed scenarios");
        if (failures != 0) throw new AssertionError(failures + " animation scenarios failed");
    }

    private static void transitionTiming() {
        NoiseTransition activation = new NoiseTransition(0, 1);
        activation.target(1, 0);
        near(activation.value(0), 0, "activation starts at blue");
        check(activation.value(0.25F) > 0 && activation.value(0.25F) < 0.5F,
                "activation visibly progresses before the midpoint");
        near(activation.value(0.5F), 0.5F, "one-second activation midpoint");
        near(activation.value(1), 1, "activation reaches red at one second");
        near(activation.value(3), 1, "completed activation remains red");
        activation.target(0, 3);
        near(activation.value(3), 1, "closing starts at the displayed red value");
        near(activation.value(3.5F), 0.5F, "one-second deactivation midpoint");
        near(activation.value(4), 0, "deactivation reaches blue at one second");
    }

    private static void transitionReversal() {
        NoiseTransition transition = new NoiseTransition(0, 1);
        transition.target(1, 0);
        float beforeClose = transition.value(0.37F);
        transition.target(0, 0.37F);
        near(transition.value(0.37F), beforeClose, "turning off mid-animation does not jump");
        float beforeReopen = transition.value(0.59F);
        check(beforeReopen > 0 && beforeReopen < beforeClose, "reversed activation actually moves back toward blue");
        transition.target(1, 0.59F);
        near(transition.value(0.59F), beforeReopen, "turning on again keeps the current appearance");
        check(transition.value(0.8F) > beforeReopen && transition.value(0.8F) < 1,
                "second activation grows from its current value");
        near(transition.value(1.59F), 1, "second activation eventually completes");
    }

    private static void transitionFrameRates() {
        float[] times = {0.25F, 0.5F, 0.75F, 1};
        for (int fps : new int[]{15, 30, 60, 144, 240}) {
            NoiseTransition dense = new NoiseTransition(0, 1);
            NoiseTransition sparse = new NoiseTransition(0, 1);
            dense.target(1, 0);
            sparse.target(1, 0);
            int frame = 0;
            for (float targetTime : times) {
                while (frame <= (int)(targetTime * fps)) {
                    float frameTime = frame / (float)fps;
                    // Screen re-sends the same logical target while it renders.
                    dense.target(1, frameTime);
                    dense.value(frameTime);
                    frame++;
                }
                near(dense.value(targetTime), sparse.value(targetTime),
                        fps + " fps gives the same appearance at " + targetTime + " seconds");
            }
        }
    }

    private static void documentTiming() {
        NoiseDocumentMotion motion = motion();
        motion.update("A", 0, 30, 40);
        check("A".equals(motion.visibleId()), "hover chooses the description that will unfold");
        near(motion.amount(), 0, "new description begins collapsed at its center");
        motion.update("A", 0.21F, 32, 42);
        near(motion.amount(), 0.5F, "description is half open after 0.21 seconds");
        motion.update("A", 0.21F, 35, 45);
        near(motion.amount(), 1, "description finishes opening after 0.42 seconds");
        motion.update(null, 0.03F, 100, 100);
        check("A".equals(motion.visibleId()) && motion.amount() > 0 && motion.amount() < 1,
                "moving away starts a visible collapse instead of instant disappearance");
        motion.update(null, 0.18F, 100, 100);
        check(motion.visibleId() == null, "description is gone after its close time");
        near(motion.amount(), 0, "closed description has no residual size");
    }

    private static void documentHandoff() {
        NoiseDocumentMotion motion = openDocument("A", 10, 20);
        motion.update("B", 0.09F, 200, 210);
        check("A".equals(motion.visibleId()), "moving to another node keeps old text during collapse");
        near(motion.amount(), 0.5F, "old document is halfway closed before the switch");
        check(motion.anchorX() == 10 && motion.anchorY() == 20, "old document keeps its old anchor");
        motion.update("B", 0.09F, 220, 230);
        check("B".equals(motion.visibleId()), "new node replaces text only after old document closes");
        near(motion.amount(), 0, "new document starts collapsed");
        check(motion.anchorX() == 220 && motion.anchorY() == 230, "new node takes a new anchor");
        motion.update("B", 0.21F, 225, 235);
        near(motion.amount(), 0.5F, "new description unfolds after handoff");
    }

    private static void documentDelayedFrame() {
        NoiseDocumentMotion delayed = openDocument("A", 10, 20);
        NoiseDocumentMotion smooth = openDocument("A", 10, 20);
        // Both users spend 0.39 seconds over B: 0.18 closing A, then 0.21 opening B.
        delayed.update("B", 0.39F, 200, 210);
        smooth.update("B", 0.18F, 200, 210);
        smooth.update("B", 0.21F, 200, 210);
        check("B".equals(delayed.visibleId()), "delayed frame has switched to the new description");
        near(delayed.amount(), smooth.amount(), "one delayed render must not discard the new document's elapsed open time");
    }

    private static void documentReentry() {
        NoiseDocumentMotion motion = openDocument("A", 10, 20);
        motion.update(null, 0.06F, 300, 310);
        float shrinking = motion.amount();
        motion.update("A", 0, 50, 60);
        near(motion.amount(), shrinking, "re-entering mid-collapse does not jump size");
        motion.update("A", 0.042F, 55, 65);
        check(motion.amount() > shrinking && motion.amount() < 1, "same document smoothly resumes opening");
        check(motion.anchorX() == 10 && motion.anchorY() == 20, "same document does not chase tiny mouse movement");
        motion.update("B", 0.03F, 100, 110);
        float switching = motion.amount();
        motion.update("A", 0, 120, 130);
        near(motion.amount(), switching, "rapid A to B to A preserves the visible old document");
        check("A".equals(motion.visibleId()), "rapid re-entry does not leak new node text");
        check(motion.anchorX() == 10 && motion.anchorY() == 20, "rapid re-entry does not change old anchor");
    }

    private static void trailCapacityAndSpan() {
        for (int fps : new int[]{30, 60, 144, 240}) {
            NoiseCursorTrail trail = new NoiseCursorTrail();
            int largest = 0;
            for (int i = 0; i <= 3 * fps; i++) {
                float time = i / (float)fps;
                trail.sample(time * 1000, 0, time, true);
                List<Sample> samples = trail.samples(time);
                largest = Math.max(largest, samples.size());
                check(samples.size() <= 20, fps + " fps never exceeds the twenty-point budget");
            }
            List<Sample> samples = trail.samples(3);
            float historySeconds = (samples.get(samples.size() - 1).x() - samples.get(0).x()) / 1000;
            check(historySeconds > 1.05F, fps + " fps retains more than a second rather than twenty render frames");
            check(historySeconds <= NoiseCursorTrail.DURATION_SECONDS + 0.01F, "history obeys its public duration");
            check(largest >= 12, "high-speed motion leaves a useful, bounded history");
            check(NoiseCursorTrail.influenceAt(samples, 2000, 0, 10) > 0.01F,
                    "a location visited one second ago still has a fading trail");
        }
    }

    private static void trailFade() {
        NoiseCursorTrail trail = new NoiseCursorTrail();
        trail.sample(0, 0, 0, true);
        trail.sample(200, 0, 0.05F, true);
        float hot = NoiseCursorTrail.influenceAt(trail.samples(0.05F), 0, 0, 10);
        float fading = NoiseCursorTrail.influenceAt(trail.samples(0.65F), 0, 0, 10);
        check(hot > 0.9F && fading > 0 && fading < hot, "moving away gradually cools the visited position");
        float nearlyGone = NoiseCursorTrail.influenceAt(trail.samples(1.25F), 0, 0, 10);
        check(nearlyGone >= 0 && nearlyGone < 0.02F, "old position is nearly blue again around 1.25 seconds");
        check(trail.samples(NoiseCursorTrail.DURATION_SECONDS + 0.06F).isEmpty(), "expired trail points disappear");
    }

    private static void trailStationary() {
        NoiseCursorTrail trail = new NoiseCursorTrail();
        for (int i = 0; i <= 360; i++) trail.sample(50, 60, i / 60.0F, true);
        near(NoiseCursorTrail.influenceAt(trail.samples(6), 50, 60, 42), 1,
                "stationary active pointer remains fully hot after several durations");
        trail.sample(50, 60, 6.01F, false);
        check(NoiseCursorTrail.influenceAt(trail.samples(6.7F), 50, 60, 42) < 0.6F,
                "inactive stationary pointer stops refreshing the heat");
    }

    private static void trailFocusBreak() {
        NoiseCursorTrail trail = new NoiseCursorTrail();
        trail.sample(0, 0, 0, true);
        trail.sample(0, 0, 0.2F, true);
        trail.sample(0, 0, 0.21F, false);
        trail.sample(200, 0, 0.22F, true);
        trail.sample(200, 0, 0.23F, true);
        List<Sample> samples = trail.samples(0.23F);
        check(NoiseCursorTrail.influenceAt(samples, 0, 0, 10) > 0.8F, "old trail survives focus loss and keeps fading");
        near(NoiseCursorTrail.influenceAt(samples, 200, 0, 10), 1, "new focused position is hot");
        near(NoiseCursorTrail.influenceAt(samples, 100, 0, 10), 0, "focus return does not draw a false bridge");
        NoiseCursorTrail pause = new NoiseCursorTrail();
        pause.sample(0, 0, 0, true);
        pause.sample(200, 0, 0.5F, true);
        near(NoiseCursorTrail.influenceAt(pause.samples(0.5F), 100, 0, 10), 0,
                "long sampling pause also breaks teleported pointer segments");
    }

    private static void trailClear() {
        NoiseCursorTrail trail = new NoiseCursorTrail();
        trail.sample(10, 20, 2, true);
        List<Sample> previousFrame = trail.samples(2);
        trail.clear();
        check(trail.samples(2).isEmpty(), "resize/clear removes all history");
        check(previousFrame.size() == 1, "returned frame snapshots stay valid after clear");
        trail.sample(300, 400, 0, true);
        List<Sample> fresh = trail.samples(0);
        check(fresh.size() == 1 && !fresh.get(0).connected(), "new clock and pointer start a disconnected segment");
        near(NoiseCursorTrail.influenceAt(fresh, 10, 20, 42), 0, "cleared pointer position cannot remain red");
    }

    private static NoiseDocumentMotion motion() { return new NoiseDocumentMotion(0.42F, 0.18F); }

    private static NoiseDocumentMotion openDocument(String id, int x, int y) {
        NoiseDocumentMotion motion = motion();
        motion.update(id, 0.42F, x, y);
        near(motion.amount(), 1, "test document begins fully open");
        return motion;
    }

    private static void run(String name, Runnable scenario) {
        try { scenario.run(); }
        catch (AssertionError failure) {
            failures++;
            System.err.println("FAIL [" + name + "]: " + failure.getMessage());
        }
    }

    private static void near(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) <= 0.0001F,
                message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}
