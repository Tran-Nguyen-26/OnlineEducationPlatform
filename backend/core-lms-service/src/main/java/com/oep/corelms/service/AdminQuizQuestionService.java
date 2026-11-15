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

/** Each question is created directly under one quiz (no cross-quiz question bank reuse in this version). */
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
        quizQuestions.delete(link);
        options.deleteByQuestion_Id(questionId);
        questions.deleteById(questionId);
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
