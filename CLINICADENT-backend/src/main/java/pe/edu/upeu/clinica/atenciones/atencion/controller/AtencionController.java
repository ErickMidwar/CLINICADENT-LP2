package pe.edu.upeu.clinica.atenciones.atencion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.upeu.clinica.atenciones.atencion.dto.AtencionRequest;
import pe.edu.upeu.clinica.atenciones.atencion.dto.AtencionResponse;
import pe.edu.upeu.clinica.atenciones.atencion.service.AtencionService;
import pe.edu.upeu.clinica.shared.dto.PageResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/atenciones")
@RequiredArgsConstructor
@Tag(name = "Atenciones", description = "Módulo 3 - Ficha médica y odontograma transaccional")
public class AtencionController {

    private final AtencionService atencionService;

    @PostMapping
    @Operation(summary = "Registrar atención clínica y marcar la cita como ATENDIDA")
    public ResponseEntity<AtencionResponse> registrar(@Valid @RequestBody AtencionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(atencionService.registrarAtencion(request));
    }

    @GetMapping
    @Operation(summary = "Listar atenciones (más recientes primero)")
    public ResponseEntity<List<AtencionResponse>> listar(
            @RequestParam(required = false) Long idCita) {
        if (idCita != null) {
            return ResponseEntity.ok(atencionService.listarPorCita(idCita));
        }
        return ResponseEntity.ok(atencionService.listar());
    }

    @GetMapping("/paginado")
    @Operation(summary = "Listar atenciones por páginas (más recientes primero)")
    public ResponseEntity<PageResponse<AtencionResponse>> listarPaginado(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La página debe ser >= 0 y el tamaño debe estar entre 1 y 100");
        }
        return ResponseEntity.ok(atencionService.listarPaginado(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener atención por id")
    public ResponseEntity<AtencionResponse> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(atencionService.obtenerPorId(id));
    }
}
