package com.learnmap.mastery;

import com.learnmap.evidence.SelfConfidence;
import com.learnmap.mastery.MasteryEngine.State;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MasteryEngineTest {

    @Test
    void repeatedCorrectAnswersOnReviewedNodeReachMastered() {
        State s = State.EMPTY;
        for (int i = 0; i < 6; i++) {
            s = MasteryEngine.update(s, 1, 0.6, SelfConfidence.SURE, true);
        }
        assertThat(s.mastered()).isTrue();
        assertThat(s.evidenceCount()).isEqualTo(6);
    }

    @Test
    void unreviewedNodeConfidenceIsCapped() {
        State s = State.EMPTY;
        for (int i = 0; i < 20; i++) {
            s = MasteryEngine.update(s, 1, 0.6, SelfConfidence.SURE, false);
        }
        assertThat(s.confidence()).isEqualTo(MasteryEngine.UNREVIEWED_CONFIDENCE_CAP);
        assertThat(s.mastered()).isTrue();
    }

    @Test
    void wrongAnswerOnHardQuestionHurtsLessThanOnEasyQuestion() {
        State start = new State(0.8, 0.5, 3);
        State hard = MasteryEngine.update(start, 0, 0.9, null, true);
        State easy = MasteryEngine.update(start, 0, 0.1, null, true);
        assertThat(hard.mastery()).isGreaterThan(easy.mastery());
        assertThat(hard.mastery()).isLessThan(start.mastery());
    }

    @Test
    void correctButSelfRatedDontKnowCountsAsGuess() {
        double sure = MasteryEngine.target(1, 0.5, SelfConfidence.SURE);
        double guess = MasteryEngine.target(1, 0.5, SelfConfidence.DONT_KNOW);
        assertThat(guess).isCloseTo(sure / 2, within(1e-9));
    }

    @Test
    void recentEvidenceWeighsMoreAfterManyAnswers() {
        State s = State.EMPTY;
        for (int i = 0; i < 10; i++) {
            s = MasteryEngine.update(s, 1, 0.5, null, true);
        }
        State afterMiss = MasteryEngine.update(s, 0, 0.5, null, true);
        assertThat(s.mastery() - afterMiss.mastery()).isGreaterThan(0.15);
    }
}
