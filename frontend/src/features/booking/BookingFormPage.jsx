import { useState } from 'react';
import { navigate } from '../../app/navigation.js';
import { useAuth } from '../identity/AuthProvider.jsx';
import BookingFrame from './BookingFrame.jsx';
import { createQuotePreparationRequest } from './bookingFormApi.js';
import './booking-form.css';
import './booking-brand-theme.css';

const steps = [
  ['Pickup Information', 'Sender details and pickup address'],
  ['Recipient Information', 'Receiver details and delivery address'],
  ['Parcel Information', 'Package weight, size and description'],
  ['Get Quote', 'Price and confirmation'],
];

const fields = {
  1: [['senderName', 'Full name', 'text', 'Enter sender name'], ['senderPhone', 'Contact number', 'tel', '0771234567'], ['pickupAddress', 'Pickup address', 'text', 'House number, street and area'], ['pickupCity', 'City / Town', 'text', 'Colombo'], ['pickupPostalCode', 'Postal code', 'text', '00600']],
  2: [['recipientName', 'Full name', 'text', 'Enter recipient name'], ['recipientPhone', 'Contact number', 'tel', '0771234567'], ['deliveryAddress', 'Delivery address', 'text', 'House number, street and area'], ['deliveryCity', 'City / Town', 'text', 'Colombo'], ['deliveryPostalCode', 'Postal code', 'text', '00600']],
};

function labelFor(name) { return name.replace(/([A-Z])/g, ' $1').replace(/^./, letter => letter.toUpperCase()); }
function errorsFor(step, draft) {
  const next = {};
  const phone = /^(?:\+94|0)7\d{8}$/;
  const postal = /^\d{5}$/;
  if (step === 1 || step === 2) {
    const prefix = step === 1 ? 'sender' : 'recipient';
    const address = step === 1 ? 'pickupAddress' : 'deliveryAddress';
    const city = step === 1 ? 'pickupCity' : 'deliveryCity';
    const post = step === 1 ? 'pickupPostalCode' : 'deliveryPostalCode';
    for (const key of [`${prefix}Name`, address, city, post]) if (!draft[key].trim()) next[key] = `${labelFor(key)} is required.`;
    if (draft[`${prefix}Phone`] && !phone.test(draft[`${prefix}Phone`].replaceAll(' ', ''))) next[`${prefix}Phone`] = 'Enter a valid Sri Lankan mobile number, e.g. 0771234567.';
    if (draft[post] && !postal.test(draft[post])) next[post] = 'Enter a five-digit postal code.';
  }
  if (step === 3) {
    const weight = Number(draft.weightGrams);
    if (!draft.weightGrams || !Number.isInteger(weight) || weight <= 0) next.weightGrams = 'Enter a whole weight in grams greater than zero.';
    if (!draft.parcelSize) next.parcelSize = 'Choose a parcel size.';
    if (!draft.description.trim()) next.description = 'Describe the parcel contents.';
  }
  return next;
}

function TextField({ name, label, type, placeholder, draft, errors, update }) {
  const id = `booking-${name}`;
  return <div className={`booking-field ${name.includes('Address') ? 'field-wide' : ''}`}><label htmlFor={id}>{label} <b>*</b></label>
    <input id={id} type={type} inputMode={type === 'tel' || name.includes('Postal') ? 'numeric' : undefined} value={draft[name]} maxLength={name.includes('Address') ? 500 : name.includes('Name') ? 120 : 32} placeholder={placeholder} onChange={event => update(name, event.target.value)} aria-invalid={Boolean(errors[name])} aria-describedby={errors[name] ? `${id}-error` : undefined} />
    {errors[name] && <small id={`${id}-error`} className="field-error">{errors[name]}</small>}</div>;
}

function addressLine(address, city, postalCode) {
  return [address, city, postalCode].filter(Boolean).join(', ');
}

function QuoteReadiness({ draft }) {
  const quoteRequest = createQuotePreparationRequest(draft);
  const parcelSize = quoteRequest.parcel.size.replaceAll('_', ' ').toLowerCase();

  return <section className="quote-ready" aria-labelledby="quote-ready-title">
    <div className="quote-ready-icon" aria-hidden="true">◇</div>
    <p className="quote-eyebrow">Booking details reviewed</p>
    <h3 id="quote-ready-title">Ready to request a delivery quote</h3>
    <p>M3 owns delivery-rate calculation and the quotation API. Your booking has not been created and no payment will be requested until M3 returns an accepted quote.</p>
    <div className="booking-review" aria-label="Booking details for quote">
      <section>
        <h4>Pickup</h4>
        <strong>{quoteRequest.pickup.fullName}</strong>
        <span>{quoteRequest.pickup.contactNumber}</span>
        <span>{addressLine(quoteRequest.pickup.address, quoteRequest.pickup.city, quoteRequest.pickup.postalCode)}</span>
      </section>
      <section>
        <h4>Recipient</h4>
        <strong>{quoteRequest.recipient.fullName}</strong>
        <span>{quoteRequest.recipient.contactNumber}</span>
        <span>{addressLine(quoteRequest.recipient.address, quoteRequest.recipient.city, quoteRequest.recipient.postalCode)}</span>
      </section>
      <section className="review-parcel">
        <h4>Parcel</h4>
        <strong>{quoteRequest.parcel.weightGrams.toLocaleString()} g · {parcelSize}</strong>
        <span>{quoteRequest.parcel.description}</span>
      </section>
    </div>
    <div className="quote-integration-status" id="quote-service-note" role="status">
      <strong>Quotation service pending</strong>
      <span>The validated request is ready for the M3 integration. A booking confirmation will be available only after M3 returns an accepted quote.</span>
    </div>
  </section>;
}

export default function BookingFormPage() {
  const { user } = useAuth();
  const [step, setStep] = useState(1);
  const [errors, setErrors] = useState({});
  const [draft, setDraft] = useState(() => ({
    senderName: user.fullName || '', senderPhone: user.phone || '', pickupAddress: '', pickupCity: '', pickupPostalCode: '',
    recipientName: '', recipientPhone: '', deliveryAddress: '', deliveryCity: '', deliveryPostalCode: '',
    weightGrams: '', parcelSize: '', description: '',
  }));
  const update = (name, value) => { setDraft(current => ({ ...current, [name]: value })); setErrors(current => ({ ...current, [name]: '' })); };
  const advance = event => {
    event.preventDefault();
    const invalid = errorsFor(step, draft);
    setErrors(invalid);
    if (Object.keys(invalid).length) return;
    setStep(current => Math.min(4, current + 1));
  };
  const back = () => { setErrors({}); setStep(current => Math.max(1, current - 1)); };

  return <BookingFrame activePath="/bookings/new"><div className="booking-form-page">
    <section className="form-banner"><div><p>Book Parcel <span>›</span> Create Booking</p><h1>Create Parcel Booking</h1><span>Fill in the details below to book your parcel delivery.</span></div><div className="form-banner-art" aria-hidden="true">▱</div></section>
    <ol className="progress-steps" aria-label="Booking progress">{steps.map(([title, subtitle], index) => <li key={title} className={step === index + 1 ? 'current' : step > index + 1 ? 'complete' : ''}><span>{index + 1}</span><div><strong>{title}</strong><small>{subtitle}</small></div></li>)}</ol>
    <div className="booking-form-grid"><form className="booking-form-card" onSubmit={advance} noValidate>
      <div className="form-section-heading"><span aria-hidden="true">{step === 1 ? '⌖' : step === 2 ? '♙' : step === 3 ? '□' : '◇'}</span><div><h2>{steps[step - 1][0]}</h2><p>{steps[step - 1][1]}</p></div></div>
      {step < 3 && <div className="booking-field-grid">{fields[step].map(([name, label, type, placeholder]) => <TextField key={name} name={name} label={label} type={type} placeholder={placeholder} draft={draft} errors={errors} update={update} />)}</div>}
      {step === 3 && <div className="booking-field-grid"><TextField name="weightGrams" label="Weight in grams" type="number" placeholder="e.g. 1500" draft={draft} errors={errors} update={update} />
        <div className="booking-field"><label htmlFor="booking-parcelSize">Parcel size <b>*</b></label><select id="booking-parcelSize" value={draft.parcelSize} onChange={event => update('parcelSize', event.target.value)} aria-invalid={Boolean(errors.parcelSize)}><option value="">Select size</option><option value="SMALL">Small</option><option value="MEDIUM">Medium</option><option value="LARGE">Large</option><option value="EXTRA_LARGE">Extra large</option></select>{errors.parcelSize && <small className="field-error">{errors.parcelSize}</small>}</div>
        <div className="booking-field field-wide"><label htmlFor="booking-description">Parcel description <b>*</b></label><textarea id="booking-description" value={draft.description} maxLength="200" placeholder="Describe parcel contents (no prohibited items)." onChange={event => update('description', event.target.value)} aria-invalid={Boolean(errors.description)} />{errors.description && <small className="field-error">{errors.description}</small>}</div></div>}
      {step === 4 && <QuoteReadiness draft={draft} />}
      <div className="form-actions"><button type="button" className="cancel-booking" onClick={() => navigate('/booking')}>← Cancel</button><span />{step > 1 && <button type="button" className="back-step" onClick={back}>← Back</button>}{step < 4 ? <button className="next-step">Next: {steps[step][0]} →</button> : <button type="button" className="next-step" disabled aria-describedby="quote-service-note">Quote service pending</button>}</div>
    </form>
    <aside className="booking-steps-card"><h2>▱ Booking Steps</h2><ol>{steps.map(([title, subtitle], index) => <li key={title} className={step === index + 1 ? 'active-step' : ''}><span>{index + 1}</span><div><strong>{title}</strong><small>{subtitle}</small></div></li>)}</ol><div className="quick-tips"><h3>☼ Quick Tips</h3><ul><li>Use a reachable contact number.</li><li>Provide a complete pickup address.</li><li>Check all details before getting a quote.</li></ul></div></aside>
    </div>
  </div></BookingFrame>;
}
