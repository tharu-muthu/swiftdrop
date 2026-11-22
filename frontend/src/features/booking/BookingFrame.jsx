import PortalShell from '../../components/PortalShell.jsx';
import './booking.css';
import './booking-brand-theme.css';

export default function BookingFrame({ activePath, children, headerTitle = 'Create Booking' }) {
  return <PortalShell title={headerTitle} titleAs="p" className="booking-shell" contentClassName="booking-main" activePath={activePath}>
    {children}
  </PortalShell>;
}
