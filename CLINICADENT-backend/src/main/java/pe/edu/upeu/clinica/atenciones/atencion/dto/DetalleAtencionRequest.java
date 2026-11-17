package pe.edu.upeu.clinica.atenciones.atencion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleAtencionRequest {

    @NotNull(message = "Selecciona el procedimiento realizado")
    private Long idProcedimiento;

    @NotBlank(message = "La pieza dental es obligatoria")
    @Pattern(
            regexp = "^\\s*([1-4][1-8]|[5-8][1-5])\\s*$",
            message = "Pieza dental FDI inválida: usa 11-48 (permanentes) o 51-85 (temporales)")
    private String piezaDental;

    @Size(max = 255, message = "La observación no debe exceder 255 caracteres")
    private String observacion;
}
