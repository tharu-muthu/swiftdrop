import { useEffect, useState } from 'react';

export default function App() {
  const [state, setState] = useState('loading');
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    const timeout = setTimeout(() => controller.abort(), 8000);

    async function checkHealth() {
      setState('loading');
      try {
        const response = await fetch('/api/v1/health', { signal: controller.signal });
        if (!response.ok) throw new Error('Health request failed');
        const health = await response.json();
        if (health.status !== 'UP' || health.application !== 'swiftdrop') {
          throw new Error('Unexpected health response');
        }
        if (active) setState('online');
      } catch {
        if (active) setState('offline');
      } finally {
        clearTimeout(timeout);
      }
    }

    checkHealth();
    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
    };
  }, [attempt]);

  const status = {
    loading: 'Checking backend…',
    online: 'Backend connected',
    offline: 'Backend unavailable',
  }[state];

  return (
    <main className="shell">
      <header className="brand" aria-label="SwiftDrop">Swift<span>Drop</span></header>
      <section className="intro" aria-labelledby="page-title">
        <p className="eyebrow">University courier management project</p>
        <h1 id="page-title">One foundation.<br />Built together.</h1>
        <p className="description">The shared starting point for our nine-member team.</p>
      </section>
      <section className="connection" aria-labelledby="connection-title">
        <p className="eyebrow">Project foundation</p>
        <h2 id="connection-title">Connection check</h2>
        <p className={`status ${state}`} role="status" aria-live="polite">
          <span className="dot" aria-hidden="true" />{status}
        </p>
        <p className="hint">
          {state === 'online'
            ? 'The React page can reach the SwiftDrop health endpoint.'
            : state === 'loading'
              ? 'Waiting for the health endpoint to respond.'
              : 'Start the backend and check your local setup, then try again.'}
        </p>
        <button disabled={state === 'loading'} onClick={() => setAttempt(value => value + 1)}>
          Check again
        </button>
      </section>
      <footer>Foundation only. Courier workflows and sign-in will be added in later milestones.</footer>
    </main>
  );
}
