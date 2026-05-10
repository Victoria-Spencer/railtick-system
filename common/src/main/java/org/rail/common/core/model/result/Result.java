package org.rail.common.core.model.result;

import cn.hutool.http.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

//统一响应结果
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_SUCCESS_CODE = HttpStatus.HTTP_OK;
    private static final int DEFAULT_ERROR_CODE = HttpStatus.HTTP_INTERNAL_ERROR;

    /**
     * 业务状态码：使用标准HTTP状态码
     * 200-成功  400-参数错误  401-未授权  404-不存在  500-服务器错误
     */
    private Integer code;
    /** 响应消息 */
    private String message;
    /** 响应数据 */
    private T data;
    /** 请求id (可选，用于追踪请求链路) */
    private String requestId;
    /** 操作是否成功 */
    private boolean success;

    public static <T> Result<T> success() {
        return new Result<>(DEFAULT_SUCCESS_CODE, "success", null, null, true);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(DEFAULT_SUCCESS_CODE, "success", data, null, true);
    }

    public static <T> Result<T> success(T data, String requestId) {
        return new Result<>(DEFAULT_SUCCESS_CODE, "success", data, requestId, true);
    }

    public static <T> Result<T> error() {
        return new Result<>(DEFAULT_ERROR_CODE, "服务器繁忙，请稍后再试", null, null, false);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(DEFAULT_ERROR_CODE, message, null, null, false);
    }

    public static <T> Result<T> error(String message, String requestId) {
        return new Result<>(DEFAULT_ERROR_CODE, message, null, requestId, false);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, null, false);
    }


    public static <T> Result<T> error(int code, String message, String requestId) {
        return new Result<>(code, message, null, requestId, false);
    }
}
