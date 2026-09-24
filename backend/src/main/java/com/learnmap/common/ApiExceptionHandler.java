package com.learnmap.common;

import com.learnmap.ai.AiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ProblemDetail badRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ProblemDetail unsupported(UnsupportedOperationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, e.getMessage());
    }

    @ExceptionHandler(AiException.class)
    ProblemDetail ai(AiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail unauthenticated(MissingRequestHeaderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "缺少当前用户（请求头 " + CurrentUser.HEADER + "）");
    }
}
