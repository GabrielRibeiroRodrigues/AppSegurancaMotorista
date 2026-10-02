import { useCallback, useEffect, useState } from 'react';
import type { OperatorActionType } from '@panic/shared';
import { useAlerts } from './useAlerts';
import { AlertCard } from './components/AlertCard';
import { StatsBar } from './components/StatsBar';
import { LoginScreen } from './components/LoginScreen';
import { ShieldIcon, InboxIcon } from './components/icons';
import { sendOperatorAction } from './api';
import { fetchMe, isLoggedIn, logout, type OperatorInfo } from './auth';

function useSecondsSince(date: Date | null): number {
  const [, force] = useState(0);
  useEffect(() => {
    const interval = setInterval(() => force((n) => n + 1), 1000);
    return () => clearInterval(interval);
  }, []);
  return date ? Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000)) : 0;
}

export default function App() {
  // null = still checking the stored session; then either an operator or not.
  const [operator, setOperator] = useState<OperatorInfo | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (isLoggedIn()) {
        const me = await fetchMe();
        if (!cancelled && me?.is_operator) setOperator(me);
      }
      if (!cancelled) setChecking(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSignOut = useCallback(async () => {
    await logout();
    setOperator(null);
  }, []);

  if (checking) {
    return <div className="min-h-screen bg-[#05070d]" />;
  }
  if (!operator) {
    return <LoginScreen onLoggedIn={setOperator} />;
  }
  return <Dashboard operator={operator} onSignOut={handleSignOut} />;
}

function Dashboard({
  operator,
  onSignOut,
}: {
  operator: OperatorInfo;
  onSignOut: () => void;
}) {
  const { alerts, lastUpdated, connected, refetch } = useAlerts(true, onSignOut);
  const secondsSinceUpdate = useSecondsSince(lastUpdated);

  async function handleAction(alertId: string, action: OperatorActionType) {
    await sendOperatorAction(alertId, action);
    refetch();
  }

  return (
    <div className="min-h-screen bg-[#05070d] text-slate-100">
      <div className="pointer-events-none fixed inset-x-0 top-0 h-64 bg-gradient-to-b from-red-950/20 via-transparent to-transparent" />

      <header className="relative border-b border-white/5 bg-slate-950/60 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-5">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-500/10 ring-1 ring-inset ring-red-500/30">
              <ShieldIcon className="h-5 w-5 text-red-400" />
            </div>
            <div>
              <h1 className="text-lg font-bold tracking-tight text-white">Central de Operações</h1>
              <p className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                <span
                  className={`h-1.5 w-1.5 rounded-full ${connected ? 'bg-emerald-400 pulse-live' : 'bg-red-500'}`}
                />
                {connected
                  ? `Ao vivo · atualizado há ${secondsSinceUpdate}s`
                  : 'Sem conexão com o servidor'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-slate-500 sm:inline">{operator.username}</span>
            <button
              onClick={onSignOut}
              className="rounded-lg border border-white/10 px-3 py-1.5 text-xs font-medium text-slate-300 transition hover:bg-white/5"
            >
              Sair
            </button>
          </div>
        </div>
      </header>

      <main className="relative mx-auto max-w-7xl px-6 py-6">
        <div className="mb-6">
          <StatsBar alerts={alerts} />
        </div>

        {lastUpdated === null ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="h-[420px] animate-pulse rounded-2xl border border-white/5 bg-slate-900/40"
              />
            ))}
          </div>
        ) : alerts.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-white/10 py-24 text-center">
            <InboxIcon className="h-10 w-10 text-slate-700" />
            <p className="mt-4 text-sm font-medium text-slate-400">Nenhum alerta recebido ainda</p>
            <p className="mt-1 text-xs text-slate-600">
              Alertas disparados pelo app do motorista aparecem aqui automaticamente.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {alerts.map((alert) => (
              <AlertCard key={alert.id} alert={alert} onAction={handleAction} />
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
