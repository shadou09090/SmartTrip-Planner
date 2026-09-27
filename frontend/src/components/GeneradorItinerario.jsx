import React, { useState } from 'react';

export default function GeneradorItinerario() {
    const [fechaInicio, setFechaInicio] = useState('');
    const [fechaFin, setFechaFin] = useState('');
    const [itinerario, setItinerario] = useState([]);

    const generarItinerario = async (e) => {
        e.preventDefault();
        try {
            const respuesta = await fetch('http://localhost:8080/api/itinerario/generar', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ fechaInicio, fechaFin })
            });
            if(respuesta.ok) {
                const datos = await respuesta.json();
                setItinerario(datos.dias || []);
            }
        } catch (error) {
            console.error("Error conectando al backend:", error);
        }
    };

    return (
        <div className="p-4 max-w-2xl mx-auto font-sans">
            <h2 className="text-xl font-bold mb-4">Planificador de Itinerario</h2>
            <form onSubmit={generarItinerario} className="flex gap-4 mb-6 items-center">
                <div className="flex flex-col">
                    <label className="text-sm text-gray-600">Fecha de inicio</label>
                    <input type="date" value={fechaInicio} onChange={e => setFechaInicio(e.target.value)} required className="border p-2 rounded" />
                </div>
                <div className="flex flex-col">
                    <label className="text-sm text-gray-600">Fecha de fin</label>
                    <input type="date" value={fechaFin} onChange={e => setFechaFin(e.target.value)} required className="border p-2 rounded" />
                </div>
                <button type="submit" className="bg-blue-600 text-white px-4 py-2 rounded mt-5">
                    Generar Espacio
                </button>
            </form>

            <div className="espacio-itinerario space-y-4">
                {itinerario.map((dia, index) => (
                    <div key={index} className="border p-4 bg-gray-50 rounded shadow-sm">
                        <h3 className="font-semibold border-b pb-2 mb-2">Día {index + 1}: {dia.fecha}</h3>
                        <button type="button" className="text-blue-600 text-sm font-medium border border-dashed border-blue-400 px-3 py-2 rounded w-full mt-2">
                            + Agregar actividad de forma manual
                        </button>
                    </div>
                ))}
            </div>
        </div>
    );
}