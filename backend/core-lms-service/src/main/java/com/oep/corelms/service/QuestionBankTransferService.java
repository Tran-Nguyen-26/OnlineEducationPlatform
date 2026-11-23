package com.oep.corelms.service;

import com.oep.corelms.dto.admin.OptionInput;
import com.oep.corelms.dto.admin.QuestionImportResult;
import com.oep.corelms.dto.admin.QuestionTransferItem;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.QuestionBankRepository;
import com.oep.corelms.repository.QuestionOptionRepository;
import com.oep.corelms.repository.QuestionRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.quiz.Question;
import com.oep.coremodel.entity.quiz.QuestionBank;
import com.oep.coremodel.entity.quiz.QuestionOption;
import com.oep.coremodel.enums.QuestionType;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Question bank import/export in JSON and CSV (BRD interoperability). CSV columns:
 * type,question,keyword_answer,correct,option_1..option_6 — "correct" lists 1-based option numbers separated by '|' (e.g. 1|3).
 */
@Service
@RequiredArgsConstructor
public class QuestionBankTransferService {

    static final int MAX_OPTIONS = 6;
    static final int MAX_IMPORT_ROWS = 1000;
    private static final java.util.Set<QuestionType> CHOICE =
            EnumSet.of(QuestionType.TRUE_FALSE, QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE);

    private final CourseRepository courses;
    private final QuestionBankRepository banks;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<QuestionTransferItem> export(UUID courseId) {
        return banks.findFirstByCourse_IdOrderByCreatedAtAsc(courseId)
                .map(b -> questions.findByQuestionBank_IdOrderByCreatedAtDesc(b.getId()).stream()
                        .map(q -> new QuestionTransferItem(q.getQuestionType(), q.getQuestionText(), q.getKeywordAnswer(),
                                options.findByQuestion_IdOrderByPositionAsc(q.getId()).stream()
                                        .map(o -> new OptionInput(o.getOptionText(), o.isCorrect(), o.getPosition())).toList()))
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public QuestionImportResult importItems(UUID courseId, UUID adminId, List<QuestionTransferItem> items) {
        Course course = courses.findById(courseId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không có câu hỏi nào để nhập");
        }
        if (items.size() > MAX_IMPORT_ROWS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tối đa " + MAX_IMPORT_ROWS + " câu hỏi mỗi lần nhập");
        }
        QuestionBank bank = banks.findFirstByCourse_IdOrderByCreatedAtAsc(courseId).orElseGet(() -> {
            QuestionBank b = new QuestionBank();
            b.setCourse(course);
            b.setName("Ngân hàng câu hỏi");
            return banks.save(b);
        });
        int imported = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            QuestionTransferItem item = items.get(i);
            String problem = problemWith(item);
            if (problem != null) {
                errors.add("Câu " + (i + 1) + ": " + problem);
                continue;
            }
            Question q = new Question();
            q.setQuestionType(item.questionType());
            q.setQuestionText(item.questionText().trim());
            q.setKeywordAnswer(item.questionType() == QuestionType.KEYWORD_FILL ? item.keywordAnswer().trim() : null);
            q.setCreatedBy(users.getReferenceById(adminId));
            q.setQuestionBank(bank);
            Question saved = questions.save(q);
            if (item.options() != null && CHOICE.contains(item.questionType())) {
                int pos = 1;
                for (OptionInput in : item.options()) {
                    QuestionOption o = new QuestionOption();
                    o.setQuestion(saved);
                    o.setOptionText(in.optionText().trim());
                    o.setCorrect(in.correct());
                    o.setPosition(pos++);
                    options.save(o);
                }
            }
            imported++;
        }
        return new QuestionImportResult(imported, items.size() - imported, errors);
    }

    private String problemWith(QuestionTransferItem item) {
        if (item == null || item.questionType() == null || item.questionText() == null || item.questionText().isBlank()) {
            return "thiếu loại câu hỏi hoặc nội dung";
        }
        if (CHOICE.contains(item.questionType())) {
            if (item.options() == null || item.options().size() < 2) return "cần ít nhất 2 phương án";
            if (item.options().size() > MAX_OPTIONS) return "tối đa " + MAX_OPTIONS + " phương án";
            if (item.options().stream().anyMatch(o -> o.optionText() == null || o.optionText().isBlank())) return "có phương án rỗng";
            long correct = item.options().stream().filter(OptionInput::correct).count();
            if (correct == 0) return "chưa đánh dấu đáp án đúng";
            if (item.questionType() != QuestionType.MULTIPLE_CHOICE && correct > 1) return "loại này chỉ có 1 đáp án đúng";
        } else if (item.questionType() == QuestionType.KEYWORD_FILL
                && (item.keywordAnswer() == null || item.keywordAnswer().isBlank())) {
            return "thiếu đáp án (keyword_answer)";
        }
        return null;
    }

    // ---- CSV ----

    public String toCsv(List<QuestionTransferItem> items) {
        StringBuilder sb = new StringBuilder("﻿type,question,keyword_answer,correct");
        for (int i = 1; i <= MAX_OPTIONS; i++) sb.append(",option_").append(i);
        sb.append('\n');
        for (QuestionTransferItem it : items) {
            List<OptionInput> opts = it.options() == null ? List.of() : it.options();
            List<String> correct = new ArrayList<>();
            for (int i = 0; i < opts.size(); i++) if (opts.get(i).correct()) correct.add(String.valueOf(i + 1));
            sb.append(it.questionType()).append(',').append(csv(it.questionText())).append(',').append(csv(it.keywordAnswer()))
                    .append(',').append(String.join("|", correct));
            for (int i = 0; i < MAX_OPTIONS; i++) sb.append(',').append(i < opts.size() ? csv(opts.get(i).optionText()) : "");
            sb.append('\n');
        }
        return sb.toString();
    }

    public List<QuestionTransferItem> fromCsv(String text) {
        List<List<String>> rows = parseCsv(text.startsWith("﻿") ? text.substring(1) : text);
        List<QuestionTransferItem> items = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {      // row 0 = header
            List<String> c = rows.get(r);
            if (c.stream().allMatch(String::isBlank)) continue;
            QuestionType type;
            try {
                type = QuestionType.valueOf(cell(c, 0).trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                items.add(new QuestionTransferItem(null, cell(c, 1), null, null));
                continue;
            }
            java.util.Set<Integer> correct = new java.util.HashSet<>();
            for (String n : cell(c, 3).split("\\|")) {
                try { if (!n.isBlank()) correct.add(Integer.parseInt(n.trim())); } catch (NumberFormatException ignored) { }
            }
            List<OptionInput> opts = new ArrayList<>();
            for (int i = 0; i < MAX_OPTIONS; i++) {
                String t = cell(c, 4 + i);
                if (!t.isBlank()) opts.add(new OptionInput(t, correct.contains(i + 1), opts.size() + 1));
            }
            items.add(new QuestionTransferItem(type, cell(c, 1), cell(c, 2).isBlank() ? null : cell(c, 2), opts));
        }
        return items;
    }

    private static String cell(List<String> row, int i) {
        return i < row.size() && row.get(i) != null ? row.get(i) : "";
    }

    /** Quotes the value and neutralises spreadsheet formula injection (cells starting with = + - @). */
    private static String csv(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Minimal RFC 4180 parser: quoted fields, doubled quotes, newlines inside quotes. */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; } else quoted = false;
                } else cur.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == ',') { row.add(cur.toString()); cur.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString()); cur.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else cur.append(ch);
        }
        if (cur.length() > 0 || !row.isEmpty()) { row.add(cur.toString()); rows.add(row); }
        return rows;
    }
}
