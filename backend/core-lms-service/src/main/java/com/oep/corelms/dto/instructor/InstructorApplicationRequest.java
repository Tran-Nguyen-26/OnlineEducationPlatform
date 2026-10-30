package com.oep.corelms.dto.instructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InstructorApplicationRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 255, message = "Họ và tên phải từ 2 đến 255 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^(0|\\+84)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ")
        @Size(max = 50, message = "Số điện thoại tối đa 50 ký tự")
        String phone,

        @Size(max = 255, message = "Cơ quan/Tổ chức tối đa 255 ký tự")
        String organization,

        @Size(max = 255, message = "Chức danh tối đa 255 ký tự")
        String jobTitle,

        @Size(max = 255, message = "Lĩnh vực chuyên môn tối đa 255 ký tự")
        String expertise,

        @Min(value = 0, message = "Số năm kinh nghiệm không được âm")
        @Max(value = 60, message = "Số năm kinh nghiệm không hợp lệ")
        Integer yearsOfExperience,

        @Size(max = 5000, message = "Tiểu sử năng lực tối đa 5000 ký tự")
        String professionalBio,

        @Size(max = 5000, message = "Mục tiêu giảng dạy tối đa 5000 ký tự")
        String motivation
) {}
