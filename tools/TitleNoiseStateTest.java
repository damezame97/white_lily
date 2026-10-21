import com.damezame97.white_lily.client.TitleNoiseState;

import java.util.function.DoubleSupplier;

/** Pure JDK 17 behavior checks; no Minecraft, Forge, Mixin, desktop or random flakiness. */
public final class TitleNoiseStateTest {
    private static int checks;

    public static void main(String[] args) {
        constructionDoesNotRoll();
        twentyPercentBoundary();
        oneRollPerActualEntry();
        renderResizeReloadDoNotRoll();
        genuineReturnRollsAgain();
        visitedSpecialWindowLocksLaterEntries();
        visitDuringEntryDoesNotConsumeRandomness();
        independentLaunchStartsFresh();
        System.out.println("TitleNoiseStateTest: " + checks + " checks passed");
    }

    private static void constructionDoesNotRoll() {
        Draws draws = new Draws(0.01);
        TitleNoiseState state = new TitleNoiseState(draws);
        check(draws.count() == 0, "constructing a title that may never open must not consume a random roll");
        check(!state.isTitleActive(), "title is inactive before a committed entry");
        check(!state.hasVisitedSpecialWindow(), "a fresh launch has not visited the special window");
        state.leaveTitle();
        state.leaveTitle();
        check(draws.count() == 0 && !state.isTitleActive(), "redundant leave has no random side effects");
    }

    private static void twentyPercentBoundary() {
        for (double value : new double[]{0.0, 0.199999999, 0.20, 0.999999999}) {
            Draws draws = new Draws(value);
            TitleNoiseState state = new TitleNoiseState(draws);
            boolean expected = value < 0.20;
            check(state.enterTitle() == expected, "entry result observes the exact 20% boundary at " + value);
            check(state.isNoiseBackground() == expected, "background selection observes the exact boundary");
            check(draws.count() == 1, "one boundary sample consumes exactly one draw");
        }
        final int[] next = {0};
        TitleNoiseState state = new TitleNoiseState(() -> (next[0]++ + 0.5) / 100.0);
        int selected = 0;
        for (int i = 0; i < 100; i++) {
            if (state.enterTitle()) selected++;
            state.leaveTitle();
        }
        check(selected == 20 && next[0] == 100, "exactly twenty of a deterministic hundred equiprobable samples select noise");
    }

    private static void oneRollPerActualEntry() {
        Draws draws = new Draws(0.01, 0.99);
        TitleNoiseState state = new TitleNoiseState(draws);
        check(state.enterTitle(), "first entry selected the special background");
        for (int i = 0; i < 50; i++) {
            // Minecraft.setScreen(theSameTitleObject) can call added() again without removed().
            check(state.enterTitle() && state.isNoiseBackground(), "duplicate added while active cannot flicker or reroll");
        }
        check(draws.count() == 1 && state.isTitleActive(), "active entry has consumed exactly one draw");
    }

    private static void renderResizeReloadDoNotRoll() {
        Draws draws = new Draws(0.99, 0.01);
        TitleNoiseState state = new TitleNoiseState(draws);
        check(!state.enterTitle(), "ordinary panorama was selected");
        // Vanilla resizing/reloading invokes init/rebuild, not a committed leave/enter.
        for (int i = 0; i < 500; i++) {
            check(state.isTitleActive(), "title stays active while rendering or rebuilding widgets");
            check(!state.isNoiseBackground(), "panorama decision remains fixed throughout the entry");
        }
        check(draws.count() == 1, "render, resize and resource-reload queries never draw randomness");
    }

    private static void genuineReturnRollsAgain() {
        Draws draws = new Draws(0.05, 0.95, 0.10);
        TitleNoiseState state = new TitleNoiseState(draws);
        check(state.enterTitle(), "first real entry selected noise");
        state.leaveTitle();
        check(!state.isTitleActive(), "leaving the title closes its entry");
        check(!state.enterTitle(), "returning from another screen draws a new ordinary background");
        state.leaveTitle();
        check(state.enterTitle(), "a later real return can select noise again");
        check(draws.count() == 3, "three committed entries consume three draws before the visit lock");
    }

    private static void visitedSpecialWindowLocksLaterEntries() {
        Draws draws = new Draws(0.99);
        TitleNoiseState state = new TitleNoiseState(draws);
        check(!state.enterTitle(), "launch can begin with the ordinary background");
        state.leaveTitle();
        state.visitSpecialWindow();
        state.visitSpecialWindow();
        check(state.hasVisitedSpecialWindow() && draws.count() == 1,
                "real special-window visit records the launch lock without sampling randomness");
        for (int i = 0; i < 20; i++) {
            check(state.enterTitle() && state.isNoiseBackground(), "every later title entry uses noise after the visit");
            check(state.isTitleActive(), "forced entry is active");
            state.leaveTitle();
        }
        check(draws.count() == 1, "forced entries do not even consult the random supplier");
    }

    private static void visitDuringEntryDoesNotConsumeRandomness() {
        Draws draws = new Draws(0.99);
        TitleNoiseState state = new TitleNoiseState(draws);
        state.enterTitle();
        state.visitSpecialWindow();
        check(draws.count() == 1 && state.hasVisitedSpecialWindow(), "setting the visit lock is deterministic even during an active entry");
        state.leaveTitle();
        check(state.enterTitle() && state.isNoiseBackground(), "the next entry observes the visit lock");
        check(draws.count() == 1, "the lock prevents additional draws");
    }

    private static void independentLaunchStartsFresh() {
        TitleNoiseState oldLaunch = new TitleNoiseState(() -> { throw new AssertionError("visited launch should not roll"); });
        oldLaunch.visitSpecialWindow();
        check(oldLaunch.enterTitle(), "visited launch selects noise without random access");
        Draws freshDraws = new Draws(0.99);
        TitleNoiseState freshLaunch = new TitleNoiseState(freshDraws);
        check(!freshLaunch.hasVisitedSpecialWindow(), "visit lock belongs to its launch state");
        check(!freshLaunch.enterTitle() && freshDraws.count() == 1,
                "a new launch returns to the normal probability instead of inheriting an old visit");
    }

    private static final class Draws implements DoubleSupplier {
        private final double[] values;
        private int count;

        private Draws(double... values) { this.values = values; }

        @Override
        public double getAsDouble() {
            if (count >= values.length) throw new AssertionError("unexpected extra random draw #" + (count + 1));
            return values[count++];
        }

        private int count() { return count; }
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}
