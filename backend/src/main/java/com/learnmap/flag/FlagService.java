package com.learnmap.flag;

import com.learnmap.common.ForbiddenException;
import com.learnmap.common.NotFoundException;
import com.learnmap.evidence.ActivityType;
import com.learnmap.graph.KnowledgeGraph;
import com.learnmap.graph.KnowledgeGraphRepository;
import com.learnmap.graph.KnowledgeNode;
import com.learnmap.graph.KnowledgeNodeRepository;
import com.learnmap.retest.RetestReason;
import com.learnmap.retest.RetestService;
import com.learnmap.user.AppUser;
import com.learnmap.user.Role;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 对学习者某个节点的标记：辅导者说“诊断不对 / 确实不会”，或系统发现“补了两轮还卡住”。
 * “诊断不对”会马上安排一次复测来验证；所有“诊断不对”按书本图谱节点汇总给运维，用来发现题目或图谱的问题。
 */
@Service
@RequiredArgsConstructor
public class FlagService {

    private final LearnerFlagRepository flags;
    private final KnowledgeNodeRepository nodes;
    private final KnowledgeGraphRepository graphs;
    private final RetestService retests;
    private final UserService users;
    private final Clock clock;

    public record FlagView(Long id, Long learnerId, Long nodeId, String nodeName, FlagKind kind, String note,
                           Long createdBy, boolean resolved, LocalDateTime createdAt) {
    }

    /** 运维看的汇总：同一个书本图谱节点被标了几次“诊断不对” */
    public record FlaggedNode(Long masterNodeId, String nodeName, Long bookId, long learners, long flags) {
    }

    @Transactional
    public FlagView tutorFlag(AppUser actor, Long learnerId, Long nodeId, FlagKind kind, String note) {
        if (kind == FlagKind.STUCK) {
            throw new IllegalArgumentException("这种标记由系统自动生成");
        }
        if (!users.isTutorOf(actor.getId(), learnerId) && actor.getRole() != Role.OPS) {
            throw new ForbiddenException("只有学习者的辅导者可以标记");
        }
        KnowledgeNode node = learnerNode(learnerId, nodeId);
        LearnerFlag f = save(learnerId, node.getId(), kind, note, actor.getId());
        if (kind == FlagKind.DIAGNOSIS_WRONG) {
            retests.schedule(learnerId, node.getId(), RetestReason.TUTOR_CHECK, null);
        }
        return view(f, node);
    }

    /** 系统标记（不重复标同一个未处理的） */
    @Transactional
    public void systemFlag(Long learnerId, Long nodeId, FlagKind kind, String note) {
        if (!flags.existsByLearnerIdAndNodeIdAndKindAndResolvedFalse(learnerId, nodeId, kind)) {
            save(learnerId, nodeId, kind, note, null);
        }
    }

    @Transactional
    public FlagView resolve(AppUser actor, Long flagId) {
        LearnerFlag f = flags.findById(flagId).orElseThrow(() -> NotFoundException.of("标记", flagId));
        if (!users.isTutorOf(actor.getId(), f.getLearnerId()) && actor.getRole() != Role.OPS) {
            throw new ForbiddenException("只有学习者的辅导者可以处理标记");
        }
        f.setResolved(true);
        f.setResolvedAt(LocalDateTime.now(clock));
        return view(f, nodes.findById(f.getNodeId()).orElse(null));
    }

    public List<FlagView> of(AppUser actor, Long learnerId) {
        users.checkCanView(actor, learnerId);
        return flags.findByLearnerIdOrderByIdDesc(learnerId).stream()
                .map(f -> view(f, nodes.findById(f.getNodeId()).orElse(null))).toList();
    }

    public List<FlaggedNode> diagnosisWrongByMasterNode(AppUser actor) {
        if (actor.getRole() != Role.OPS) {
            throw new ForbiddenException("只有运维可以看全平台的标记");
        }
        record Key(Long master, String name, Long bookId) {
        }
        Map<Key, List<LearnerFlag>> grouped = new HashMap<>();
        for (LearnerFlag f : flags.findByKind(FlagKind.DIAGNOSIS_WRONG)) {
            KnowledgeNode n = nodes.findById(f.getNodeId()).orElse(null);
            if (n == null) {
                continue;
            }
            Long master = n.getOriginNodeId() != null ? n.getOriginNodeId() : n.getId();
            Long bookId = graphs.findById(n.getGraphId()).map(KnowledgeGraph::getBookId).orElse(null);
            grouped.computeIfAbsent(new Key(master, n.getName(), bookId), k -> new ArrayList<>()).add(f);
        }
        return grouped.entrySet().stream()
                .map(e -> new FlaggedNode(e.getKey().master(), e.getKey().name(), e.getKey().bookId(),
                        e.getValue().stream().map(LearnerFlag::getLearnerId).distinct().count(), e.getValue().size()))
                .sorted(Comparator.comparingLong(FlaggedNode::learners).reversed().thenComparing(FlaggedNode::masterNodeId))
                .toList();
    }

    private LearnerFlag save(Long learnerId, Long nodeId, FlagKind kind, String note, Long by) {
        LearnerFlag f = new LearnerFlag();
        f.setLearnerId(learnerId);
        f.setNodeId(nodeId);
        f.setKind(kind);
        f.setNote(note);
        f.setCreatedBy(by);
        return flags.save(f);
    }

    private KnowledgeNode learnerNode(Long learnerId, Long nodeId) {
        KnowledgeNode n = nodes.findById(nodeId).orElseThrow(() -> NotFoundException.of("节点", nodeId));
        KnowledgeGraph g = graphs.findById(n.getGraphId()).orElseThrow();
        if (!Objects.equals(g.getOwnerLearnerId(), learnerId)) {
            throw new IllegalArgumentException("这个节点不在该学习者的图谱上");
        }
        return n;
    }

    private FlagView view(LearnerFlag f, KnowledgeNode n) {
        return new FlagView(f.getId(), f.getLearnerId(), f.getNodeId(), n == null ? null : n.getName(), f.getKind(),
                f.getNote(), f.getCreatedBy(), f.isResolved(), f.getCreatedAt());
    }
}
