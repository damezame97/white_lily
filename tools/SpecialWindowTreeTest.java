import com.damezame97.white_lily.client.SpecialWindowTree;
import com.damezame97.white_lily.client.SpecialWindowTree.Connection;
import com.damezame97.white_lily.client.SpecialWindowTree.Definition;
import com.damezame97.white_lily.client.SpecialWindowTree.NodeSpec;
import com.damezame97.white_lily.client.SpecialWindowTree.Point;
import com.damezame97.white_lily.client.SpecialWindowTree.Selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Standalone JDK 17 checks; no Minecraft, Forge, JUnit, Gradle, or assertion flags needed. */
public final class SpecialWindowTreeTest {
    private static int checks;

    public static void main(String[] args) {
        singlePrerequisite();
        andAndCascade();
        immutableSnapshots();
        customRoot();
        invalidDefinitions();
        defaultGraph();
        System.out.println("SpecialWindowTreeTest: " + checks + " checks passed");
    }

    private static void singlePrerequisite() {
        Selection selection = new Selection(definition(List.of(node("r"), node("a", "r")), "r"));
        check(selection.enabledIds().isEmpty(), "selection starts disabled");
        check(!selection.canEnable("a"), "child is locked without its parent");
        check(!selection.toggle("a"), "locked toggle does not change selection");
        selection.setRootEnabled(true);
        check(selection.canEnable("a"), "root unlocks its child");
        check(selection.toggle("a") && selection.isEnabled("a"), "child can turn on");
        selection.setRootEnabled(true);
        check(selection.isEnabled("a"), "repeated root synchronization preserves children");
        selection.setRootEnabled(false);
        check(selection.enabledIds().isEmpty(), "root disable cascades");
        selection.setRootEnabled(true);
        check(!selection.isEnabled("a"), "root re-enable does not revive children");
        check(selection.toggle("r") && !selection.isEnabled("r"), "root can toggle off");
        check(selection.toggle("r") && selection.isEnabled("r"), "root can toggle on again");
    }

    private static void andAndCascade() {
        Definition graph = definition(List.of(node("r"), node("a", "r"), node("b", "r"),
                node("a_leaf", "a"), node("b_leaf", "b"),
                node("merge", "a_leaf", "b_leaf"), node("tail", "merge")), "r");
        Selection selection = new Selection(graph);
        selection.setRootEnabled(true);
        selection.toggle("a");
        selection.toggle("a_leaf");
        check(!selection.canEnable("merge"), "AND rejects one missing parent");
        check(!selection.toggle("merge"), "AND missing-parent toggle has no effect");
        selection.toggle("b");
        selection.toggle("b_leaf");
        check(selection.canEnable("merge"), "AND permits both parents");
        selection.toggle("merge");
        selection.toggle("tail");
        check(selection.enabledIds().size() == 7, "entire shared-ancestor graph enabled");
        selection.toggle("a");
        check(!selection.isEnabled("a_leaf"), "single-parent descendant clears");
        check(!selection.isEnabled("merge"), "AND merge clears when one parent closes");
        check(!selection.isEnabled("tail"), "AND failure cascades through shared descendants");
        check(selection.isEnabled("b") && selection.isEnabled("b_leaf"), "independent branch remains on");
        selection.toggle("a");
        selection.toggle("a_leaf");
        check(selection.canEnable("merge") && !selection.isEnabled("merge"), "restored parents do not revive merge");
        check(!selection.isEnabled("tail"), "restored ancestors do not revive tail");
        selection.toggle("merge");
        selection.toggle("tail");
        selection.setRootEnabled(false);
        check(selection.enabledIds().isEmpty(), "shared root closes every branch exactly once");
    }

    private static void immutableSnapshots() {
        List<String> parents = new ArrayList<>(List.of("r"));
        NodeSpec child = new NodeSpec("a", 1, 2, 3, "a.title", "a.description", parents);
        parents.clear();
        check(child.prerequisites().equals(List.of("r")), "NodeSpec copies prerequisite input");
        List<Point> bends = new ArrayList<>(List.of(new Point(1, 2)));
        Connection edge = new Connection("r", "a", bends);
        bends.clear();
        check(edge.bends().size() == 1, "Connection copies bend input");
        List<NodeSpec> nodes = new ArrayList<>(List.of(node("r"), child));
        List<Connection> edges = new ArrayList<>(List.of(edge));
        Definition graph = new Definition("r", nodes, edges);
        nodes.clear(); edges.clear();
        check(graph.nodes().size() == 2 && graph.connections().size() == 1, "Definition copies input lists");
        expectUnsupported(() -> graph.nodes().clear(), "node list is immutable");
        expectUnsupported(() -> graph.prerequisitesOf("a").clear(), "prerequisite list is immutable");
        expectUnsupported(() -> graph.connections().get(0).bends().clear(), "bend list is immutable");
        Selection selection = new Selection(graph);
        selection.setRootEnabled(true);
        Set<String> snapshot = selection.enabledIds();
        selection.toggle("a");
        check(snapshot.equals(Set.of("r")), "enabledIds returns a snapshot");
        expectUnsupported(() -> snapshot.add("a"), "selection snapshot is immutable");
    }

    private static void customRoot() {
        Definition graph = definition(List.of(node("custom"), node("child", "custom")), "custom");
        Selection selection = new Selection(graph);
        selection.setRootEnabled(true);
        check(graph.rootId().equals("custom") && selection.isEnabled("custom"), "custom root controls correct id");
        expectInvalid(() -> graph.node("typo"), "unknown node lookup");
        expectInvalid(() -> graph.prerequisitesOf("typo"), "unknown prerequisite lookup");
        expectInvalid(() -> selection.toggle("typo"), "unknown toggle");
        expectInvalid(() -> selection.isEnabled("typo"), "unknown state query");
        expectInvalid(() -> selection.canEnable("typo"), "unknown availability query");
    }

    private static void invalidDefinitions() {
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("r")), List.of()), "duplicate node id");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a", "missing")), List.of()), "unknown prerequisite");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a", "a")), List.of()), "self dependency");
        expectInvalid(() -> definition(List.of(node("r"), node("a", "b"), node("b", "a")), "r"), "dependency cycle");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a", "r")), List.of()), "missing dependency edge");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a")), List.of(edge("r", "a"))), "extra edge");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a", "r")), List.of(edge("r", "a"), edge("r", "a"))), "duplicate edge");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a", "r")), List.of(edge("a", "r"))), "reversed dependency edge");
        expectInvalid(() -> new Definition("r", List.of(node("r")), List.of(edge("r", "missing"))), "unknown edge endpoint");
        expectInvalid(() -> new Definition("r", List.of(node("r"), node("a", "r", "r")), List.of(edge("r", "a"))), "duplicate prerequisite");
        expectInvalid(() -> new Definition("missing", List.of(node("r")), List.of()), "missing root");
        expectInvalid(() -> definition(List.of(node("r", "a"), node("a")), "r"), "root with a prerequisite");
        expectInvalid(() -> new Point(Float.NaN, 0), "non-finite bend coordinate");
        expectInvalid(() -> new NodeSpec("a", Float.POSITIVE_INFINITY, 0, 0, "t", "d", List.of()), "non-finite node coordinate");
    }

    private static void defaultGraph() {
        Definition graph = SpecialWindowTree.defaultDefinition();
        check(graph.rootId().equals(SpecialWindowTree.ROOT_ID), "default root id");
        check(graph.nodes().size() == 25 && graph.connections().size() == 28, "default graph has 25 nodes and 28 real dependency edges");
        check(graph.node(SpecialWindowTree.ROOT_ID).x() == 0 && graph.node(SpecialWindowTree.ROOT_ID).y() == 0, "root at origin");
        for (String direction : List.of("east", "south", "west", "north")) {
            check(graph.prerequisitesOf(direction + "_main").equals(List.of(SpecialWindowTree.ROOT_ID)), "first layer requires root: " + direction);
            check(graph.prerequisitesOf(direction + "_merge").equals(List.of(direction + "_left_leaf", direction + "_right_leaf")), "visible AND merge: " + direction);
        }
        for (NodeSpec node : graph.nodes()) {
            check(node.titleKey().equals("white_lily.special_window.node." + node.id() + ".title"), "stable title key: " + node.id());
            check(node.descriptionKey().equals("white_lily.special_window.node." + node.id() + ".description"), "stable description key: " + node.id());
        }
        for (Connection connection : graph.connections()) {
            List<Point> path = new ArrayList<>();
            NodeSpec from = graph.node(connection.fromId()), to = graph.node(connection.toId());
            path.add(new Point(from.x(), from.y())); path.addAll(connection.bends()); path.add(new Point(to.x(), to.y()));
            for (int i = 1; i < path.size(); i++) {
                Point a = path.get(i - 1), b = path.get(i);
                check(a.x() == b.x() || a.y() == b.y(), "default edge is an orthogonal polyline");
                for (NodeSpec other : graph.nodes()) {
                    if (other.id().equals(from.id()) || other.id().equals(to.id())) continue;
                    check(distance(other.x(), other.y(), a, b) > 28, "edge avoids unrelated node hit area: " + other.id());
                }
            }
        }
    }

    private static double distance(float x, float y, Point a, Point b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double t = Math.max(0, Math.min(1, ((x-a.x())*dx + (y-a.y())*dy) / (dx*dx+dy*dy)));
        return Math.hypot(x-a.x()-t*dx,y-a.y()-t*dy);
    }

    private static Definition definition(List<NodeSpec> nodes, String root) {
        List<Connection> connections = new ArrayList<>();
        for (NodeSpec node : nodes) for (String parent : node.prerequisites()) connections.add(edge(parent, node.id()));
        return new Definition(root, nodes, connections);
    }

    private static NodeSpec node(String id, String... prerequisites) {
        return new NodeSpec(id, 0, 0, 0, id + ".title", id + ".description", List.of(prerequisites));
    }

    private static Connection edge(String from, String to) { return new Connection(from, to, List.of()); }

    private static void expectInvalid(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected IllegalArgumentException: " + message);
    }

    private static void expectUnsupported(Runnable action, String message) {
        try { action.run(); } catch (UnsupportedOperationException expected) { checks++; return; }
        throw new AssertionError("Expected UnsupportedOperationException: " + message);
    }

    private static void check(boolean success, String message) {
        checks++;
        if (!success) throw new AssertionError(message);
    }
}
