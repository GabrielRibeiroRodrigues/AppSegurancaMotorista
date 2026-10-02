import type { Alert } from '@panic/shared';
import { InboxIcon, RadioIcon, PhoneCallIcon, BanIcon } from './icons';

interface StatsBarProps {
  alerts: Alert[];
}

export function StatsBar({ alerts }: StatsBarProps) {
  // Alertas de teste nunca contam como "Ativos" — senão um clique no botão
  // de teste infla o número que mais importa pro operador de relance.
  const reais = alerts.filter((a) => !a.isTest);
  const ativos = reais.filter((a) => a.status === 'ativo').length;
  const emAtendimento = reais.filter((a) => a.status === 'em_atendimento').length;
  const encerrados = reais.filter((a) => a.status === 'encerrado').length;
  const testes = alerts.filter((a) => a.isTest).length;

  const stats = [
    { label: 'Total', value: alerts.length, icon: InboxIcon, tone: 'text-slate-300', chip: 'bg-slate-500/10' },
    { label: 'Ativos', value: ativos, icon: RadioIcon, tone: 'text-red-400', chip: 'bg-red-500/10' },
    { label: 'Em atendimento', value: emAtendimento, icon: PhoneCallIcon, tone: 'text-amber-400', chip: 'bg-amber-400/10' },
    { label: 'Encerrados', value: encerrados, icon: BanIcon, tone: 'text-slate-500', chip: 'bg-slate-500/10' },
    { label: 'Testes', value: testes, icon: RadioIcon, tone: 'text-indigo-400', chip: 'bg-indigo-500/10' },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {stats.map(({ label, value, icon: Icon, tone, chip }) => (
        <div
          key={label}
          className="flex items-center gap-3 rounded-xl border border-white/5 bg-slate-900/60 px-4 py-3.5 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.04)] transition-colors hover:border-white/10"
        >
          <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${chip}`}>
            <Icon className={`h-5 w-5 ${tone}`} />
          </div>
          <div>
            <p className="font-mono text-xl font-semibold leading-none text-white">{value}</p>
            <p className="mt-1 text-[11px] uppercase tracking-wide text-slate-500">{label}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
