package io.github.medtahabri.expenseflow.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as a problem document (RFC 9457). Extends
 * Spring's handler, so framework errors (unreadable body, wrong method, a
 * ResponseStatusException...) get the same format, and adds field errors to
 * validation failures.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ProblemDetail business(ApiException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        if (!error.getFieldErrors().isEmpty()) {
            problem.setProperty("errors", error.getFieldErrors());
        }
        return problem;
    }

    /** Two people changed the same claim at the same time; the second one lost. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentChange(ObjectOptimisticLockingFailureException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The claim was changed by someone else at the same time; reload it and try again");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors()
                .forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return badRequest("The request is not valid", errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        error.getParameterValidationResults().forEach(result -> errors.putIfAbsent(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().getFirst().getDefaultMessage()));
        return badRequest("The request parameters are not valid", errors);
    }

    private static ResponseEntity<Object> badRequest(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
