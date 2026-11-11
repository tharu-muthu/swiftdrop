import FoundationPage from './FoundationPage.jsx';
import RegisterPage from '../features/identity/RegisterPage.jsx';
import { useEffect, useState } from 'react';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import LoginPage from '../features/identity/LoginPage.jsx';
import AccountPage from '../features/identity/AccountPage.jsx';
import AuthCompletePage from '../features/identity/AuthCompletePage.jsx';
import BookingHomePage from '../features/booking/BookingHomePage.jsx';
import BookingPlaceholder from '../features/booking/BookingPlaceholder.jsx';
import WebhookSettingsPage from '../features/webhook/WebhookSettingsPage.jsx';
import BookingFormPage from '../features/booking/BookingFormPage.jsx';
import MyBookingsPage from '../features/booking/MyBookingsPage.jsx';
import BookingDetailsPage from '../features/booking/BookingDetailsPage.jsx';
import CourierDashboardPage from '../features/execution/CourierDashboardPage.jsx';
import PublicTrackingPage from '../features/tracking/PublicTrackingPage.jsx';
import DispatchDashboardPage from '../features/dispatch/DispatchDashboardPage.jsx';

export default function App() {
  const [path, setPath] = useState(window.location.pathname);
  const auth = useAuth();
  useEffect(() => {
    const update = () => setPath(window.location.pathname);
    window.addEventListener('popstate', update);
    return () => window.removeEventListener('popstate', update);
  }, []);
  if (path === '/foundation') return <FoundationPage />;
  if (path === '/auth/complete') return <AuthCompletePage />;
  if (path === '/tracking' || path.startsWith('/tracking/')) {
    const trackingNumber = path.startsWith('/tracking/')
      ? decodeURIComponent(path.slice('/tracking/'.length))
      : '';
    return <PublicTrackingPage trackingNumber={trackingNumber} />;
  }
  if (auth.status === 'loading') return <main className="auth-loading" role="status">Checking your session…</main>;
  if (auth.status === 'authenticated') {
    if (path === '/merchant/webhooks') return <WebhookSettingsPage />;
    if (path === '/courier' && auth.user.roles.includes('COURIER')) return <CourierDashboardPage />;
    if (path === '/dispatch' && auth.user.roles.includes('DISPATCHER_ADMIN')) return <DispatchDashboardPage />;
    if (path.startsWith('/bookings/') && path !== '/bookings/new') return <BookingDetailsPage bookingId={path.split('/')[2]} />;
    if (['/booking', '/bookings/new', '/bookings', '/quote', '/receipts', '/help'].includes(path)) {
      if (path === '/booking') return <BookingHomePage />;
      if (path === '/bookings/new') return <BookingFormPage />;
      return path === '/bookings' ? <MyBookingsPage /> : <BookingPlaceholder path={path} />;
    }
    return <AccountPage />;
  }
  return path === '/register' || path === '/' ? <RegisterPage /> : <LoginPage />;
}
