package com.oep.corelms.dto.admin;

import java.util.List;

/** Rows that fail validation are skipped and reported; the valid ones are imported. */
public record QuestionImportResult(int imported, int skipped, List<String> errors) {
}
