import { useEffect, useState } from 'react';

export function useElapsedTime(isoTimestamp: string): string {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, []);

  const seconds = Math.max(0, Math.floor((now - new Date(isoTimestamp).getTime()) / 1000));
  const minutes = Math.floor(seconds / 60);
  const remSeconds = seconds % 60;
  return `${minutes}m ${remSeconds.toString().padStart(2, '0')}s`;
}
