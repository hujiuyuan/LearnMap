package com.learnmap.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.ai.AiProvider;
import com.learnmap.ai.AiProvider.ExtractedNode;
import com.learnmap.ai.AiProvider.ExtractedRelation;
import com.learnmap.ai.AiProvider.ExtractionRequest;
import com.learnmap.ai.AiProvider.ExtractionResult;
import com.learnmap.ai.AiProvider.GeneratedQuestion;
import com.learnmap.ai.AiProvider.NodeBrief;
import com.learnmap.book.Book;
import com.learnmap.book.BookSection;
import com.learnmap.book.ReviewMode;
import com.learnmap.book.SourceChunk;
import com.learnmap.question.Question;
import com.learnmap.question.QuestionRepository;
import com.learnmap.question.QuestionSource;
import com.learnmap.question.QuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书 → 书本图谱（MASTER）：对每个小节调用 AI 抽知识点和关系，按名称合并去重，挂原文出处，再按节点出题。
 * 生成完后：书的校对方式是“直接发布”就立即发布（标“未校对”），否则留在草稿等人校对。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphGenerationService {

    static final int QUESTIONS_PER_NODE = 2;

    private final AiProvider ai;
    private final KnowledgeGraphRepository graphs;
    private final KnowledgeNodeRepository nodes;
    private final NodeRelationRepository relations;
    private final NodeSourceRepository nodeSources;
    private final QuestionRepository questions;
    private final GraphService graphService;
    private final ObjectMapper json;

    @Transactional
    public KnowledgeGraph generate(Book book, List<BookSection> sections, Map<Long, List<SourceChunk>> chunksBySection) {
        int nextVersion = graphs.findByBookIdAndKindOrderByVersionNoDesc(book.getId(), GraphKind.MASTER).stream()
                .mapToInt(KnowledgeGraph::getVersionNo).max().orElse(0) + 1;
        KnowledgeGraph graph = new KnowledgeGraph();
        graph.setBookId(book.getId());
        graph.setKind(GraphKind.MASTER);
        graph.setVersionNo(nextVersion);
        graph.setStatus(GraphStatus.DRAFT);
        graph.setReviewStatus(GraphReviewStatus.UNREVIEWED);
        graph = graphs.save(graph);

        Map<Long, BookSection> sectionById = new HashMap<>();
        sections.forEach(s -> sectionById.put(s.getId(), s));
        Map<String, KnowledgeNode> byName = new LinkedHashMap<>();
        Map<Long, List<String>> namesByChapter = new HashMap<>();
        List<ExtractedRelation> pendingRelations = new ArrayList<>();
        int ordinal = 0;

        for (BookSection section : sections) {
            List<SourceChunk> chunks = chunksBySection.getOrDefault(section.getId(), List.of());
            if (chunks.isEmpty()) {
                continue;
            }
            List<String> path = sectionPath(section, sectionById);
            Long chapterId = chapterOf(section, sectionById).getId();
            List<String> earlier = namesByChapter.computeIfAbsent(chapterId, k -> new ArrayList<>());
            ExtractionResult result = ai.extractKnowledge(new ExtractionRequest(
                    book.getTitle(), path, chunks.stream().map(SourceChunk::getContent).toList(), List.copyOf(earlier)));

            for (ExtractedNode extracted : result.nodes()) {
                KnowledgeNode node = byName.get(normalize(extracted.name()));
                if (node == null) {
                    node = new KnowledgeNode();
                    node.setGraphId(graph.getId());
                    node.setSectionId(section.getId());
                    node.setName(extracted.name());
                    node.setDefinition(extracted.definition());
                    node.setNodeType(extracted.type());
                    node.setSourceConfidence(extracted.confidence());
                    node.setReviewStatus(ItemReviewStatus.PENDING);
                    node.setOrdinal(ordinal++);
                    node = nodes.save(node);
                    byName.put(normalize(extracted.name()), node);
                    earlier.add(extracted.name());
                }
                // 同一概念在多处出现：合并成一个节点，出处都挂上
                for (Integer idx : extracted.chunkIndexes()) {
                    if (idx >= 0 && idx < chunks.size()) {
                        nodeSources.save(new NodeSource(node.getId(), chunks.get(idx).getId()));
                    }
                }
            }
            pendingRelations.addAll(result.relations());
        }

        for (ExtractedRelation r : pendingRelations) {
            KnowledgeNode from = byName.get(normalize(r.fromName()));
            KnowledgeNode to = byName.get(normalize(r.toName()));
            if (from == null || to == null || from.getId().equals(to.getId())) {
                log.debug("跳过无法解析的关系 {} -> {}", r.fromName(), r.toName());
                continue;
            }
            NodeRelation rel = new NodeRelation();
            rel.setGraphId(graph.getId());
            rel.setFromNodeId(from.getId());
            rel.setToNodeId(to.getId());
            rel.setRelationType(r.type());
            rel.setConfidence(r.confidence());
            rel.setEvidence(r.evidence());
            rel.setReviewStatus(ItemReviewStatus.PENDING);
            relations.save(rel);
        }

        for (KnowledgeNode node : byName.values()) {
            generateQuestions(book, node, chunksBySection.getOrDefault(node.getSectionId(), List.of()));
        }

        log.info("书 {} 生成图谱 v{}：{} 个节点，AI 提供方 {}", book.getId(), nextVersion, byName.size(), ai.name());
        if (book.getReviewMode() == ReviewMode.NONE) {
            graph = graphService.publishMaster(graph, false);
        }
        return graph;
    }

    private void generateQuestions(Book book, KnowledgeNode node, List<SourceChunk> chunks) {
        NodeBrief brief = new NodeBrief(node.getName(), node.getDefinition(), node.getNodeType(),
                chunks.stream().map(SourceChunk::getContent).limit(3).toList());
        for (GeneratedQuestion g : ai.generateQuestions(brief, QUESTIONS_PER_NODE)) {
            QuestionType type;
            try {
                type = QuestionType.valueOf(g.type());
            } catch (IllegalArgumentException e) {
                log.warn("跳过不支持的题型 {}", g.type());
                continue;
            }
            Question q = new Question();
            q.setBookId(book.getId());
            q.setNodeId(node.getId());
            q.setQuestionType(type);
            q.setStem(g.stem());
            q.setOptionsJson(toJson(g.options()));
            q.setAnswer(g.answer());
            q.setDifficulty(g.difficulty());
            q.setSource(QuestionSource.AI);
            q.setVerified(false);
            questions.save(q);
        }
    }

    private String toJson(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> sectionPath(BookSection section, Map<Long, BookSection> byId) {
        List<String> path = new ArrayList<>();
        for (BookSection s = section; s != null; s = s.getParentId() == null ? null : byId.get(s.getParentId())) {
            path.add(0, s.getTitle());
        }
        return path;
    }

    private static BookSection chapterOf(BookSection section, Map<Long, BookSection> byId) {
        BookSection s = section;
        while (s.getParentId() != null && byId.containsKey(s.getParentId())) {
            s = byId.get(s.getParentId());
        }
        return s;
    }

    static String normalize(String name) {
        return name.strip().replaceAll("\\s+", "").toLowerCase();
    }
}
