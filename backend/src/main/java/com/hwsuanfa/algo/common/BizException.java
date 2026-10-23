package com.hwsuanfa.algo.common;

import lombok.Getter;

/**
 * 业务异常。抛出后由 GlobalExceptionHandler 统一转成 ApiResult。
 * 不要用这个类表达「参数校验失败」——那走 MethodArgumentNotValidException。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 需要覆盖默认文案时用这个（例如带上具体数值） */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    /** 覆盖默认文案时用（例如带上具体上限数值） */
    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }
}
