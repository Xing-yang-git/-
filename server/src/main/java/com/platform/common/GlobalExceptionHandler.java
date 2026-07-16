package com.platform.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst()
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(Result.error(msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntime(RuntimeException e) {
        return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAll(Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务器内部错误: " + e.getClass().getName() + " - " + e.getMessage()));
    }
}
