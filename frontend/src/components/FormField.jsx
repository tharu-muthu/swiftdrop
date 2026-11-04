import { useState } from 'react';

export default function FormField({ name, label, error, hint, type = 'text', ...props }) {
  const [visible, setVisible] = useState(false);
  const password = type === 'password';
  return <div className="form-field">
    <label htmlFor={name}>{label}</label>
    <div className={password ? 'password-input' : undefined}>
      <input id={name} name={name} type={password && visible ? 'text' : type}
        aria-invalid={Boolean(error)} aria-describedby={[hint && name + '-hint', error && name + '-error'].filter(Boolean).join(' ') || undefined}
        {...props} />
      {password && <button className="visibility-button" type="button" aria-label={`${visible ? 'Hide' : 'Show'} ${label.toLowerCase()}`}
        aria-pressed={visible} aria-controls={name} disabled={props.disabled} onClick={() => setVisible(value => !value)}>
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.8"
          strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
          <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
          <circle cx="12" cy="12" r="3" />
          {visible && <path d="m3 3 18 18" />}
        </svg>
      </button>}
    </div>
    {hint && <p className="field-hint" id={name + '-hint'}>{hint}</p>}
    {error && <p className="field-error" id={name + '-error'}>{error}</p>}
  </div>;
}
