package io.github.mirco104.budget.common;

import io.github.mirco104.budget.auth.InvalidCredentialsException;
import io.github.mirco104.budget.auth.LoginLockedException;
import java.util.List;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps errors to RFC 7807 ProblemDetail with German, user-facing texts.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String BAD_REQUEST_TITLE = "Ungültige Anfrage";

    public record FieldErrorResponse(String field, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Nicht gefunden", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Konflikt", ex.getMessage());
    }

    @ExceptionHandler(GoneException.class)
    public ProblemDetail handleGone(GoneException ex) {
        return problem(HttpStatus.GONE, "Nicht mehr gültig", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Anmeldung fehlgeschlagen", ex.getMessage());
    }

    @ExceptionHandler(LoginLockedException.class)
    public ProblemDetail handleLoginLocked(LoginLockedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Anmeldung gesperrt", ex.getMessage());
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ProblemDetail handleInvalidField(InvalidFieldException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE, "Die Eingaben sind ungültig.");
        problem.setProperty("errors", List.of(new FieldErrorResponse(ex.getField(), ex.getMessage())));
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE,
                "Der Parameter '" + ex.getParameterName() + "' fehlt.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    // e.g. a mapping that requires "scope=THIS" or "scope=FOLLOWING" and gets neither
    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE,
                "Ein Parameter fehlt oder ist ungültig.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE, "Die Eingaben sind ungültig.");
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE,
                "Die Anfrage konnte nicht gelesen werden.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE,
                "Ein Parameter hat ein ungültiges Format.");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
