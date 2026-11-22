import './brand.css';

export default function Brand({ compact = false }) {
  return <div className={`identity-brand${compact ? ' identity-brand-compact' : ''}`} aria-label="SwiftDrop">
    <svg viewBox="0 0 54 52" aria-hidden="true"><path fill="currentColor" d="M4 4h48l-10 10H22l13 8h14l-10 10H28l14 8-18 8L6 38l9-9 13 7 7-3-14-8H10L4 15h19L4 4z"/></svg>
    {!compact && <span>Swift<span className="brand-orange">Drop</span></span>}
  </div>;
}
