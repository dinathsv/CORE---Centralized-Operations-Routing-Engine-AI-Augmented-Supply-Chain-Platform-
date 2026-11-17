package com.core.inventory.api;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A record with these unique values already exists.");
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail concurrentUpdate(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This record changed while you were editing it. Refresh and try again.");
    }
}
