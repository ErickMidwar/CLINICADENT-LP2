package pe.edu.upeu.clinica.atenciones.odontologo.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.upeu.clinica.atenciones.atencion.repository.AtencionRepository;
import pe.edu.upeu.clinica.atenciones.odontologo.dto.OdontologoRequest;
import pe.edu.upeu.clinica.atenciones.odontologo.entity.Odontologo;
import pe.edu.upeu.clinica.atenciones.odontologo.mapper.OdontologoMapper;
import pe.edu.upeu.clinica.atenciones.odontologo.repository.OdontologoRepository;
import pe.edu.upeu.clinica.auditoria.AuditoriaService;
import pe.edu.upeu.clinica.shared.exception.ReglaNegocioException;
import pe.edu.upeu.clinica.tratamientos.catalogo.repository.EspecialidadRepository;

@ExtendWith(MockitoExtension.class)
class OdontologoServiceImplTest {

    @Mock
    private OdontologoRepository odontologoRepository;

    @Mock
    private OdontologoMapper odontologoMapper;

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private OdontologoServiceImpl odontologoService;

    @Test
    void noPermiteRegistrarCopDuplicado() {
        when(odontologoRepository.existsByCop("COP-123")).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> odontologoService.crear(request("Ortodoncia")));

        verify(odontologoRepository, never()).save(any(Odontologo.class));
    }

    @Test
    void noPermiteRegistrarEspecialidadFueraDelCatalogo() {
        when(odontologoRepository.existsByCop("COP-123")).thenReturn(false);
        when(especialidadRepository.existsByNombre("Inventada")).thenReturn(false);

        assertThrows(ReglaNegocioException.class, () -> odontologoService.crear(request("Inventada")));

        verify(odontologoRepository, never()).save(any(Odontologo.class));
    }

    @Test
    void noPermiteActualizarConCopDeOtroOdontologo() {
        when(odontologoRepository.findById(4L)).thenReturn(Optional.of(odontologo()));
        when(odontologoRepository.existsByCopAndIdNot("COP-123", 4L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> odontologoService.actualizar(4L, request("Ortodoncia")));

        verify(odontologoRepository, never()).save(any(Odontologo.class));
    }

    @Test
    void noPermiteEliminarOdontologoConAtenciones() {
        Odontologo odontologo = odontologo();
        when(odontologoRepository.findById(4L)).thenReturn(Optional.of(odontologo));
        when(atencionRepository.existsByIdOdontologo(4L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class, () -> odontologoService.eliminar(4L));

        verify(odontologoRepository, never()).delete(odontologo);
    }

    @Test
    void eliminaOdontologoSinAtencionesYLoAudita() {
        Odontologo odontologo = odontologo();
        when(odontologoRepository.findById(4L)).thenReturn(Optional.of(odontologo));
        when(atencionRepository.existsByIdOdontologo(4L)).thenReturn(false);

        odontologoService.eliminar(4L);

        verify(odontologoRepository).delete(odontologo);
        verify(auditoriaService).registrar("ODONTOLOGO", 4L, "ELIMINAR", "Odontólogo eliminado");
    }

    private OdontologoRequest request(String especialidad) {
        return OdontologoRequest.builder()
                .cop("COP-123")
                .nombres("Ana")
                .apellidos("Torres")
                .especialidad(especialidad)
                .build();
    }

    private Odontologo odontologo() {
        return Odontologo.builder()
                .id(4L)
                .cop("COP-999")
                .nombres("Ana")
                .apellidos("Torres")
                .especialidad("Ortodoncia")
                .build();
    }
}
