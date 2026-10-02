// Dados de contexto da corrida (passageiro, origem, destino) — o backend
// não envia isso hoje (o alerta real só tem localização + transcrição), mas
// pra Central de Operações parecer completa numa demo, mockamos esses
// campos de forma determinística por alerta: o mesmo alertId sempre mostra
// a mesma pessoa/rota, mas alertas diferentes mostram pessoas diferentes.

const PASSENGER_NAMES = [
  'Maria Fernanda',
  'João Pedro Almeida',
  'Ana Beatriz Souza',
  'Lucas Gabriel Rocha',
  'Camila Nascimento',
  'Rafael Teixeira',
  'Juliana Alves',
  'Bruno Costa Lima',
  'Fernanda Ribeiro',
  'Pedro Henrique Dias',
];

// Pontos reais de Muzambinho-MG, mesma região do trajeto simulado no
// driver-pwa (TripMap.tsx) — mantém a demo consistente entre os dois apps.
const ROUTES: Array<{ origin: string; destination: string }> = [
  { origin: 'IFSULDEMINAS — Campus Muzambinho', destination: 'Rodoviária de Muzambinho' },
  { origin: 'Praça Dr. Augusto Silva', destination: 'Jardim Anápolis' },
  { origin: 'Jardim Primavera', destination: 'Parque da Colina' },
  { origin: 'Vila D’oro', destination: 'Jardim América' },
  { origin: 'Bela Vista', destination: 'Centro de Muzambinho' },
  { origin: 'Jardim do Sol', destination: 'BR-146, km 35' },
];

function hashSeed(input: string): number {
  let hash = 0;
  for (let i = 0; i < input.length; i++) {
    hash = (hash * 31 + input.charCodeAt(i)) >>> 0;
  }
  return hash;
}

export interface MockTripInfo {
  passenger: string;
  origin: string;
  destination: string;
  photoUrl: string;
}

export function getMockTripInfo(alertId: string): MockTripInfo {
  const seed = hashSeed(alertId);
  const route = ROUTES[seed % ROUTES.length];
  const passenger = PASSENGER_NAMES[Math.floor(seed / ROUTES.length) % PASSENGER_NAMES.length];
  // pravatar.cc gera um retrato consistente por seed — cada passageiro mockado
  // fica com uma foto própria (em vez do mesmo avatar genérico repetido).
  const photoUrl = `https://i.pravatar.cc/80?u=${encodeURIComponent(passenger)}`;
  return { passenger, origin: route.origin, destination: route.destination, photoUrl };
}
