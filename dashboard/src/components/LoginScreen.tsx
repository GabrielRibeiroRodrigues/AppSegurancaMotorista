import { useState } from 'react';
import { login, type OperatorInfo } from '../auth';
import { ShieldIcon } from './icons';

export function LoginScreen({ onLoggedIn }: { onLoggedIn: (op: OperatorInfo) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const op = await login(username.trim(), password);
      onLoggedIn(op);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha ao entrar.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#05070d] px-6 text-slate-100">
      <form
        onSubmit={submit}
        className="w-full max-w-sm rounded-2xl border border-white/10 bg-slate-950/60 p-8 backdrop-blur"
      >
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-red-500/10 ring-1 ring-inset ring-red-500/30">
            <ShieldIcon className="h-5 w-5 text-red-400" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight text-white">Central de Operações</h1>
            <p className="text-xs text-slate-500">Acesso restrito a operadores</p>
          </div>
        </div>

        <label className="mb-1 block text-xs font-medium text-slate-400">Usuário</label>
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoFocus
          className="mb-4 w-full rounded-lg border border-white/10 bg-slate-900/60 px-3 py-2 text-sm outline-none focus:border-red-500/50"
        />

        <label className="mb-1 block text-xs font-medium text-slate-400">Senha</label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="mb-4 w-full rounded-lg border border-white/10 bg-slate-900/60 px-3 py-2 text-sm outline-none focus:border-red-500/50"
        />

        {error && (
          <p className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs text-red-300">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={busy || !username || !password}
          className="w-full rounded-lg bg-red-600 py-2.5 text-sm font-semibold text-white transition hover:bg-red-500 disabled:opacity-40"
        >
          {busy ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
