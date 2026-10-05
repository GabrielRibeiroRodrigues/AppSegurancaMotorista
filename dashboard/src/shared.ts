export type AlertStatus = 'ativo' | 'em_atendimento' | 'encerrado';
export type OperatorActionType = 'acionar_policia' | 'contato_ativo' | 'falso_positivo';
/** Como o alerta foi disparado. */
export type AlertOrigin = 'APP' | 'VOZ' | 'BOTAO_PANICO' | 'TESTE';

export interface LatLng {
  lat: number;
  lng: number;
}

export interface Alert {
  id: string;
  driverId: string;
  timestamp: string; // ISO — quando o motorista disparou o alerta (enviado pelo app)
  receivedAt: string; // ISO — quando o backend recebeu o alerta
  location: LatLng;
  transcript: string;
  status: AlertStatus;
  operatorAction: OperatorActionType | null;
  // Como o alerta foi disparado (voz, botão físico ESP32, etc.).
  origin?: AlertOrigin;
  // Disparado pelo botão de teste (driver-pwa ou dashboard), não por uma
  // detecção de voz real nem por um alerta manual de verdade — a Central de
  // Operações precisa deixar isso visualmente claro pro operador nunca
  // confundir um teste com uma emergência real.
  isTest: boolean;
}

export interface CreateAlertPayload {
  driverId: string;
  timestamp: string;
  location: LatLng;
  transcript: string;
  status: 'ativo';
  isTest?: boolean;
}

export interface UpdateAlertPayload {
  operatorAction: OperatorActionType;
}
