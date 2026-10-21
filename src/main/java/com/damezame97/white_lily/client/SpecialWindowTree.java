package com.damezame97.white_lily.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 特殊窗口的节点定义与依赖状态。只依赖 JDK，不读取 Minecraft 世界，也不执行游戏效果。
 *
 * <p>扩展节点时，先为 {@link NodeSpec} 分配稳定且唯一的 id，再添加两条语言键对应的文本。
 * x/y 是以原点为中心、缩放之前的树坐标；{@code visualSeed} 仅控制碎片外观，不能充当节点身份。
 * {@code prerequisites} 是依赖关系的唯一权威：空列表表示无需前置，单项表示单前置，多项表示
 * 所列前置必须全部开启（AND）。不要仅为了画一条线而添加依赖，也不要只画线而遗漏前置。</p>
 *
 * <p>每条依赖都必须对应一条 {@link Connection}，方向为「前置 -> 后继」。bends 只定义显示路径，
 * 不改变依赖语义；完整折线是 from 节点坐标、按顺序排列的 bends、to 节点坐标。新增折点时，
 * 应避开其他节点及其可点击区域，以免看起来像连接到了错误节点。四向默认图使用同一局部坐标
 * 旋转，保留大体树形，并为每个方向安排一个拥有两条输入线的汇合节点。</p>
 *
 * <p>调试定义时直接构造 {@link Definition}：它会拒绝重复 id、未知前置、自依赖、环，以及
 * 连线遗漏、额外连线、重复连线。定义及所有嵌套列表不可变，因此可被多个创建世界草稿共享。
 * 每份草稿单独持有 {@link Selection}，不要把可变选择状态存进静态默认定义。</p>
 *
 * <p>普通节点当前只代表示例选择。未来的游戏效果应由 Screen 在成功修改 Selection 后调用，
 * 并同时处理级联关闭的节点；本类始终只维护数据和依赖，不在这里接入 Minecraft 或存档副作用。</p>
 */
public final class SpecialWindowTree {
    public static final String ROOT_ID = "happy_mode";
    private static final String TEXT_PREFIX = "white_lily.special_window.node.";

    /** 节点身份、布局、外观种子、文本与真实前置；列表会防御性复制。 */
    public record NodeSpec(String id, float x, float y, int visualSeed,
                           String titleKey, String descriptionKey, List<String> prerequisites) {
        public NodeSpec {
            requireText(id, "节点 id");
            requireText(titleKey, "节点标题语言键");
            requireText(descriptionKey, "节点说明语言键");
            requireFinite(x, y);
            prerequisites = immutableList(prerequisites, "前置列表");
            for (String prerequisite : prerequisites) requireText(prerequisite, "前置 id");
        }
    }

    /** 连线的一个中转点，坐标系与 NodeSpec 相同。 */
    public record Point(float x, float y) {
        public Point { requireFinite(x, y); }
    }

    /** fromId 必须是真实前置，toId 必须是依赖它的节点；折点只影响画线。 */
    public record Connection(String fromId, String toId, List<Point> bends) {
        public Connection {
            requireText(fromId, "连线起点 id");
            requireText(toId, "连线终点 id");
            bends = immutableList(bends, "连线折点");
        }
    }

    /** 完整、已校验且不可变的图。nodes/connections 顺序保留，方便稳定渲染与调试。 */
    public static final class Definition {
        private final String rootId;
        private final List<NodeSpec> nodes;
        private final List<Connection> connections;
        private final Map<String, NodeSpec> byId;
        private final Map<String, List<String>> dependents;

        public Definition(List<NodeSpec> nodes, List<Connection> connections) {
            this(ROOT_ID, nodes, connections);
        }

        public Definition(String rootId, List<NodeSpec> nodes, List<Connection> connections) {
            requireText(rootId, "根节点 id");
            this.rootId = rootId;
            this.nodes = immutableList(nodes, "节点列表");
            this.connections = immutableList(connections, "连线列表");
            Map<String, NodeSpec> index = new LinkedHashMap<>();
            Map<String, List<String>> children = new LinkedHashMap<>();
            for (NodeSpec node : this.nodes) {
                if (index.putIfAbsent(node.id(), node) != null)
                    throw new IllegalArgumentException("重复节点 id: " + node.id());
                children.put(node.id(), new ArrayList<>());
            }
            if (!index.containsKey(rootId)) throw new IllegalArgumentException("根节点不存在: " + rootId);
            if (!index.get(rootId).prerequisites().isEmpty())
                throw new IllegalArgumentException("根节点不能拥有前置: " + rootId);

            Set<Edge> expected = new LinkedHashSet<>();
            for (NodeSpec node : this.nodes) {
                Set<String> unique = new LinkedHashSet<>();
                for (String prerequisite : node.prerequisites()) {
                    if (!index.containsKey(prerequisite))
                        throw new IllegalArgumentException("未知前置: " + node.id() + " <- " + prerequisite);
                    if (node.id().equals(prerequisite))
                        throw new IllegalArgumentException("节点不能依赖自身: " + node.id());
                    if (!unique.add(prerequisite))
                        throw new IllegalArgumentException("重复前置: " + node.id() + " <- " + prerequisite);
                    expected.add(new Edge(prerequisite, node.id()));
                    children.get(prerequisite).add(node.id());
                }
            }
            verifyAcyclic(this.nodes, children);

            Set<Edge> drawn = new LinkedHashSet<>();
            for (Connection connection : this.connections) {
                Edge edge = new Edge(connection.fromId(), connection.toId());
                if (!index.containsKey(edge.from()) || !index.containsKey(edge.to()))
                    throw new IllegalArgumentException("连线引用未知节点: " + edge);
                if (!expected.contains(edge))
                    throw new IllegalArgumentException("连线不对应真实前置: " + edge);
                if (!drawn.add(edge)) throw new IllegalArgumentException("重复连线: " + edge);
            }
            if (!drawn.equals(expected)) {
                Set<Edge> missing = new LinkedHashSet<>(expected);
                missing.removeAll(drawn);
                throw new IllegalArgumentException("遗漏前置连线: " + missing);
            }
            this.byId = Collections.unmodifiableMap(index);
            Map<String, List<String>> frozenChildren = new LinkedHashMap<>();
            children.forEach((id, ids) -> frozenChildren.put(id, List.copyOf(ids)));
            this.dependents = Collections.unmodifiableMap(frozenChildren);
        }

        public String rootId() { return rootId; }
        public List<NodeSpec> nodes() { return nodes; }
        public List<Connection> connections() { return connections; }

        /** 未知 id 视为编程错误，立即报告，避免拼错 id 后悄悄显示错误状态。 */
        public NodeSpec node(String id) {
            NodeSpec node = byId.get(id);
            if (node == null) throw new IllegalArgumentException("未知节点 id: " + id);
            return node;
        }

        public List<String> prerequisitesOf(String id) { return node(id).prerequisites(); }

        private static void verifyAcyclic(List<NodeSpec> nodes, Map<String, List<String>> children) {
            Map<String, Integer> remaining = new LinkedHashMap<>();
            ArrayDeque<String> ready = new ArrayDeque<>();
            for (NodeSpec node : nodes) {
                int count = node.prerequisites().size();
                remaining.put(node.id(), count);
                if (count == 0) ready.add(node.id());
            }
            int visited = 0;
            while (!ready.isEmpty()) {
                String id = ready.remove();
                visited++;
                for (String child : children.get(id)) {
                    int count = remaining.compute(child, (key, value) -> value - 1);
                    if (count == 0) ready.add(child);
                }
            }
            if (visited != nodes.size()) throw new IllegalArgumentException("节点前置中存在环");
        }
    }

    /**
     * 一份创建草稿的可变选择，初始全部关闭。所有公开查询都会检查 id 是否存在。
     * canEnable 按所有前置做 AND；toggle 对锁定节点返回 false，不产生任何状态改变。
     * 关闭节点会沿后继表迭代清除失效节点，即使某个后继还有另一个开启的前置，也必须关闭。
     * 之后重新开启前置不会复活后继，玩家必须重新主动选择，避免无提示恢复旧选择。
     */
    public static final class Selection {
        private final Definition definition;
        private final Set<String> enabled = new LinkedHashSet<>();

        public Selection(Definition definition) {
            if (definition == null) throw new IllegalArgumentException("节点定义不能为空");
            this.definition = definition;
        }

        public Definition definition() { return definition; }

        public boolean isEnabled(String id) {
            definition.node(id);
            return enabled.contains(id);
        }

        public boolean canEnable(String id) {
            return enabled.containsAll(definition.prerequisitesOf(id));
        }

        /** 返回是否发生实际变更；已开启的节点始终可以关闭。 */
        public boolean toggle(String id) {
            definition.node(id);
            if (enabled.contains(id)) {
                disableAndCascade(id);
                return true;
            }
            if (!canEnable(id)) return false;
            enabled.add(id);
            return true;
        }

        /** 用世界草稿的实际 Happy Mode 同步根节点；重复同步相同值不会清空子节点。 */
        public void setRootEnabled(boolean value) {
            if (value) enabled.add(definition.rootId());
            else disableAndCascade(definition.rootId());
        }

        /** 不可变快照；后续 toggle 不会改变已返回的集合。 */
        public Set<String> enabledIds() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(enabled));
        }

        private void disableAndCascade(String id) {
            if (!enabled.remove(id)) return;
            ArrayDeque<String> changed = new ArrayDeque<>();
            changed.add(id);
            while (!changed.isEmpty()) {
                for (String child : definition.dependents.get(changed.remove())) {
                    if (enabled.contains(child) && !canEnable(child)) {
                        enabled.remove(child);
                        changed.add(child);
                    }
                }
            }
        }
    }

    private static final Definition DEFAULT_DEFINITION = createDefaultDefinition();

    public static Definition defaultDefinition() { return DEFAULT_DEFINITION; }

    private static Definition createDefaultDefinition() {
        List<NodeSpec> nodes = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        nodes.add(spec(ROOT_ID, 0, 0, 97, List.of()));
        String[] directions = {"east", "south", "west", "north"};
        for (int turn = 0; turn < directions.length; turn++) {
            String prefix = directions[turn] + "_";
            String main = prefix + "main", left = prefix + "left", right = prefix + "right";
            String leftLeaf = prefix + "left_leaf", rightLeaf = prefix + "right_leaf", merge = prefix + "merge";
            addNode(nodes, main, 110, 0, 41 + turn * 67, turn, List.of(ROOT_ID));
            addNode(nodes, left, 220, -85, 79 + turn * 67, turn, List.of(main));
            addNode(nodes, right, 220, 85, 113 + turn * 67, turn, List.of(main));
            addNode(nodes, leftLeaf, 330, -125, 151 + turn * 67, turn, List.of(left));
            addNode(nodes, rightLeaf, 330, 125, 193 + turn * 67, turn, List.of(right));
            addNode(nodes, merge, 420, 0, 229 + turn * 67, turn, List.of(leftLeaf, rightLeaf));

            // 每条路径恰好对应一条真实依赖。汇合点保留两条独立输入，不能画成一条虚假的单前置线。
            connections.add(new Connection(ROOT_ID, main, List.of()));
            addConnection(connections, main, left, turn, 160, 0, 160, -85);
            addConnection(connections, main, right, turn, 160, 0, 160, 85);
            addConnection(connections, left, leftLeaf, turn, 275, -85, 275, -125);
            addConnection(connections, right, rightLeaf, turn, 275, 85, 275, 125);
            addConnection(connections, leftLeaf, merge, turn, 385, -125, 385, -38, 420, -38);
            addConnection(connections, rightLeaf, merge, turn, 385, 125, 385, 38, 420, 38);
        }
        return new Definition(nodes, connections);
    }

    private static NodeSpec spec(String id, float x, float y, int seed, List<String> prerequisites) {
        return new NodeSpec(id, x, y, seed, TEXT_PREFIX + id + ".title",
                TEXT_PREFIX + id + ".description", prerequisites);
    }

    private static void addNode(List<NodeSpec> nodes, String id, float x, float y,
                                int seed, int turn, List<String> prerequisites) {
        Point p = rotate(x, y, turn);
        nodes.add(spec(id, p.x(), p.y(), seed, prerequisites));
    }

    private static void addConnection(List<Connection> connections, String from, String to,
                                      int turn, float... coordinates) {
        List<Point> bends = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2)
            bends.add(rotate(coordinates[i], coordinates[i + 1], turn));
        connections.add(new Connection(from, to, bends));
    }

    private static Point rotate(float x, float y, int turn) {
        return switch (turn) {
            case 1 -> new Point(-y, x);
            case 2 -> new Point(-x, -y);
            case 3 -> new Point(y, -x);
            default -> new Point(x, y);
        };
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    }

    private static void requireFinite(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("节点/折点坐标必须有限");
    }

    private static <T> List<T> immutableList(List<T> values, String label) {
        if (values == null || values.stream().anyMatch(value -> value == null))
            throw new IllegalArgumentException(label + "及其元素不能为空");
        return List.copyOf(values);
    }

    private record Edge(String from, String to) { }

    private SpecialWindowTree() { }
}
