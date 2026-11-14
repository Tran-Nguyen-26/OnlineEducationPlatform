package com.oep.corelms.service;

import com.oep.corelms.dto.quiz.QuizDtos;
import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.LearningOutcome;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.quiz.*;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.QuestionType;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QuizManagementService {

    private final QuizRepository quizRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizResultRepository quizResultRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final CourseInstructorRepository courseInstructorRepository;
    private final LearningOutcomeRepository learningOutcomeRepository;

    // ==========================================
    // QUIZ CRUD
    // ==========================================

    @Transactional(readOnly = true)
    public QuizDtos.QuizPage listQuizzes(UUID actorId, UUID courseId, int page, int pageSize) {
        requireCourseManager(courseId, actorId);
        validatePage(page, pageSize);
        Page<Quiz> result = quizRepository.findByCourseId(courseId, PageRequest.of(page - 1, pageSize));
        return new QuizDtos.QuizPage(result.getContent().stream().map(this::toQuizResponse).toList(), pageInfo(result));
    }

    @Transactional(readOnly = true)
    public QuizDtos.QuizResponse getQuiz(UUID actorId, UUID courseId, UUID quizId) {
        requireCourseManager(courseId, actorId);
        Quiz quiz = quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));
        return toQuizResponse(quiz);
    }

    @Transactional
    public QuizDtos.QuizResponse createQuiz(UUID actorId, UUID courseId, QuizDtos.CreateQuizRequest req) {
        Course course = requireEditableCourse(courseId, actorId);
        User actor = requireUser(actorId);

        Lesson lesson = null;
        if (req.lessonId() != null) {
            lesson = lessonRepository.findById(req.lessonId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson not found"));
            if (!lesson.getModule().getCourse().getId().equals(courseId)) {
                throw new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson does not belong to course");
            }
        }

        Quiz quiz = new Quiz();
        quiz.setCourse(course);
        quiz.setLesson(lesson);
        quiz.setTitle(req.title());
        quiz.setDescription(req.description());
        if (req.assessmentKind() != null) quiz.setAssessmentKind(req.assessmentKind());
        quiz.setQuestionSource(req.questionSource() != null ? req.questionSource() : "FIXED");
        quiz.setTimeLimitSeconds(req.timeLimitSeconds());
        quiz.setDeadline(req.deadline());
        if (req.maxAttempts() != null) {
            quiz.setMaxAttempts(req.maxAttempts());
        }
        if (req.gradingPolicy() != null) {
            quiz.setGradingPolicy(req.gradingPolicy());
        }
        if (req.randomizeQuestions() != null) {
            quiz.setRandomizeQuestions(req.randomizeQuestions());
        }
        if (req.randomizeAnswers() != null) {
            quiz.setRandomizeAnswers(req.randomizeAnswers());
        }
        if (req.showScore() != null) {
            quiz.setShowScore(req.showScore());
        }
        if (req.showCorrectAnswers() != null) {
            quiz.setShowCorrectAnswers(req.showCorrectAnswers());
        }
        quiz.setLocked(false);
        quiz.setCreatedBy(actor);

        quiz = quizRepository.save(quiz);
        return toQuizResponse(quiz);
    }

    @Transactional
    public QuizDtos.QuizResponse updateQuiz(UUID actorId, UUID courseId, UUID quizId, QuizDtos.UpdateQuizRequest req) {
        requireEditableCourse(courseId, actorId);
        Quiz quiz = quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_EDITABLE, "Quiz is locked and cannot be edited");
        }

        if (req.title() != null) quiz.setTitle(req.title());
        if (req.description() != null) quiz.setDescription(req.description());
        if (req.assessmentKind() != null) quiz.setAssessmentKind(req.assessmentKind());
        if (req.questionSource() != null) quiz.setQuestionSource(req.questionSource());
        if (req.timeLimitSeconds() != null) quiz.setTimeLimitSeconds(req.timeLimitSeconds());
        if (req.deadline() != null) quiz.setDeadline(req.deadline());
        if (req.maxAttempts() != null) quiz.setMaxAttempts(req.maxAttempts());
        if (req.gradingPolicy() != null) quiz.setGradingPolicy(req.gradingPolicy());
        if (req.randomizeQuestions() != null) quiz.setRandomizeQuestions(req.randomizeQuestions());
        if (req.randomizeAnswers() != null) quiz.setRandomizeAnswers(req.randomizeAnswers());
        if (req.showScore() != null) quiz.setShowScore(req.showScore());
        if (req.showCorrectAnswers() != null) quiz.setShowCorrectAnswers(req.showCorrectAnswers());

        if (req.lessonId() != null) {
            Lesson lesson = lessonRepository.findById(req.lessonId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson not found"));
            if (!lesson.getModule().getCourse().getId().equals(courseId)) {
                throw new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson does not belong to course");
            }
            quiz.setLesson(lesson);
        }

        quiz = quizRepository.save(quiz);
        return toQuizResponse(quiz);
    }

    @Transactional
    public QuizDtos.DeleteResponse deleteQuiz(UUID actorId, UUID courseId, UUID quizId) {
        requireEditableCourse(courseId, actorId);
        Quiz quiz = quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        if (quizAttemptRepository.existsByQuizId(quizId)) {
            throw new BusinessException(ErrorCode.QUIZ_HAS_ATTEMPTS, "Quiz has attempts and cannot be deleted");
        }

        quizQuestionRepository.deleteByQuizId(quizId);
        quizRepository.delete(quiz);
        return new QuizDtos.DeleteResponse(true, quizId);
    }

    // ==========================================
    // QUESTION BANK CRUD
    // ==========================================

    @Transactional(readOnly = true)
    public QuizDtos.BankPage listQuestionBanks(UUID actorId, UUID courseId, int page, int pageSize) {
        requireCourseManager(courseId, actorId);
        validatePage(page, pageSize);
        Page<QuestionBank> result = questionBankRepository.findByCourseId(courseId, PageRequest.of(page - 1, pageSize));
        return new QuizDtos.BankPage(result.getContent().stream().map(this::toBankResponse).toList(), pageInfo(result));
    }

    @Transactional(readOnly = true)
    public List<QuizDtos.QuestionOutcomeResponse> listQuestionOutcomes(UUID actorId, UUID courseId) {
        requireCourseManager(courseId, actorId);
        return learningOutcomeRepository.findByCourseIdOrderByCode(courseId).stream()
                .map(outcome -> new QuizDtos.QuestionOutcomeResponse(
                        outcome.getId(), outcome.getCode(), outcome.getDescription(), outcome.getBloom()))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuizDtos.BankResponse getQuestionBank(UUID actorId, UUID courseId, UUID bankId) {
        requireCourseManager(courseId, actorId);
        QuestionBank bank = questionBankRepository.findByIdAndCourseId(bankId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND, "Question bank not found"));
        return toBankResponse(bank);
    }

    @Transactional
    public QuizDtos.BankResponse createQuestionBank(UUID actorId, UUID courseId, QuizDtos.CreateBankRequest req) {
        Course course = requireEditableCourse(courseId, actorId);
        User actor = requireUser(actorId);

        QuestionBank bank = new QuestionBank();
        bank.setCourse(course);
        bank.setName(req.name());
        bank.setCreatedBy(actor);

        bank = questionBankRepository.save(bank);
        return toBankResponse(bank);
    }

    @Transactional
    public QuizDtos.BankResponse updateQuestionBank(UUID actorId, UUID courseId, UUID bankId, QuizDtos.UpdateBankRequest req) {
        requireEditableCourse(courseId, actorId);
        QuestionBank bank = questionBankRepository.findByIdAndCourseId(bankId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND, "Question bank not found"));

        bank.setName(req.name());
        bank = questionBankRepository.save(bank);
        return toBankResponse(bank);
    }

    @Transactional
    public QuizDtos.DeleteResponse deleteQuestionBank(UUID actorId, UUID courseId, UUID bankId) {
        requireEditableCourse(courseId, actorId);
        QuestionBank bank = questionBankRepository.findByIdAndCourseId(bankId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND, "Question bank not found"));

        if (questionRepository.existsByQuestionBankId(bankId)) {
            throw new BusinessException(ErrorCode.QUESTION_BANK_NOT_EMPTY, "Question bank contains questions and cannot be deleted");
        }

        questionBankRepository.delete(bank);
        return new QuizDtos.DeleteResponse(true, bankId);
    }

    // ==========================================
    // QUESTION & OPTIONS CRUD (IN QUIZ)
    // ==========================================

    @Transactional(readOnly = true)
    public QuizDtos.QuestionPage listQuestions(UUID actorId, UUID courseId, UUID quizId, int page, int pageSize) {
        requireCourseManager(courseId, actorId);
        validatePage(page, pageSize);
        quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        Page<QuizQuestion> qqPage = quizQuestionRepository.findByQuizIdOrderByPositionAsc(quizId, PageRequest.of(page - 1, pageSize));
        List<QuizDtos.QuestionResponse> items = qqPage.getContent().stream().map(qq -> {
            List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByPositionAsc(qq.getQuestion().getId());
            return toQuestionResponse(qq, options);
        }).toList();

        return new QuizDtos.QuestionPage(items, pageInfo(qqPage));
    }

    @Transactional(readOnly = true)
    public QuizDtos.QuestionResponse getQuestion(UUID actorId, UUID courseId, UUID quizId, UUID questionId) {
        requireCourseManager(courseId, actorId);
        quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        QuizQuestion qq = quizQuestionRepository.findByQuizIdAndQuestionId(quizId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Question not found in quiz"));

        List<QuestionOption> options = questionOptionRepository.findByQuestionIdOrderByPositionAsc(questionId);
        return toQuestionResponse(qq, options);
    }

    @Transactional
    public QuizDtos.QuestionResponse createQuestion(UUID actorId, UUID courseId, UUID quizId, QuizDtos.CreateQuestionRequest req) {
        requireEditableCourse(courseId, actorId);
        User actor = requireUser(actorId);

        Quiz quiz = quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_EDITABLE, "Quiz is locked and cannot be edited");
        }

        validateQuestionOptions(req.questionType(), req.options());

        QuestionBank bank = null;
        if (req.questionBankId() != null) {
            bank = questionBankRepository.findByIdAndCourseId(req.questionBankId(), courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND, "Question bank not found in course"));
        }

        Question question = new Question();
        question.setQuestionBank(bank);
        if (req.learningOutcomeId() != null) {
            question.setLearningOutcome(learningOutcomeRepository.findByIdAndCourseId(req.learningOutcomeId(), courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Learning outcome not found in course")));
        }
        question.setBloomLevel(req.bloomLevel());
        question.setQuestionType(req.questionType());
        question.setQuestionText(req.questionText());
        question.setKeywordAnswer(req.keywordAnswer());
        question.setCreatedBy(actor);
        question = questionRepository.save(question);

        List<QuestionOption> savedOptions = new ArrayList<>();
        if (req.options() != null && !req.options().isEmpty()) {
            for (int i = 0; i < req.options().size(); i++) {
                QuizDtos.OptionRequest optReq = req.options().get(i);
                QuestionOption opt = new QuestionOption();
                opt.setQuestion(question);
                opt.setOptionText(optReq.optionText());
                opt.setCorrect(optReq.isCorrect());
                opt.setPosition(optReq.position() != null ? optReq.position() : i);
                savedOptions.add(opt);
            }
            savedOptions = questionOptionRepository.saveAll(savedOptions);
        }

        int position = req.position() != null ? req.position() : quizQuestionRepository.findMaxPositionByQuizId(quizId) + 1;
        BigDecimal points = req.points() != null ? req.points() : BigDecimal.ONE;

        QuizQuestion qq = new QuizQuestion();
        qq.setId(new QuizQuestionId(quiz.getId(), question.getId()));
        qq.setQuiz(quiz);
        qq.setQuestion(question);
        qq.setPosition(position);
        qq.setPoints(points);
        qq = quizQuestionRepository.save(qq);

        return toQuestionResponse(qq, savedOptions);
    }

    @Transactional
    public QuizDtos.QuestionResponse updateQuestion(UUID actorId, UUID courseId, UUID quizId, UUID questionId, QuizDtos.UpdateQuestionRequest req) {
        requireEditableCourse(courseId, actorId);

        Quiz quiz = quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_EDITABLE, "Quiz is locked and cannot be edited");
        }

        QuizQuestion qq = quizQuestionRepository.findByQuizIdAndQuestionId(quizId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Question not found in quiz"));

        Question question = qq.getQuestion();

        if (req.questionType() != null) question.setQuestionType(req.questionType());
        if (req.questionText() != null) question.setQuestionText(req.questionText());
        if (req.keywordAnswer() != null) question.setKeywordAnswer(req.keywordAnswer());

        if (req.clearQuestionBank()) question.setQuestionBank(null);
        if (req.questionBankId() != null) {
            QuestionBank bank = questionBankRepository.findByIdAndCourseId(req.questionBankId(), courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_BANK_NOT_FOUND, "Question bank not found in course"));
            question.setQuestionBank(bank);
        }
        if (req.clearLearningOutcome()) question.setLearningOutcome(null);
        if (req.learningOutcomeId() != null) {
            question.setLearningOutcome(learningOutcomeRepository.findByIdAndCourseId(req.learningOutcomeId(), courseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Learning outcome not found in course")));
        }
        if (req.bloomLevel() != null) question.setBloomLevel(req.bloomLevel());

        if (req.points() != null) qq.setPoints(req.points());
        if (req.position() != null) qq.setPosition(req.position());

        List<QuestionOption> options;
        if (req.options() != null) {
            validateQuestionOptions(question.getQuestionType(), req.options());
            questionOptionRepository.deleteByQuestionId(questionId);
            List<QuestionOption> newOptions = new ArrayList<>();
            for (int i = 0; i < req.options().size(); i++) {
                QuizDtos.OptionRequest optReq = req.options().get(i);
                QuestionOption opt = new QuestionOption();
                opt.setQuestion(question);
                opt.setOptionText(optReq.optionText());
                opt.setCorrect(optReq.isCorrect());
                opt.setPosition(optReq.position() != null ? optReq.position() : i);
                newOptions.add(opt);
            }
            options = questionOptionRepository.saveAll(newOptions);
        } else {
            options = questionOptionRepository.findByQuestionIdOrderByPositionAsc(questionId);
        }

        question = questionRepository.save(question);
        qq = quizQuestionRepository.save(qq);

        return toQuestionResponse(qq, options);
    }

    @Transactional
    public QuizDtos.DeleteResponse deleteQuestion(UUID actorId, UUID courseId, UUID quizId, UUID questionId) {
        requireEditableCourse(courseId, actorId);

        Quiz quiz = quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_EDITABLE, "Quiz is locked and cannot be edited");
        }

        QuizQuestion qq = quizQuestionRepository.findByQuizIdAndQuestionId(quizId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUESTION_NOT_FOUND, "Question not found in quiz"));

        Question question = qq.getQuestion();
        quizQuestionRepository.deleteByQuizIdAndQuestionId(quizId, questionId);

        boolean usedElsewhere = quizQuestionRepository.existsByQuestionId(questionId);
        if (!usedElsewhere && question.getQuestionBank() == null) {
            questionOptionRepository.deleteByQuestionId(questionId);
            questionRepository.delete(question);
        }

        return new QuizDtos.DeleteResponse(true, questionId);
    }

    @Transactional
    public QuizDtos.ReorderResponse reorderQuestions(UUID actorId, UUID courseId, UUID quizId, QuizDtos.ReorderRequest req) {
        requireEditableCourse(courseId, actorId);

        Quiz quiz = quizRepository.findByIdAndCourseIdForUpdate(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_EDITABLE, "Quiz is locked and cannot be edited");
        }

        List<QuizQuestion> current = quizQuestionRepository.findByQuizIdOrderByPositionAsc(quizId);
        List<UUID> existingIds = current.stream().map(qq -> qq.getQuestion().getId()).toList();

        List<UUID> orderedIds;
        if (req.orderedIds() != null && !req.orderedIds().isEmpty()) {
            orderedIds = req.orderedIds();
        } else if (req.items() != null && !req.items().isEmpty()) {
            orderedIds = req.items().stream()
                    .sorted(Comparator.comparing(QuizDtos.ReorderItem::newPosition))
                    .map(QuizDtos.ReorderItem::questionId)
                    .toList();
        } else {
            throw new BusinessException(ErrorCode.QUESTION_ORDER_INVALID, "Ordered IDs or items must be provided");
        }

        validatePermutation(orderedIds, existingIds, ErrorCode.QUESTION_ORDER_INVALID);

        List<QuizQuestion> ordered = orderedIds.stream()
                .map(id -> current.stream().filter(qq -> qq.getQuestion().getId().equals(id)).findFirst().orElseThrow())
                .toList();

        // Phase 1: negative positions to avoid unique conflicts
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(-(i + 1));
        }
        quizQuestionRepository.saveAllAndFlush(ordered);

        // Phase 2: zero-based contiguous positions
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
        quizQuestionRepository.saveAllAndFlush(ordered);

        return new QuizDtos.ReorderResponse(true, orderedIds.size(), orderedIds);
    }

    // ==========================================
    // RESULTS & SCORE ADJUSTMENT
    // ==========================================

    @Transactional(readOnly = true)
    public QuizDtos.ResultPage listResults(UUID actorId, UUID courseId, UUID quizId, int page, int pageSize) {
        requireCourseManager(courseId, actorId);
        validatePage(page, pageSize);
        quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        Page<QuizResult> results = quizResultRepository.findByQuizId(quizId, PageRequest.of(page - 1, pageSize));
        return new QuizDtos.ResultPage(results.getContent().stream().map(this::toResultResponse).toList(), pageInfo(results));
    }

    @Transactional
    public QuizDtos.QuizResultResponse adjustScore(UUID actorId, UUID courseId, UUID quizId, UUID resultId, QuizDtos.AdjustScoreRequest req) {
        requireCourseManager(courseId, actorId);
        User actor = requireUser(actorId);

        quizRepository.findByIdAndCourseId(quizId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "Quiz not found"));

        QuizResult result = quizResultRepository.findByIdAndQuizId(resultId, quizId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESULT_NOT_FOUND, "Quiz result not found"));

        BigDecimal totalPoints = quizQuestionRepository.sumPointsByQuizId(quizId);
        if (req.finalScore().compareTo(BigDecimal.ZERO) < 0 || req.finalScore().compareTo(totalPoints) > 0) {
            throw new BusinessException(ErrorCode.SCORE_OUT_OF_RANGE, "Final score must be between 0 and " + totalPoints);
        }

        result.setFinalScore(req.finalScore());
        result.setAdjustedBy(actor);
        result.setAdjustmentReason(req.adjustmentReason());
        result = quizResultRepository.save(result);

        return toResultResponse(result);
    }

    // ==========================================
    // ADMIN ENDPOINTS
    // ==========================================

    @Transactional(readOnly = true)
    public QuizDtos.AdminQuizPage adminListAllQuizzes(UUID actorId, UUID courseIdFilter, int page, int pageSize) {
        requireAdmin(actorId);
        validatePage(page, pageSize);

        Page<Quiz> quizzes = quizRepository.findAllAdmin(courseIdFilter, PageRequest.of(page - 1, pageSize));
        List<QuizDtos.AdminQuizSummary> summaries = quizzes.getContent().stream().map(q -> {
            long totalAttempts = quizRepository.countAttemptsByQuizId(q.getId());
            return new QuizDtos.AdminQuizSummary(
                    q.getId(),
                    q.getCourse().getId(),
                    q.getCourse().getTitle(),
                    q.getTitle(),
                    q.isLocked(),
                    totalAttempts,
                    q.getCreatedAt()
            );
        }).toList();

        return new QuizDtos.AdminQuizPage(summaries, pageInfo(quizzes));
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private Course requireCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
    }

    private Course requireEditableCourse(UUID courseId, UUID actorId) {
        Course course = requireCourse(courseId);
        requireCourseManager(courseId, actorId);
        if (course.getLifecycleStatus() == CourseLifecycleStatus.ARCHIVED || course.getLifecycleStatus() == CourseLifecycleStatus.DISABLED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Archived or disabled courses cannot be edited");
        }
        return course;
    }

    private void requireCourseManager(UUID courseId, UUID actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required");
        }
        User actor = requireUser(actorId);
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        CourseInstructor link = courseInstructorRepository.findByIdCourseIdAndIdInstructorId(courseId, actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required"));
        if (link.getCourseRole() != CourseInstructorRole.OWNER && link.getCourseRole() != CourseInstructorRole.CO_INSTRUCTOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required");
        }
    }

    private void requireAdmin(UUID actorId) {
        if (actorId == null) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "Admin access required");
        }
        User actor = requireUser(actorId);
        if (actor.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "Admin access required");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page must be >= 1 and page_size must be between 1 and 100");
        }
    }

    private void validatePermutation(List<UUID> requested, List<UUID> existing, ErrorCode errorCode) {
        if (requested == null || requested.size() != existing.size()
                || new HashSet<>(requested).size() != requested.size()
                || !new HashSet<>(requested).equals(new HashSet<>(existing))) {
            throw new BusinessException(errorCode, "ordered_ids must contain every direct child exactly once");
        }
    }

    private void validateQuestionOptions(QuestionType type, List<QuizDtos.OptionRequest> options) {
        if (type == QuestionType.TRUE_FALSE) {
            if (options == null || options.size() != 2) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "True/False questions must have exactly 2 options");
            }
            long correctCount = options.stream().filter(QuizDtos.OptionRequest::isCorrect).count();
            if (correctCount != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "True/False questions must have exactly 1 correct option");
            }
        } else if (type == QuestionType.SINGLE_CHOICE) {
            if (options == null || options.size() < 2) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Single choice questions must have at least 2 options");
            }
            long correctCount = options.stream().filter(QuizDtos.OptionRequest::isCorrect).count();
            if (correctCount != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Single choice questions must have exactly 1 correct option");
            }
        } else if (type == QuestionType.MULTIPLE_CHOICE) {
            if (options == null || options.size() < 2) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Multiple choice questions must have at least 2 options");
            }
            long correctCount = options.stream().filter(QuizDtos.OptionRequest::isCorrect).count();
            if (correctCount < 1) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Multiple choice questions must have at least 1 correct option");
            }
        }
    }

    private QuizDtos.PageInfo pageInfo(Page<?> p) {
        return new QuizDtos.PageInfo(p.getNumber() + 1, p.getSize(), p.getTotalElements(), p.hasNext());
    }

    private QuizDtos.QuizResponse toQuizResponse(Quiz q) {
        return new QuizDtos.QuizResponse(
                q.getId(),
                q.getCourse().getId(),
                q.getLesson() != null ? q.getLesson().getId() : null,
                q.getTitle(),
                q.getDescription(),
                q.getQuestionSource(),
                q.getTimeLimitSeconds(),
                q.getDeadline(),
                q.getMaxAttempts(),
                q.getGradingPolicy(),
                q.getRandomizeQuestions(),
                q.getRandomizeAnswers(),
                q.getShowScore(),
                q.getShowCorrectAnswers(),
                q.isLocked(),
                q.getCreatedBy() != null ? q.getCreatedBy().getId() : null,
                q.getCreatedAt(),
                q.getUpdatedAt(),
                q.getAssessmentKind()
        );
    }

    private QuizDtos.BankResponse toBankResponse(QuestionBank b) {
        return new QuizDtos.BankResponse(
                b.getId(),
                b.getCourse().getId(),
                b.getName(),
                b.getCreatedBy() != null ? b.getCreatedBy().getId() : null,
                b.getCreatedAt()
        );
    }

    private QuizDtos.QuestionResponse toQuestionResponse(QuizQuestion qq, List<QuestionOption> options) {
        Question q = qq.getQuestion();
        List<QuizDtos.OptionResponse> optDtos = options.stream()
                .map(o -> new QuizDtos.OptionResponse(o.getId(), o.getOptionText(), o.isCorrect(), o.getPosition()))
                .toList();
        return new QuizDtos.QuestionResponse(
                q.getId(),
                q.getQuestionBank() != null ? q.getQuestionBank().getId() : null,
                q.getLearningOutcome() != null ? q.getLearningOutcome().getId() : null,
                q.getBloomLevel(),
                q.getQuestionType(),
                q.getQuestionText(),
                q.getKeywordAnswer(),
                qq.getPoints(),
                qq.getPosition(),
                optDtos,
                q.getCreatedBy() != null ? q.getCreatedBy().getId() : null,
                q.getCreatedAt(),
                q.getUpdatedAt()
        );
    }

    private QuizDtos.QuizResultResponse toResultResponse(QuizResult r) {
        return new QuizDtos.QuizResultResponse(
                r.getId(),
                r.getAttempt().getId(),
                r.getAutoScore(),
                r.getFinalScore(),
                r.getAdjustedBy() != null ? r.getAdjustedBy().getId() : null,
                r.getAdjustmentReason(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
