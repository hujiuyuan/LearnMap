package com.learnmap.palace;

import com.learnmap.ai.AiProvider;
import com.learnmap.ai.AiProvider.PalaceItem;
import com.learnmap.card.CardState;
import com.learnmap.card.Fsrs;
import com.learnmap.card.Rating;
import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.evidence.Evidence;
import com.learnmap.evidence.EvidenceRepository;
import com.learnmap.evidence.EvidenceResult;
import com.learnmap.graph.GraphKind;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeGraphRepository;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.graph.NodeType;
import com.learnmap.mastery.MasteryService;
import com.learnmap.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 记忆宫殿：陪学习者一步步建好宫殿（选地方 → 定地点 → 走熟路线 → 挂内容 → 回忆验证 → 间隔重走）。
 * 按顺序回忆的结果写成证据（MEMORY_PALACE），重走时间用 FSRS 排。
 */
@Service
@RequiredArgsConstructor
public class PalaceService {

    /** 按顺序回忆知识点名称比做题容易 */
    static final double RECALL_DIFFICULTY = 0.35;
    /** 适合放进宫殿的节点类型：有顺序的清单、术语、公式步骤 */
    static final List<NodeType> PREFERRED = List.of(NodeType.FACT, NodeType.METHOD, NodeType.PRINCIPLE);

    private final MemoryPalaceRepository palaces;
    private final PalaceLocusRepository loci;
    private final PalacePlacementRepository placements;
    private final PalaceWalkRepository walks;
    private final KnowledgeNodeRepository nodes;
    private final KnowledgeGraphRepository graphs;
    private final EvidenceRepository evidences;
    private final MasteryService mastery;
    private final AiProvider ai;
    private final Clock clock;
    private final Random random = new Random();

    public record PlacementView(Long id, Long nodeId, String nodeName, String content, String image, String aiImage,
                                boolean ownImage) {
    }

    public record LocusView(Long id, int ordinal, String name, PlacementView placement) {
    }

    public record PalaceView(Long id, String name, String place, Long graphId, PalaceStatus status, boolean forwardOk,
                             boolean backwardOk, int randomOk, int randomNeeded, List<LocusView> loci,
                             LocalDateTime dueAt, LocalDateTime lastWalkAt, Double lastRecallRate) {
    }

    public record PalaceSummary(Long id, String name, String place, PalaceStatus status, int loci, int placed,
                                LocalDateTime dueAt, boolean due) {
    }

    public record DrillQuestion(int ordinal, String locus) {
    }

    public record DrillResult(boolean correct, List<String> expected, List<Boolean> each, PalaceView palace) {
    }

    public record CandidateNode(Long id, String name, NodeType type, String definition) {
    }

    public record PlacementRequest(Long locusId, Long nodeId, String content, String image) {
    }

    public record RecallItem(String locus, String expected, String answer, boolean correct, String image) {
    }

    public record RecallResult(int correct, int total, List<RecallItem> items, LocalDateTime nextWalkAt, PalaceView palace) {
    }

    // ---------- 2. 选地方 ----------

    @Transactional
    public PalaceView create(AppUser actor, String name, String place, Long graphId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("给宫殿起个名字");
        }
        if (graphId != null) {
            ownGraph(actor, graphId);
        }
        MemoryPalace p = new MemoryPalace();
        p.setOwnerId(actor.getId());
        p.setName(name.strip());
        p.setPlace(place == null ? null : place.strip());
        p.setGraphId(graphId);
        return view(palaces.save(p));
    }

    // ---------- 3. 定地点 ----------

    /** 按路线顺序定地点；改了地点要重新走熟路线，已经挂的内容清空 */
    @Transactional
    public PalaceView setLoci(AppUser actor, Long id, List<String> names) {
        MemoryPalace p = own(actor, id);
        List<String> clean = names == null ? List.of() : names.stream().map(n -> n == null ? "" : n.strip()).toList();
        List<String> issues = PalaceRules.lociIssues(clean);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", issues));
        }
        placements.deleteAll(placements.findByPalaceId(id));
        loci.deleteAll(loci.findByPalaceIdOrderByOrdinal(id));
        loci.flush();
        for (int i = 0; i < clean.size(); i++) {
            PalaceLocus l = new PalaceLocus();
            l.setPalaceId(id);
            l.setOrdinal(i + 1);
            l.setName(clean.get(i));
            loci.save(l);
        }
        p.setStatus(PalaceStatus.DRILLING);
        p.setForwardOk(false);
        p.setBackwardOk(false);
        p.setRandomOk(0);
        p.setDueAt(null);
        return view(p);
    }

    // ---------- 4. 走熟路线 ----------

    /** 顺背或倒背：按顺序写出全部地点 */
    @Transactional
    public DrillResult drillSequence(AppUser actor, Long id, boolean backward, List<String> answers) {
        MemoryPalace p = own(actor, id);
        List<String> expected = loci.findByPalaceIdOrderByOrdinal(id).stream().map(PalaceLocus::getName).collect(Collectors.toList());
        if (expected.isEmpty()) {
            throw new IllegalArgumentException("先定好地点");
        }
        if (backward) {
            java.util.Collections.reverse(expected);
        }
        List<Boolean> each = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) {
            each.add(answers != null && i < answers.size() && PalaceRules.sameLocus(expected.get(i), answers.get(i)));
        }
        boolean ok = each.stream().allMatch(b -> b);
        walks.save(new PalaceWalk(id, backward ? "BACKWARD" : "FORWARD", (int) each.stream().filter(b -> b).count(), expected.size()));
        if (ok) {
            if (backward) {
                p.setBackwardOk(true);
            } else {
                p.setForwardOk(true);
            }
        }
        checkBuilt(p);
        return new DrillResult(ok, expected, each, view(p));
    }

    /** 随机抽一个地点，问下一个是什么（不会抽到最后一个） */
    public DrillQuestion nextQuestion(AppUser actor, Long id) {
        own(actor, id);
        List<PalaceLocus> ls = loci.findByPalaceIdOrderByOrdinal(id);
        if (ls.size() < 2) {
            throw new IllegalArgumentException("先定好地点");
        }
        PalaceLocus l = ls.get(random.nextInt(ls.size() - 1));
        return new DrillQuestion(l.getOrdinal(), l.getName());
    }

    @Transactional
    public DrillResult drillNext(AppUser actor, Long id, int fromOrdinal, String answer) {
        MemoryPalace p = own(actor, id);
        List<PalaceLocus> ls = loci.findByPalaceIdOrderByOrdinal(id);
        PalaceLocus next = ls.stream().filter(l -> l.getOrdinal() == fromOrdinal + 1).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("没有这个地点的下一个"));
        boolean ok = PalaceRules.sameLocus(next.getName(), answer);
        p.setRandomOk(ok ? p.getRandomOk() + 1 : 0);
        walks.save(new PalaceWalk(id, "NEXT", ok ? 1 : 0, 1));
        checkBuilt(p);
        return new DrillResult(ok, List.of(next.getName()), List.of(ok), view(p));
    }

    private void checkBuilt(MemoryPalace p) {
        if (p.getStatus() == PalaceStatus.DRILLING && p.isForwardOk() && p.isBackwardOk() && p.getRandomOk() >= PalaceRules.RANDOM_NEEDED) {
            p.setStatus(PalaceStatus.READY);
        }
    }

    // ---------- 5. 挂内容 ----------

    /** 适合放进宫殿的知识点：事实术语、方法步骤、原理公式优先，按图谱顺序，已经挂上的不再列 */
    public List<CandidateNode> candidates(AppUser actor, Long id, Long graphId) {
        MemoryPalace p = own(actor, id);
        Long gid = graphId != null ? graphId : p.getGraphId();
        if (gid == null) {
            throw new IllegalArgumentException("选一张图谱");
        }
        ownGraph(actor, gid);
        Set<Long> placed = placements.findByPalaceId(id).stream().map(PalacePlacement::getNodeId).filter(x -> x != null).collect(Collectors.toSet());
        return nodes.findByGraphIdOrderByOrdinal(gid).stream()
                .filter(n -> !n.isDeleted() && !placed.contains(n.getId()))
                .sorted(Comparator.comparingInt((KnowledgeNode n) -> PREFERRED.contains(n.getNodeType()) ? 0 : 1)
                        .thenComparingInt(KnowledgeNode::getOrdinal))
                .map(n -> new CandidateNode(n.getId(), n.getName(), n.getNodeType(), n.getDefinition()))
                .toList();
    }

    /** 把内容逐个放到地点上（整体替换）；挂知识点时内容默认是知识点名称 */
    @Transactional
    public PalaceView place(AppUser actor, Long id, List<PlacementRequest> items) {
        MemoryPalace p = own(actor, id);
        if (p.getStatus() != PalaceStatus.READY) {
            throw new IllegalArgumentException("先把路线走熟（顺背、倒背、随机问下一个都答对），宫殿建成后再挂内容");
        }
        Map<Long, PalaceLocus> byId = loci.findByPalaceIdOrderByOrdinal(id).stream().collect(Collectors.toMap(PalaceLocus::getId, Function.identity()));
        Map<Long, PalacePlacement> old = placements.findByPalaceId(id).stream().collect(Collectors.toMap(PalacePlacement::getLocusId, Function.identity()));
        Set<Long> used = new HashSet<>();
        List<PalacePlacement> keep = new ArrayList<>();
        for (PlacementRequest r : items == null ? List.<PlacementRequest>of() : items) {
            if (!byId.containsKey(r.locusId())) {
                throw new IllegalArgumentException("地点不属于这座宫殿");
            }
            if (!used.add(r.locusId())) {
                throw new IllegalArgumentException("一个地点只放一样东西");
            }
            KnowledgeNode kn = null;
            if (r.nodeId() != null) {
                kn = nodes.findById(r.nodeId()).filter(n -> !n.isDeleted()).orElseThrow(() -> NotFoundException.of("节点", r.nodeId()));
                ownGraph(actor, kn.getGraphId());
            }
            String content = r.content() != null && !r.content().isBlank() ? r.content().strip() : kn != null ? kn.getName() : "";
            if (content.isEmpty()) {
                throw new IllegalArgumentException("「" + byId.get(r.locusId()).getName() + "」上要放点什么");
            }
            PalacePlacement pp = old.getOrDefault(r.locusId(), new PalacePlacement());
            boolean changed = pp.getId() == null || !content.equals(pp.getContent());
            pp.setPalaceId(id);
            pp.setLocusId(r.locusId());
            pp.setNodeId(kn == null ? null : kn.getId());
            pp.setContent(content);
            if (changed) {
                pp.setAiImage(null);
                pp.setImage(null);
            }
            if (r.image() != null && !r.image().isBlank()) {
                pp.setImage(r.image().strip());
            }
            keep.add(placements.save(pp));
        }
        old.values().stream().filter(o -> !used.contains(o.getLocusId())).forEach(placements::delete);
        if (!keep.isEmpty() && p.getDueAt() == null) {
            // 挂完先马上回忆一遍
            p.setDueAt(LocalDateTime.now(clock));
        }
        return view(p);
    }

    /** AI 给还没有画面的内容编画面；学习者没写自己的画面时先用 AI 的 */
    @Transactional
    public PalaceView suggestImages(AppUser actor, Long id, boolean regenerate) {
        MemoryPalace p = own(actor, id);
        Map<Long, PalaceLocus> byId = loci.findByPalaceIdOrderByOrdinal(id).stream().collect(Collectors.toMap(PalaceLocus::getId, Function.identity()));
        List<PalacePlacement> todo = placements.findByPalaceId(id).stream()
                .filter(pp -> regenerate || pp.getAiImage() == null || pp.getAiImage().isBlank())
                .sorted(Comparator.comparingInt(pp -> byId.get(pp.getLocusId()).getOrdinal()))
                .toList();
        if (todo.isEmpty()) {
            return view(p);
        }
        List<String> images = ai.suggestPalaceImages(todo.stream()
                .map(pp -> new PalaceItem(byId.get(pp.getLocusId()).getName(), describe(pp))).toList());
        for (int i = 0; i < todo.size(); i++) {
            PalacePlacement pp = todo.get(i);
            String img = i < images.size() ? images.get(i) : "";
            if (img.isBlank()) {
                img = "想象「" + pp.getContent() + "」大大地出现在「" + byId.get(pp.getLocusId()).getName() + "」上";
            }
            boolean usingAi = pp.getImage() == null || pp.getImage().isBlank() || pp.getImage().equals(pp.getAiImage());
            pp.setAiImage(img);
            if (usingAi) {
                pp.setImage(img);
            }
        }
        return view(p);
    }

    /** 学习者改写画面（自己编的记得更牢） */
    @Transactional
    public PalaceView editImage(AppUser actor, Long placementId, String image) {
        PalacePlacement pp = placements.findById(placementId).orElseThrow(() -> NotFoundException.of("内容", placementId));
        MemoryPalace p = own(actor, pp.getPalaceId());
        pp.setImage(image == null || image.isBlank() ? pp.getAiImage() : image.strip());
        return view(p);
    }

    /** 清空内容，宫殿留着下次复用 */
    @Transactional
    public PalaceView clear(AppUser actor, Long id) {
        MemoryPalace p = own(actor, id);
        placements.deleteAll(placements.findByPalaceId(id));
        p.setDueAt(null);
        p.setState(CardState.NEW);
        p.setStability(0);
        p.setDifficulty(0);
        p.setReps(0);
        return view(p);
    }

    // ---------- 6. 回忆验证 / 7. 间隔重走 ----------

    /**
     * 在脑中走一遍宫殿，按地点顺序说出内容。挂了知识点的每一项写一条证据；
     * 整体正确率决定下次重走的时间（FSRS）。
     */
    @Transactional
    public RecallResult recall(AppUser actor, Long id, List<String> answers) {
        MemoryPalace p = own(actor, id);
        List<PalaceLocus> ls = loci.findByPalaceIdOrderByOrdinal(id);
        Map<Long, PalacePlacement> byLocus = placements.findByPalaceId(id).stream()
                .collect(Collectors.toMap(PalacePlacement::getLocusId, Function.identity()));
        List<PalaceLocus> used = ls.stream().filter(l -> byLocus.containsKey(l.getId())).toList();
        if (used.isEmpty()) {
            throw new IllegalArgumentException("宫殿里还没有放内容");
        }
        List<RecallItem> items = new ArrayList<>();
        int correct = 0;
        for (int i = 0; i < used.size(); i++) {
            PalaceLocus l = used.get(i);
            PalacePlacement pp = byLocus.get(l.getId());
            String answer = answers != null && i < answers.size() ? answers.get(i) : "";
            KnowledgeNode kn = pp.getNodeId() == null ? null : nodes.findById(pp.getNodeId()).orElse(null);
            boolean ok = PalaceRules.recalled(pp.getContent(), kn == null ? null : kn.getName(), answer);
            if (ok) {
                correct++;
            }
            items.add(new RecallItem(l.getName(), pp.getContent(), answer, ok, pp.getImage()));
            if (kn != null && !kn.isDeleted()) {
                Evidence ev = new Evidence();
                ev.setLearnerId(actor.getId());
                ev.setNodeId(kn.getId());
                ev.setActivityType(ActivityType.MEMORY_PALACE);
                ev.setResult(ok ? EvidenceResult.CORRECT : EvidenceResult.INCORRECT);
                ev.setScore(ok ? 1.0 : 0.0);
                ev.setDifficulty(RECALL_DIFFICULTY);
                evidences.save(ev);
                mastery.apply(ev, kn);
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        double elapsedDays = p.getLastWalkAt() == null ? 0 : Duration.between(p.getLastWalkAt(), now).toMinutes() / (60.0 * 24);
        Rating rating = PalaceRules.rating(correct, used.size());
        Fsrs.Result r = Fsrs.review(new Fsrs.Memory(p.getState(), p.getStability(), p.getDifficulty()), rating, elapsedDays);
        p.setState(r.state());
        p.setStability(r.stability());
        p.setDifficulty(r.difficulty());
        p.setReps(p.getReps() + 1);
        p.setLastWalkAt(now);
        p.setDueAt(now.plus(r.interval()));
        walks.save(new PalaceWalk(id, "RECALL", correct, used.size()));
        return new RecallResult(correct, used.size(), items, p.getDueAt(), view(p));
    }

    // ---------- 查看 ----------

    public List<PalaceSummary> list(AppUser actor) {
        LocalDateTime now = LocalDateTime.now(clock);
        return palaces.findByOwnerIdOrderByIdDesc(actor.getId()).stream().map(p -> {
            int placed = placements.findByPalaceId(p.getId()).size();
            return new PalaceSummary(p.getId(), p.getName(), p.getPlace(), p.getStatus(),
                    loci.findByPalaceIdOrderByOrdinal(p.getId()).size(), placed, p.getDueAt(),
                    placed > 0 && p.getDueAt() != null && !p.getDueAt().isAfter(now));
        }).toList();
    }

    public long dueCount(AppUser actor) {
        return palaces.findByOwnerIdAndDueAtLessThanEqual(actor.getId(), LocalDateTime.now(clock)).stream()
                .filter(p -> !placements.findByPalaceId(p.getId()).isEmpty()).count();
    }

    public PalaceView get(AppUser actor, Long id) {
        return view(own(actor, id));
    }

    @Transactional
    public void delete(AppUser actor, Long id) {
        MemoryPalace p = own(actor, id);
        placements.deleteAll(placements.findByPalaceId(id));
        loci.deleteAll(loci.findByPalaceIdOrderByOrdinal(id));
        walks.deleteAll(walks.findByPalaceIdOrderByIdDesc(id));
        palaces.delete(p);
    }

    private PalaceView view(MemoryPalace p) {
        Map<Long, PalacePlacement> byLocus = placements.findByPalaceId(p.getId()).stream()
                .collect(Collectors.toMap(PalacePlacement::getLocusId, Function.identity()));
        Map<Long, String> nodeNames = nodes.findAllById(byLocus.values().stream().map(PalacePlacement::getNodeId)
                .filter(x -> x != null).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(KnowledgeNode::getId, KnowledgeNode::getName));
        List<LocusView> lv = loci.findByPalaceIdOrderByOrdinal(p.getId()).stream().map(l -> {
            PalacePlacement pp = byLocus.get(l.getId());
            PlacementView v = pp == null ? null : new PlacementView(pp.getId(), pp.getNodeId(), nodeNames.get(pp.getNodeId()),
                    pp.getContent(), pp.getImage(), pp.getAiImage(),
                    pp.getImage() != null && !pp.getImage().isBlank() && !pp.getImage().equals(pp.getAiImage()));
            return new LocusView(l.getId(), l.getOrdinal(), l.getName(), v);
        }).toList();
        Double lastRate = walks.findByPalaceIdOrderByIdDesc(p.getId()).stream().filter(w -> "RECALL".equals(w.getKind())).findFirst()
                .map(w -> w.getTotal() == 0 ? 0 : Math.round(w.getCorrect() * 100.0 / w.getTotal()) / 100.0).orElse(null);
        return new PalaceView(p.getId(), p.getName(), p.getPlace(), p.getGraphId(), p.getStatus(), p.isForwardOk(),
                p.isBackwardOk(), p.getRandomOk(), PalaceRules.RANDOM_NEEDED, lv, p.getDueAt(), p.getLastWalkAt(), lastRate);
    }

    /** 给 AI 的内容带上定义，画面能编得更贴切 */
    private String describe(PalacePlacement pp) {
        if (pp.getNodeId() == null) {
            return pp.getContent();
        }
        return nodes.findById(pp.getNodeId()).map(n -> n.getDefinition() == null || n.getDefinition().isBlank()
                ? pp.getContent() : pp.getContent() + "（" + n.getDefinition() + "）").orElse(pp.getContent());
    }

    private MemoryPalace own(AppUser actor, Long id) {
        MemoryPalace p = palaces.findById(id).orElseThrow(() -> NotFoundException.of("记忆宫殿", id));
        if (!p.getOwnerId().equals(actor.getId())) {
            throw new ForbiddenException("只能用自己的记忆宫殿");
        }
        return p;
    }

    private void ownGraph(AppUser actor, Long graphId) {
        KnowledgeGraph g = graphs.findById(graphId).orElseThrow(() -> NotFoundException.of("图谱", graphId));
        if (g.getKind() != GraphKind.LEARNER_COPY || !actor.getId().equals(g.getOwnerLearnerId())) {
            throw new ForbiddenException("只能用自己图谱里的知识点");
        }
    }
}
