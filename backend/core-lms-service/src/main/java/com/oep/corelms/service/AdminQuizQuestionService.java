package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AddQuestionRequest;
import com.oep.corelms.dto.admin.OptionInput;
import com.oep.corelms.dto.admin.OptionResponse;
import com.oep.corelms.dto.admin.QuizQuestionResponse;
import com.oep.corelms.repository.QuestionOptionRepository;
import com.oep.corelms.repository.QuestionRepository;
import com.oep.corelms.repository.QuizQuestionRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.quiz.Question;
import com.oep.coremodel.entity.quiz.QuestionOption;
import com.oep.coremodel.entity.quiz.Quiz;
import com.oep.coremodel.entity.quiz.QuizQuestion;
import com.oep.coremodel.entity.quiz.QuizQuestionId;
import com.oep.coremodel.enums.QuestionType;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Questions live in a per-course bank (auto-created) and are linked to quizzes, so one question can be reused across quizzes. */
@Service
@RequiredArgsConstructor
public class AdminQuizQuestionService {

    private static final Set<QuestionType> CHOICE_TYPES =
            EnumSet.of(QuestionType.TRUE_FALSE, QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE);

    private final AdminQuizService quizService;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final QuizQuestionRepository quizQuestions;
    private final UserRepository users;
    private final com.oep.corelms.repository.QuestionBankRepository banks;
    private final com.oep.corelms.repository.QuizRepository quizRepository;

    @Transactional
    public QuizQuestionResponse addQuestion(UUID quizId, UUID adminId, AddQuestionRequest request) {
        Quiz quiz = quizService.findOrThrow(quizId);
        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Quiz is locked");
        }
        validate(request);

        Question newQuestion = new Question();
        newQuestion.setQuestionType(request.questionType());
        newQuestion.setQuestionText(request.questionText());
        newQuestion.setKeywordAnswer(request.keywordAnswer());
        newQuestion.setCreatedBy(users.getReferenceById(adminId));
        newQuestion.setQuestionBank(bankFor(quiz));
        Question question = questions.save(newQuestion);

        List<OptionResponse> optionResponses = List.of();
        if (request.options() != null && !request.options().isEmpty()) {
            optionResponses = request.options().stream().map(input -> saveOption(question, input)).map(OptionResponse::from).toList();
        }

        QuizQuestion link = new QuizQuestion();
        link.setId(new QuizQuestionId(quizId, question.getId()));
        link.setQuiz(quiz);
        link.setQuestion(question);
        link.setPosition(request.position());
        if (request.points() != null) {
            link.setPoints(request.points());
        }
        link = quizQuestions.save(link);

        return QuizQuestionResponse.from(link, optionResponses);
    }

    @Transactional(readOnly = true)
    public List<QuizQuestionResponse> list(UUID quizId) {
        return quizQuestions.findByQuiz_IdOrderByPositionAsc(quizId).stream()
                .map(link -> QuizQuestionResponse.from(link, optionsOf(link.getQuestion().getId())))
                .toList();
    }

    @Transactional
    public void removeQuestion(UUID quizId, UUID questionId) {
        QuizQuestion link = quizQuestions.findByQuiz_IdAndQuestion_Id(quizId, questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Question not found in this quiz"));
        // Unlink only: the question stays in the course bank (it may be reused, and past attempts reference it).
        quizQuestions.delete(link);
    }

    /** Also adopts pre-bank questions (created before banks existed) into the course bank, so older quizzes' questions are reusable. */
    @Transactional
    public List<com.oep.corelms.dto.admin.BankQuestionResponse> bank(UUID courseId) {
        List<Quiz> courseQuizzes = quizRepository.findByCourse_IdOrderByCreatedAtDesc(courseId);
        if (!courseQuizzes.isEmpty()) {
            com.oep.coremodel.entity.quiz.QuestionBank bank = bankFor(courseQuizzes.get(0));
            for (Quiz q : courseQuizzes) {
                for (QuizQuestion link : quizQuestions.findByQuiz_IdOrderByPositionAsc(q.getId())) {
                    if (link.getQuestion().getQuestionBank() == null) {
                        link.getQuestion().setQuestionBank(bank);
                        questions.save(link.getQuestion());
                    }
                }
            }
        }
        return banks.findFirstByCourse_IdOrderByCreatedAtAsc(courseId)
                .map(b -> questions.findByQuestionBank_IdOrderByCreatedAtDesc(b.getId()).stream()
                        .map(q -> new com.oep.corelms.dto.admin.BankQuestionResponse(q.getId(), q.getQuestionType(), q.getQuestionText(),
                                q.getKeywordAnswer(), optionsOf(q.getId()), quizQuestions.countByQuestion_Id(q.getId()), q.getCreatedAt()))
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public QuizQuestionResponse addFromBank(UUID quizId, com.oep.corelms.dto.admin.AddFromBankRequest request) {
        Quiz quiz = quizService.findOrThrow(quizId);
        if (quiz.isLocked()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Quiz is locked");
        }
        Question question = questions.findById(request.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Question not found"));
        if (question.getQuestionBank() == null || !question.getQuestionBank().getCourse().getId().equals(quiz.getCourse().getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Question belongs to a different course's bank");
        }
        if (quizQuestions.findByQuiz_IdAndQuestion_Id(quizId, question.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED, "Question is already in this quiz");
        }
        QuizQuestion link = new QuizQuestion();
        link.setId(new QuizQuestionId(quizId, question.getId()));
        link.setQuiz(quiz);
        link.setQuestion(question);
        link.setPosition(request.position());
        if (request.points() != null) {
            link.setPoints(request.points());
        }
        return QuizQuestionResponse.from(quizQuestions.save(link), optionsOf(question.getId()));
    }

    @Transactional
    public void deleteFromBank(UUID questionId) {
        Question question = questions.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Question not found"));
        if (quizQuestions.countByQuestion_Id(questionId) > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Question is used by a quiz; remove it from the quiz first");
        }
        options.deleteByQuestion_Id(questionId);
        questions.delete(question);
    }

    private com.oep.coremodel.entity.quiz.QuestionBank bankFor(Quiz quiz) {
        return banks.findFirstByCourse_IdOrderByCreatedAtAsc(quiz.getCourse().getId()).orElseGet(() -> {
            com.oep.coremodel.entity.quiz.QuestionBank b = new com.oep.coremodel.entity.quiz.QuestionBank();
            b.setCourse(quiz.getCourse());
            b.setName("Ngân hàng câu hỏi");
            return banks.save(b);
        });
    }

    private QuestionOption saveOption(Question question, OptionInput input) {
        QuestionOption option = new QuestionOption();
        option.setQuestion(question);
        option.setOptionText(input.optionText());
        option.setCorrect(input.correct());
        option.setPosition(input.position());
        return options.save(option);
    }

    private List<OptionResponse> optionsOf(UUID questionId) {
        return options.findByQuestion_IdOrderByPositionAsc(questionId).stream().map(OptionResponse::from).toList();
    }

    private void validate(AddQuestionRequest request) {
        if (CHOICE_TYPES.contains(request.questionType())) {
            if (request.options() == null || request.options().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "options are required for this question type");
            }
            if (request.options().stream().noneMatch(OptionInput::correct)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "at least one option must be marked correct");
            }
        } else if (request.questionType() == QuestionType.KEYWORD_FILL
                && (request.keywordAnswer() == null || request.keywordAnswer().isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "keywordAnswer is required for KEYWORD_FILL");
        }
    }
}
