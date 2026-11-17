package pe.edu.upeu.clinica.atenciones.odontologo.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upeu.clinica.atenciones.atencion.repository.AtencionRepository;
import pe.edu.upeu.clinica.atenciones.odontologo.dto.OdontologoRequest;
import pe.edu.upeu.clinica.atenciones.odontologo.dto.OdontologoResponse;
import pe.edu.upeu.clinica.atenciones.odontologo.entity.Odontologo;
import pe.edu.upeu.clinica.atenciones.odontologo.mapper.OdontologoMapper;
import pe.edu.upeu.clinica.atenciones.odontologo.repository.OdontologoRepository;
import pe.edu.upeu.clinica.atenciones.odontologo.service.OdontologoService;
import pe.edu.upeu.clinica.auditoria.AuditoriaService;
import pe.edu.upeu.clinica.shared.exception.ReglaNegocioException;
import pe.edu.upeu.clinica.shared.exception.ResourceNotFoundException;
import pe.edu.upeu.clinica.tratamientos.catalogo.repository.EspecialidadRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OdontologoServiceImpl implements OdontologoService {

    private final OdontologoRepository odontologoRepository;
    private final OdontologoMapper odontologoMapper;
    private final AtencionRepository atencionRepository;
    private final EspecialidadRepository especialidadRepository;
    private final AuditoriaService auditoriaService;

    @Override
    public OdontologoResponse crear(OdontologoRequest request) {
        if (odontologoRepository.existsByCop(request.getCop())) {
            throw new ReglaNegocioException("Ya existe un odontólogo con COP " + request.getCop());
        }
        validarEspecialidad(request.getEspecialidad());
        Odontologo guardado = odontologoRepository.save(odontologoMapper.toEntity(request));
        auditoriaService.registrar("ODONTOLOGO", guardado.getId(), "CREAR", "Alta de odontólogo");
        return odontologoMapper.toResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public OdontologoResponse obtenerPorId(Long id) {
        return odontologoMapper.toResponse(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OdontologoResponse> listar() {
        return odontologoRepository.findAll(Sort.by("apellidos", "nombres")).stream()
                .map(odontologoMapper::toResponse)
                .toList();
    }

    @Override
    public OdontologoResponse actualizar(Long id, OdontologoRequest request) {
        Odontologo odontologo = buscar(id);
        if (odontologoRepository.existsByCopAndIdNot(request.getCop(), id)) {
            throw new ReglaNegocioException("Ya existe un odontólogo con COP " + request.getCop());
        }
        if (!request.getEspecialidad().equals(odontologo.getEspecialidad())) {
            validarEspecialidad(request.getEspecialidad());
        }
        odontologoMapper.updateEntity(odontologo, request);
        Odontologo actualizado = odontologoRepository.save(odontologo);
        auditoriaService.registrar("ODONTOLOGO", id, "ACTUALIZAR", "Datos de odontólogo actualizados");
        return odontologoMapper.toResponse(actualizado);
    }

    @Override
    public void eliminar(Long id) {
        Odontologo odontologo = buscar(id);
        if (atencionRepository.existsByIdOdontologo(id)) {
            throw new ReglaNegocioException(
                    "No se puede eliminar al odontólogo porque tiene atenciones clínicas registradas");
        }
        auditoriaService.registrar("ODONTOLOGO", id, "ELIMINAR", "Odontólogo eliminado");
        odontologoRepository.delete(odontologo);
    }

    private void validarEspecialidad(String especialidad) {
        if (!especialidadRepository.existsByNombre(especialidad)) {
            throw new ReglaNegocioException(
                    "La especialidad '" + especialidad + "' no existe en el catálogo de especialidades");
        }
    }

    private Odontologo buscar(Long id) {
        return odontologoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo", id));
    }
}
