package com.oep.corelms.controller;

import com.oep.corelms.service.CurriculumAlignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.oep.corelms.service.CurriculumAlignmentService.*;

@RestController
@RequestMapping("/api/v1/courses/{courseId}/curriculum")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CurriculumAlignmentController {
    private final CurriculumAlignmentService service;

    @GetMapping
    public Curriculum get(@PathVariable UUID courseId) {
        return service.get(courseId);
    }

    @PutMapping("/weights")
    public Weights weights(@PathVariable UUID courseId, @Valid @RequestBody Weights weights) {
        return service.setWeights(courseId, weights);
    }

    @PostMapping("/outcomes")
    public Outcome outcome(@PathVariable UUID courseId, @Valid @RequestBody OutcomeInput input) {
        return service.saveOutcome(courseId, null, input);
    }

    @PutMapping("/outcomes/{outcomeId}")
    public Outcome updateOutcome(@PathVariable UUID courseId, @PathVariable UUID outcomeId,
                                 @Valid @RequestBody OutcomeInput input) {
        return service.saveOutcome(courseId, outcomeId, input);
    }

    @PutMapping("/materials/{materialId}")
    public void material(@PathVariable UUID courseId, @PathVariable UUID materialId,
                         @Valid @RequestBody OutcomeLink link) {
        service.tagMaterial(courseId, materialId, link.outcomeId());
    }

    @PutMapping("/quizzes/{quizId}")
    public void quiz(@PathVariable UUID courseId, @PathVariable UUID quizId,
                     @Valid @RequestBody QuizClassification classification) {
        service.classifyQuiz(courseId, quizId, classification.kind());
    }

    @PutMapping("/quizzes/{quizId}/questions/{questionId}")
    public void question(@PathVariable UUID courseId, @PathVariable UUID quizId,
                         @PathVariable UUID questionId, @Valid @RequestBody OutcomeLink link) {
        service.tagQuestion(courseId, quizId, questionId, link.outcomeId());
    }
}
