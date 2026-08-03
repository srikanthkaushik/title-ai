package com.marion.dmv;

import com.marion.dmv.agent.ThreadNotPausedException;
import com.marion.dmv.agent.UnknownThreadException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(String error, String detail) {}

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleParseFailure(IllegalArgumentException ex) {
        return new ErrorResponse("PARSE_FAILED", ex.getMessage());
    }

    @ExceptionHandler(UnknownThreadException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUnknownThread(UnknownThreadException ex) {
        return new ErrorResponse("THREAD_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ThreadNotPausedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleThreadNotPaused(ThreadNotPausedException ex) {
        return new ErrorResponse("THREAD_NOT_PAUSED", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAgentError(IllegalStateException ex) {
        return new ErrorResponse("AGENT_ERROR", ex.getMessage());
    }
}
