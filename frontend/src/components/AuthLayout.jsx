import Brand from './Brand.jsx';
import './auth.css';

function DeliveryIllustration() {
  return <svg className="delivery-illustration" viewBox="0 0 600 260" role="img" aria-label="Delivery vans connecting locations across the island">
    <defs><linearGradient id="night" x2="0" y2="1"><stop stopColor="#06275b"/><stop offset="1" stopColor="#071e44"/></linearGradient></defs>
    <path fill="url(#night)" d="M0 60 80 20 160 70 230 40 310 85 390 35 480 75 600 30V260H0Z"/>
    <g fill="#082650" stroke="#16477b" strokeWidth="2">
      <path d="M15 150v-45h22v45m15 0V88h24v62m18 0V69h23v81m31 0v-39h23v39m220 0V78h24v72m16 0v-45h26v45m15 0V64h24v86m24 0V95h20v55"/>
      <path d="M285 150l7-98h7l8 98m-24-98h27v-8h-27zm13-25v17M543 150l6-86h6l8 86m-20-86h25v-8h-25z"/>
    </g>
    <path d="M0 151h600M0 211q130-55 290 5t310 0" fill="none" stroke="#185080" strokeWidth="2"/>
    <path d="M190 115C340 60 430 170 318 213S160 203 226 176" fill="none" stroke="#1ba1e4" strokeWidth="2" strokeDasharray="6 5"/>
    {[ [184,104],[336,155],[311,205] ].map(([x,y]) => <g key={x} transform={`translate(${x} ${y})`} fill="#3b98da"><path d="M0 10C-24-18 24-18 0 10"/><circle cy="-5" r="3" fill="#09264d"/></g>)}
    {[[60,160],[422,166]].map(([x,y]) => <g key={x} transform={`translate(${x} ${y})`} stroke="#65a3d7" strokeWidth="1.7" fill="#09254b"><path d="M0 0h74v38H0zm74 8h14l15 19v11H74z"/><path d="M79 12h6l10 14H79z"/><circle cx="18" cy="38" r="8"/><circle cx="87" cy="38" r="8"/><path d="M20 12h31m-25 7h19" stroke="#168cff" strokeWidth="5"/></g>)}
    <g fill="#082450" stroke="#60a0d6" strokeWidth="1.5"><path d="m263 119 23-9 23 9v27l-23 10-23-10zm0 0 23 11 23-11m-23 11v26m-12-42 24 10v12"/><path d="m280 222 24-10 24 10v25l-24 11-24-11zm0 0 24 10 24-10m-24 10v26"/></g>
  </svg>;
}
export default function AuthLayout({ children }) {
  return <main className="auth-layout">
    <aside className="auth-story">
      <Brand />
      <div className="story-copy"><h1>The fastest way to<br className="desktop-break" /> send and track<br className="desktop-break" /> across the island.</h1>
        <p>Manage shipments, dispatch couriers,<br className="desktop-break" /> and monitor operations in one place.</p></div>
      <DeliveryIllustration />
    </aside>
    <section className="auth-main" aria-label="Customer registration">{children}</section>
  </main>;
}
