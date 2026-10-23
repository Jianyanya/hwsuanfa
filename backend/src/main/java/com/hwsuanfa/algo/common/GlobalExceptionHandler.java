package com.hwsuanfa.algo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * ⚠️ 关键约定：必须同时设置 HTTP 状态码与响应体 code。
 * 前端 lib/http.ts 的 401 处理读的是 error.response.status（HTTP 层），
 * 只设 body 里的 code 不会触发跳登录。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 —— 按 ErrorCode 映射 HTTP 状态 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        ErrorCode ec = e.getErrorCode();
        HttpStatus status = toHttpStatus(ec);
        if (status.is5xxServerError()) {
            log.error("业务异常 {}: {}", ec.name(), e.getMessage(), e);
        } else {
            log.warn("业务异常 {}: {}", ec.name(), e.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResult.fail(ec.getCode(), e.getMessage()));
    }

    /** @Valid 校验失败 —— 取第一条字段错误，前端直接展示 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.fail(ErrorCode.BAD_REQUEST, msg.isEmpty() ? "请求参数有误" : msg));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResult<Void>> handleMalformedRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiResult.fail(ErrorCode.BAD_REQUEST, "请求格式或参数类型有误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleUnsupportedMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResult.fail(HttpStatus.METHOD_NOT_ALLOWED.value(), "该接口不支持此请求方法"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleUnsupportedMedia(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResult.fail(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "请使用接口支持的请求格式"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleMissingResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.fail(ErrorCode.NOT_FOUND));
    }

    /** Spring Security 认证失败（未登录 / 令牌无效） */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<Void>> handleAuth(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResult.fail(ErrorCode.UNAUTHORIZED));
    }

    /** Spring Security 授权失败（已登录但无权限） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.fail(ErrorCode.FORBIDDEN));
    }

    /** 兜底 —— 不把堆栈暴露给前端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(ErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus toHttpStatus(ErrorCode ec) {
        return switch (ec) {
            case BAD_REQUEST, ASSIGNMENT_NOT_STARTED, ASSIGNMENT_EXPIRED, TEAM_FULL,
                 MEMBER_LIMIT_EXCEEDED, LEADER_REQUIRED, LEADER_MUST_BE_OTHER_MEMBER,
                 USER_ALREADY_IN_OTHER_TEAM_LIMIT, INVITE_CODE_INVALID,
                 ASSIGNMENT_NEEDS_PROBLEM, INVALID_TIME_RANGE, DAILY_DATE_REQUIRED,
                 CONTEST_NOT_SELECTED -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, LOGIN_FAILED, TOKEN_INVALID, TOKEN_EXPIRED, REFRESH_TOKEN_INVALID ->
                    HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, LEADER_IMMUTABLE, PRIVACY_DENIED, ACCOUNT_DISABLED, TEAM_FROZEN,
                 NOT_TEAM_LEADER, REGISTER_DISABLED, NOT_YOUR_ASSIGNMENT,
                 NOT_CONTEST_PARTICIPANT -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, TEAM_NOT_FOUND, ASSIGNMENT_NOT_FOUND, SCORE_RULE_NOT_FOUND,
                 USER_NOT_FOUND, PROBLEM_NOT_FOUND, CONTEST_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT, ALREADY_TEAM_MEMBER, ASSIGNMENT_ALREADY_DONE, SCORE_ALREADY_GRANTED,
                 LEADER_MUST_BE_MEMBER, CANNOT_REMOVE_LAST_LEADER, NOT_TEAM_MEMBER,
                 TEAM_NAME_EXISTS, REMOVE_LEADER_NEEDS_TRANSFER, TEAM_HAS_SCORE_HISTORY,
                 STUDENT_NO_EXISTS, NICKNAME_EXISTS, DAILY_ALREADY_PUBLISHED,
                 PROBLEM_DUPLICATE, CONTEST_ALREADY_SELECTED, CONTEST_LOG_CONFIRMED ->
                    HttpStatus.CONFLICT;
            // 限流用 429，语义比 403 更准确，也方便前端区分提示
            case REGISTER_ATTEMPT_LIMIT, LOGIN_ATTEMPT_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            case GONE -> HttpStatus.GONE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
