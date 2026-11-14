package com.oep.corelms.service;

import com.oep.corelms.dto.quiz.QuizDtos;
import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.quiz.*;
import com.oep.coremodel.enums.*;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizManagementServiceTest {

    @Mock private QuizRepository quizRepository;
    @Mock private QuestionBankRepository questionBankRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private QuestionOptionRepository questionOptionRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private QuizResultRepository quizResultRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseInstructorRepository courseInstructorRepository;
    @Mock private LearningOutcomeRepository learningOutcomeRepository;

    private QuizManagementService service;

    private final UUID actorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID quizId = UUID.randomUUID();

    private User actor;
    private Course course;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        service = new QuizManagementService(
                quizRepository,
                questionBankRepository,
                questionRepository,
                questionOptionRepository,
                quizQuestionRepository,
                quizAttemptRepository,
                quizResultRepository,
                courseRepository,
                lessonRepository,
                userRepository,
                courseInstructorRepository,
                learningOutcomeRepository
        );

        actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.INSTRUCTOR);

        course = new Course();
        course.setId(courseId);
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);

        quiz = new Quiz();
        quiz.setId(quizId);
        quiz.setCourse(course);
        quiz.setTitle("Midterm Quiz");
        quiz.setLocked(false);

        CourseInstructor instructorLink = new CourseInstructor();
        instructorLink.setCourseRole(CourseInstructorRole.OWNER);

        lenient().when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        lenient().when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        lenient().when(courseInstructorRepository.findByIdCourseIdAndIdInstructorId(courseId, actorId))
                .thenReturn(Optional.of(instructorLink));
    }

    @Test
    void createQuiz_success() {
        QuizDtos.CreateQuizRequest req = new QuizDtos.CreateQuizRequest(
                "Introduction Quiz",
                "A quiz to test basics",
                null,
                "FIXED",
                1800,
                null,
                2,
                QuizGradingPolicy.HIGHEST,
                true,
                false,
                true,
                false,
                Quiz.Kind.MINI_QUIZ
        );

        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        QuizDtos.QuizResponse resp = service.createQuiz(actorId, courseId, req);

        assertNotNull(resp);
        assertEquals("Introduction Quiz", resp.title());
        assertEquals("FIXED", resp.questionSource());
        assertEquals(2, resp.maxAttempts());
        assertFalse(resp.isLocked());
        verify(quizRepository).save(any(Quiz.class));
    }

    @Test
    void updateQuiz_fails_when_locked() {
        quiz.setLocked(true);
        when(quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizDtos.UpdateQuizRequest req = new QuizDtos.UpdateQuizRequest(
                "Updated Title", null, null, null, null, null, null, null, null, null, null, null, null
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.updateQuiz(actorId, courseId, quizId, req)
        );

        assertEquals(ErrorCode.QUIZ_NOT_EDITABLE, ex.getCode());
        verify(quizRepository, never()).save(any());
    }

    @Test
    void deleteQuiz_fails_when_has_attempts() {
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.existsByQuizId(quizId)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.deleteQuiz(actorId, courseId, quizId)
        );

        assertEquals(ErrorCode.QUIZ_HAS_ATTEMPTS, ex.getCode());
        verify(quizRepository, never()).delete(any());
    }

    @Test
    void deleteQuiz_success_when_no_attempts() {
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizAttemptRepository.existsByQuizId(quizId)).thenReturn(false);

        QuizDtos.DeleteResponse resp = service.deleteQuiz(actorId, courseId, quizId);

        assertTrue(resp.success());
        assertEquals(quizId, resp.deletedId());
        verify(quizQuestionRepository).deleteByQuizId(quizId);
        verify(quizRepository).delete(quiz);
    }

    @Test
    void createQuestion_fails_on_invalid_tf_options() {
        when(quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)).thenReturn(Optional.of(quiz));

        // 3 options instead of 2 for True/False
        QuizDtos.CreateQuestionRequest req = new QuizDtos.CreateQuestionRequest(
                QuestionType.TRUE_FALSE,
                "Is the earth flat?",
                null,
                null,
                null,
                null,
                BigDecimal.ONE,
                null,
                List.of(
                        new QuizDtos.OptionRequest("True", true, 0),
                        new QuizDtos.OptionRequest("False", false, 1),
                        new QuizDtos.OptionRequest("Maybe", false, 2)
                )
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.createQuestion(actorId, courseId, quizId, req)
        );

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getCode());
        assertTrue(ex.getMessage().contains("exactly 2 options"));
    }

    @Test
    void createQuestion_success_adds_options_and_quiz_question() {
        when(quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)).thenReturn(Optional.of(quiz));
        when(quizQuestionRepository.findMaxPositionByQuizId(quizId)).thenReturn(0);

        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        when(questionOptionRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            List<QuestionOption> list = new ArrayList<>();
            for (QuestionOption opt : (Iterable<QuestionOption>) inv.getArgument(0)) {
                opt.setId(UUID.randomUUID());
                list.add(opt);
            }
            return list;
        });

        when(quizQuestionRepository.save(any(QuizQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizDtos.CreateQuestionRequest req = new QuizDtos.CreateQuestionRequest(
                QuestionType.SINGLE_CHOICE,
                "What is 2+2?",
                null,
                null,
                null,
                null,
                new BigDecimal("2.5"),
                null,
                List.of(
                        new QuizDtos.OptionRequest("3", false, 0),
                        new QuizDtos.OptionRequest("4", true, 1),
                        new QuizDtos.OptionRequest("5", false, 2)
                )
        );

        QuizDtos.QuestionResponse resp = service.createQuestion(actorId, courseId, quizId, req);

        assertNotNull(resp);
        assertEquals("What is 2+2?", resp.questionText());
        assertEquals(QuestionType.SINGLE_CHOICE, resp.questionType());
        assertEquals(new BigDecimal("2.5"), resp.points());
        assertEquals(1, resp.position()); // maxPosition 0 + 1
        assertEquals(3, resp.options().size());
        verify(questionRepository).save(any(Question.class));
        verify(questionOptionRepository).saveAll(anyIterable());
        verify(quizQuestionRepository).save(any(QuizQuestion.class));
    }

    @Test
    void reorderQuestions_success_contiguous_positions() {
        when(quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)).thenReturn(Optional.of(quiz));

        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        UUID q3Id = UUID.randomUUID();

        QuizQuestion qq1 = createQuizQuestion(q1Id, 0);
        QuizQuestion qq2 = createQuizQuestion(q2Id, 1);
        QuizQuestion qq3 = createQuizQuestion(q3Id, 2);

        when(quizQuestionRepository.findByQuizIdOrderByPositionAsc(quizId))
                .thenReturn(List.of(qq1, qq2, qq3));

        // Reorder: q3, q1, q2
        QuizDtos.ReorderRequest req = new QuizDtos.ReorderRequest(
                List.of(q3Id, q1Id, q2Id),
                null
        );

        QuizDtos.ReorderResponse resp = service.reorderQuestions(actorId, courseId, quizId, req);

        assertTrue(resp.success());
        assertEquals(3, resp.reorderedCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuizQuestion>> captor = ArgumentCaptor.forClass(List.class);
        verify(quizQuestionRepository, times(2)).saveAllAndFlush(captor.capture());

        List<QuizQuestion> secondPhase = captor.getAllValues().get(1);
        assertEquals(q3Id, secondPhase.get(0).getQuestion().getId());
        assertEquals(0, secondPhase.get(0).getPosition());
        assertEquals(q1Id, secondPhase.get(1).getQuestion().getId());
        assertEquals(1, secondPhase.get(1).getPosition());
        assertEquals(q2Id, secondPhase.get(2).getQuestion().getId());
        assertEquals(2, secondPhase.get(2).getPosition());
    }

    @Test
    void reorderQuestions_fails_on_missing_id() {
        when(quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)).thenReturn(Optional.of(quiz));

        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();
        QuizQuestion qq1 = createQuizQuestion(q1Id, 0);
        QuizQuestion qq2 = createQuizQuestion(q2Id, 1);

        when(quizQuestionRepository.findByQuizIdOrderByPositionAsc(quizId))
                .thenReturn(List.of(qq1, qq2));

        // Missing q2Id
        QuizDtos.ReorderRequest req = new QuizDtos.ReorderRequest(
                List.of(q1Id),
                null
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.reorderQuestions(actorId, courseId, quizId, req)
        );

        assertEquals(ErrorCode.QUESTION_ORDER_INVALID, ex.getCode());
        verify(quizQuestionRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void adjustScore_fails_when_exceeds_total_points() {
        UUID resultId = UUID.randomUUID();
        when(quizRepository.findByIdAndCourseId(quizId, courseId)).thenReturn(Optional.of(quiz));

        QuizResult result = new QuizResult();
        result.setId(resultId);
        result.setAutoScore(new BigDecimal("8.0"));
        result.setFinalScore(new BigDecimal("8.0"));

        when(quizResultRepository.findByIdAndQuizId(resultId, quizId)).thenReturn(Optional.of(result));
        when(quizQuestionRepository.sumPointsByQuizId(quizId)).thenReturn(new BigDecimal("10.0"));

        QuizDtos.AdjustScoreRequest req = new QuizDtos.AdjustScoreRequest(
                new BigDecimal("12.0"), "Bonus points"
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.adjustScore(actorId, courseId, quizId, resultId, req)
        );

        assertEquals(ErrorCode.SCORE_OUT_OF_RANGE, ex.getCode());
        verify(quizResultRepository, never()).save(any());
    }

    @Test
    void deleteQuestionBank_fails_when_contains_questions() {
        UUID bankId = UUID.randomUUID();
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setCourse(course);

        when(questionBankRepository.findByIdAndCourseId(bankId, courseId)).thenReturn(Optional.of(bank));
        when(questionRepository.existsByQuestionBankId(bankId)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.deleteQuestionBank(actorId, courseId, bankId)
        );

        assertEquals(ErrorCode.QUESTION_BANK_NOT_EMPTY, ex.getCode());
        verify(questionBankRepository, never()).delete(any());
    }

    @Test
    void adminListAllQuizzes_requires_admin() {
        // Actor is INSTRUCTOR, not ADMIN
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.adminListAllQuizzes(actorId, null, 1, 20)
        );

        assertEquals(ErrorCode.ADMIN_ROLE_REQUIRED, ex.getCode());
        verify(quizRepository, never()).findAllAdmin(any(), any(Pageable.class));
    }

    private QuizQuestion createQuizQuestion(UUID questionId, int position) {
        Question q = new Question();
        q.setId(questionId);
        QuizQuestion qq = new QuizQuestion();
        qq.setId(new QuizQuestionId(quizId, questionId));
        qq.setQuiz(quiz);
        qq.setQuestion(q);
        qq.setPosition(position);
        qq.setPoints(BigDecimal.ONE);
        return qq;
    }
}
