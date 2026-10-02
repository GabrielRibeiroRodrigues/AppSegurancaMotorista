import type { Alert, OperatorActionType } from '@panic/shared';
import { authFetch } from './auth';

// Alerts are operator-only now, so every call goes through authFetch (bearer token
// + refresh on 401). Configure the backend URL with VITE_SERVER_URL in production.

export async function fetchAlerts(): Promise<Alert[]> {
  const res = await authFetch('/api/alerts/');
  if (!res.ok) throw new Error('falha ao buscar alertas');
  return res.json();
}

export async function sendOperatorAction(
  alertId: string,
  operatorAction: OperatorActionType,
): Promise<Alert> {
  const res = await authFetch(`/api/alerts/${alertId}/`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    // O Django espera snake_case.
    body: JSON.stringify({ operator_action: operatorAction }),
  });
  if (!res.ok) throw new Error('falha ao atualizar alerta');
  return res.json();
}
