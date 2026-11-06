package pe.edu.pucp.spats.comun.error;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce las excepciones a respuestas {@code application/problem+json} uniformes: {@code title},
 * {@code status}, {@code detail} (en español, para mostrarlo tal cual) y {@code codigo} (estable).
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaNegocio(ReglaNegocioException ex) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio incumplida", ex);
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex);
    }

    @ExceptionHandler(ArchivoInvalidoException.class)
    ProblemDetail archivoInvalido(ArchivoInvalidoException ex) {
        ProblemDetail pd = problema(HttpStatus.UNPROCESSABLE_CONTENT, "El archivo presenta errores", ex);
        pd.setProperty("problemas", ex.problemas());
        return pd;
    }

    @ExceptionHandler(FalloPlanificacionException.class)
    ProblemDetail falloPlanificacion(FalloPlanificacionException ex) {
        LOG.error("Fallo de la planificación: {} {}", ex.getMessage(), ex.detalles(), ex.getCause());
        ProblemDetail pd = problema(HttpStatus.INTERNAL_SERVER_ERROR, "La planificación no se generó", ex);
        pd.setProperty("detalles", ex.detalles());
        return pd;
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    ProblemDetail credenciales(CredencialesInvalidasException ex) {
        return problema(HttpStatus.UNAUTHORIZED, "Acceso denegado", ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accesoDenegado(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Tu perfil no permite ejecutar esta operación.");
        pd.setTitle("Sin permiso");
        pd.setProperty("codigo", "SIN_PERMISO");
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridad(DataIntegrityViolationException ex) {
        LOG.warn("Restricción de la base de datos incumplida", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La operación no cumple una restricción de la base de datos; no se guardó ningún cambio.");
        pd.setTitle("Datos inconsistentes");
        pd.setProperty("codigo", "RESTRICCION_BD");
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail parametroInvalido(MethodArgumentTypeMismatchException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El valor «" + ex.getValue() + "» no es válido para " + ex.getName() + ".");
        pd.setTitle("Datos inválidos");
        pd.setProperty("codigo", "PARAMETRO_INVALIDO");
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        LOG.error("Error inesperado", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. No se aplicó ningún cambio.");
        pd.setTitle("Error interno");
        pd.setProperty("codigo", "ERROR_INTERNO");
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Hay datos inválidos o incompletos en el formulario.");
        pd.setTitle("Datos inválidos");
        pd.setProperty("codigo", "DATOS_INVALIDOS");
        pd.setProperty("errores", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("campo", e.getField(), "mensaje", String.valueOf(e.getDefaultMessage())))
                .toList());
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud no tiene un formato válido (revisa fechas y números).");
        pd.setTitle("Datos inválidos");
        pd.setProperty("codigo", "SOLICITUD_ILEGIBLE");
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido (10 MB).");
        pd.setTitle("Archivo demasiado grande");
        pd.setProperty("codigo", "ARCHIVO_GRANDE");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(pd);
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, SpatsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        pd.setTitle(titulo);
        pd.setProperty("codigo", ex.codigo());
        return pd;
    }
}
