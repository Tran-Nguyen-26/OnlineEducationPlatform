package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record OptionInput(@NotBlank String optionText, boolean correct, int position) {
}
