package com.learnmap.palace;

import com.learnmap.common.CurrentUser;
import com.learnmap.palace.PalaceService.CandidateNode;
import com.learnmap.palace.PalaceService.DrillQuestion;
import com.learnmap.palace.PalaceService.DrillResult;
import com.learnmap.palace.PalaceService.PalaceSummary;
import com.learnmap.palace.PalaceService.PalaceView;
import com.learnmap.palace.PalaceService.PlacementRequest;
import com.learnmap.palace.PalaceService.RecallResult;
import com.learnmap.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/palaces")
@RequiredArgsConstructor
public class PalaceController {

    private final PalaceService palaceService;
    private final UserService users;

    public record CreateRequest(String name, String place, Long graphId) {
    }

    public record LociRequest(List<String> names) {
    }

    /** mode：FORWARD 顺背 / BACKWARD 倒背（answers 按顺序）/ NEXT 随机问下一个（fromOrdinal + answer） */
    public record DrillRequest(String mode, List<String> answers, Integer fromOrdinal, String answer) {
    }

    public record ImageRequest(String image) {
    }

    public record RecallRequest(List<String> answers) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PalaceView create(@RequestHeader(CurrentUser.HEADER) Long userId, @RequestBody CreateRequest req) {
        return palaceService.create(users.require(userId), req.name(), req.place(), req.graphId());
    }

    @GetMapping
    public List<PalaceSummary> list(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return palaceService.list(users.require(userId));
    }

    @GetMapping("/due")
    public Map<String, Long> due(@RequestHeader(CurrentUser.HEADER) Long userId) {
        return Map.of("dueCount", palaceService.dueCount(users.require(userId)));
    }

    @GetMapping("/{id}")
    public PalaceView get(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return palaceService.get(users.require(userId), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        palaceService.delete(users.require(userId), id);
    }

    @PutMapping("/{id}/loci")
    public PalaceView loci(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @RequestBody LociRequest req) {
        return palaceService.setLoci(users.require(userId), id, req.names());
    }

    @GetMapping("/{id}/drill/next")
    public DrillQuestion nextQuestion(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return palaceService.nextQuestion(users.require(userId), id);
    }

    @PostMapping("/{id}/drill")
    public DrillResult drill(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @RequestBody DrillRequest req) {
        var actor = users.require(userId);
        return switch (req.mode() == null ? "" : req.mode()) {
            case "FORWARD" -> palaceService.drillSequence(actor, id, false, req.answers());
            case "BACKWARD" -> palaceService.drillSequence(actor, id, true, req.answers());
            case "NEXT" -> {
                if (req.fromOrdinal() == null) {
                    throw new IllegalArgumentException("缺少 fromOrdinal");
                }
                yield palaceService.drillNext(actor, id, req.fromOrdinal(), req.answer());
            }
            default -> throw new IllegalArgumentException("mode 只能是 FORWARD / BACKWARD / NEXT");
        };
    }

    @GetMapping("/{id}/candidates")
    public List<CandidateNode> candidates(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id,
                                          @RequestParam(required = false) Long graphId) {
        return palaceService.candidates(users.require(userId), id, graphId);
    }

    @PutMapping("/{id}/placements")
    public PalaceView place(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id,
                            @RequestBody List<PlacementRequest> items) {
        return palaceService.place(users.require(userId), id, items);
    }

    @PostMapping("/{id}/images")
    public PalaceView images(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id,
                             @RequestParam(defaultValue = "false") boolean regenerate) {
        return palaceService.suggestImages(users.require(userId), id, regenerate);
    }

    @PatchMapping("/placements/{placementId}")
    public PalaceView editImage(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long placementId,
                                @RequestBody ImageRequest req) {
        return palaceService.editImage(users.require(userId), placementId, req.image());
    }

    @PostMapping("/{id}/clear")
    public PalaceView clear(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id) {
        return palaceService.clear(users.require(userId), id);
    }

    @PostMapping("/{id}/recall")
    public RecallResult recall(@RequestHeader(CurrentUser.HEADER) Long userId, @PathVariable Long id, @RequestBody RecallRequest req) {
        return palaceService.recall(users.require(userId), id, req.answers());
    }
}
