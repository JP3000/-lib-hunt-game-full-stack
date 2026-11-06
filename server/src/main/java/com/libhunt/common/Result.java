package com.libhunt.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 统一响应体。
 * <p>
 * {@code code == 0} 表示成功，非 0 表示失败。
 * 之所以不直接把 HTTP 状态码当业务码，是为了让前端只需判断一个字段。
 */
@Getter
@Schema(description = "统一响应体")
public class Result<T> {

    @Schema(description = "0 = 成功，非 0 = 失败")
    private final int code;

    @Schema(description = "提示信息")
    private final String msg;

    @Schema(description = "业务数据")
    private final T data;

    private Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(1, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }
}
