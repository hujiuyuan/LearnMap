package com.learnmap.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 两张图谱的差异（纯逻辑）：节点按名称对齐（忽略大小写和空白），关系按“前置名称 → 后续名称 + 类型”对齐。
 */
public final class GraphDiff {

    private GraphDiff() {
    }

    public record DiffNode(Long id, String name, String definition, NodeType type, Long sectionId) {
        public static DiffNode of(KnowledgeNode n) {
            return new DiffNode(n.getId(), n.getName(), n.getDefinition(), n.getNodeType(), n.getSectionId());
        }
    }

    public record DiffRelation(String fromName, String toName, RelationType type) {
    }

    /** @param fields 改了哪些：定义 / 类型 */
    public record Changed(DiffNode before, DiffNode after, List<String> fields) {
    }

    /**
     * @param matched 旧节点 id → 新节点 id（名称相同的，不论内容是否变了）
     */
    public record Result(List<DiffNode> added, List<DiffNode> removed, List<Changed> changed,
                         List<DiffRelation> addedRelations, List<DiffRelation> removedRelations,
                         Map<Long, Long> matched) {
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && changed.isEmpty() && addedRelations.isEmpty()
                    && removedRelations.isEmpty();
        }
    }

    public static String key(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase();
    }

    public static Result compute(List<DiffNode> oldNodes, List<DiffRelation> oldRelations,
                                 List<DiffNode> newNodes, List<DiffRelation> newRelations) {
        Map<String, DiffNode> oldByKey = new LinkedHashMap<>();
        oldNodes.forEach(n -> oldByKey.putIfAbsent(key(n.name()), n));
        Map<String, DiffNode> newByKey = new LinkedHashMap<>();
        newNodes.forEach(n -> newByKey.putIfAbsent(key(n.name()), n));

        List<DiffNode> added = new ArrayList<>();
        List<Changed> changed = new ArrayList<>();
        Map<Long, Long> matched = new HashMap<>();
        for (Map.Entry<String, DiffNode> e : newByKey.entrySet()) {
            DiffNode before = oldByKey.get(e.getKey());
            DiffNode after = e.getValue();
            if (before == null) {
                added.add(after);
                continue;
            }
            matched.put(before.id(), after.id());
            List<String> fields = new ArrayList<>();
            if (!Objects.equals(norm(before.definition()), norm(after.definition()))) {
                fields.add("定义");
            }
            if (before.type() != after.type()) {
                fields.add("类型");
            }
            if (!fields.isEmpty()) {
                changed.add(new Changed(before, after, fields));
            }
        }
        List<DiffNode> removed = oldByKey.entrySet().stream().filter(e -> !newByKey.containsKey(e.getKey()))
                .map(Map.Entry::getValue).toList();

        Set<DiffRelation> oldRel = normalize(oldRelations);
        Set<DiffRelation> newRel = normalize(newRelations);
        List<DiffRelation> addedRel = newRelations.stream().filter(r -> !oldRel.contains(norm(r))).distinct().toList();
        List<DiffRelation> removedRel = oldRelations.stream().filter(r -> !newRel.contains(norm(r))).distinct().toList();
        return new Result(added, removed, changed, addedRel, removedRel, matched);
    }

    private static Set<DiffRelation> normalize(List<DiffRelation> rels) {
        Set<DiffRelation> out = new HashSet<>();
        rels.forEach(r -> out.add(norm(r)));
        return out;
    }

    private static DiffRelation norm(DiffRelation r) {
        return new DiffRelation(key(r.fromName()), key(r.toName()), r.type());
    }

    private static String norm(String s) {
        return s == null ? "" : s.strip();
    }
}
