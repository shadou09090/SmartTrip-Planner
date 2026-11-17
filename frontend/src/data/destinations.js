export const DESTINATIONS = [
  {
    keys: ['cartagena'],
    name: 'Cartagena, Colombia',
    center: [10.391, -75.4794],
    places: [
      { id: 'ciudad-amurallada', name: 'Ciudad Amurallada', location: 'Centro histórico de Cartagena', coordinates: [10.4236, -75.5503] },
      { id: 'castillo-san-felipe', name: 'Castillo de San Felipe de Barajas', location: 'Pie del Cerro, Cartagena', coordinates: [10.4225, -75.5392] },
      { id: 'getsemani', name: 'Barrio Getsemaní', location: 'Getsemaní, Cartagena', coordinates: [10.4204, -75.5445] },
      { id: 'bocagrande', name: 'Playa de Bocagrande', location: 'Bocagrande, Cartagena', coordinates: [10.4012, -75.557] },
      { id: 'islas-rosario', name: 'Islas del Rosario', location: 'Archipiélago de Nuestra Señora del Rosario', coordinates: [10.1789, -75.7486] },
      { id: 'convento-popa', name: 'Convento de la Popa', location: 'Cerro de la Popa, Cartagena', coordinates: [10.4194, -75.5255] }
    ],
  },
  {
    keys: ['bogota', 'bogotá'],
    name: 'Bogotá, Colombia',
    center: [4.6486, -74.0822],
    places: [
      { id: 'monserrate', name: 'Monserrate', location: 'Cerro de Monserrate, Bogotá', coordinates: [4.6057, -74.0555] },
      { id: 'museo-oro', name: 'Museo del Oro', location: 'La Candelaria, Bogotá', coordinates: [4.6019, -74.072] },
      { id: 'plaza-bolivar', name: 'Plaza de Bolívar', location: 'Centro histórico, Bogotá', coordinates: [4.5981, -74.0758] },
      { id: 'jardin-botanico', name: 'Jardín Botánico de Bogotá', location: 'Engativá, Bogotá', coordinates: [4.6687, -74.1005] },
      { id: 'parque-simon-bolivar', name: 'Parque Simón Bolívar', location: 'Teusaquillo, Bogotá', coordinates: [4.6583, -74.0936] },
      { id: 'catedral-sal-zipaquira', name: 'Catedral de Sal de Zipaquirá', location: 'Zipaquirá, Cundinamarca', coordinates: [5.0192, -74.0044] },
      { id: 'laguna-guatavita', name: 'Laguna de Guatavita', location: 'Sesquilé, Cundinamarca', coordinates: [4.9786, -73.7744] }
    ],
  },
  {
    keys: ['medellin', 'medellín'],
    name: 'Medellín, Colombia',
    center: [6.2442, -75.5812],
    places: [
      { id: 'comuna-13', name: 'Comuna 13', location: 'San Javier, Medellín', coordinates: [6.2566, -75.6217] },
      { id: 'plaza-botero', name: 'Plaza Botero', location: 'La Candelaria, Medellín', coordinates: [6.2525, -75.5684] },
      { id: 'parque-arvi', name: 'Parque Arví', location: 'Santa Elena, Medellín', coordinates: [6.2811, -75.502] },
      { id: 'pueblito-paisa', name: 'Pueblito Paisa', location: 'Cerro Nutibara, Medellín', coordinates: [6.2351, -75.5805] },
      { id: 'parque-explora', name: 'Parque Explora', location: 'Aranjuez, Medellín', coordinates: [6.2707, -75.5658] },
      { id: 'guatape-piedra', name: 'Piedra del Peñol y Guatapé', location: 'Guatapé, Antioquia', coordinates: [6.2209, -75.1793] }
    ],
  },
  {
    keys: ['santa marta'],
    name: 'Santa Marta, Colombia',
    center: [11.2408, -74.199],
    places: [
      { id: 'parque-tayrona', name: 'Parque Nacional Natural Tayrona', location: 'Magdalena, cerca de Santa Marta', coordinates: [11.306, -73.922] },
      { id: 'quinta-san-pedro', name: 'Quinta de San Pedro Alejandrino', location: 'Mamatoco, Santa Marta', coordinates: [11.2284, -74.178] },
      { id: 'bahia-santa-marta', name: 'Bahía de Santa Marta', location: 'Centro histórico, Santa Marta', coordinates: [11.2435, -74.2167] },
      { id: 'taganga', name: 'Taganga', location: 'Distrito de Santa Marta, Magdalena', coordinates: [11.2672, -74.191] },
      { id: 'ciudad-perdida', name: 'Teyuna (Ciudad Perdida)', location: 'Sierra Nevada de Santa Marta', coordinates: [11.0336, -73.9255] }
    ],
  },
  {
    keys: ['cali'],
    name: 'Cali, Colombia',
    center: [3.4516, -76.532],
    places: [
      { id: 'san-antonio-cali', name: 'Barrio San Antonio', location: 'San Antonio, Cali', coordinates: [3.4475, -76.539] },
      { id: 'bulevar-del-rio', name: 'Bulevar del Río', location: 'Centro, Cali', coordinates: [3.4518, -76.5332] },
      { id: 'cristo-rey-cali', name: 'Monumento a Cristo Rey', location: 'Los Cristales, Cali', coordinates: [3.435, -76.565] },
      { id: 'zoologico-cali', name: 'Zoológico de Cali', location: 'Santa Teresita, Cali', coordinates: [3.449, -76.558] }
    ]
  }
];

export function findDestination(destinationName) {
  if (!destinationName) return null;
  const normalized = destinationName.toLocaleLowerCase('es').trim();
  return (
    DESTINATIONS.find(({ keys }) =>
      keys.some((key) => normalized.includes(key))
    ) || null
  );
}

export function getAllKnownPlaces() {
  const list = [];
  DESTINATIONS.forEach((d) => {
    d.places.forEach((p) => {
      list.push({ ...p, destinationName: d.name });
    });
  });
  return list;
}

export function resolveCoordinates(locationText, destinationName = '') {
  if (!locationText) return null;

  // Direct lat,lon format: "4.6019, -74.072"
  const coordsMatch = String(locationText).match(/(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)/);
  if (coordsMatch) {
    const lat = parseFloat(coordsMatch[1]);
    const lng = parseFloat(coordsMatch[2]);
    if (!isNaN(lat) && !isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
      return [lat, lng];
    }
  }

  const query = locationText.toLowerCase().trim();

  // 1. Look in current destination places first
  const currentDest = findDestination(destinationName);
  if (currentDest) {
    const match = currentDest.places.find(
      (p) =>
        query.includes(p.name.toLowerCase()) ||
        p.name.toLowerCase().includes(query) ||
        (p.location && query.includes(p.location.toLowerCase()))
    );
    if (match) return match.coordinates;
  }

  // 2. Look across all places
  const allPlaces = getAllKnownPlaces();
  const globalMatch = allPlaces.find(
    (p) =>
      query.includes(p.name.toLowerCase()) ||
      p.name.toLowerCase().includes(query) ||
      (p.location && query.includes(p.location.toLowerCase()))
  );
  if (globalMatch) return globalMatch.coordinates;

  return null;
}
