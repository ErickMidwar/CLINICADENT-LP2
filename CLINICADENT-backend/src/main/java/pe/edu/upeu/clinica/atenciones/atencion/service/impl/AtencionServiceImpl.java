package pe.edu.upeu.clinica.atenciones.atencion.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.clinica.atenciones.atencion.dto.AtencionRequest;
import pe.edu.upeu.clinica.atenciones.atencion.dto.AtencionResponse;
import pe.edu.upeu.clinica.atenciones.atencion.entity.Atencion;
import pe.edu.upeu.clinica.atenciones.atencion.entity.DetalleAtencion;
import pe.edu.upeu.clinica.atenciones.atencion.mapper.AtencionMapper;
import pe.edu.upeu.clinica.atenciones.atencion.repository.AtencionRepository;
import pe.edu.upeu.clinica.atenciones.atencion.service.AtencionService;
import pe.edu.upeu.clinica.atenciones.odontologo.entity.Odontologo;
import pe.edu.upeu.clinica.atenciones.odontologo.repository.OdontologoRepository;
import pe.edu.upeu.clinica.auditoria.AuditoriaService;
import pe.edu.upeu.clinica.citas.CitaEstadoPort;
import pe.edu.upeu.clinica.shared.dto.PageResponse;
import pe.edu.upeu.clinica.shared.exception.ReglaNegocioException;
import pe.edu.upeu.clinica.shared.exception.ResourceNotFoundException;
import pe.edu.upeu.clinica.tratamientos.catalogo.entity.Procedimiento;
import pe.edu.upeu.clinica.tratamientos.catalogo.repository.ProcedimientoRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AtencionServiceImpl implements AtencionService {

    private static final Sort MAS_RECIENTES_PRIMERO =
            Sort.by(Sort.Direction.DESC, "faAtencion").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AtencionRepository atencionRepository;
    private final OdontologoRepository odontologoRepository;
    private final AtencionMapper atencionMapper;
    private final CitaEstadoPort citaEstadoPort;
    private final ProcedimientoRepository procedimientoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional
    public AtencionResponse registrarAtencion(AtencionRequest request) {
        Odontologo odontologo = odontologoRepository.findById(request.getIdOdontologo())
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo", request.getIdOdontologo()));
        if (atencionRepository.existsByIdCita(request.getIdCita())) {
            throw new ReglaNegocioException(
                    "La cita " + request.getIdCita() + " ya tiene una atención registrada");
        }

        Atencion atencion = atencionMapper.toEntity(request);
        Set<String> procedimientosPorPieza = new HashSet<>();
        request.getDetalles().forEach(detalleRequest -> {
            String pieza = detalleRequest.getPiezaDental().trim();
            String clave = pieza + ":" + detalleRequest.getIdProcedimiento();
            if (!procedimientosPorPieza.add(clave)) {
                throw new ReglaNegocioException("No repitas el mismo procedimiento en una pieza dental");
            }
            Procedimiento procedimiento = procedimientoRepository.findById(detalleRequest.getIdProcedimiento())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Procedimiento", detalleRequest.getIdProcedimiento()));
            DetalleAtencion detalle = atencionMapper.toDetalleEntity(detalleRequest);
            detalle.setPiezaDental(pieza);
            detalle.setProcedimiento(procedimiento);
            detalle.setIntervencion(procedimiento.getNombre());
            atencion.agregarDetalle(detalle);
        });

        atencion.setEstado(request.getEstado() == null ? "PENDIENTE" : request.getEstado());
        Atencion guardada = atencionRepository.saveAndFlush(atencion);
        citaEstadoPort.actualizarEstado(request.getIdCita(), CitaEstadoPort.ESTADO_ATENDIDA);
        auditoriaService.registrar("ATENCION", guardada.getId(), "REGISTRAR", "Atención clínica registrada");
        return conNombreOdontologo(atencionMapper.toResponse(guardada), Map.of(odontologo.getId(), odontologo));
    }

    @Override
    @Transactional(readOnly = true)
    public AtencionResponse obtenerPorId(Long id) {
        return enriquecer(List.of(buscar(id))).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtencionResponse> listar() {
        return enriquecer(atencionRepository.findAll(MAS_RECIENTES_PRIMERO));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AtencionResponse> listarPaginado(int page, int size) {
        var pagina = atencionRepository.findAll(PageRequest.of(page, size, MAS_RECIENTES_PRIMERO));
        Map<Long, Odontologo> odontologos = odontologosDe(pagina.getContent());
        return PageResponse.from(pagina.map(atencion ->
                conNombreOdontologo(atencionMapper.toResponse(atencion), odontologos)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AtencionResponse> listarPorCita(Long idCita) {
        return enriquecer(atencionRepository.findByIdCita(idCita));
    }

    private List<AtencionResponse> enriquecer(List<Atencion> atenciones) {
        Map<Long, Odontologo> odontologos = odontologosDe(atenciones);
        return atenciones.stream()
                .map(atencion -> conNombreOdontologo(atencionMapper.toResponse(atencion), odontologos))
                .toList();
    }

    private Map<Long, Odontologo> odontologosDe(Collection<Atencion> atenciones) {
        Set<Long> ids = atenciones.stream().map(Atencion::getIdOdontologo).collect(Collectors.toSet());
        return odontologoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Odontologo::getId, Function.identity()));
    }

    private AtencionResponse conNombreOdontologo(AtencionResponse response, Map<Long, Odontologo> odontologos) {
        Odontologo odontologo = odontologos.get(response.getIdOdontologo());
        if (odontologo != null) {
            response.setNombreOdontologo(odontologo.getApellidos() + ", " + odontologo.getNombres());
        }
        return response;
    }

    private Atencion buscar(Long id) {
        return atencionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Atención", id));
    }
}
