import { useEffect, useState } from 'react';
import { useAuth } from '../identity/AuthProvider.jsx';
import WorkspacePage, { JsonRequest } from '../../components/WorkspacePage.jsx';
import './pricing-admin.css';

const defaults = {
  pickupZoneId: '',
  dropoffZoneId: '',
  baseMinor: 0,
  includedKg: 1,
  extraKgMinor: 0,
  sizeSurchargeSmall: 0,
  sizeSurchargeMedium: 0,
  sizeSurchargeLarge: 0,
  sizeSurchargeExtraLarge: 0,
  effectiveFrom: '',
};

function formatLkr(minor) {
  const value = Number(minor) || 0;
  return new Intl.NumberFormat('en-LK', {
    style: 'currency',
    currency: 'LKR',
    minimumFractionDigits: 2,
  }).format(value / 100);
}

export default function PricingAdminPage() {
  const { client } = useAuth();
  const [zones, setZones] = useState([]);
  const [rates, setRates] = useState([]);
  const [postcodes, setPostcodes] = useState([]);
  const [name, setName] = useState('');
  const [postcode, setPostcode] = useState('');
  const [zoneId, setZoneId] = useState('');
  const [rate, setRate] = useState(defaults);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  async function load() {
    try {
      const [z, r, p] = await Promise.all([
        client.request('/api/v1/admin/zones'),
        client.request('/api/v1/admin/rate-cards'),
        client.request('/api/v1/admin/postcodes'),
      ]);
      setZones(z);
      setRates(r);
      setPostcodes(p);
    } catch (failure) {
      setError(failure.message);
    }
  }

  useEffect(() => {
    load();
  }, [client]);

  async function action(work) {
    setPending(true);
    setError('');
    try {
      await work();
      await load();
    } catch (failure) {
      setError(failure.message);
    } finally {
      setPending(false);
    }
  }

  const zoneName = id => zones.find(zone => zone.zoneId === id)?.name || id;

  function handleRateSubmit(event) {
    event.preventDefault();
    const body = Object.fromEntries(
      Object.entries(rate).map(([key, value]) => [
        key,
        key.endsWith('ZoneId')
          ? value
          : key === 'effectiveFrom'
            ? (value ? new Date(value).toISOString() : null)
            : Number(value),
      ])
    );
    action(() => client.request('/api/v1/admin/rate-cards', JsonRequest('POST', body)));
  }

  return (
    <WorkspacePage title="Pricing management">
      {error && <p className="error" role="alert">{error}</p>}

      {/* 1. Delivery Zones Section */}
      <section>
        <h2>Delivery zones</h2>
        <form onSubmit={event => {
          event.preventDefault();
          action(async () => {
            await client.request('/api/v1/admin/zones', JsonRequest('POST', { name }));
            setName('');
          });
        }}>
          <label>
            New zone name
            <input required maxLength={100} value={name} onChange={event => setName(event.target.value)} />
          </label>
          <button disabled={pending}>Create zone</button>
        </form>
        <ul>
          {zones.map(zone => (
            <li key={zone.zoneId}>
              {zone.name} · {zone.active ? 'Active' : 'Inactive'}{' '}
              <button
                className="secondary"
                disabled={pending}
                onClick={() => action(() => client.request(`/api/v1/admin/zones/${zone.zoneId}`, JsonRequest('PATCH', { active: !zone.active })))}
              >
                {zone.active ? 'Deactivate' : 'Activate'}
              </button>
            </li>
          ))}
        </ul>
      </section>

      {/* 2. Postal Code Mappings Section */}
      <section>
        <h2>Postal code mappings</h2>
        <form onSubmit={event => {
          event.preventDefault();
          action(async () => {
            await client.request(`/api/v1/admin/zones/${zoneId}/postcodes`, JsonRequest('POST', { postcode }));
            setPostcode('');
          });
        }}>
          <label>
            Zone
            <select required value={zoneId} onChange={event => setZoneId(event.target.value)}>
              <option value="">Select zone</option>
              {zones.map(zone => <option key={zone.zoneId} value={zone.zoneId}>{zone.name}</option>)}
            </select>
          </label>
          <label>
            Postal code
            <input required pattern="[0-9]{5}" maxLength={5} value={postcode} onChange={event => setPostcode(event.target.value)} />
          </label>
          <button disabled={pending}>Add mapping</button>
        </form>
        <ul>
          {postcodes.map(row => (
            <li key={row.postcode}>
              {row.postcode} → {zoneName(row.zoneId)}{' '}
              <button
                className="secondary"
                disabled={pending}
                onClick={() => action(() => client.request(`/api/v1/admin/postcodes/${row.postcode}`, JsonRequest('DELETE')))}
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      </section>

      {/* 3. Create Rate Version Section with Clear Field Descriptions */}
      <section className="pricing-section">
        <div>
          <h2>Create rate version</h2>
          <p>
            Define pricing rules for deliveries between selected zones. Amounts are entered in LKR minor units (100 = LKR 1.00).
            Existing quote amounts booked by customers remain safely preserved.
          </p>
        </div>

        <div className="pricing-formula-banner" role="note">
          <p>
            <strong>Pricing Formula:</strong> Total Delivery Fee = Base Fee + (Billable Extra kg × Extra kg Rate) + Parcel Size Surcharge.
          </p>
        </div>

        <form onSubmit={handleRateSubmit}>
          {/* Group A: Zone Route */}
          <div className="pricing-group-card">
            <h3 className="pricing-group-heading">1. Route zones</h3>
            <p className="pricing-group-sub">Select the origin and destination coverage areas for this rate version.</p>
            <div className="pricing-fields-grid">
              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-pickup-zone" className="pricing-field-label">Pickup zone</label>
                </div>
                <select
                  id="rate-pickup-zone"
                  required
                  aria-describedby="rate-pickup-zone-desc"
                  value={rate.pickupZoneId}
                  onChange={event => setRate({ ...rate, pickupZoneId: event.target.value })}
                >
                  <option value="">Select zone</option>
                  {zones.map(zone => <option key={zone.zoneId} value={zone.zoneId}>{zone.name}</option>)}
                </select>
                <p id="rate-pickup-zone-desc" className="pricing-field-desc">
                  Geographic zone where parcels will be collected from the sender.
                </p>
              </div>

              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-dropoff-zone" className="pricing-field-label">Delivery zone</label>
                </div>
                <select
                  id="rate-dropoff-zone"
                  required
                  aria-describedby="rate-dropoff-zone-desc"
                  value={rate.dropoffZoneId}
                  onChange={event => setRate({ ...rate, dropoffZoneId: event.target.value })}
                >
                  <option value="">Select zone</option>
                  {zones.map(zone => <option key={zone.zoneId} value={zone.zoneId}>{zone.name}</option>)}
                </select>
                <p id="rate-dropoff-zone-desc" className="pricing-field-desc">
                  Destination geographic zone where parcels will be delivered.
                </p>
              </div>
            </div>
          </div>

          {/* Group B: Base Fee & Weight Allowance */}
          <div className="pricing-group-card">
            <h3 className="pricing-group-heading">2. Base fee & weight allowance</h3>
            <p className="pricing-group-sub">Configure the minimum starting fee and standard weight included with every parcel.</p>
            <div className="pricing-fields-grid">
              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-base-minor" className="pricing-field-label">Base delivery fee</label>
                  <span className="pricing-preview-badge">{formatLkr(rate.baseMinor)}</span>
                </div>
                <input
                  id="rate-base-minor"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-base-minor-desc"
                  value={rate.baseMinor}
                  onChange={event => setRate({ ...rate, baseMinor: event.target.value })}
                />
                <p id="rate-base-minor-desc" className="pricing-field-desc">
                  Starting delivery charge covering parcels up to the included weight. Enter in minor units (e.g., 50000 = LKR 500.00).
                </p>
              </div>

              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-included-kg" className="pricing-field-label">Included weight</label>
                  <span className="pricing-preview-badge">{rate.includedKg || 0} kg allowance</span>
                </div>
                <input
                  id="rate-included-kg"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-included-kg-desc"
                  value={rate.includedKg}
                  onChange={event => setRate({ ...rate, includedKg: event.target.value })}
                />
                <p id="rate-included-kg-desc" className="pricing-field-desc">
                  Weight in kilograms covered by the base fee. Extra charges only apply when parcel weight exceeds this threshold.
                </p>
              </div>

              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-extra-kg-minor" className="pricing-field-label">Extra weight rate</label>
                  <span className="pricing-preview-badge">{formatLkr(rate.extraKgMinor)} / extra kg</span>
                </div>
                <input
                  id="rate-extra-kg-minor"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-extra-kg-minor-desc"
                  value={rate.extraKgMinor}
                  onChange={event => setRate({ ...rate, extraKgMinor: event.target.value })}
                />
                <p id="rate-extra-kg-minor-desc" className="pricing-field-desc">
                  Additional fee charged for each full or partial kilogram beyond the included weight allowance.
                </p>
              </div>
            </div>
          </div>

          {/* Group C: Parcel Size Surcharges */}
          <div className="pricing-group-card">
            <h3 className="pricing-group-heading">3. Parcel size surcharges</h3>
            <p className="pricing-group-sub">Optional dimensional surcharges added based on customer selected package size.</p>
            <div className="pricing-fields-grid">
              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-surcharge-small" className="pricing-field-label">Small parcel surcharge</label>
                  <span className="pricing-preview-badge">{formatLkr(rate.sizeSurchargeSmall)}</span>
                </div>
                <input
                  id="rate-surcharge-small"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-surcharge-small-desc"
                  value={rate.sizeSurchargeSmall}
                  onChange={event => setRate({ ...rate, sizeSurchargeSmall: event.target.value })}
                />
                <p id="rate-surcharge-small-desc" className="pricing-field-desc">
                  Additional fee for small parcels (e.g., envelopes, documents, small pouches). Often set to 0.
                </p>
              </div>

              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-surcharge-medium" className="pricing-field-label">Medium parcel surcharge</label>
                  <span className="pricing-preview-badge">{formatLkr(rate.sizeSurchargeMedium)}</span>
                </div>
                <input
                  id="rate-surcharge-medium"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-surcharge-medium-desc"
                  value={rate.sizeSurchargeMedium}
                  onChange={event => setRate({ ...rate, sizeSurchargeMedium: event.target.value })}
                />
                <p id="rate-surcharge-medium-desc" className="pricing-field-desc">
                  Additional fee for standard medium parcels (e.g., shoeboxes, standard courier boxes).
                </p>
              </div>

              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-surcharge-large" className="pricing-field-label">Large parcel surcharge</label>
                  <span className="pricing-preview-badge">{formatLkr(rate.sizeSurchargeLarge)}</span>
                </div>
                <input
                  id="rate-surcharge-large"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-surcharge-large-desc"
                  value={rate.sizeSurchargeLarge}
                  onChange={event => setRate({ ...rate, sizeSurchargeLarge: event.target.value })}
                />
                <p id="rate-surcharge-large-desc" className="pricing-field-desc">
                  Additional fee for large bulky parcels (e.g., small appliances, larger storage boxes).
                </p>
              </div>

              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-surcharge-xlarge" className="pricing-field-label">Extra Large parcel surcharge</label>
                  <span className="pricing-preview-badge">{formatLkr(rate.sizeSurchargeExtraLarge)}</span>
                </div>
                <input
                  id="rate-surcharge-xlarge"
                  required
                  type="number"
                  min={0}
                  step={1}
                  aria-describedby="rate-surcharge-xlarge-desc"
                  value={rate.sizeSurchargeExtraLarge}
                  onChange={event => setRate({ ...rate, sizeSurchargeExtraLarge: event.target.value })}
                />
                <p id="rate-surcharge-xlarge-desc" className="pricing-field-desc">
                  Additional fee for oversized, heavy-dimension, or extra large packages requiring special handling.
                </p>
              </div>
            </div>
          </div>

          {/* Group D: Activation Schedule */}
          <div className="pricing-group-card">
            <h3 className="pricing-group-heading">4. Activation schedule</h3>
            <p className="pricing-group-sub">Determine the effective starting timestamp for this pricing version.</p>
            <div className="pricing-fields-grid">
              <div className="pricing-field">
                <div className="pricing-field-header">
                  <label htmlFor="rate-effective-from" className="pricing-field-label">Effective date & time</label>
                  <span className="pricing-preview-badge">{rate.effectiveFrom ? 'Scheduled' : 'Immediate'}</span>
                </div>
                <input
                  id="rate-effective-from"
                  type="datetime-local"
                  aria-describedby="rate-effective-from-desc"
                  value={rate.effectiveFrom}
                  onChange={event => setRate({ ...rate, effectiveFrom: event.target.value })}
                />
                <p id="rate-effective-from-desc" className="pricing-field-desc">
                  Timestamp from which this rate card becomes active. Leave empty to activate immediately upon creation.
                </p>
              </div>
            </div>
          </div>

          <button className="pricing-submit-btn" disabled={pending}>Create rate version</button>
        </form>
      </section>

      {/* 4. Rate Versions Table */}
      <section>
        <h2>Rate versions</h2>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Route</th>
                <th>Base</th>
                <th>Extra kg</th>
                <th>Effective</th>
                <th>State</th>
              </tr>
            </thead>
            <tbody>
              {rates.map(item => (
                <tr key={item.rateVersionId}>
                  <td>{zoneName(item.pickupZoneId)} → {zoneName(item.dropoffZoneId)}</td>
                  <td>{(item.baseMinor / 100).toFixed(2)}</td>
                  <td>{(item.extraKgMinor / 100).toFixed(2)}</td>
                  <td>{new Date(item.effectiveFrom).toLocaleString()}</td>
                  <td>
                    <button
                      disabled={pending}
                      onClick={() => action(() => client.request(`/api/v1/admin/rate-cards/${item.rateVersionId}`, JsonRequest('PATCH', { active: !item.active })))}
                    >
                      {item.active ? 'Deactivate' : 'Activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </WorkspacePage>
  );
}
