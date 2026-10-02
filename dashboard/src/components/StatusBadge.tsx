import type { AlertStatus } from '@panic/shared';

const LABELS: Record<AlertStatus, string> = {
  ativo: 'Ativo',
  em_atendimento: 'Em atendimento',
  encerrado: 'Encerrado',
};

const STYLES: Record<AlertStatus, string> = {
  ativo: 'bg-red-500/10 text-red-300 ring-1 ring-inset ring-red-500/30',
  em_atendimento: 'bg-amber-500/10 text-amber-300 ring-1 ring-inset ring-amber-500/30',
  encerrado: 'bg-slate-500/10 text-slate-400 ring-1 ring-inset ring-slate-500/20',
};

const DOT_STYLES: Record<AlertStatus, string> = {
  ativo: 'bg-red-500',
  em_atendimento: 'bg-amber-400',
  encerrado: 'bg-slate-500',
};

export function StatusBadge({ status }: { status: AlertStatus }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide ${STYLES[status]}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${DOT_STYLES[status]} ${status === 'ativo' ? 'pulse-live' : ''}`} />
      {LABELS[status]}
    </span>
  );
}
