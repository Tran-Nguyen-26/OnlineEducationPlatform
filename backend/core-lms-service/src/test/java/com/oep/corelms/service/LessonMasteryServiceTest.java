package com.oep.corelms.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LessonMasteryServiceTest {
    @Test
    void requiresAllThreeConditionsBeforeUnlockingNextLesson() {
        for (var first : List.of(evidence(false, true, true, false), evidence(true, false, true, false),
                evidence(true, true, false, false))) {
            var state = LessonMasteryService.evaluate(List.of(first, evidence(false, false, false, false)));
            assertThat(state).extracting(LessonMasteryService.Lesson::unlocked).containsExactly(true, false);
        }
        var state = LessonMasteryService.evaluate(List.of(evidence(true, true, true, false), evidence(false, false, false, false)));
        assertThat(state).extracting(LessonMasteryService.Lesson::unlocked).containsExactly(true, true);
    }

    @Test
    void fastTrackUnlocksOnlyTheMatchingLessonAcrossAnUnfinishedGap() {
        var state = LessonMasteryService.evaluate(List.of(evidence(false, false, false, false),
                evidence(false, false, false, true), evidence(false, false, false, false)));
        assertThat(state).extracting(LessonMasteryService.Lesson::unlocked).containsExactly(true, true, false);
    }

    private LessonMasteryService.Evidence evidence(boolean video, boolean documents, boolean quiz, boolean fastTrack) {
        return new LessonMasteryService.Evidence(UUID.randomUUID(), "Lesson", video, documents, quiz, fastTrack);
    }
}
