package com.learnmap.graph;

import com.learnmap.book.Book;
import com.learnmap.book.BookAccessPolicy;
import com.learnmap.book.BookRepository;
import com.learnmap.book.BookSection;
import com.learnmap.book.BookSectionRepository;
import com.learnmap.book.SourceChunk;
import com.learnmap.book.SourceChunkRepository;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.graph.GraphDiff.DiffNode;
import com.learnmap.graph.GraphDiff.DiffRelation;
import com.learnmap.graph.GraphService.GraphSummary;
import com.learnmap.mastery.NodeMasteryRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 同一本书生成了新版本图谱：列出差异，由人选择“合并 / 保留旧版 / 换成新版”（产品方案 2.5）。
 * <ul>
 *   <li>书本图谱之间：运维 / 上传者重新生成后看新旧版本差异，再决定发布；</li>
 *   <li>学习者副本：不自动覆盖。学习者自己上传的书由学习者选，分配下来的书由辅导者选（没有辅导者就由学习者自己选）。</li>
 * </ul>
 * 有学习记录的节点不改内容、不删除，保证画像数据不丢；学习者自己删掉的节点和标记“有误”的关系保持原样。
 */
@Service
@RequiredArgsConstructor
public class GraphUpdateService {

    private final KnowledgeGraphRepository graphs;
    private final KnowledgeNodeRepository nodes;
    private final NodeRelationRepository relations;
    private final GraphMergeRecordRepository decisions;
    private final NodeMasteryRepository masteries;
    private final BookRepository books;
    private final BookSectionRepository sections;
    private final SourceChunkRepository chunks;
    private final BookAccessPolicy access;
    private final GraphGenerationService generation;
    private final GraphService graphService;
    private final UserService users;
    private final ApplicationEventPublisher events;

    public record NodeItem(Long id, String name, NodeType type, boolean hasRecords) {
    }

    public record ChangedItem(String name, List<String> fields, String before, String after, NodeType typeBefore,
                              NodeType typeAfter, boolean hasRecords) {
    }

    public record DiffView(Long fromGraphId, int fromVersion, Long toGraphId, int toVersion, List<NodeItem> added,
                           List<NodeItem> removed, List<ChangedItem> changed, List<String> addedRelations,
                           List<String> removedRelations, boolean empty) {
    }

    /**
     * @param available  有没有还没处理的新版本
     * @param canDecide  当前用户能不能做选择
     * @param decider    谁来选：“学习者本人”或“辅导者”
     */
    public record UpdateView(boolean available, boolean canDecide, String decider, DiffView diff) {
    }

    public record RegenerateResult(GraphSummary graph, DiffView diffFromPublished) {
    }

    public record ApplyResult(MergeDecision decision, String summary, GraphSummary graph) {
    }

    // ---------- 书本图谱：重新生成、版本差异 ----------

    @Transactional
    public RegenerateResult regenerate(AppUser actor, Long bookId) {
        Book book = books.findById(bookId).orElseThrow(() -> NotFoundException.of("书", bookId));
        access.checkCanManage(actor, book);
        Optional<KnowledgeGraph> before = latestPublished(bookId);
        List<BookSection> ss = sections.findByBookIdOrderByOrdinal(bookId);
        Map<Long, List<SourceChunk>> bySection = chunks.findByBookIdOrderByOrdinal(bookId).stream()
                .collect(Collectors.groupingBy(SourceChunk::getSectionId));
        KnowledgeGraph g = generation.generate(book, ss, bySection);
        DiffView diff = before.map(b -> diff(b, masterNodes(b), masterRelations(b), g, masterNodes(g), masterRelations(g), Set.of()))
                .orElse(null);
        return new RegenerateResult(GraphSummary.of(g), diff);
    }

    @Transactional(readOnly = true)
    public DiffView masterDiff(AppUser actor, Long graphId, Long againstId) {
        KnowledgeGraph to = graphService.require(graphId);
        KnowledgeGraph from = graphService.require(againstId);
        if (to.getKind() != GraphKind.MASTER || from.getKind() != GraphKind.MASTER || !to.getBookId().equals(from.getBookId())) {
            throw new IllegalArgumentException("只能比较同一本书的两个图谱版本");
        }
        graphService.checkCanView(actor, graphId);
        graphService.checkCanView(actor, againstId);
        return diff(from, masterNodes(from), masterRelations(from), to, masterNodes(to), masterRelations(to), Set.of());
    }

    // ---------- 学习者副本：有新版本时怎么处理 ----------

    @Transactional(readOnly = true)
    public UpdateView pendingUpdate(AppUser actor, Long copyId) {
        KnowledgeGraph copy = requireCopy(copyId);
        users.checkCanView(actor, copy.getOwnerLearnerId());
        Optional<KnowledgeGraph> latest = newerMaster(copy);
        String decider = deciderLabel(copy);
        if (latest.isEmpty()) {
            return new UpdateView(false, canDecide(actor, copy), decider, null);
        }
        KnowledgeGraph master = latest.get();
        return new UpdateView(true, canDecide(actor, copy), decider, copyDiff(copy, master));
    }

    @Transactional
    public ApplyResult apply(AppUser actor, Long copyId, MergeDecision decision) {
        KnowledgeGraph copy = requireCopy(copyId);
        if (!canDecide(actor, copy)) {
            throw new ForbiddenException("这本书的图谱更新由" + deciderLabel(copy) + "来选择");
        }
        KnowledgeGraph master = newerMaster(copy).orElseThrow(() -> new IllegalStateException("没有需要处理的新版本"));
        Long fromId = copy.getSourceGraphId();
        String summary = switch (decision) {
            case KEEP -> "保留旧版，没有改动";
            case MERGE, REPLACE -> update(copy, master, decision == MergeDecision.REPLACE);
        };
        GraphMergeRecord r = new GraphMergeRecord();
        r.setCopyGraphId(copy.getId());
        r.setFromGraphId(fromId);
        r.setToGraphId(master.getId());
        r.setDecision(decision);
        r.setDecidedBy(actor.getId());
        r.setSummary(summary);
        decisions.save(r);
        return new ApplyResult(decision, summary, GraphSummary.of(copy));
    }

    /** 真正改副本：返回一句话的改动摘要 */
    private String update(KnowledgeGraph copy, KnowledgeGraph master, boolean replace) {
        Long learner = copy.getOwnerLearnerId();
        List<KnowledgeNode> copyNodes = nodes.findByGraphIdOrderByOrdinal(copy.getId());
        List<KnowledgeNode> newNodes = masterNodes(master);
        Map<Long, KnowledgeNode> copyById = copyNodes.stream().collect(Collectors.toMap(KnowledgeNode::getId, Function.identity()));
        Map<Long, KnowledgeNode> newById = newNodes.stream().collect(Collectors.toMap(KnowledgeNode::getId, Function.identity()));
        Set<Long> withRecords = withRecords(learner, copyById.keySet());
        GraphDiff.Result d = GraphDiff.compute(copyNodes.stream().map(DiffNode::of).toList(), relationsOf(copy.getId(), copyById),
                newNodes.stream().map(DiffNode::of).toList(), masterRelations(master));

        int updated = 0;
        int kept = 0;
        // 名称对上的节点：题目改挂到新版节点上；没有学习记录的按新版更新内容
        for (Map.Entry<Long, Long> m : d.matched().entrySet()) {
            KnowledgeNode c = copyById.get(m.getKey());
            KnowledgeNode n = newById.get(m.getValue());
            c.setOriginNodeId(n.getId());
            if (!withRecords.contains(c.getId()) && !c.isDeleted()) {
                boolean changed = !Objects.equals(c.getDefinition(), n.getDefinition()) || c.getNodeType() != n.getNodeType();
                c.setDefinition(n.getDefinition());
                c.setNodeType(n.getNodeType());
                c.setSectionId(n.getSectionId());
                c.setReviewStatus(n.getReviewStatus());
                c.setSourceConfidence(n.getSourceConfidence());
                if (changed) {
                    updated++;
                }
            } else if (d.changed().stream().anyMatch(ch -> ch.before().id().equals(c.getId()))) {
                kept++;
            }
        }
        // 新版多出来的节点加进副本
        int ordinal = copyNodes.stream().mapToInt(KnowledgeNode::getOrdinal).max().orElse(0);
        Map<String, Long> copyIdByKey = new HashMap<>();
        copyNodes.forEach(c -> copyIdByKey.putIfAbsent(GraphDiff.key(c.getName()), c.getId()));
        List<Long> addedIds = new ArrayList<>();
        for (DiffNode a : d.added()) {
            KnowledgeNode c = newById.get(a.id()).copyTo(copy.getId());
            c.setOrdinal(++ordinal);
            c = nodes.save(c);
            copyIdByKey.put(GraphDiff.key(c.getName()), c.getId());
            addedIds.add(c.getId());
        }
        // 新版里没有了的节点：合并时保留；替换时没有学习记录的删掉
        int removed = 0;
        if (replace) {
            for (DiffNode r : d.removed()) {
                KnowledgeNode c = copyById.get(r.id());
                if (c.isDeleted()) {
                    continue;
                }
                if (withRecords.contains(c.getId())) {
                    kept++;
                } else {
                    c.setDeleted(true);
                    removed++;
                }
            }
        }
        // 关系：加上新版的；替换时去掉新版里没有的（学习者标过“有误”的保持原样）
        Map<String, KnowledgeNode> newByKey = new HashMap<>();
        newNodes.forEach(n -> newByKey.putIfAbsent(GraphDiff.key(n.getName()), n));
        for (NodeRelation r : relations.findByGraphId(master.getId())) {
            if (r.getReviewStatus() == ItemReviewStatus.REJECTED) {
                continue;
            }
            KnowledgeNode from = newById.get(r.getFromNodeId());
            KnowledgeNode to = newById.get(r.getToNodeId());
            if (from == null || to == null) {
                continue;
            }
            DiffRelation key = new DiffRelation(from.getName(), to.getName(), r.getRelationType());
            if (d.addedRelations().stream().noneMatch(x -> same(x, key))) {
                continue;
            }
            NodeRelation c = new NodeRelation();
            c.setGraphId(copy.getId());
            c.setFromNodeId(copyIdByKey.get(GraphDiff.key(from.getName())));
            c.setToNodeId(copyIdByKey.get(GraphDiff.key(to.getName())));
            c.setRelationType(r.getRelationType());
            c.setConfidence(r.getConfidence());
            c.setEvidence(r.getEvidence());
            c.setReviewStatus(r.getReviewStatus());
            relations.save(c);
        }
        int removedRelations = 0;
        if (replace) {
            for (NodeRelation r : relations.findByGraphId(copy.getId())) {
                KnowledgeNode from = copyById.get(r.getFromNodeId());
                KnowledgeNode to = copyById.get(r.getToNodeId());
                if (from == null || to == null || r.isDisputed()) {
                    continue;
                }
                DiffRelation key = new DiffRelation(from.getName(), to.getName(), r.getRelationType());
                if (d.removedRelations().stream().anyMatch(x -> same(x, key))) {
                    relations.delete(r);
                    removedRelations++;
                }
            }
        }
        copy.setSourceGraphId(master.getId());
        copy.setVersionNo(master.getVersionNo());
        copy.setReviewStatus(master.getReviewStatus());
        if (!addedIds.isEmpty()) {
            events.publishEvent(new LearnerNodesAdded(copy.getId(), learner, addedIds));
        }
        StringBuilder sb = new StringBuilder(replace ? "已换成新版：" : "已合并新版：");
        sb.append("新增 ").append(addedIds.size()).append(" 个知识点，更新 ").append(updated).append(" 个");
        if (replace) {
            sb.append("，删除 ").append(removed).append(" 个、").append(removedRelations).append(" 条关系");
        }
        if (kept > 0) {
            sb.append("；").append(kept).append(" 个有学习记录的知识点保持不变");
        }
        return sb.toString();
    }

    // ---------- helpers ----------

    private DiffView copyDiff(KnowledgeGraph copy, KnowledgeGraph master) {
        List<KnowledgeNode> copyNodes = nodes.findByGraphIdOrderByOrdinal(copy.getId());
        Map<Long, KnowledgeNode> byId = copyNodes.stream().collect(Collectors.toMap(KnowledgeNode::getId, Function.identity()));
        Set<Long> records = withRecords(copy.getOwnerLearnerId(), byId.keySet());
        DiffView v = diff(copy, copyNodes, relationsOf(copy.getId(), byId), master, masterNodes(master), masterRelations(master), records);
        // 学习者自己删掉的节点不算“新版删除”
        Set<Long> deleted = copyNodes.stream().filter(KnowledgeNode::isDeleted).map(KnowledgeNode::getId).collect(Collectors.toSet());
        return new DiffView(v.fromGraphId(), v.fromVersion(), v.toGraphId(), v.toVersion(), v.added(),
                v.removed().stream().filter(n -> !deleted.contains(n.id())).toList(),
                v.changed(), v.addedRelations(), v.removedRelations(), v.empty());
    }

    private DiffView diff(KnowledgeGraph from, List<KnowledgeNode> fromNodes, List<DiffRelation> fromRels,
                          KnowledgeGraph to, List<KnowledgeNode> toNodes, List<DiffRelation> toRels, Set<Long> withRecords) {
        GraphDiff.Result d = GraphDiff.compute(fromNodes.stream().map(DiffNode::of).toList(), fromRels,
                toNodes.stream().map(DiffNode::of).toList(), toRels);
        int fromVersion = from.getKind() == GraphKind.MASTER ? from.getVersionNo()
                : graphs.findById(from.getSourceGraphId()).map(KnowledgeGraph::getVersionNo).orElse(from.getVersionNo());
        return new DiffView(from.getKind() == GraphKind.MASTER ? from.getId() : from.getSourceGraphId(), fromVersion,
                to.getId(), to.getVersionNo(),
                d.added().stream().map(n -> new NodeItem(n.id(), n.name(), n.type(), false)).toList(),
                d.removed().stream().map(n -> new NodeItem(n.id(), n.name(), n.type(), withRecords.contains(n.id()))).toList(),
                d.changed().stream().map(c -> new ChangedItem(c.before().name(), c.fields(), c.before().definition(),
                        c.after().definition(), c.before().type(), c.after().type(), withRecords.contains(c.before().id()))).toList(),
                d.addedRelations().stream().map(GraphUpdateService::label).toList(),
                d.removedRelations().stream().map(GraphUpdateService::label).toList(),
                d.isEmpty());
    }

    private static String label(DiffRelation r) {
        String type = switch (r.type()) {
            case PREREQUISITE -> "前置";
            case RELATED -> "相关";
            case CONTRASTS -> "易混淆";
        };
        return r.fromName() + " → " + r.toName() + "（" + type + "）";
    }

    private static boolean same(DiffRelation a, DiffRelation b) {
        return GraphDiff.key(a.fromName()).equals(GraphDiff.key(b.fromName()))
                && GraphDiff.key(a.toName()).equals(GraphDiff.key(b.toName())) && a.type() == b.type();
    }

    /** 书本图谱里有效的节点：没删、没被校对驳回 */
    private List<KnowledgeNode> masterNodes(KnowledgeGraph g) {
        return nodes.findByGraphIdOrderByOrdinal(g.getId()).stream()
                .filter(n -> !n.isDeleted() && n.getReviewStatus() != ItemReviewStatus.REJECTED).toList();
    }

    private List<DiffRelation> masterRelations(KnowledgeGraph g) {
        Map<Long, KnowledgeNode> byId = masterNodes(g).stream().collect(Collectors.toMap(KnowledgeNode::getId, Function.identity()));
        return relations.findByGraphId(g.getId()).stream()
                .filter(r -> r.getReviewStatus() != ItemReviewStatus.REJECTED)
                .filter(r -> byId.containsKey(r.getFromNodeId()) && byId.containsKey(r.getToNodeId()))
                .map(r -> new DiffRelation(byId.get(r.getFromNodeId()).getName(), byId.get(r.getToNodeId()).getName(), r.getRelationType()))
                .toList();
    }

    private List<DiffRelation> relationsOf(Long graphId, Map<Long, KnowledgeNode> byId) {
        return relations.findByGraphId(graphId).stream()
                .filter(r -> byId.containsKey(r.getFromNodeId()) && byId.containsKey(r.getToNodeId()))
                .map(r -> new DiffRelation(byId.get(r.getFromNodeId()).getName(), byId.get(r.getToNodeId()).getName(), r.getRelationType()))
                .toList();
    }

    private Set<Long> withRecords(Long learnerId, Set<Long> nodeIds) {
        Set<Long> out = new HashSet<>();
        masteries.findByLearnerIdAndNodeIdIn(learnerId, nodeIds).forEach(m -> out.add(m.getNodeId()));
        return out;
    }

    private Optional<KnowledgeGraph> latestPublished(Long bookId) {
        return graphs.findFirstByBookIdAndKindAndStatusOrderByVersionNoDesc(bookId, GraphKind.MASTER, GraphStatus.PUBLISHED);
    }

    /** 比副本所基于的版本更新、且还没为它做过选择的已发布书本图谱 */
    private Optional<KnowledgeGraph> newerMaster(KnowledgeGraph copy) {
        return latestPublished(copy.getBookId())
                .filter(m -> !m.getId().equals(copy.getSourceGraphId()))
                .filter(m -> !decisions.existsByCopyGraphIdAndToGraphId(copy.getId(), m.getId()));
    }

    private KnowledgeGraph requireCopy(Long copyId) {
        KnowledgeGraph g = graphService.require(copyId);
        if (g.getKind() != GraphKind.LEARNER_COPY) {
            throw new IllegalArgumentException("只有学习者的图谱副本需要处理版本更新");
        }
        return g;
    }

    /** 学习者自己上传的书由学习者选；分配下来的书由辅导者选（没有辅导者就由学习者自己选） */
    private boolean tutorDecides(KnowledgeGraph copy) {
        Book book = books.findById(copy.getBookId()).orElseThrow();
        return !book.getOwnerId().equals(copy.getOwnerLearnerId()) && !users.tutorIdsOf(copy.getOwnerLearnerId()).isEmpty();
    }

    private boolean canDecide(AppUser actor, KnowledgeGraph copy) {
        return tutorDecides(copy) ? users.isTutorOf(actor.getId(), copy.getOwnerLearnerId())
                : actor.getId().equals(copy.getOwnerLearnerId());
    }

    private String deciderLabel(KnowledgeGraph copy) {
        return tutorDecides(copy) ? "辅导者" : "学习者本人";
    }
}
