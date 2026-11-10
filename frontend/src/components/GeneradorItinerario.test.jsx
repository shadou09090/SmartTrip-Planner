// @vitest-environment jsdom
import React from 'react';
import { afterEach, expect, test, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import GeneradorItinerario from './GeneradorItinerario';
import { costoEnCentavos, resumirPresupuesto, formatearCosto } from '../utils/presupuesto';

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

test('actualiza el total entre días y retira la alerta al eliminar el exceso', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({ dias: [
      { id: 'uno', fecha: '2026-12-01', actividades: [] },
      { id: 'dos', fecha: '2026-12-02', actividades: [] },
    ] }),
  }));
  render(<GeneradorItinerario tripData={{ startDate: '2026-12-01', endDate: '2026-12-02', budget: 100000 }} />);
  await screen.findByText('Día 1');
  const resumen = screen.getByRole('region', { name: 'Control de presupuesto' });
  const agregar = (nombre, costo) => {
    fireEvent.change(screen.getByPlaceholderText(/Ej. Desayuno/), { target: { value: nombre } });
    fireEvent.change(screen.getByLabelText('Costo de la actividad (COP)'), { target: { value: costo } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar actividad' }));
  };
  agregar('Museo', '60000');
  expect(within(resumen).getByText(formatearCosto(60000), { normalizer: (text) => text })).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
  fireEvent.click(screen.getByText('Día 2'));
  agregar('Tour', '40000');
  expect(screen.queryByRole('alert')).toBeNull(); // Igualar el límite no lo excede.
  agregar('Entrada', '0.01');
  expect(within(resumen).getByText(formatearCosto(100000.01), { normalizer: (text) => text })).toBeTruthy();
  expect(screen.getByRole('alert').textContent).toContain('Presupuesto excedido');
  const actividad = screen.getByText('Entrada').closest('.actividad-item');
  fireEvent.click(within(actividad).getByRole('button', { name: 'Eliminar actividad' }));
  expect(screen.queryByRole('alert')).toBeNull();
  agregar('Paseo gratuito', '0');
  expect(screen.getByText('Paseo gratuito')).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
});

test('rechaza costos negativos, vacíos, no finitos o de precisión inválida', () => {
  for (const valor of ['', ' ', '-1', 'abc', '1.001', Infinity, NaN, null, true]) {
    expect(costoEnCentavos(valor)).toBeNull();
  }
  expect(costoEnCentavos('0')).toBe(0);
  expect(costoEnCentavos('123.45')).toBe(12345);
});

test('suma centavos sin falsas alertas y admite actividades antiguas sin costo', () => {
  expect(resumirPresupuesto([{ actividades: [{ costo: 0.1 }, { costo: 0.2 }, { texto: 'Antigua' }] }], 0.3))
    .toEqual({ total: 0.3, limite: 0.3, excedido: false, diferencia: 0 });
  expect(resumirPresupuesto([], undefined)).toEqual({ total: 0, limite: null, excedido: false, diferencia: null });
});

test('sin presupuesto definido muestra una indicación y no una falsa alerta', () => {
  render(<GeneradorItinerario />);
  expect(screen.getByText('Sin definir')).toBeTruthy();
  expect(screen.getByText(/Define el presupuesto al crear/)).toBeTruthy();
  expect(screen.queryByRole('alert')).toBeNull();
});
