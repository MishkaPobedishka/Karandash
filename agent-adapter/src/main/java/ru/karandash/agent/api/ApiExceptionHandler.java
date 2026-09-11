package ru.karandash.agent.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ru.karandash.agent.cli.CliCallException;
import ru.karandash.agent.recognition.AgentBusyException;
import ru.karandash.agent.recognition.InvalidInputException;
import ru.karandash.contracts.ai.RecognitionContractException;

/**
 * Ошибки внутреннего API. Тела ответов содержат только собственные сообщения сервиса.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    record ErrorBody(String error, String message) {
    }

    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ErrorBody> invalidInput(InvalidInputException exception) {
        return error(HttpStatus.BAD_REQUEST, "invalid_input", exception.getMessage());
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            MultipartException.class
    })
    ResponseEntity<ErrorBody> malformedRequest(Exception exception) {
        if (exception instanceof MaxUploadSizeExceededException) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large", "Фотография слишком большая");
        }
        return error(HttpStatus.BAD_REQUEST, "invalid_request", "Некорректный запрос");
    }

    @ExceptionHandler(AgentBusyException.class)
    ResponseEntity<ErrorBody> busy(AgentBusyException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(new ErrorBody("agent_busy", exception.getMessage()));
    }

    @ExceptionHandler(CliCallException.class)
    ResponseEntity<ErrorBody> cliFailure(CliCallException exception) {
        return switch (exception.reason()) {
            case TIMEOUT -> error(HttpStatus.GATEWAY_TIMEOUT, "cli_timeout", exception.getMessage());
            case AUTH_REJECTED -> error(HttpStatus.SERVICE_UNAVAILABLE, "cli_auth_rejected", exception.getMessage());
            case START_FAILED -> error(HttpStatus.SERVICE_UNAVAILABLE, "cli_unavailable", exception.getMessage());
            case UNSAFE_BEHAVIOR -> error(HttpStatus.BAD_GATEWAY, "unsafe_cli_behavior", exception.getMessage());
            case OUTPUT_LIMIT -> error(HttpStatus.BAD_GATEWAY, "cli_output_limit", exception.getMessage());
            case BAD_OUTPUT -> error(HttpStatus.BAD_GATEWAY, "bad_model_output", exception.getMessage());
            case FAILED -> error(HttpStatus.BAD_GATEWAY, "cli_failed", exception.getMessage());
        };
    }

    @ExceptionHandler(RecognitionContractException.class)
    ResponseEntity<ErrorBody> contractViolation(RecognitionContractException exception) {
        log.warn("Ответ модели не прошёл проверку контракта: {}", exception.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "bad_model_output", exception.getMessage());
    }

    private static ResponseEntity<ErrorBody> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorBody(code, message));
    }
}
