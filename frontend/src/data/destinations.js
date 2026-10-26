export const DESTINATIONS = [
  {
    keys: ['cartagena'],
    name: 'Cartagena, Colombia',
    center: [10.391, -75.4794],
    places: [
      { id: 'ciudad-amurallada', name: 'Ciudad Amurallada', location: 'Centro histórico de Cartagena', coordinates: [10.4236, -75.5503], category: 'Cultura' },
      { id: 'castillo-san-felipe', name: 'Castillo de San Felipe de Barajas', location: 'Pie del Cerro, Cartagena', coordinates: [10.4225, -75.5392], category: 'Historia' },
      { id: 'getsemani', name: 'Barrio Getsemaní', location: 'Getsemaní, Cartagena', coordinates: [10.4204, -75.5445], category: 'Vida nocturna y arte' },
      { id: 'bocagrande', name: 'Playa de Bocagrande', location: 'Bocagrande, Cartagena', coordinates: [10.4012, -75.557], category: 'Playa' },
      { id: 'islas-rosario', name: 'Islas del Rosario', location: 'Archipiélago de Nuestra Señora del Rosario', coordinates: [10.1772, -75.7483], category: 'Naturaleza' },
    ],
  },
  {
    keys: ['bogota', 'bogotá'],
    name: 'Bogotá, Colombia',
    center: [4.6486, -74.0822],
    places: [
      { id: 'monserrate', name: 'Cerro de Monserrate', location: 'Cerro de Monserrate, Bogotá', coordinates: [4.6057, -74.0555], category: 'Aventura y Vistas' },
      { id: 'museo-oro', name: 'Museo del Oro', location: 'La Candelaria, Bogotá', coordinates: [4.6019, -74.072], category: 'Cultura' },
      { id: 'plaza-bolivar', name: 'Plaza de Bolívar', location: 'Centro histórico, Bogotá', coordinates: [4.5981, -74.0758], category: 'Historia' },
      { id: 'jardin-botanico', name: 'Jardín Botánico de Bogotá', location: 'Engativá, Bogotá', coordinates: [4.6687, -74.1005], category: 'Naturaleza' },
      { id: 'zona-t', name: 'Zona T y Parque de la 93', location: 'Chapinero / Chico, Bogotá', coordinates: [4.6672, -74.0545], category: 'Gastronomía y Vida nocturna' },
    ],
  },
  {
    keys: ['medellin', 'medellín'],
    name: 'Medellín, Colombia',
    center: [6.2442, -75.5812],
    places: [
      { id: 'comuna-13', name: 'Tour Comuna 13 y Graffitis', location: 'San Javier, Medellín', coordinates: [6.2566, -75.6217], category: 'Cultura y Arte' },
      { id: 'plaza-botero', name: 'Plaza Botero y Museo de Antioquia', location: 'La Candelaria, Medellín', coordinates: [6.2525, -75.5684], category: 'Cultura' },
      { id: 'parque-arvi', name: 'Parque Ecológico Arví', location: 'Santa Elena, Medellín', coordinates: [6.2811, -75.502], category: 'Naturaleza y Aventura' },
      { id: 'pueblito-paisa', name: 'Pueblito Paisa', location: 'Cerro Nutibara, Medellín', coordinates: [6.2351, -75.5805], category: 'Vistas y Tradición' },
      { id: 'el-poblado', name: 'Parque Lleras y El Poblado', location: 'El Poblado, Medellín', coordinates: [6.2088, -75.5678], category: 'Gastronomía y Noche' },
    ],
  },
  {
    keys: ['santa marta'],
    name: 'Santa Marta, Colombia',
    center: [11.2408, -74.199],
    places: [
      { id: 'parque-tayrona', name: 'Parque Nacional Natural Tayrona', location: 'Magdalena, cerca de Santa Marta', coordinates: [11.306, -73.922], category: 'Naturaleza y Playa' },
      { id: 'quinta-san-pedro', name: 'Quinta de San Pedro Alejandrino', location: 'Mamatoco, Santa Marta', coordinates: [11.2284, -74.178], category: 'Historia' },
      { id: 'bahia-santa-marta', name: 'Bahía y Centro Histórico', location: 'Centro histórico, Santa Marta', coordinates: [11.2435, -74.2167], category: 'Cultura y Vistas' },
      { id: 'taganga', name: 'Pueblo de Taganga', location: 'Distrito de Santa Marta, Magdalena', coordinates: [11.2672, -74.191], category: 'Playa y Buceo' },
    ],
  },
  {
    keys: ['cali'],
    name: 'Cali, Colombia',
    center: [3.4516, -76.532],
    places: [
      { id: 'cristo-rey', name: 'Monumento a Cristo Rey', location: 'Cerro Los Cristales, Cali', coordinates: [3.4357, -76.5654], category: 'Vistas' },
      { id: 'san-antonio', name: 'Barrio San Antonio', location: 'San Antonio, Cali', coordinates: [3.4475, -76.5401], category: 'Cultura y Gastronomía' },
      { id: 'zoologico-cali', name: 'Zoológico de Cali', location: 'Santa Teresita, Cali', coordinates: [3.4478, -76.5583], category: 'Naturaleza' },
    ],
  },
];

export const FALLBACK_DESTINATION = {
  name: 'Destino seleccionado',
  center: [4.5709, -74.2973],
  places: [],
};

export function findDestination(destination) {
  if (!destination) return null;
  const normalized = destination.toLocaleLowerCase('es').trim();
  return DESTINATIONS.find(({ keys }) => (
    keys.some((key) => normalized.includes(key))
  )) || null;
}
