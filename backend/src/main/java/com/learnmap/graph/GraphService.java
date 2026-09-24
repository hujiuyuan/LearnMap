package com.learnmap.graph;

import com.learnmap.book.Book;
import com.learnmap.book.BookAccessPolicy;
import com.learnmap.book.BookRepository;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.mastery.NodeMastery;
import com.learnmap.mastery.NodeMasteryRepository;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final KnowledgeGraphRepository graphs;
    private final KnowledgeNodeRepository nodes;
    private final NodeRelationRepository relations;
    private final NodeMasteryRepository masteries;
    private final BookRepository books;
    private final BookAccessPolicy access;
    private final UserService users;
    private final ApplicationEventPublisher events;

    // ---------- 查询 ----------

    public record NodeView(Long id, String name, String definition, NodeType type, double sourceConfidence,
                           ItemReviewStatus reviewStatus, Long sectionId, int ordinal,
                           Double mastery, Double confidence) {
    }

    public record RelationView(Long id, Long fromNodeId, Long toNodeId, RelationType type, double confidence,
                               String evidence, ItemReviewStatus reviewStatus, boolean disputed) {
    }

    public record GraphView(Long id, Long bookId, GraphKind kind, int versionNo, Long ownerLearnerId,
                            GraphStatus status, GraphReviewStatus reviewStatus,
                            List<NodeView> nodes, List<RelationView> relations) {
    }

    public record GraphSummary(Long id, Long bookId, GraphKind kind, int versionNo, Long ownerLearnerId,
                               GraphStatus status, GraphReviewStatus reviewStatus) {
        static GraphSummary of(KnowledgeGraph g) {
            return new GraphSummary(g.getId(), g.getBookId(), g.getKind(), g.getVersionNo(), g.getOwnerLearnerId(),
                    g.getStatus(), g.getReviewStatus());
        }
    }

    public KnowledgeGraph require(Long graphId) {
        return graphs.findById(graphId).orElseThrow(() -> NotFoundException.of("图谱", graphId));
    }

    @Transactional(readOnly = true)
    public GraphView view(AppUser actor, Long graphId) {
        KnowledgeGraph g = require(graphId);
        checkCanView(actor, g);
        List<KnowledgeNode> ns = nodes.findByGraphIdOrderByOrdinal(graphId).stream().filter(n -> !n.isDeleted()).toList();
        Set<Long> visible = ns.stream().map(KnowledgeNode::getId).collect(Collectors.toSet());
        Map<Long, NodeMastery> mastery = g.getKind() == GraphKind.LEARNER_COPY
                ? masteries.findByLearnerIdAndNodeIdIn(g.getOwnerLearnerId(), visible).stream()
                .collect(Collectors.toMap(NodeMastery::getNodeId, Function.identity()))
                : Map.of();
        List<NodeView> nodeViews = ns.stream().map(n -> {
            NodeMastery m = mastery.get(n.getId());
            return new NodeView(n.getId(), n.getName(), n.getDefinition(), n.getNodeType(), n.getSourceConfidence(),
                    n.getReviewStatus(), n.getSectionId(), n.getOrdinal(),
                    m == null ? null : m.getMastery(), m == null ? null : m.getConfidence());
        }).toList();
        List<RelationView> relViews = relations.findByGraphId(graphId).stream()
                .filter(r -> visible.contains(r.getFromNodeId()) && visible.contains(r.getToNodeId()))
                .map(r -> new RelationView(r.getId(), r.getFromNodeId(), r.getToNodeId(), r.getRelationType(),
                        r.getConfidence(), r.getEvidence(), r.getReviewStatus(), r.isDisputed()))
                .toList();
        return new GraphView(g.getId(), g.getBookId(), g.getKind(), g.getVersionNo(), g.getOwnerLearnerId(),
                g.getStatus(), g.getReviewStatus(), nodeViews, relViews);
    }

    public List<GraphSummary> mastersOf(AppUser actor, Long bookId) {
        Book book = requireBook(bookId);
        access.checkCanSee(actor, book);
        boolean manager = actor.getRole() == Role.OPS || book.getOwnerId().equals(actor.getId());
        return graphs.findByBookIdAndKindOrderByVersionNoDesc(bookId, GraphKind.MASTER).stream()
                .filter(g -> manager || g.getStatus() == GraphStatus.PUBLISHED)
                .map(GraphSummary::of).toList();
    }

    public List<GraphSummary> learnerGraphs(AppUser actor, Long learnerId) {
        users.checkCanView(actor, learnerId);
        return graphs.findByOwnerLearnerIdAndKind(learnerId, GraphKind.LEARNER_COPY).stream().map(GraphSummary::of).toList();
    }

    public void checkCanView(AppUser actor, Long graphId) {
        checkCanView(actor, require(graphId));
    }

    private void checkCanView(AppUser actor, KnowledgeGraph g) {
        if (g.getKind() == GraphKind.LEARNER_COPY) {
            users.checkCanView(actor, g.getOwnerLearnerId());
            return;
        }
        Book book = requireBook(g.getBookId());
        access.checkCanSee(actor, book);
        if (g.getStatus() != GraphStatus.PUBLISHED) {
            access.checkCanManage(actor, book);
        }
    }

    // ---------- 发布 ----------

    @Transactional
    public GraphSummary publish(AppUser actor, Long graphId, boolean reviewed) {
        KnowledgeGraph g = require(graphId);
        if (g.getKind() != GraphKind.MASTER) {
            throw new IllegalArgumentException("只有书本图谱需要发布");
        }
        access.checkCanManage(actor, requireBook(g.getBookId()));
        return GraphSummary.of(publishMaster(g, reviewed));
    }

    /** 发布一个 MASTER 版本：旧的已发布版本标为 SUPERSEDED（已分配给学习者的副本不受影响）。 */
    @Transactional
    public KnowledgeGraph publishMaster(KnowledgeGraph g, boolean reviewed) {
        graphs.findByBookIdAndKindOrderByVersionNoDesc(g.getBookId(), GraphKind.MASTER).stream()
                .filter(old -> old.getStatus() == GraphStatus.PUBLISHED && !old.getId().equals(g.getId()))
                .forEach(old -> old.setStatus(GraphStatus.SUPERSEDED));
        g.setStatus(GraphStatus.PUBLISHED);
        if (reviewed) {
            g.setReviewStatus(GraphReviewStatus.REVIEWED);
            // 校对者整体确认：还没被单独驳回的节点和关系视为已确认
            nodes.findByGraphIdOrderByOrdinal(g.getId()).stream()
                    .filter(n -> n.getReviewStatus() == ItemReviewStatus.PENDING)
                    .forEach(n -> n.setReviewStatus(ItemReviewStatus.CONFIRMED));
            relations.findByGraphId(g.getId()).stream()
                    .filter(r -> r.getReviewStatus() == ItemReviewStatus.PENDING)
                    .forEach(r -> r.setReviewStatus(ItemReviewStatus.CONFIRMED));
        }
        return graphs.save(g);
    }

    // ---------- 分配：每个学习者一份副本 ----------

    /**
     * 把书的最新已发布图谱复制一份给学习者。学习者已经有这本书的副本时直接返回原副本；
     * 新旧版本不同时的“合并 / 保留 / 替换”选择在后续 PR 实现。
     */
    @Transactional
    public GraphSummary assign(AppUser actor, Long bookId, Long learnerId) {
        Book book = requireBook(bookId);
        AppUser learner = users.require(learnerId);
        if (learner.getRole() != Role.LEARNER) {
            throw new IllegalArgumentException("只能把书分配给学习者");
        }
        if (!actor.getId().equals(learnerId)) {
            if (!users.isTutorOf(actor.getId(), learnerId)) {
                throw new ForbiddenException("只有本人或他的辅导者可以分配书");
            }
        }
        access.checkCanSee(actor, book);
        access.checkCanSee(learner, book);

        Optional<KnowledgeGraph> existing = graphs.findByOwnerLearnerIdAndBookIdAndKind(learnerId, bookId, GraphKind.LEARNER_COPY);
        if (existing.isPresent()) {
            return GraphSummary.of(existing.get());
        }
        KnowledgeGraph master = graphs.findFirstByBookIdAndKindAndStatusOrderByVersionNoDesc(bookId, GraphKind.MASTER, GraphStatus.PUBLISHED)
                .orElseThrow(() -> new IllegalStateException("这本书还没有已发布的图谱"));
        return GraphSummary.of(copyForLearner(master, learnerId));
    }

    private KnowledgeGraph copyForLearner(KnowledgeGraph master, Long learnerId) {
        KnowledgeGraph copy = new KnowledgeGraph();
        copy.setBookId(master.getBookId());
        copy.setKind(GraphKind.LEARNER_COPY);
        copy.setVersionNo(master.getVersionNo());
        copy.setOwnerLearnerId(learnerId);
        copy.setSourceGraphId(master.getId());
        copy.setStatus(GraphStatus.PUBLISHED);
        copy.setReviewStatus(master.getReviewStatus());
        copy = graphs.save(copy);

        Map<Long, Long> idMap = new HashMap<>();
        for (KnowledgeNode n : nodes.findByGraphIdOrderByOrdinal(master.getId())) {
            idMap.put(n.getId(), nodes.save(n.copyTo(copy.getId())).getId());
        }
        for (NodeRelation r : relations.findByGraphId(master.getId())) {
            NodeRelation c = new NodeRelation();
            c.setGraphId(copy.getId());
            c.setFromNodeId(idMap.get(r.getFromNodeId()));
            c.setToNodeId(idMap.get(r.getToNodeId()));
            c.setRelationType(r.getRelationType());
            c.setConfidence(r.getConfidence());
            c.setEvidence(r.getEvidence());
            c.setReviewStatus(r.getReviewStatus());
            relations.save(c);
        }
        events.publishEvent(new LearnerGraphCreated(copy.getId(), learnerId));
        return copy;
    }

    /** MASTER 节点 id → 该学习者副本里的对应节点（题目挂在 MASTER 节点上，证据写在副本节点上）。 */
    public Map<Long, KnowledgeNode> learnerNodesFor(Long learnerId, Long bookId, Collection<Long> masterNodeIds) {
        KnowledgeGraph copy = graphs.findByOwnerLearnerIdAndBookIdAndKind(learnerId, bookId, GraphKind.LEARNER_COPY)
                .orElseThrow(() -> new IllegalStateException("学习者还没有这本书的图谱，请先分配"));
        return nodes.findByGraphIdAndOriginNodeIdIn(copy.getId(), masterNodeIds).stream()
                .filter(n -> !n.isDeleted())
                .collect(Collectors.toMap(KnowledgeNode::getOriginNodeId, Function.identity()));
    }

    // ---------- 学习者对自己副本的轻量修正 ----------

    @Transactional
    public void deleteNode(AppUser actor, Long graphId, Long nodeId) {
        KnowledgeGraph g = requireOwnCopy(actor, graphId);
        KnowledgeNode n = nodes.findById(nodeId).filter(x -> x.getGraphId().equals(g.getId()))
                .orElseThrow(() -> NotFoundException.of("节点", nodeId));
        n.setDeleted(true);
    }

    @Transactional
    public void disputeRelation(AppUser actor, Long graphId, Long relationId) {
        KnowledgeGraph g = requireOwnCopy(actor, graphId);
        NodeRelation r = relations.findById(relationId).filter(x -> x.getGraphId().equals(g.getId()))
                .orElseThrow(() -> NotFoundException.of("关系", relationId));
        r.setDisputed(true);
    }

    private KnowledgeGraph requireOwnCopy(AppUser actor, Long graphId) {
        KnowledgeGraph g = require(graphId);
        if (g.getKind() != GraphKind.LEARNER_COPY || !actor.getId().equals(g.getOwnerLearnerId())) {
            throw new ForbiddenException("只能修正自己的图谱副本");
        }
        return g;
    }

    private Book requireBook(Long bookId) {
        return books.findById(bookId).orElseThrow(() -> NotFoundException.of("书", bookId));
    }
}
