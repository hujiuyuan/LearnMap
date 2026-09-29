package com.learnmap.card;

import com.learnmap.card.Fsrs.Memory;
import com.learnmap.card.Fsrs.Result;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FsrsTest {

    @Test
    void newCardStabilityComesFromRating() {
        Memory fresh = new Memory(CardState.NEW, 0, 0);
        assertThat(Fsrs.review(fresh, Rating.GOOD, 0).stability()).isEqualTo(Fsrs.W[2]);
        assertThat(Fsrs.review(fresh, Rating.EASY, 0).interval()).isGreaterThan(Fsrs.review(fresh, Rating.GOOD, 0).interval());
        assertThat(Fsrs.review(fresh, Rating.AGAIN, 0).interval()).isEqualTo(Fsrs.RELEARN_DELAY);
    }

    @Test
    void intervalEqualsStabilityAtNinetyPercentRetention() {
        assertThat(Fsrs.interval(10)).isCloseTo(10, within(1e-6));
        assertThat(Fsrs.retrievability(10, 10)).isCloseTo(0.9, within(1e-6));
    }

    @Test
    void successfulReviewsGrowIntervals() {
        Memory m = new Memory(CardState.NEW, 0, 0);
        Duration last = Duration.ZERO;
        double elapsed = 0;
        for (int i = 0; i < 4; i++) {
            Result r = Fsrs.review(m, Rating.GOOD, elapsed);
            assertThat(r.interval()).isGreaterThan(last);
            last = r.interval();
            elapsed = r.interval().toDays();
            m = new Memory(r.state(), r.stability(), r.difficulty());
        }
        assertThat(last.toDays()).isGreaterThan(20);
    }

    @Test
    void forgettingDropsStabilityAndRaisesDifficulty() {
        Memory m = new Memory(CardState.REVIEW, 20, 5);
        Result r = Fsrs.review(m, Rating.AGAIN, 20);
        assertThat(r.stability()).isLessThan(20);
        assertThat(r.difficulty()).isGreaterThan(5);
        assertThat(r.state()).isEqualTo(CardState.RELEARNING);
    }
}
