package bio.terra.buffer.app.controller;

import bio.terra.buffer.generated.model.ErrorReport;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * This module provides a top-level exception handler for controllers. All exceptions that rise
 * through the controllers are caught in this handler. It converts the exceptions into standard
 * ErrorReport responses.
 *
 * <p>TODO: This class and other exception classes are exactly the same as Workspace Manager and
 * Data Repo's code. Use the common library once we have.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler<ErrorReport> {
  @Override
  ErrorReport generateErrorReport(Throwable ex, HttpStatus statusCode, List<String> causes) {
    return new ErrorReport().message(ex.getMessage()).statusCode(statusCode.value()).causes(causes);
  }
}
