import { useEffect, useState } from 'react';
import './refresh-control.css';

export default function RefreshControl({ lastRefreshed, loading, disabled, onRefresh, label = 'Refresh' }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    setNow(Date.now());
    if (!lastRefreshed) return;
    const timer = window.setInterval(() => setNow(Date.now()), 30000);
    return () => window.clearInterval(timer);
  }, [lastRefreshed]);
  const minutes = Math.max(0, Math.floor((now - lastRefreshed) / 60000));
  const age = minutes < 1 ? 'just now' : minutes < 60
    ? `${minutes} min ago` : `${Math.floor(minutes / 60)} hr ago`;

  return <div className="refresh-control">
    <span className="refresh-control-label" title={lastRefreshed ? new Date(lastRefreshed).toLocaleString() : undefined}>
      {loading ? 'Refreshing…' : lastRefreshed ? `Last refreshed ${age}` : 'Not refreshed yet'}
    </span>
    <button type="button" className="refresh-control-button" aria-label={label} title={label}
      aria-busy={loading} disabled={disabled || loading} onClick={onRefresh}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7v5h-5 M20 12a8 8 0 1 0-2.3 5.7 M20 12a8 8 0 0 0-2.3-5.7" /></svg>
    </button>
  </div>;
}
