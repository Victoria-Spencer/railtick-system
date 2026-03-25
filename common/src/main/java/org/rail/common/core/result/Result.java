package org.rail.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

//统一响应结果
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> implements Serializable {
    private String code; // 业务状态码  "0"-成功  "1"-失败
    private String message; // 提示信息
    private T data; // 响应数据
    private String requestId; // 请求id
    private boolean success; // 是否成功

    //快速返回操作成功响应结果
    public static <T> Result<T> success() {
        Result<T> result = new Result<T>();
        result.code = "0";
        result.message = "success";
        result.data = null;
        result.requestId = null;
        result.success = true;
        return result;
    }

    //快速返回操作成功响应结果(带响应数据)
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<T>();
        result.code = "0";
        result.message = "success";
        result.data = data;
        result.requestId = null;
        result.success = true;
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<T>();
        result.code = "1";
        result.message = message;
        result.data = null;
        result.requestId = null;
        result.success = false;
        return result;
    }

    // 支持传入requestID的错误响应
    public static <T> Result<T> error(String message, String requestId) {
        Result<T> result = new Result<>();
        result.setCode("1");
        result.setMessage(message);
        result.setData(null);
        result.setRequestId(requestId);
        result.setSuccess(false);
        return result;
    }
}
