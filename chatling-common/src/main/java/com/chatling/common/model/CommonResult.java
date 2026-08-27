package com.chatling.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommonResult<T> {
    private int code;
    private String message;
    private T data;

    public static <T> CommonResult<T> success(T data) {
        return CommonResult.<T>builder().code(0).message("success").data(data).build();
    }

    public static <T> CommonResult<T> success() {
        return success(null);
    }

    public static <T> CommonResult<T> fail(int code, String message) {
        return CommonResult.<T>builder().code(code).message(message).data(null).build();
    }

    public static <T> CommonResult<T> fail(String message) {
        return fail(500, message);
    }
}
