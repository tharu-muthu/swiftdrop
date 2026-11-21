import { render,screen } from '@testing-library/react';
import { it,expect,vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import DispatchDashboardPage from './DispatchDashboardPage.jsx';

it('loads the paginated dispatch board and an empty fleet without requiring manual booking IDs',async()=>{
  const request=vi.fn(async(path)=>path.startsWith('/api/v1/dispatch/bookings')?{items:[],totalPages:0}:[]);
  const state={status:'authenticated',user:{fullName:'Admin',roles:['DISPATCHER_ADMIN']}};
  render(<AuthProvider client={{subscribe:()=>()=>{},getSnapshot:()=>state,bootstrap:()=>{},request}}><DispatchDashboardPage /></AuthProvider>);
  expect(await screen.findByText('No deliveries found.')).toBeInTheDocument();
  expect(request).toHaveBeenCalledWith('/api/v1/dispatch/bookings?page=0&size=50&unassigned=false');
  expect(screen.queryByLabelText('Pending Booking ID')).not.toBeInTheDocument();
});
