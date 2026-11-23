package com.oep.corelms.service;

import com.oep.corelms.dto.admin.OptionInput;
import com.oep.corelms.dto.admin.QuestionTransferItem;
import com.oep.coremodel.enums.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionBankTransferServiceTest {

    private final QuestionBankTransferService service = new QuestionBankTransferService(null, null, null, null, null);

    @Test
    void csvRoundTripKeepsQuotesCommasAndCorrectOptions() {
        var item = new QuestionTransferItem(QuestionType.MULTIPLE_CHOICE, "Chọn \"đúng\", nhiều đáp án\nxuống dòng", null,
                List.of(new OptionInput("A, một", true, 1), new OptionInput("B", false, 2), new OptionInput("C", true, 3)));
        List<QuestionTransferItem> back = service.fromCsv(service.toCsv(List.of(item)));
        assertEquals(1, back.size());
        assertEquals(item.questionText(), back.get(0).questionText());
        assertEquals(QuestionType.MULTIPLE_CHOICE, back.get(0).questionType());
        assertEquals(List.of(true, false, true), back.get(0).options().stream().map(OptionInput::correct).toList());
        assertEquals("A, một", back.get(0).options().get(0).optionText());
    }

    @Test
    void exportNeutralisesSpreadsheetFormulas() {
        var item = new QuestionTransferItem(QuestionType.KEYWORD_FILL, "=HYPERLINK(\"http://x\")", "đáp án", List.of());
        String csv = service.toCsv(List.of(item));
        assertTrue(csv.contains("\"'=HYPERLINK"), csv);
    }

    @Test
    void unknownTypeRowBecomesAnInvalidItemInsteadOfFailingTheWholeFile() {
        List<QuestionTransferItem> items = service.fromCsv("type,question\nNOPE,Câu hỏi\n");
        assertEquals(1, items.size());
        assertEquals(null, items.get(0).questionType());
    }
}
