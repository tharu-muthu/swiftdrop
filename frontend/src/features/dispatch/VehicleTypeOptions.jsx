export const VEHICLE_TYPES = [
  ['BICYCLE', 'Bicycle'],
  ['E_BICYCLE', 'Electric bicycle'],
  ['MOTORCYCLE', 'Motorcycle'],
  ['SCOOTER', 'Scooter'],
  ['THREE_WHEELER', 'Three-wheeler'],
  ['CAR', 'Car'],
  ['VAN', 'Van'],
  ['PICKUP_TRUCK', 'Pickup truck'],
  ['LIGHT_TRUCK', 'Light truck'],
];

export function VehicleTypeOptions({ current = '' }) {
  return <>{current && !VEHICLE_TYPES.some(([code]) => code === current)
    && <option value={current}>{current} (existing value)</option>}
    {VEHICLE_TYPES.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</>;
}
