import { render,screen,fireEvent,waitFor } from '@testing-library/react';
import { it,expect,vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import NotificationAdminPage from './NotificationAdminPage.jsx';

it('saves the edited message with its optimistic version and reports mock delivery',async()=>{
  const template={status:'CREATED',body:'Booked {trackingNumber}',enabled:true,version:4};
  const request=vi.fn(async(path,options)=>path.endsWith('/templates')?[template]:options?.method==='PUT'?undefined:[]);
  const state={status:'authenticated',user:{fullName:'Admin',roles:['DISPATCHER_ADMIN']}};
  render(<AuthProvider client={{subscribe:()=>()=>{},getSnapshot:()=>state,bootstrap:()=>{},request}}><NotificationAdminPage /></AuthProvider>);
  fireEvent.change(await screen.findByLabelText('CREATED'),{target:{value:'Your parcel {trackingNumber} was booked.'}});
  fireEvent.click(screen.getByRole('button',{name:'Save CREATED'}));
  await waitFor(()=>expect(request.mock.calls.some(([,options])=>options?.method==='PUT')).toBe(true));
  const [path,options]=request.mock.calls.find(([,options])=>options?.method==='PUT');
  expect(path).toBe('/api/v1/admin/notifications/templates/CREATED');
  expect(JSON.parse(options.body)).toEqual({body:'Your parcel {trackingNumber} was booked.',enabled:true,version:4});
  expect(screen.getByText(/without sending real SMS/)).toBeInTheDocument();
});
