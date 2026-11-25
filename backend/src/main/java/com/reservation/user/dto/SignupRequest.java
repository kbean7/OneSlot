package com.reservation.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "아이디를 입력하세요.")
        @Pattern(regexp = "^[a-zA-Z0-9]{4,20}$", message = "아이디는 영문·숫자 4~20자입니다.")
        String loginId,

        @NotBlank(message = "비밀번호를 입력하세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$",
                message = "비밀번호는 영문·숫자·특수문자를 포함해 8자 이상입니다.")
        String password,

        @NotBlank(message = "이름을 입력하세요.")
        @Size(max = 50, message = "이름은 50자 이하입니다.")
        String name,

        @NotBlank(message = "이메일을 입력하세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하입니다.")
        String email,

        // 선택 입력
        @Pattern(regexp = "^$|^01\\d-?\\d{3,4}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
        String phone
) {
}
