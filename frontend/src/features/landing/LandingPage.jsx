import { useState } from 'react';
import Brand from '../../components/Brand.jsx';
import AppLink from '../../components/AppLink.jsx';
import { navigate } from '../../app/navigation.js';
import van from '../../assets/booking-hero-van.png';
import './landing.css';

const audiences = [
  { icon: 'M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10 M7.5 5l9 4', title: 'For customers', text: 'Estimate delivery fees, book parcels, follow every status update, review booking history and download receipts.' },
  { icon: 'M3 9h18l-2-5H5z M5 9v11h14V9 M10 20v-6h4v6', title: 'For merchants', text: 'Create business orders from a dashboard or secured API, then receive signed webhook updates as deliveries progress.' },
  { icon: 'M3 6h11v11H8 M14 10h4l3 3v4h-2 M14 17h1 M3 17h1 M8 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0 M19 17a2 2 0 1 1-4 0 2 2 0 0 1 4 0', title: 'For couriers', text: 'See assigned deliveries for the day, record each delivery stage and capture recipient or failure details.' },
  { icon: 'M8 5H5v16h14V5h-3 M8 3h8v5H8z M8 13l1 1 2-2 M13 13h3 M8 17l1 1 2-2 M13 17h3', title: 'For dispatch teams', text: 'Assign unallocated parcels, monitor courier workloads, manage delivery zones and review daily operations.' },
];

function LandingIcon({ path }) {
  return <svg className="landing-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={path} /></svg>;
}

const deliveryStages = ['Created', 'Picked up', 'In transit', 'Out for delivery', 'Delivered'];

export default function LandingPage() {
  const [number, setNumber] = useState('');
  return <div className="landing">
    <a className="landing-skip" href="#track-package">Skip to tracking</a>
    <header className="landing-header">
      <AppLink href="/" aria-label="SwiftDrop home"><Brand /></AppLink>
      <nav aria-label="Main navigation"><AppLink className="landing-button" href="/login">Sign in</AppLink></nav>
    </header>
    <main>
      <section className="landing-hero">
        <div className="landing-hero-copy">
          <p className="landing-eyebrow">One connected delivery platform</p>
          <h1>Send smarter.<br />Deliver clearly.<br /><span>Stay connected.</span></h1>
          <p className="landing-intro">SwiftDrop brings customers, merchants, couriers and dispatch teams into one reliable parcel journey—from a clear price estimate to the final delivery update.</p>
        </div>
        <div className="landing-art">
          <div className="landing-orbit" />
          <img src={van} alt="SwiftDrop delivery van" />
          <div className="landing-art-note"><span><LandingIcon path="M12 3l8 3v5c0 5-3 8-8 10-5-2-8-5-8-10V6z M8 12l3 3 5-5" /></span><div><strong>Every delivery, connected</strong><small>Booking, dispatch, progress and proof in one system.</small></div></div>
        </div>
      </section>

      <section className="landing-tracking" id="track-package" aria-labelledby="tracking-title">
        <div><p className="landing-eyebrow">Public parcel tracking</p><h2 id="tracking-title">Find your delivery.</h2><p>No account needed. Just your tracking number.</p></div>
        <form onSubmit={event => { event.preventDefault(); if (number.trim()) navigate(`/tracking/${encodeURIComponent(number.trim())}`); }}>
          <label htmlFor="landing-tracking-number">Tracking number</label>
          <div className="landing-track-input"><input id="landing-tracking-number" value={number} onChange={event => setNumber(event.target.value)} required pattern=".*\S.*" maxLength={40} placeholder="e.g. SDP-A8K4P2M7Q9" autoComplete="off" /><button className="landing-button" type="submit">Track parcel</button></div>
          <small>You’ll find it in your booking details or the information shared by your sender.</small>
        </form>
      </section>

      <section className="landing-platform" aria-labelledby="platform-title">
        <div className="landing-section-heading"><p className="landing-eyebrow">Built around the whole operation</p><h2 id="platform-title">One system for everyone involved.</h2><p>Each person gets the tools and information relevant to their role, while the delivery stays connected from end to end.</p></div>
        <div className="landing-audience-grid">{audiences.map(item => <article key={item.title}><span><LandingIcon path={item.icon} /></span><h3>{item.title}</h3><p>{item.text}</p></article>)}</div>
      </section>

      <section className="landing-journey" aria-labelledby="journey-title">
        <div><p className="landing-eyebrow">From request to doorstep</p><h2 id="journey-title">A delivery journey everyone can understand.</h2><p>Customers and merchants create orders, dispatchers assign suitable couriers, and each courier update becomes a live tracking event. Status changes can also trigger notifications and merchant webhooks.</p></div>
        <ol aria-label="Parcel delivery stages">{deliveryStages.map((stage, index) => <li key={stage}><span>{index + 1}</span><strong>{stage}</strong></li>)}</ol>
      </section>

      <section className="landing-connected" aria-labelledby="connected-title">
        <div className="landing-connected-copy"><p className="landing-eyebrow">Designed for real delivery work</p><h2 id="connected-title">Clear updates. Fewer blind spots.</h2><p>SwiftDrop keeps operational actions and customer visibility connected, so parcels do not disappear into phone calls and spreadsheets.</p></div>
        <div className="landing-connected-points"><article><strong>Live parcel timeline</strong><p>Public tracking reflects recorded delivery progress in real time.</p></article><article><strong>Asynchronous updates</strong><p>Status events drive mock SMS notifications and merchant webhook delivery.</p></article><article><strong>Role-based access</strong><p>Customer, merchant, courier and dispatcher portals expose the tools each role needs.</p></article></div>
      </section>

      <section className="landing-faq"><h2>A few helpful answers.</h2><details><summary>Do I need to sign in to track a parcel?</summary><p>No. Enter your tracking number above to view its public delivery status and timeline.</p></details><details><summary>I don’t have a tracking number. What should I do?</summary><p>Ask the sender or merchant for your SwiftDrop tracking number. If you booked the delivery, sign in and open My bookings.</p></details><details><summary>Can a business create orders automatically?</summary><p>Approved merchants can create orders through secured APIs and subscribe to signed parcel-status webhooks.</p></details><details><summary>Why hasn’t the status changed?</summary><p>The timeline updates when a new delivery status is recorded. If you need help with a delay or failed delivery, contact your sender or merchant with the tracking number.</p></details><details><summary>How much does sending a parcel cost?</summary><p>Sign in to enter pickup and delivery postal codes, parcel weight and size. You can review the estimate before confirming your booking.</p></details></section>
    </main>
    <footer className="landing-footer"><Brand /><p>Every delivery, connected.</p></footer>
  </div>;
}
