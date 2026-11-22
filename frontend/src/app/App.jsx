import WorkspacesPage from '../features/identity/WorkspacesPage.jsx';
import AppLoading from './AppLoading.jsx';
import ConfirmEmailChangePage from '../features/identity/ConfirmEmailChangePage.jsx';
import WorkspacePage from '../components/WorkspacePage.jsx';
import { availableWorkspaces, canAccess } from './workspaces.js';
import { navigate } from './navigation.js';
import FoundationPage from './FoundationPage.jsx';
import RegisterPage from '../features/identity/RegisterPage.jsx';
import { useLayoutEffect, useState } from 'react';
import { useAuth } from '../features/identity/AuthProvider.jsx';
import LoginPage from '../features/identity/LoginPage.jsx';
import AccountPage from '../features/identity/AccountPage.jsx';
import AuthCompletePage from '../features/identity/AuthCompletePage.jsx';
import { ForgotPasswordPage, ResetPasswordPage } from '../features/identity/PasswordRecoveryPages.jsx';
import { RequestVerificationPage, VerifyEmailPage } from '../features/identity/EmailVerificationPages.jsx';
import BookingHomePage from '../features/booking/BookingHomePage.jsx';
import WebhookSettingsPage from '../features/webhook/WebhookSettingsPage.jsx';
import BookingFormPage from '../features/booking/BookingFormPage.jsx';
import MyBookingsPage from '../features/booking/MyBookingsPage.jsx';
import BookingDetailsPage from '../features/booking/BookingDetailsPage.jsx';
import CourierDashboardPage from '../features/execution/CourierDashboardPage.jsx';
import PublicTrackingPage from '../features/tracking/PublicTrackingPage.jsx';
import DispatchDashboardPage from '../features/dispatch/DispatchDashboardPage.jsx';
import AdminUsersPage from '../features/identity/AdminUsersPage.jsx';
import MerchantDashboardPage from '../features/merchant/MerchantDashboardPage.jsx';
import MerchantReviewPage from '../features/merchant/MerchantReviewPage.jsx';
import PricingAdminPage from '../features/pricing/PricingAdminPage.jsx';
import NotificationAdminPage from '../features/notification/NotificationAdminPage.jsx';
import CustomerToolsPage from '../features/booking/CustomerToolsPage.jsx';

export default function App() {
  const [requestedPath, setPath] = useState(window.location.pathname);
  const auth = useAuth();
  const portals = auth.status === 'authenticated' ? availableWorkspaces(auth.user) : [];
  const path = portals.length === 1 && ['/', '/login', '/register', '/workspaces'].includes(requestedPath)
    ? portals[0].path : requestedPath;
  useLayoutEffect(() => {
    const update = () => setPath(window.location.pathname);
    window.addEventListener('popstate', update);
    return () => window.removeEventListener('popstate', update);
  }, []);
  useLayoutEffect(() => {
    if (path !== requestedPath) navigate(path, { replace: true });
  }, [path, requestedPath]);
  if (path === '/change-email') return <ConfirmEmailChangePage />;
  if (path === '/foundation') return <FoundationPage />;
  if (path === '/auth/complete') return <AuthCompletePage />;
  if (path === '/forgot-password') return <ForgotPasswordPage />;
  if (path === '/reset-password') return <ResetPasswordPage />;
  if (path === '/verify-email/request') return <RequestVerificationPage />;
  if (path === '/verify-email') return <VerifyEmailPage />;
  if (path === '/tracking' || path.startsWith('/tracking/')) {
    const trackingNumber = path.startsWith('/tracking/')
      ? decodeURIComponent(path.slice('/tracking/'.length))
      : new URLSearchParams(window.location.search).get('number') || '';
    return <PublicTrackingPage trackingNumber={trackingNumber} inPortal={auth.status === 'authenticated'} />;
  }
  if (auth.status === 'loading') return <AppLoading />;
  if (auth.status === 'authenticated') {
    if (!canAccess(auth.user, path)) return <WorkspacePage title="Access unavailable"><p role="alert">Your account does not have the role required for this workspace.</p><a href="/workspaces">View your workspaces</a></WorkspacePage>;
    if (['/', '/login', '/register', '/workspaces'].includes(path)) return <WorkspacesPage />;
    if (path === '/merchant') return <MerchantDashboardPage />;
    if (path === '/merchant/orders/new') return <BookingFormPage merchant />;
    if (auth.user.roles.includes('DISPATCHER_ADMIN')) {
      if (path === '/admin/merchants') return <MerchantReviewPage />;
      if (path === '/admin/pricing') return <PricingAdminPage />;
      if (path === '/admin/notifications') return <NotificationAdminPage />;
    }
    if (path === '/merchant/webhooks') return <WebhookSettingsPage />;
    if (path === '/courier' && auth.user.roles.includes('COURIER')) return <CourierDashboardPage />;
    if (path === '/dispatch' && auth.user.roles.includes('DISPATCHER_ADMIN')) return <DispatchDashboardPage />;
    if (path === '/admin/users' && auth.user.roles.includes('DISPATCHER_ADMIN')) return <AdminUsersPage />;
    if (path.startsWith('/bookings/') && path !== '/bookings/new') return <BookingDetailsPage bookingId={path.split('/')[2]} />;
    if (['/booking', '/bookings/new', '/bookings', '/quote', '/receipts', '/help'].includes(path)) {
      if (path === '/booking') return <BookingHomePage />;
      if (path === '/bookings/new') return <BookingFormPage />;
      return path === '/bookings' ? <MyBookingsPage /> : <CustomerToolsPage path={path} />;
    }
    if (path === '/account' || path === '/profile') return <AccountPage />;
    return <WorkspacePage title="Page not found"><a href="/workspaces">View your workspaces</a></WorkspacePage>;
  }
  return path === '/register' || path === '/' ? <RegisterPage /> : <LoginPage />;
}
