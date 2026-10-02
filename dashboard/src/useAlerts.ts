import { useEffect, useRef, useState } from 'react';
import type { Alert } from '@panic/shared';
import { fetchAlerts } from './api';
import { playAlertSound } from './alertSound';

const POLL_INTERVAL_MS = 3000;

export function useAlerts() {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [connected, setConnected] = useState(true);
  const seenIds = useRef<Set<string> | null>(null);

  // Toca um beep quando um alerta com id novo aparece — mas nunca na
  // primeira carga da página (senão todo histórico já existente "chegaria"
  // de uma vez ao abrir a Central).
  function noteArrivals(data: Alert[]): void {
    if (seenIds.current === null) {
      seenIds.current = new Set(data.map((a) => a.id));
      return;
    }
    const hasNewArrival = data.some((a) => !seenIds.current!.has(a.id));
    data.forEach((a) => seenIds.current!.add(a.id));
    if (hasNewArrival) playAlertSound();
  }

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const data = await fetchAlerts();
        if (!cancelled) {
          noteArrivals(data);
          setAlerts(data);
          setLastUpdated(new Date());
          setConnected(true);
        }
      } catch (err) {
        console.error('[dashboard] erro ao buscar alertas', err);
        if (!cancelled) setConnected(false);
      }
    }

    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  return {
    alerts,
    lastUpdated,
    connected,
    refetch: () =>
      fetchAlerts().then((data) => {
        noteArrivals(data);
        setAlerts(data);
        setLastUpdated(new Date());
      }),
  };
}
