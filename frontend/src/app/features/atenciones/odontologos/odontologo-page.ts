import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ClinicApiService, Odontologo, Especialidad } from '../../../core/services/clinic-api.service';
import { mensajeDeError } from '../../../shared/api-error';

@Component({
  selector: 'app-odontologo-page',
  imports: [ReactiveFormsModule],
  templateUrl: './odontologo-page.html',
  styleUrl: './odontologo-page.css',
})
export class OdontologoPage implements OnInit, OnDestroy {
  private readonly api = inject(ClinicApiService);
  private readonly fb = inject(FormBuilder);
  private successTimer?: ReturnType<typeof setTimeout>;

  protected readonly items = signal<Odontologo[]>([]);
  protected readonly especialidades = signal<Especialidad[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly busqueda = signal('');
  protected readonly porEliminar = signal<Odontologo | null>(null);
  protected readonly eliminando = signal(false);

  protected readonly filtrados = computed(() => {
    const termino = this.normalizar(this.busqueda());
    if (!termino) return this.items();
    return this.items().filter((item) =>
      this.normalizar(`${item.nombres} ${item.apellidos} ${item.cop} ${item.especialidad}`).includes(termino),
    );
  });

  protected readonly totalEspecialidades = computed(
    () => new Set(this.items().map((item) => item.especialidad.toLowerCase())).size,
  );

  protected readonly form = this.fb.nonNullable.group({
    cop: ['', [Validators.required, Validators.maxLength(20)]],
    nombres: ['', [Validators.required, Validators.maxLength(80)]],
    apellidos: ['', [Validators.required, Validators.maxLength(80)]],
    especialidad: ['', [Validators.required, Validators.maxLength(100)]],
  });

  ngOnInit(): void {
    this.cargar();
    this.cargarEspecialidades();
  }

  ngOnDestroy(): void {
    clearTimeout(this.successTimer);
  }

  cargar(): void {
    this.loading.set(true);
    this.api.odontologos().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudieron cargar los odontólogos.'));
        this.loading.set(false);
      },
    });
  }

  cargarEspecialidades(): void {
    this.api.especialidades().subscribe({
      next: (items) => this.especialidades.set(items),
      error: (err) => this.error.set(mensajeDeError(err, 'No se pudieron cargar las especialidades.')),
    });
  }

  tieneEspecialidad(nombre: string): boolean {
    return this.especialidades().some((e) => e.nombre.toLowerCase() === nombre.toLowerCase());
  }

  invalido(campo: 'cop' | 'nombres' | 'apellidos' | 'especialidad'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && control.touched;
  }

  iniciales(item: Odontologo): string {
    return `${item.nombres.charAt(0)}${item.apellidos.charAt(0)}`.toUpperCase();
  }

  editar(item: Odontologo): void {
    this.editingId.set(item.id);
    this.form.setValue({
      cop: item.cop,
      nombres: item.nombres,
      apellidos: item.apellidos,
      especialidad: item.especialidad,
    });
    this.error.set(null);
    document.getElementById('odontologo-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  cancelarEdicion(): void {
    this.editingId.set(null);
    this.form.reset();
  }

  guardar(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request = {
      cop: value.cop.trim(),
      nombres: value.nombres.trim(),
      apellidos: value.apellidos.trim(),
      especialidad: value.especialidad.trim(),
    };
    const id = this.editingId();
    this.saving.set(true);
    (id ? this.api.actualizarOdontologo(id, request) : this.api.crearOdontologo(request)).subscribe({
      next: (guardado) => {
        this.items.update((items) =>
          id ? items.map((item) => (item.id === id ? guardado : item)) : [...items, guardado],
        );
        this.mostrarExito(id ? 'Odontólogo actualizado correctamente.' : 'Odontólogo registrado correctamente.');
        this.cancelarEdicion();
        this.saving.set(false);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo guardar el odontólogo.'));
        this.saving.set(false);
      },
    });
  }

  pedirEliminacion(item: Odontologo): void {
    this.error.set(null);
    this.porEliminar.set(item);
  }

  cerrarConfirmacion(): void {
    if (!this.eliminando()) this.porEliminar.set(null);
  }

  confirmarEliminacion(): void {
    const item = this.porEliminar();
    if (!item) return;
    this.eliminando.set(true);
    this.api.eliminarOdontologo(item.id).subscribe({
      next: () => {
        this.items.update((items) => items.filter((odontologo) => odontologo.id !== item.id));
        if (this.editingId() === item.id) this.cancelarEdicion();
        this.mostrarExito(`Se eliminó a ${item.nombres} ${item.apellidos}.`);
        this.eliminando.set(false);
        this.porEliminar.set(null);
      },
      error: (err) => {
        this.error.set(mensajeDeError(err, 'No se pudo eliminar el odontólogo.'));
        this.eliminando.set(false);
        this.porEliminar.set(null);
      },
    });
  }

  private mostrarExito(mensaje: string): void {
    this.success.set(mensaje);
    clearTimeout(this.successTimer);
    this.successTimer = setTimeout(() => this.success.set(null), 4500);
  }

  private normalizar(texto: string): string {
    return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
  }
}
