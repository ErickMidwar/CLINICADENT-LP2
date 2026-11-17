import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';
import {
  Atencion,
  Cita,
  ClinicApiService,
  Odontologo,
  Paciente,
  Procedimiento,
} from '../../../core/services/clinic-api.service';
import { mensajeDeError } from '../../../shared/api-error';

interface DetalleAtencionDraft {
  idProcedimiento: number;
  nombreProcedimiento: string;
  precio: number;
  piezaDental: string;
  observacion: string;
}

type Denticion = 'permanente' | 'temporal';

const PIEZA_FDI = /^([1-4][1-8]|[5-8][1-5])$/;
const TAMANO_PAGINA = 8;

const ARCADAS: Record<Denticion, { superior: number[][]; inferior: number[][] }> = {
  permanente: {
    superior: [[18, 17, 16, 15, 14, 13, 12, 11], [21, 22, 23, 24, 25, 26, 27, 28]],
    inferior: [[48, 47, 46, 45, 44, 43, 42, 41], [31, 32, 33, 34, 35, 36, 37, 38]],
  },
  temporal: {
    superior: [[55, 54, 53, 52, 51], [61, 62, 63, 64, 65]],
    inferior: [[85, 84, 83, 82, 81], [71, 72, 73, 74, 75]],
  },
};

@Component({
  selector: 'app-atencion-page',
  imports: [DatePipe, DecimalPipe, ReactiveFormsModule],
  templateUrl: './atencion-page.html',
  styleUrls: ['../../../shared/transaction-workflow.css', './atencion-page.css'],
})
export class AtencionPage implements OnInit, OnDestroy {
  private readonly api = inject(ClinicApiService);
  private readonly fb = inject(FormBuilder);
  private successTimer?: ReturnType<typeof setTimeout>;

  protected readonly items = signal<Atencion[]>([]);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  protected readonly totalAtenciones = signal(0);
  protected readonly citas = signal<Cita[]>([]);
  protected readonly todasLasCitas = signal<Cita[]>([]);
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly detalles = signal<DetalleAtencionDraft[]>([]);
  protected readonly odontologos = signal<Odontologo[]>([]);
  protected readonly procedimientos = signal<Procedimiento[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly denticion = signal<Denticion>('permanente');
  protected readonly arcadas = computed(() => ARCADAS[this.denticion()]);

  protected readonly form = this.fb.nonNullable.group({
    idCita: ['', Validators.required],
    idOdontologo: ['', Validators.required],
    diagnostico: ['', [Validators.required, Validators.maxLength(500)]],
    notaMedica: ['', Validators.maxLength(500)],
    estado: ['PENDIENTE', Validators.required],
    idProcedimiento: ['', Validators.required],
    piezaDental: ['', [Validators.required, Validators.pattern(PIEZA_FDI)]],
    observacion: ['', Validators.maxLength(255)],
  });

  private readonly valores = toSignal(
    this.form.valueChanges.pipe(startWith(this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );

  protected readonly piezaSeleccionada = computed(() => this.valores().piezaDental?.trim() ?? '');

  protected readonly citaSeleccionada = computed(() => {
    const id = Number(this.valores().idCita);
    return this.citas().find((cita) => cita.id === id) ?? null;
  });

  protected readonly pacienteSeleccionado = computed(() => {
    const cita = this.citaSeleccionada();
    if (!cita) return null;
    return this.pacientes().find((paciente) => paciente.id === cita.idPaciente) ?? null;
  });

  protected readonly odontologoSeleccionado = computed(() => {
    const id = Number(this.valores().idOdontologo);
    return this.odontologos().find((odontologo) => odontologo.id === id) ?? null;
  });

  protected readonly procedimientosPorPieza = computed(() => {
    const conteo = new Map<string, number>();
    this.detalles().forEach((detalle) =>
      conteo.set(detalle.piezaDental, (conteo.get(detalle.piezaDental) ?? 0) + 1),
    );
    return conteo;
  });

  protected readonly costoEstimado = computed(() =>
    this.detalles().reduce((total, detalle) => total + detalle.precio, 0),
  );

  ngOnInit(): void {
    this.cargar(0);
    this.api.citas().subscribe({
      next: (citas) => {
        this.todasLasCitas.set(citas);
        this.citas.set(citas.filter((cita) => cita.estado === 'PROGRAMADA'));
      },
      error: (err) => this.error.set(mensajeDeError(err, 'No se pudieron cargar las citas disponibles.')),
    });
    this.api.pacientes().subscribe({
      next: (items) => this.pacientes.set(items),
      error: (err) => this.error.set(mensajeDeError(err, 'No se pudo cargar el padrón de pacientes.')),
    });
    this.api.odontologos().subscribe({
      next: (items) => this.odontologos.set(items),
      error: (err) => this.error.set(mensajeDeError(err, 'No se pudo cargar el listado de odontólogos.')),
    });
    this.api.procedimientos().subscribe({
      next: (items) => this.procedimientos.set(items),
      error: (err) => this.error.set(mensajeDeError(err, 'No se pudo cargar el catálogo de procedimientos.')),
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.successTimer);
  }

  cargar(pagina: number): void {
    this.loading.set(true);
    this.api.atencionesPaginadas(pagina, TAMANO_PAGINA).subscribe({
      next: (resultado) => {
        this.items.set(resultado.content);
        this.pagina.set(resultado.page);
        this.totalPaginas.set(resultado.totalPages);
        this.totalAtenciones.set(resultado.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar las atenciones.'));
        this.loading.set(false);
      },
    });
  }

  invalido(campo: 'idCita' | 'idOdontologo' | 'diagnostico' | 'estado' | 'idProcedimiento' | 'piezaDental'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && control.touched;
  }

  cambiarDenticion(denticion: Denticion): void {
    this.denticion.set(denticion);
  }

  seleccionarPieza(pieza: number): void {
    const control = this.form.controls.piezaDental;
    control.setValue(String(pieza));
    control.markAsTouched();
  }

  guardar(): void {
    this.error.set(null);
    const { idCita, idOdontologo, diagnostico, notaMedica, estado } = this.form.controls;
    if (idCita.invalid || idOdontologo.invalid || diagnostico.invalid || notaMedica.invalid || estado.invalid) {
      [idCita, idOdontologo, diagnostico, notaMedica, estado].forEach((control) => control.markAsTouched());
      this.error.set('Completa la cabecera clínica: cita, odontólogo y diagnóstico.');
      return;
    }
    if (this.detalles().length === 0) {
      this.error.set('Agrega al menos una pieza dental con su procedimiento.');
      return;
    }
    const values = this.form.getRawValue();
    this.saving.set(true);
    this.api.registrarAtencion({
      idCita: Number(values.idCita),
      idOdontologo: Number(values.idOdontologo),
      diagnostico: values.diagnostico.trim(),
      notaMedica: values.notaMedica.trim(),
      estado: values.estado,
      detalles: this.detalles().map(({ idProcedimiento, piezaDental, observacion }) => ({
        idProcedimiento,
        piezaDental,
        observacion,
      })),
    }).subscribe({
      next: (atencion) => {
        this.citas.update((citas) => citas.filter((cita) => cita.id !== atencion.idCita));
        this.detalles.set([]);
        this.form.reset();
        this.mostrarExito(`Atención #${atencion.id} registrada. La cita quedó marcada como ATENDIDA.`);
        this.saving.set(false);
        this.cargar(0);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo registrar la atención.'));
        this.saving.set(false);
      },
    });
  }

  agregarDetalle(): void {
    const controls = this.form.controls;
    controls.piezaDental.markAsTouched();
    controls.idProcedimiento.markAsTouched();
    if (controls.piezaDental.invalid || controls.idProcedimiento.invalid || controls.observacion.invalid) {
      return;
    }
    const values = this.form.getRawValue();
    const idProcedimiento = Number(values.idProcedimiento);
    const procedimiento = this.procedimientos().find((item) => item.id === idProcedimiento);
    if (!procedimiento) return;
    const pieza = values.piezaDental.trim();
    if (this.detalles().some((detalle) => detalle.idProcedimiento === idProcedimiento && detalle.piezaDental === pieza)) {
      this.error.set(`"${procedimiento.nombre}" ya está registrado para la pieza ${pieza}.`);
      return;
    }
    this.detalles.update((current) => [
      ...current,
      {
        idProcedimiento,
        nombreProcedimiento: procedimiento.nombre,
        precio: Number(procedimiento.precio) || 0,
        piezaDental: pieza,
        observacion: values.observacion.trim(),
      },
    ]);
    controls.idProcedimiento.reset('');
    controls.piezaDental.reset('');
    controls.observacion.reset('');
    this.error.set(null);
  }

  quitarDetalle(index: number): void {
    this.detalles.update((current) => current.filter((_, itemIndex) => itemIndex !== index));
  }

  pacienteDeCita(idCita: number): string {
    const cita = this.todasLasCitas().find((item) => item.id === idCita);
    if (!cita) return 'Paciente no disponible';
    const paciente = this.pacientes().find((item) => item.id === cita.idPaciente);
    return paciente ? `${paciente.apellidos}, ${paciente.nombres}` : `Paciente #${cita.idPaciente}`;
  }

  nombreOdontologo(atencion: Atencion): string {
    if (atencion.nombreOdontologo) return atencion.nombreOdontologo;
    const odontologo = this.odontologos().find((item) => item.id === atencion.idOdontologo);
    return odontologo ? `${odontologo.apellidos}, ${odontologo.nombres}` : `Odontólogo #${atencion.idOdontologo}`;
  }

  private mostrarExito(mensaje: string): void {
    this.success.set(mensaje);
    clearTimeout(this.successTimer);
    this.successTimer = setTimeout(() => this.success.set(null), 6000);
  }
}
