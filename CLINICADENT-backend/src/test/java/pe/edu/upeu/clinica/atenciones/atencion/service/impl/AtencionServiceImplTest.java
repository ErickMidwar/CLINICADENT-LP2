package pe.edu.upeu.clinica.atenciones.atencion.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.upeu.clinica.atenciones.atencion.dto.AtencionRequest;
import pe.edu.upeu.clinica.atenciones.atencion.dto.AtencionResponse;
import pe.edu.upeu.clinica.atenciones.atencion.dto.DetalleAtencionRequest;
import pe.edu.upeu.clinica.atenciones.atencion.entity.Atencion;
import pe.edu.upeu.clinica.atenciones.atencion.entity.DetalleAtencion;
import pe.edu.upeu.clinica.atenciones.atencion.mapper.AtencionMapper;
import pe.edu.upeu.clinica.atenciones.atencion.repository.AtencionRepository;
import pe.edu.upeu.clinica.atenciones.odontologo.entity.Odontologo;
import pe.edu.upeu.clinica.atenciones.odontologo.repository.OdontologoRepository;
import pe.edu.upeu.clinica.auditoria.AuditoriaService;
import pe.edu.upeu.clinica.citas.CitaEstadoPort;
import pe.edu.upeu.clinica.shared.exception.ReglaNegocioException;
import pe.edu.upeu.clinica.shared.exception.ResourceNotFoundException;
import pe.edu.upeu.clinica.tratamientos.catalogo.entity.Procedimiento;
import pe.edu.upeu.clinica.tratamientos.catalogo.repository.ProcedimientoRepository;

@ExtendWith(MockitoExtension.class)
class AtencionServiceImplTest {

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private OdontologoRepository odontologoRepository;

    @Mock
    private AtencionMapper atencionMapper;

    @Mock
    private CitaEstadoPort citaEstadoPort;

    @Mock
    private ProcedimientoRepository procedimientoRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private AtencionServiceImpl atencionService;

    private final Odontologo odontologo = Odontologo.builder()
            .id(3L)
            .cop("COP-123")
            .nombres("Ana")
            .apellidos("Torres")
            .especialidad("Ortodoncia")
            .build();

    @Test
    void rechazaOdontologoInexistente() {
        when(odontologoRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> atencionService.registrarAtencion(request(detalle(1L, "16"))));

        verify(atencionRepository, never()).saveAndFlush(any(Atencion.class));
        verify(citaEstadoPort, never()).actualizarEstado(anyLong(), any());
    }

    @Test
    void noPermiteAtenderDosVecesLaMismaCita() {
        when(odontologoRepository.findById(3L)).thenReturn(Optional.of(odontologo));
        when(atencionRepository.existsByIdCita(10L)).thenReturn(true);

        assertThrows(ReglaNegocioException.class,
                () -> atencionService.registrarAtencion(request(detalle(1L, "16"))));

        verify(atencionRepository, never()).saveAndFlush(any(Atencion.class));
        verify(citaEstadoPort, never()).actualizarEstado(anyLong(), any());
    }

    @Test
    void noPermiteRepetirProcedimientoEnLaMismaPieza() {
        when(odontologoRepository.findById(3L)).thenReturn(Optional.of(odontologo));
        when(atencionRepository.existsByIdCita(10L)).thenReturn(false);
        when(atencionMapper.toEntity(any(AtencionRequest.class))).thenReturn(Atencion.builder().build());
        when(atencionMapper.toDetalleEntity(any(DetalleAtencionRequest.class)))
                .thenAnswer(invocation -> new DetalleAtencion());
        when(procedimientoRepository.findById(1L)).thenReturn(Optional.of(procedimiento()));

        assertThrows(ReglaNegocioException.class,
                () -> atencionService.registrarAtencion(request(detalle(1L, "16"), detalle(1L, " 16 "))));

        verify(atencionRepository, never()).saveAndFlush(any(Atencion.class));
    }

    @Test
    void registraAtencionMarcaCitaAtendidaYDevuelveNombreDelOdontologo() {
        Atencion guardada = Atencion.builder().id(50L).idCita(10L).idOdontologo(3L).build();
        when(odontologoRepository.findById(3L)).thenReturn(Optional.of(odontologo));
        when(atencionRepository.existsByIdCita(10L)).thenReturn(false);
        when(atencionMapper.toEntity(any(AtencionRequest.class))).thenReturn(Atencion.builder().build());
        when(atencionMapper.toDetalleEntity(any(DetalleAtencionRequest.class)))
                .thenAnswer(invocation -> new DetalleAtencion());
        when(procedimientoRepository.findById(1L)).thenReturn(Optional.of(procedimiento()));
        when(atencionRepository.saveAndFlush(any(Atencion.class))).thenReturn(guardada);
        when(atencionMapper.toResponse(guardada))
                .thenReturn(AtencionResponse.builder().id(50L).idCita(10L).idOdontologo(3L).build());

        AtencionResponse response = atencionService.registrarAtencion(request(detalle(1L, " 16 ")));

        assertEquals(50L, response.getId());
        assertEquals("Torres, Ana", response.getNombreOdontologo());
        ArgumentCaptor<Atencion> captor = ArgumentCaptor.forClass(Atencion.class);
        verify(atencionRepository).saveAndFlush(captor.capture());
        assertEquals("PENDIENTE", captor.getValue().getEstado());
        verify(citaEstadoPort).actualizarEstado(10L, CitaEstadoPort.ESTADO_ATENDIDA);
        verify(auditoriaService).registrar("ATENCION", 50L, "REGISTRAR", "Atención clínica registrada");
    }

    @Test
    void guardaElEstadoElegidoEnLaAtencion() {
        Atencion guardada = Atencion.builder().id(51L).idCita(10L).idOdontologo(3L).estado("ATENDIDA").build();
        when(odontologoRepository.findById(3L)).thenReturn(Optional.of(odontologo));
        when(atencionRepository.existsByIdCita(10L)).thenReturn(false);
        when(atencionMapper.toEntity(any(AtencionRequest.class))).thenReturn(Atencion.builder().build());
        when(atencionMapper.toDetalleEntity(any(DetalleAtencionRequest.class)))
                .thenAnswer(invocation -> new DetalleAtencion());
        when(procedimientoRepository.findById(1L)).thenReturn(Optional.of(procedimiento()));
        when(atencionRepository.saveAndFlush(any(Atencion.class))).thenReturn(guardada);
        when(atencionMapper.toResponse(guardada))
                .thenReturn(AtencionResponse.builder().id(51L).idCita(10L).idOdontologo(3L).estado("ATENDIDA").build());

        AtencionRequest request = request(detalle(1L, "16"));
        request.setEstado("ATENDIDA");
        AtencionResponse response = atencionService.registrarAtencion(request);

        ArgumentCaptor<Atencion> captor = ArgumentCaptor.forClass(Atencion.class);
        verify(atencionRepository).saveAndFlush(captor.capture());
        assertEquals("ATENDIDA", captor.getValue().getEstado());
        assertEquals("ATENDIDA", response.getEstado());
    }

    @Test
    void listarDevuelveNombreDelOdontologoDeCadaAtencion() {
        Atencion atencion = Atencion.builder().id(7L).idCita(10L).idOdontologo(3L).build();
        when(atencionRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(atencion));
        when(odontologoRepository.findAllById(any())).thenReturn(List.of(odontologo));
        when(atencionMapper.toResponse(atencion))
                .thenReturn(AtencionResponse.builder().id(7L).idOdontologo(3L).build());

        List<AtencionResponse> atenciones = atencionService.listar();

        assertEquals(1, atenciones.size());
        assertEquals("Torres, Ana", atenciones.get(0).getNombreOdontologo());
    }

    private AtencionRequest request(DetalleAtencionRequest... detalles) {
        return AtencionRequest.builder()
                .idCita(10L)
                .idOdontologo(3L)
                .diagnostico("Caries oclusal")
                .detalles(List.of(detalles))
                .build();
    }

    private DetalleAtencionRequest detalle(Long idProcedimiento, String pieza) {
        return DetalleAtencionRequest.builder()
                .idProcedimiento(idProcedimiento)
                .piezaDental(pieza)
                .build();
    }

    private Procedimiento procedimiento() {
        Procedimiento procedimiento = new Procedimiento();
        procedimiento.setId(1L);
        procedimiento.setNombre("Restauración con resina");
        return procedimiento;
    }
}
