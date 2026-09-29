package com.learnmap.graph;

import com.learnmap.book.Book;
import com.learnmap.book.BookAccessPolicy;
import com.learnmap.book.BookRepository;
import com.learnmap.book.SourceChunk;
import com.learnmap.book.SourceChunkRepository;
import com.learnmap.common.NotFoundException;
import com.learnmap.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图谱编辑器（书本图谱）：运维和上传书的辅导者校对 AI 生成的节点和关系。
 * 每条关系都带着原文证据，节点能看到原文出处；改过的节点和关系记为“已确认”。
 * 已经分配给学习者的副本不受影响，学习者那边会看到“有新版本”，由人选择是否合并。
 */
@Service
@RequiredArgsConstructor
public class GraphEditService {

    static final int EXCERPT_LENGTH = 200;

    private final GraphService graphService;
    private final KnowledgeNodeRepository nodes;
    private final NodeRelationRepository relations;
    private final NodeSourceRepository nodeSources;
    private final SourceChunkRepository chunks;
    private final BookRepository books;
    private final BookAccessPolicy access;
    private final GraphGenerationService generation;

    public record NodeEdit(String name, String definition, NodeType type) {
    }

    public record NewNode(String name, String definition, NodeType type, Long sectionId) {
    }

    public record RelationLine(Long id, Long otherNodeId, String otherName, RelationType type, boolean incoming,
                               double confidence, String evidence, ItemReviewStatus reviewStatus, boolean disputed) {
    }

    public record NodeDetail(Long id, Long graphId, String name, String definition, NodeType type, double sourceConfidence,
                             ItemReviewStatus reviewStatus, Long sectionId, List<String> sources, List<RelationLine> relations) {
    }

    /** 节点详情：定义、原文出处、前置与后续（学习者、辅导者、编辑器共用） */
    @Transactional(readOnly = true)
    public NodeDetail detail(AppUser actor, Long graphId, Long nodeId) {
        graphService.checkCanView(actor, graphId);
        KnowledgeNode n = node(graphId, nodeId);
        Long sourceNode = n.getOriginNodeId() != null ? n.getOriginNodeId() : n.getId();
        List<String> sources = nodeSources.findByNodeIdIn(List.of(sourceNode)).stream()
                .map(ns -> chunks.findById(ns.getChunkId()).map(SourceChunk::getContent).orElse(null))
                .filter(c -> c != null)
                .map(c -> c.length() > EXCERPT_LENGTH ? c.substring(0, EXCERPT_LENGTH) + "…" : c)
                .toList();
        Map<Long, KnowledgeNode> all = nodes.findByGraphIdOrderByOrdinal(graphId).stream()
                .collect(Collectors.toMap(KnowledgeNode::getId, Function.identity()));
        List<RelationLine> rels = relations.findByGraphId(graphId).stream()
                .filter(r -> r.getFromNodeId().equals(nodeId) || r.getToNodeId().equals(nodeId))
                .map(r -> {
                    boolean incoming = r.getToNodeId().equals(nodeId);
                    Long other = incoming ? r.getFromNodeId() : r.getToNodeId();
                    KnowledgeNode o = all.get(other);
                    return new RelationLine(r.getId(), other, o == null ? null : o.getName(), r.getRelationType(), incoming,
                            r.getConfidence(), r.getEvidence(), r.getReviewStatus(), r.isDisputed());
                })
                .filter(l -> l.otherName() != null && !all.get(l.otherNodeId()).isDeleted())
                .toList();
        return new NodeDetail(n.getId(), graphId, n.getName(), n.getDefinition(), n.getNodeType(), n.getSourceConfidence(),
                n.getReviewStatus(), n.getSectionId(), sources, rels);
    }

    @Transactional
    public NodeDetail updateNode(AppUser actor, Long graphId, Long nodeId, NodeEdit edit) {
        checkEditable(actor, graphId);
        KnowledgeNode n = node(graphId, nodeId);
        if (edit.name() != null && !edit.name().isBlank()) {
            n.setName(edit.name().strip());
        }
        if (edit.definition() != null) {
            n.setDefinition(edit.definition().strip());
        }
        if (edit.type() != null) {
            n.setNodeType(edit.type());
        }
        n.setReviewStatus(ItemReviewStatus.CONFIRMED);
        return detail(actor, graphId, nodeId);
    }

    /** 确认 / 驳回 / 恢复待校对。驳回的节点不会复制给学习者 */
    @Transactional
    public NodeDetail reviewNode(AppUser actor, Long graphId, Long nodeId, ItemReviewStatus status) {
        checkEditable(actor, graphId);
        KnowledgeNode n = node(graphId, nodeId);
        n.setReviewStatus(status);
        n.setDeleted(status == ItemReviewStatus.REJECTED);
        return detail(actor, graphId, nodeId);
    }

    @Transactional
    public NodeDetail addNode(AppUser actor, Long graphId, NewNode req) {
        KnowledgeGraph g = checkEditable(actor, graphId);
        if (req.name() == null || req.name().isBlank() || req.type() == null) {
            throw new IllegalArgumentException("请填写知识点名称和类型");
        }
        List<KnowledgeNode> existing = nodes.findByGraphIdOrderByOrdinal(graphId);
        if (existing.stream().anyMatch(n -> !n.isDeleted() && GraphDiff.key(n.getName()).equals(GraphDiff.key(req.name())))) {
            throw new IllegalArgumentException("图谱里已经有「" + req.name() + "」了");
        }
        KnowledgeNode n = new KnowledgeNode();
        n.setGraphId(graphId);
        n.setSectionId(req.sectionId());
        n.setName(req.name().strip());
        n.setDefinition(req.definition() == null ? null : req.definition().strip());
        n.setNodeType(req.type());
        n.setSourceConfidence(1.0);
        n.setReviewStatus(ItemReviewStatus.CONFIRMED);
        n.setOrdinal(existing.stream().mapToInt(KnowledgeNode::getOrdinal).max().orElse(0) + 1);
        n = nodes.save(n);
        Book book = books.findById(g.getBookId()).orElseThrow();
        List<SourceChunk> sectionChunks = req.sectionId() == null ? List.of() : chunks.findBySectionIdOrderByOrdinal(req.sectionId());
        generation.generateQuestions(book, n, sectionChunks);
        return detail(actor, graphId, n.getId());
    }

    @Transactional
    public NodeDetail addRelation(AppUser actor, Long graphId, Long fromNodeId, Long toNodeId, RelationType type) {
        checkEditable(actor, graphId);
        node(graphId, fromNodeId);
        node(graphId, toNodeId);
        if (fromNodeId.equals(toNodeId)) {
            throw new IllegalArgumentException("关系的两端不能是同一个知识点");
        }
        boolean exists = relations.findByGraphId(graphId).stream().anyMatch(r -> r.getFromNodeId().equals(fromNodeId)
                && r.getToNodeId().equals(toNodeId) && r.getRelationType() == type);
        if (!exists) {
            NodeRelation r = new NodeRelation();
            r.setGraphId(graphId);
            r.setFromNodeId(fromNodeId);
            r.setToNodeId(toNodeId);
            r.setRelationType(type);
            r.setConfidence(1.0);
            r.setEvidence("人工添加");
            r.setReviewStatus(ItemReviewStatus.CONFIRMED);
            relations.save(r);
        }
        return detail(actor, graphId, toNodeId);
    }

    @Transactional
    public void reviewRelation(AppUser actor, Long graphId, Long relationId, ItemReviewStatus status) {
        checkEditable(actor, graphId);
        relation(graphId, relationId).setReviewStatus(status);
    }

    @Transactional
    public void deleteRelation(AppUser actor, Long graphId, Long relationId) {
        checkEditable(actor, graphId);
        relations.delete(relation(graphId, relationId));
    }

    private KnowledgeGraph checkEditable(AppUser actor, Long graphId) {
        KnowledgeGraph g = graphService.require(graphId);
        if (g.getKind() != GraphKind.MASTER) {
            throw new IllegalArgumentException("编辑器只用来校对书本图谱；学习者在自己的副本上只能删节点、标记关系有误");
        }
        if (g.getStatus() == GraphStatus.SUPERSEDED) {
            throw new IllegalStateException("这个版本已经被新版本取代，请编辑最新版本");
        }
        access.checkCanManage(actor, books.findById(g.getBookId()).orElseThrow(() -> NotFoundException.of("书", g.getBookId())));
        return g;
    }

    private KnowledgeNode node(Long graphId, Long nodeId) {
        return nodes.findById(nodeId).filter(n -> n.getGraphId().equals(graphId))
                .orElseThrow(() -> NotFoundException.of("节点", nodeId));
    }

    private NodeRelation relation(Long graphId, Long relationId) {
        return relations.findById(relationId).filter(r -> r.getGraphId().equals(graphId))
                .orElseThrow(() -> NotFoundException.of("关系", relationId));
    }
}
