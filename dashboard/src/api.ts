import type { Alert, OperatorActionType } from '@panic/shared';

// Backend Django do Copiloto. Em dev aponta pro emulador/host; em produção,
// defina VITE_SERVER_URL com a URL pública da VPS (ex.: http://SEU_IP:8000).
const API_URL = import.meta.env.VITE_SERVER_URL ?? 'http://localhost:8000';

export async function fetchAlerts(): Promise<Alert[]> {
  const res = await fetch(`${API_URL}/api/alerts/`);
  if (!res.ok) throw new Error('falha ao buscar alertas');
  return res.json();
}

export async function sendOperatorAction(
  alertId: string,
  operatorAction: OperatorActionType,
): Promise<Alert> {
  const res = await fetch(`${API_URL}/api/alerts/${alertId}/`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    // O Django espera snake_case.
    body: JSON.stringify({ operator_action: operatorAction }),
  });
  if (!res.ok) throw new Error('falha ao atualizar alerta');
  return res.json();
}
