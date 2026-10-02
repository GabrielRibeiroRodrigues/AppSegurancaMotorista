// Beep sintetizado via Web Audio API — sem depender de nenhum arquivo de
// áudio. Dois tons descendentes (880Hz → 660Hz), curto e reconhecível como
// "alerta chegou" sem ser agressivo.
export function playAlertSound(): void {
  try {
    const Ctx = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    const ctx = new Ctx();
    const now = ctx.currentTime;

    [880, 660].forEach((freq, i) => {
      const start = now + i * 0.15;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.3, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.14);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(start);
      osc.stop(start + 0.15);
    });

    window.setTimeout(() => ctx.close(), 500);
  } catch {
    // AudioContext indisponível ou bloqueado (política de autoplay antes de
    // qualquer interação do usuário na página) — silencioso, não é crítico.
  }
}
