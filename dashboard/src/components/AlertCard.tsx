import { useState } from 'react';
import type { Alert, OperatorActionType } from '@panic/shared';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { StatusBadge } from './StatusBadge';
import { VideoFeed } from './VideoFeed';
import { useElapsedTime } from '../useElapsedTime';
import { getMockTripInfo } from '../mockTripInfo';
import {
  BanIcon,
  ClockIcon,
  MapPinIcon,
  PhoneCallIcon,
  QuoteIcon,
  SirenIcon,
  VideoIcon,
  VideoOffIcon,
} from './icons';

interface AlertCardProps {
  alert: Alert;
  onAction: (alertId: string, action: OperatorActionType) => void;
}

const ACCENT_BY_STATUS: Record<Alert['status'], string> = {
  ativo: 'bg-red-500',
  em_atendimento: 'bg-amber-400',
  encerrado: 'bg-slate-600',
};

const AVATAR_RING_BY_STATUS: Record<Alert['status'], string> = {
  ativo: 'ring-red-500/40 text-red-300',
  em_atendimento: 'ring-amber-400/40 text-amber-300',
  encerrado: 'ring-slate-600/40 text-slate-400',
};

const CARD_GLOW_BY_STATUS: Record<Alert['status'], string> = {
  ativo: 'ring-1 ring-red-500/30 alert-glow',
  em_atendimento: 'ring-1 ring-amber-400/20',
  encerrado: '',
};

const PIN_COLOR_BY_STATUS: Record<Alert['status'], string> = {
  ativo: '#ef4444',
  em_atendimento: '#f59e0b',
  encerrado: '#64748b',
};

function statusPinIcon(status: Alert['status'], isTest: boolean): L.DivIcon {
  const color = isTest ? '#818cf8' : PIN_COLOR_BY_STATUS[status];
  return L.divIcon({
    className: '',
    html: `
      <svg width="30" height="40" viewBox="0 0 30 40" style="filter:drop-shadow(0 2px 4px rgba(0,0,0,0.5))">
        <path d="M15 0C6.7 0 0 6.7 0 15c0 10.5 15 25 15 25s15-14.5 15-25C30 6.7 23.3 0 15 0Z" fill="${color}"/>
        <circle cx="15" cy="15" r="6" fill="#0b0e1a"/>
      </svg>`,
    iconSize: [30, 40],
    iconAnchor: [15, 38],
  });
}

export function AlertCard({ alert, onAction }: AlertCardProps) {
  const elapsed = useElapsedTime(alert.timestamp);
  const [showVideo, setShowVideo] = useState(false);
  const trip = getMockTripInfo(alert.id);

  // Alertas de teste nunca usam o esquema de cor/urgência (vermelho
  // pulsante) dos reais — um operador precisa distinguir os dois à
  // primeira vista, sem ler a transcrição.
  const accent = alert.isTest ? 'bg-indigo-400' : ACCENT_BY_STATUS[alert.status];
  const avatarRing = alert.isTest ? 'ring-indigo-400/30 text-indigo-300' : AVATAR_RING_BY_STATUS[alert.status];
  const cardGlow = alert.isTest ? 'ring-1 ring-indigo-400/20' : CARD_GLOW_BY_STATUS[alert.status];

  return (
    <div
      className={`card-enter group relative flex h-full flex-col overflow-hidden rounded-2xl border border-white/5 bg-slate-900/70 shadow-[0_10px_15px_-3px_rgba(0,0,0,0.3),0_4px_6px_-4px_rgba(0,0,0,0.3),inset_0_1px_0_0_rgba(255,255,255,0.04)] transition-colors hover:border-white/10 ${cardGlow}`}
    >
      <span className={`absolute inset-y-0 left-0 w-1 ${accent}`} />

      <div className="flex flex-1 flex-col p-4 pl-5">
        <div className="mb-3 flex items-start justify-between gap-2">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-slate-700 to-slate-900 ring-2 ${avatarRing}`}
            >
              <SirenIcon className="h-5 w-5" />
            </div>
            <div>
              <h2 className="flex items-center gap-1.5 font-semibold leading-tight text-white">
                {alert.driverId}
                {alert.isTest && (
                  <span className="rounded bg-indigo-500/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-indigo-300">
                    Teste
                  </span>
                )}
                {alert.origin === 'BOTAO_PANICO' && (
                  <span className="rounded bg-red-500/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-red-300">
                    Botão físico
                  </span>
                )}
                {alert.origin === 'VOZ' && (
                  <span className="rounded bg-amber-500/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-300">
                    Voz
                  </span>
                )}
              </h2>
              <p className="mt-0.5 flex items-center gap-1 text-xs text-slate-500">
                <ClockIcon className="h-3.5 w-3.5" />
                <span className="font-mono">{elapsed}</span>
              </p>
            </div>
          </div>
          <StatusBadge status={alert.status} />
        </div>

        <div className="mb-3 flex items-center gap-2.5 rounded-lg border border-white/5 bg-white/5 px-3 py-2 text-xs text-slate-400">
          <img
            src={trip.photoUrl}
            alt=""
            width={28}
            height={28}
            className="h-7 w-7 shrink-0 rounded-full object-cover ring-1 ring-white/10"
          />
          <div className="min-w-0 flex-1">
            <span className="block truncate font-medium text-slate-300">{trip.passenger}</span>
            <span className="flex items-center gap-1 truncate">
              <MapPinIcon className="h-3 w-3 shrink-0 text-slate-500" />
              <span className="truncate">
                {trip.origin} <span className="text-slate-600">→</span> {trip.destination}
              </span>
            </span>
          </div>
        </div>

        <div className="mb-3 h-40 overflow-hidden rounded-xl ring-1 ring-white/5">
          <MapContainer
            center={[alert.location.lat, alert.location.lng]}
            zoom={15}
            scrollWheelZoom={false}
            dragging={false}
            zoomControl={false}
            attributionControl={false}
            style={{ height: '100%', width: '100%' }}
          >
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
            <Marker position={[alert.location.lat, alert.location.lng]} icon={statusPinIcon(alert.status, alert.isTest)}>
              <Popup>{alert.driverId}</Popup>
            </Marker>
          </MapContainer>
        </div>

        {alert.status !== 'encerrado' && (
          <button
            onClick={() => setShowVideo((v) => !v)}
            className={`press mb-3 flex items-center justify-center gap-1.5 rounded-lg border px-2 py-2 text-[11px] font-medium transition-colors ${
              showVideo
                ? 'border-red-500/30 bg-red-500/10 text-red-300 hover:bg-red-500/15'
                : 'border-white/10 bg-white/5 text-slate-300 hover:bg-white/10 hover:text-white'
            }`}
          >
            {showVideo ? <VideoOffIcon className="h-3.5 w-3.5" /> : <VideoIcon className="h-3.5 w-3.5" />}
            {showVideo ? 'Ocultar câmera' : 'Ver câmera ao vivo'}
          </button>
        )}

        {showVideo && (
          <div className="mb-3">
            <VideoFeed alertId={alert.id} />
          </div>
        )}

        <div className="relative mb-4 flex-1 rounded-xl border border-white/5 bg-black/20 p-3 pl-8 shadow-[inset_0_1px_0_0_rgba(255,255,255,0.03)]">
          <QuoteIcon className="absolute left-2.5 top-3 h-3.5 w-3.5 text-slate-600" />
          <p className="text-[10px] font-medium uppercase tracking-wider text-slate-500">Transcrição capturada</p>
          <p className="mt-0.5 text-sm italic leading-snug text-slate-200">{alert.transcript}</p>
        </div>

        {alert.status !== 'encerrado' ? (
          <div className="mt-auto grid grid-cols-3 gap-2">
            <button
              onClick={() => onAction(alert.id, 'acionar_policia')}
              className="press flex flex-col items-center gap-1 rounded-lg bg-red-600 px-2 py-2 text-[11px] font-medium text-white shadow-[0_4px_14px_-4px_rgba(220,38,38,0.6)] transition-colors hover:bg-red-500 active:bg-red-700"
            >
              <SirenIcon className="h-4 w-4" />
              Polícia
            </button>
            <button
              onClick={() => onAction(alert.id, 'contato_ativo')}
              className="press flex flex-col items-center gap-1 rounded-lg bg-amber-600 px-2 py-2 text-[11px] font-medium text-white shadow-[0_4px_14px_-4px_rgba(217,119,6,0.6)] transition-colors hover:bg-amber-500 active:bg-amber-700"
            >
              <PhoneCallIcon className="h-4 w-4" />
              Contato
            </button>
            <button
              onClick={() => onAction(alert.id, 'falso_positivo')}
              className="press flex flex-col items-center gap-1 rounded-lg border border-white/10 bg-white/5 px-2 py-2 text-[11px] font-medium text-slate-300 transition-colors hover:bg-white/10 hover:text-white"
            >
              <BanIcon className="h-4 w-4" />
              Falso +
            </button>
          </div>
        ) : (
          <p className="mt-auto text-center text-xs text-slate-600">Ocorrência encerrada</p>
        )}
      </div>
    </div>
  );
}
