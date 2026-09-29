package com.learnmap.note;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnmap.ai.AiProvider;
import com.learnmap.ai.AiProvider.FeynmanGrade;
import com.learnmap.ai.AiProvider.NodeBrief;
import com.learnmap.book.Book;
import com.learnmap.book.BookRepository;
import com.learnmap.book.BookSection;
import com.learnmap.book.BookSectionRepository;
import com.learnmap.card.CardState;
import com.learnmap.card.ReviewCard;
import com.learnmap.card.ReviewCardRepository;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.graph.GraphKind;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeGraphRepository;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.practice.LearnerGraphSnapshot;
import com.learnmap.practice.LearnerGraphSnapshotLoader;
import com.learnmap.user.AppUser;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 笔记：康奈尔 / 卡片盒 / MOC，都挂到学习者自己图谱副本的节点上。
 * 康奈尔线索栏的每一行自动变成一张主动回忆卡片；正文里的 [[标题]] 自动变成双向链接。
 * 笔记本身不打分，只把数量、原子化程度、链接密度、跨书链接写进学习行为画像。
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\[\\]\\n]{1,200})]]");
    /** 卡片盒一张卡片超过这个字数，多半不止一个观点 */
    static final int ATOMIC_MAX_CHARS = 400;
    static final int CARD_BACK_MAX = 500;
    static final int LINK_CANDIDATES = 40;

    private final NoteRepository notes;
    private final NoteNodeRepository noteNodes;
    private final NoteLinkRepository links;
    private final ReviewCardRepository cards;
    private final KnowledgeNodeRepository nodes;
    private final KnowledgeGraphRepository graphs;
    private final BookRepository books;
    private final BookSectionRepository sections;
    private final LearnerGraphSnapshotLoader snapshots;
    private final UserService users;
    private final AiProvider ai;
    private final ObjectMapper json;
    private final Clock clock;

    public record NoteRequest(NoteKind kind, String title, String cues, String body, String summary,
                              List<Long> nodeIds, Long sectionId) {
    }

    public record NodeRef(Long id, String name, Long graphId, Long bookId) {
    }

    /** @param outgoing true 表示这篇笔记指向对方，false 是对方指向这篇（反向链接） */
    public record LinkRef(Long noteId, String title, NoteKind kind, String bookTitle, boolean crossBook, String source,
                          boolean outgoing) {
    }

    public record SummaryFeedback(List<String> covered, List<String> missing, List<String> incorrect, List<String> followUps) {
    }

    public record NoteView(Long id, Long ownerId, NoteKind kind, String title, String cues, String body, String summary,
                           SummaryFeedback feedback, List<NodeRef> nodes, List<LinkRef> links, List<String> unresolved,
                           int cueCards, Long bookId, String bookTitle, Long sectionId, LocalDateTime updatedAt) {
    }

    public record NoteSummary(Long id, NoteKind kind, String title, String excerpt, List<String> nodeNames, int links,
                              String bookTitle, LocalDateTime updatedAt) {
    }

    public record LinkSuggestion(Long noteId, String title, String bookTitle, boolean crossBook) {
    }

    public record MocDraft(String title, String body, List<Long> nodeIds, Long sectionId) {
    }

    /**
     * @param atomicRate    卡片盒卡片里不超过 400 字的比例
     * @param linkDensity   平均每张卡片的链接数
     * @param crossBookLinks 连接两本不同书的链接数
     */
    public record NoteHabits(int total, int cornell, int zettel, int moc, int cueCards, Double avgZettelChars,
                             Double atomicRate, Double linkDensity, int crossBookLinks) {
    }

    // ---------- 增删改 ----------

    @Transactional
    public NoteView create(AppUser actor, NoteRequest req) {
        Note n = new Note();
        n.setOwnerId(actor.getId());
        n.setKind(req.kind() == null ? NoteKind.ZETTEL : req.kind());
        return save(actor, n, req);
    }

    @Transactional
    public NoteView update(AppUser actor, Long id, NoteRequest req) {
        Note n = own(actor, id);
        return save(actor, n, req);
    }

    private NoteView save(AppUser actor, Note n, NoteRequest req) {
        String title = req.title() == null ? "" : req.title().strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("笔记要有标题");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("标题太长了（最多 200 字）");
        }
        List<KnowledgeNode> attached = ownNodes(actor, req.nodeIds() == null ? List.of() : req.nodeIds());
        if (n.getKind() == NoteKind.CORNELL && attached.isEmpty()) {
            throw new IllegalArgumentException("康奈尔笔记要挂到至少一个知识点上，线索栏的问题才能变成复习卡片");
        }
        boolean summaryChanged = !Objects.equals(n.getSummary(), req.summary());
        n.setTitle(title);
        n.setCues(n.getKind() == NoteKind.CORNELL ? req.cues() : null);
        n.setBody(req.body());
        n.setSummary(n.getKind() == NoteKind.CORNELL ? req.summary() : null);
        if (summaryChanged) {
            n.setSummaryFeedback(null);
        }
        n.setSectionId(req.sectionId());
        n.setBookId(attached.isEmpty() ? null : graphs.findById(attached.get(0).getGraphId()).map(KnowledgeGraph::getBookId).orElse(null));
        n.setUpdatedAt(LocalDateTime.now(clock));
        n = notes.save(n);

        noteNodes.deleteByNoteId(n.getId());
        noteNodes.flush();
        for (KnowledgeNode kn : attached) {
            noteNodes.save(new NoteNode(n.getId(), kn.getId()));
        }
        syncWikiLinks(n);
        // 别的笔记里早就写了 [[这个标题]]，现在这张写出来了，把它们连上
        for (Note other : notes.findByOwnerIdOrderByUpdatedAtDesc(n.getOwnerId())) {
            if (!other.getId().equals(n.getId()) && wikiTitles(other.getBody()).contains(title)) {
                syncWikiLinks(other);
            }
        }
        if (n.getKind() == NoteKind.CORNELL) {
            syncCueCards(n, attached.get(0).getId());
        }
        return view(n);
    }

    @Transactional
    public void delete(AppUser actor, Long id) {
        Note n = own(actor, id);
        cards.deleteAll(cards.findByNoteId(id));
        links.deleteAll(links.findByFromNoteIdOrToNoteId(id, id));
        noteNodes.deleteByNoteId(id);
        notes.delete(n);
    }

    /** 学习者只能把笔记挂到自己图谱副本的节点上 */
    private List<KnowledgeNode> ownNodes(AppUser actor, List<Long> ids) {
        List<KnowledgeNode> out = new ArrayList<>();
        Map<Long, Boolean> ownGraph = new HashMap<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            KnowledgeNode kn = nodes.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> NotFoundException.of("节点", id));
            boolean ok = ownGraph.computeIfAbsent(kn.getGraphId(), g -> graphs.findById(g)
                    .map(x -> x.getKind() == GraphKind.LEARNER_COPY && actor.getId().equals(x.getOwnerLearnerId())).orElse(false));
            if (!ok) {
                throw new ForbiddenException("笔记只能挂到自己图谱里的知识点上");
            }
            out.add(kn);
        }
        return out;
    }

    /** 正文里的 [[标题]] → 指向同名笔记的链接；删掉的 [[ ]] 对应的自动链接也删掉，手动加的保留 */
    private void syncWikiLinks(Note n) {
        Set<Long> targets = new HashSet<>();
        for (String t : wikiTitles(n.getBody())) {
            latestByTitle(n.getOwnerId(), t).filter(x -> !x.getId().equals(n.getId())).ifPresent(x -> targets.add(x.getId()));
        }
        for (NoteLink l : links.findByFromNoteIdOrToNoteId(n.getId(), n.getId())) {
            if (l.getFromNoteId().equals(n.getId()) && NoteLink.WIKI.equals(l.getSource()) && !targets.contains(l.getToNoteId())) {
                links.delete(l);
            }
        }
        for (Long t : targets) {
            if (linkBetween(n.getId(), t).isEmpty()) {
                links.save(new NoteLink(n.getId(), t, NoteLink.WIKI));
            }
        }
    }

    static List<String> wikiTitles(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) {
            return out;
        }
        Matcher m = WIKI_LINK.matcher(body);
        while (m.find()) {
            String t = m.group(1).strip();
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** 线索栏一行一个问题或关键词；去掉行首的 “-”、“1.” 之类 */
    static List<String> cueLines(String cues) {
        if (cues == null) {
            return List.of();
        }
        return cues.lines().map(l -> l.strip().replaceFirst("^([-*•·]|\\d+[.、)）])\\s*", "").strip())
                .filter(l -> !l.isEmpty()).distinct().toList();
    }

    /** 线索栏 → 主动回忆卡片：新问题加卡片，删掉的问题删卡片，已有卡片保留复习进度、背面跟着笔记栏更新 */
    private void syncCueCards(Note n, Long nodeId) {
        List<String> cues = cueLines(n.getCues());
        String back = cardBack(n);
        Map<String, ReviewCard> existing = new HashMap<>();
        for (ReviewCard c : cards.findByNoteId(n.getId())) {
            if (cues.contains(c.getFront()) && !existing.containsKey(c.getFront())) {
                existing.put(c.getFront(), c);
                c.setBack(back);
                c.setNodeId(nodeId);
            } else {
                cards.delete(c);
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (String cue : cues) {
            if (existing.containsKey(cue)) {
                continue;
            }
            ReviewCard c = new ReviewCard();
            c.setLearnerId(n.getOwnerId());
            c.setNodeId(nodeId);
            c.setNoteId(n.getId());
            c.setFront(cue);
            c.setBack(back);
            c.setSource("CORNELL");
            c.setState(CardState.NEW);
            c.setDueAt(now);
            cards.save(c);
        }
    }

    private static String cardBack(Note n) {
        String text = n.getBody() == null || n.getBody().isBlank() ? (n.getSummary() == null ? "" : n.getSummary()) : n.getBody();
        text = text.strip();
        if (text.isEmpty()) {
            text = "（笔记栏还没写，回想后对照自己的笔记）";
        }
        return text.length() > CARD_BACK_MAX ? text.substring(0, CARD_BACK_MAX) + "……" : text;
    }

    // ---------- 查看 ----------

    public NoteView get(AppUser actor, Long id) {
        Note n = notes.findById(id).orElseThrow(() -> NotFoundException.of("笔记", id));
        users.checkCanView(actor, n.getOwnerId());
        return view(n);
    }

    public List<NoteSummary> list(AppUser actor, Long learnerId, NoteKind kind, Long nodeId, Long bookId) {
        users.checkCanView(actor, learnerId);
        List<Note> all = notes.findByOwnerIdOrderByUpdatedAtDesc(learnerId);
        Map<Long, List<NoteNode>> byNote = noteNodes.findByNoteIdIn(all.stream().map(Note::getId).toList()).stream()
                .collect(Collectors.groupingBy(NoteNode::getNoteId));
        Map<Long, String> nodeNames = nodes.findAllById(byNote.values().stream().flatMap(List::stream).map(NoteNode::getNodeId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(KnowledgeNode::getId, KnowledgeNode::getName));
        Map<Long, Integer> linkCount = new HashMap<>();
        for (NoteLink l : links.findByFromNoteIdIn(all.stream().map(Note::getId).toList())) {
            linkCount.merge(l.getFromNoteId(), 1, Integer::sum);
            linkCount.merge(l.getToNoteId(), 1, Integer::sum);
        }
        Map<Long, String> bookTitles = bookTitles(all.stream().map(Note::getBookId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return all.stream()
                .filter(n -> kind == null || n.getKind() == kind)
                .filter(n -> bookId == null || bookId.equals(n.getBookId()))
                .filter(n -> nodeId == null || byNote.getOrDefault(n.getId(), List.of()).stream().anyMatch(x -> x.getNodeId().equals(nodeId)))
                .map(n -> new NoteSummary(n.getId(), n.getKind(), n.getTitle(), excerpt(n),
                        byNote.getOrDefault(n.getId(), List.of()).stream().map(x -> nodeNames.get(x.getNodeId())).filter(Objects::nonNull).toList(),
                        linkCount.getOrDefault(n.getId(), 0), bookTitles.get(n.getBookId()), n.getUpdatedAt()))
                .toList();
    }

    private NoteView view(Note n) {
        List<NodeRef> refs = new ArrayList<>();
        for (NoteNode x : noteNodes.findByNoteId(n.getId())) {
            nodes.findById(x.getNodeId()).ifPresent(kn -> refs.add(new NodeRef(kn.getId(), kn.getName(), kn.getGraphId(),
                    graphs.findById(kn.getGraphId()).map(KnowledgeGraph::getBookId).orElse(null))));
        }
        List<NoteLink> ls = links.findByFromNoteIdOrToNoteId(n.getId(), n.getId());
        Set<Long> otherIds = ls.stream().map(l -> l.getFromNoteId().equals(n.getId()) ? l.getToNoteId() : l.getFromNoteId()).collect(Collectors.toSet());
        Map<Long, Note> others = notes.findAllById(otherIds).stream().collect(Collectors.toMap(Note::getId, Function.identity()));
        Set<Long> bookIds = new HashSet<>();
        others.values().forEach(o -> {
            if (o.getBookId() != null) {
                bookIds.add(o.getBookId());
            }
        });
        if (n.getBookId() != null) {
            bookIds.add(n.getBookId());
        }
        Map<Long, String> bookTitles = bookTitles(bookIds);
        List<LinkRef> linkRefs = new ArrayList<>();
        for (NoteLink l : ls) {
            boolean out = l.getFromNoteId().equals(n.getId());
            Note o = others.get(out ? l.getToNoteId() : l.getFromNoteId());
            if (o != null) {
                linkRefs.add(new LinkRef(o.getId(), o.getTitle(), o.getKind(), bookTitles.get(o.getBookId()),
                        crossBook(n, o), l.getSource(), out));
            }
        }
        List<String> unresolved = wikiTitles(n.getBody()).stream()
                .filter(t -> latestByTitle(n.getOwnerId(), t).filter(x -> !x.getId().equals(n.getId())).isEmpty()).toList();
        return new NoteView(n.getId(), n.getOwnerId(), n.getKind(), n.getTitle(), n.getCues(), n.getBody(), n.getSummary(),
                readFeedback(n.getSummaryFeedback()), refs, linkRefs, unresolved, cards.findByNoteId(n.getId()).size(),
                n.getBookId(), bookTitles.get(n.getBookId()), n.getSectionId(), n.getUpdatedAt());
    }

    // ---------- AI：康奈尔总结反馈、卡片盒相关卡片 ----------

    /** 总结栏对照知识点要点给反馈（相当于轻量版费曼）。笔记不打分，所以不写证据、不显示分数 */
    @Transactional
    public SummaryFeedback summaryFeedback(AppUser actor, Long id) {
        Note n = own(actor, id);
        if (n.getKind() != NoteKind.CORNELL) {
            throw new IllegalArgumentException("只有康奈尔笔记有总结栏");
        }
        if (n.getSummary() == null || n.getSummary().isBlank()) {
            throw new IllegalArgumentException("先在总结栏用自己的话写几句总结");
        }
        KnowledgeNode kn = noteNodes.findByNoteId(id).stream().findFirst().flatMap(x -> nodes.findById(x.getNodeId()))
                .orElseThrow(() -> new IllegalArgumentException("笔记还没有挂到知识点上"));
        FeynmanGrade g = ai.gradeFeynman(new NodeBrief(kn.getName(), kn.getDefinition(), kn.getNodeType(), List.of()), n.getSummary());
        SummaryFeedback fb = new SummaryFeedback(g.coveredPoints(), g.missingPoints(), g.incorrectStatements(), g.followUpQuestions());
        try {
            n.setSummaryFeedback(json.writeValueAsString(fb));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return fb;
    }

    /** 从自己别的卡片里找可能相关的（优先提示其他书里的，跨书链接最有价值） */
    public List<LinkSuggestion> linkSuggestions(AppUser actor, Long id) {
        Note n = own(actor, id);
        Set<Long> linked = links.findByFromNoteIdOrToNoteId(id, id).stream()
                .map(l -> l.getFromNoteId().equals(id) ? l.getToNoteId() : l.getFromNoteId()).collect(Collectors.toSet());
        List<Note> candidates = notes.findByOwnerIdOrderByUpdatedAtDesc(actor.getId()).stream()
                .filter(o -> !o.getId().equals(id) && o.getKind() == NoteKind.ZETTEL && !linked.contains(o.getId()))
                .limit(LINK_CANDIDATES).toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<Integer> picked = ai.suggestLinks(cardText(n), candidates.stream().map(NoteService::cardText).toList());
        Map<Long, String> bookTitles = bookTitles(candidates.stream().map(Note::getBookId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return picked.stream().map(candidates::get)
                .map(o -> new LinkSuggestion(o.getId(), o.getTitle(), bookTitles.get(o.getBookId()), crossBook(n, o)))
                .sorted((a, b) -> Boolean.compare(b.crossBook(), a.crossBook()))
                .toList();
    }

    @Transactional
    public NoteView link(AppUser actor, Long id, Long targetId, boolean suggested) {
        Note n = own(actor, id);
        own(actor, targetId);
        if (id.equals(targetId)) {
            throw new IllegalArgumentException("不能链接到自己");
        }
        if (linkBetween(id, targetId).isEmpty()) {
            links.save(new NoteLink(id, targetId, suggested ? NoteLink.SUGGESTED : NoteLink.MANUAL));
        }
        return view(n);
    }

    @Transactional
    public NoteView unlink(AppUser actor, Long id, Long targetId) {
        Note n = own(actor, id);
        linkBetween(id, targetId).ifPresent(links::delete);
        return view(n);
    }

    // ---------- MOC 初稿 ----------

    /** 按一章的知识点（图谱顺序）和挂在上面的笔记生成内容地图初稿，学习者再自己调整后保存 */
    @Transactional(readOnly = true)
    public MocDraft mocDraft(AppUser actor, Long graphId, Long sectionId) {
        KnowledgeGraph g = graphs.findById(graphId).orElseThrow(() -> NotFoundException.of("图谱", graphId));
        if (g.getKind() != GraphKind.LEARNER_COPY || !actor.getId().equals(g.getOwnerLearnerId())) {
            throw new ForbiddenException("只能给自己的图谱生成内容地图");
        }
        LearnerGraphSnapshot snap = snapshots.load(g);
        Map<Long, BookSection> sectionById = sections.findByBookIdOrderByOrdinal(g.getBookId()).stream()
                .collect(Collectors.toMap(BookSection::getId, Function.identity()));
        List<Long> chapterNodes = snap.nodes().keySet().stream()
                .filter(nid -> sectionId == null || sectionId.equals(snap.chapterOf().get(nid)))
                .sorted(snap.byOrdinal()).toList();
        if (chapterNodes.isEmpty()) {
            throw new IllegalArgumentException("这一章还没有知识点");
        }
        String topic = sectionId != null && sectionById.containsKey(sectionId) ? sectionById.get(sectionId).getTitle()
                : books.findById(g.getBookId()).map(Book::getTitle).orElse("主题");

        Map<Long, List<Note>> notesByNode = new HashMap<>();
        Map<Long, Note> own = notes.findByOwnerIdOrderByUpdatedAtDesc(actor.getId()).stream()
                .filter(x -> x.getKind() != NoteKind.MOC).collect(Collectors.toMap(Note::getId, Function.identity()));
        for (NoteNode x : noteNodes.findByNoteIdIn(own.keySet())) {
            notesByNode.computeIfAbsent(x.getNodeId(), k -> new ArrayList<>()).add(own.get(x.getNoteId()));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(topic).append("\n\n");
        sb.append("这一章讲了 ").append(chapterNodes.size()).append(" 个知识点，按学习顺序排列。✓ 表示已经掌握。\n\n");
        List<String> noNotes = new ArrayList<>();
        for (Long nid : chapterNodes) {
            KnowledgeNode kn = snap.nodes().get(nid);
            sb.append("- ").append(snap.mastered(nid) ? "✓ " : "").append(kn.getName());
            List<Long> pre = snap.prereqsOf(nid);
            if (!pre.isEmpty()) {
                sb.append("（先学：").append(pre.stream().filter(snap.nodes()::containsKey).map(snap::name).collect(Collectors.joining("、"))).append("）");
            }
            sb.append('\n');
            List<Note> attached = notesByNode.getOrDefault(nid, List.of());
            if (attached.isEmpty()) {
                noNotes.add(kn.getName());
            }
            for (Note x : attached) {
                sb.append("  - [[").append(x.getTitle()).append("]]\n");
            }
        }
        if (!noNotes.isEmpty()) {
            sb.append("\n## 还没有笔记的知识点\n\n").append(String.join("、", noNotes)).append('\n');
        }
        sb.append("\n## 我的理解\n\n（用自己的话写这一章讲的是什么、知识点之间怎么连起来）\n");
        return new MocDraft(topic + " · 内容地图", sb.toString(), chapterNodes, sectionId);
    }

    // ---------- 画像 ----------

    @Transactional(readOnly = true)
    public NoteHabits habits(Long learnerId) {
        List<Note> all = notes.findByOwnerIdOrderByUpdatedAtDesc(learnerId);
        List<Note> zettel = all.stream().filter(n -> n.getKind() == NoteKind.ZETTEL).toList();
        Map<Long, Note> byId = all.stream().collect(Collectors.toMap(Note::getId, Function.identity()));
        List<NoteLink> ls = links.findByFromNoteIdIn(byId.keySet());
        int crossBook = 0;
        int zettelLinkEnds = 0;
        for (NoteLink l : ls) {
            Note a = byId.get(l.getFromNoteId());
            Note b = byId.get(l.getToNoteId());
            if (a != null && b != null && crossBook(a, b)) {
                crossBook++;
            }
            if (a != null && a.getKind() == NoteKind.ZETTEL) {
                zettelLinkEnds++;
            }
            if (b != null && b.getKind() == NoteKind.ZETTEL) {
                zettelLinkEnds++;
            }
        }
        int cueCards = all.stream().filter(n -> n.getKind() == NoteKind.CORNELL).mapToInt(n -> cards.findByNoteId(n.getId()).size()).sum();
        Double avgChars = zettel.isEmpty() ? null : round(zettel.stream().mapToInt(n -> length(n.getBody())).average().orElse(0));
        Double atomic = zettel.isEmpty() ? null : round(zettel.stream().filter(n -> length(n.getBody()) <= ATOMIC_MAX_CHARS).count() / (double) zettel.size());
        Double density = zettel.isEmpty() ? null : round(zettelLinkEnds / (double) zettel.size());
        return new NoteHabits(all.size(), (int) all.stream().filter(n -> n.getKind() == NoteKind.CORNELL).count(), zettel.size(),
                (int) all.stream().filter(n -> n.getKind() == NoteKind.MOC).count(), cueCards, avgChars, atomic, density, crossBook);
    }

    // ---------- 工具 ----------

    private Note own(AppUser actor, Long id) {
        Note n = notes.findById(id).orElseThrow(() -> NotFoundException.of("笔记", id));
        if (!n.getOwnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能改自己的笔记");
        }
        return n;
    }

    private Optional<Note> latestByTitle(Long ownerId, String title) {
        return notes.findByOwnerIdAndTitle(ownerId, title).stream().max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt()));
    }

    private Optional<NoteLink> linkBetween(Long a, Long b) {
        return links.findByFromNoteIdAndToNoteId(a, b).or(() -> links.findByFromNoteIdAndToNoteId(b, a));
    }

    private Map<Long, String> bookTitles(Set<Long> ids) {
        return ids.isEmpty() ? new HashMap<>() : books.findAllById(ids).stream().collect(Collectors.toMap(Book::getId, Book::getTitle));
    }

    static boolean crossBook(Note a, Note b) {
        return a.getBookId() != null && b.getBookId() != null && !a.getBookId().equals(b.getBookId());
    }

    private static String cardText(Note n) {
        String body = n.getBody() == null ? "" : n.getBody();
        return n.getTitle() + "\n" + (body.length() > 400 ? body.substring(0, 400) : body);
    }

    private static String excerpt(Note n) {
        String text = n.getKind() == NoteKind.CORNELL && n.getSummary() != null && !n.getSummary().isBlank() ? n.getSummary() : n.getBody();
        if (text == null) {
            return "";
        }
        text = text.replaceAll("\\s+", " ").strip();
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    private static int length(String s) {
        return s == null ? 0 : s.strip().length();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private SummaryFeedback readFeedback(String s) {
        if (s == null) {
            return null;
        }
        try {
            return json.readValue(s, SummaryFeedback.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
