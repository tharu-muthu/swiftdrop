import { render,screen,fireEvent,waitFor } from '@testing-library/react';
import { it,expect,vi } from 'vitest';
import { AuthProvider } from '../identity/AuthProvider.jsx';
import MerchantDashboardPage from './MerchantDashboardPage.jsx';

function renderPage(request){const state={status:'authenticated',user:{fullName:'Owner',roles:['CUSTOMER','MERCHANT']}};render(<AuthProvider client={{subscribe:()=>()=>{},getSnapshot:()=>state,bootstrap:()=>{},request}}><MerchantDashboardPage /></AuthProvider>);}
const business={id:'merchant-1',businessName:'Fixture shop',contactEmail:'shop@example.test',contactPhone:'0771234567',businessAddress:'Shop address',approvalStatus:'APPROVED',version:2};
it('shows a newly issued API key once and allows dismissing it',async()=>{
  const request=vi.fn(async(path,options)=>path.endsWith('/registration')?business:path.includes('/orders?')?{items:[],page:0,totalPages:0,totalElements:0}:path.endsWith('/api-keys')&&options?.method==='POST'?{apiKey:'fixture-issued-key'}:[]);
  renderPage(request);await screen.findByRole('heading',{name:'API credentials'});fireEvent.click(screen.getByRole('button',{name:'Generate API key'}));
  expect(await screen.findByText('fixture-issued-key')).toBeInTheDocument();fireEvent.click(screen.getByRole('button',{name:'Dismiss key'}));
  expect(screen.queryByText('fixture-issued-key')).not.toBeInTheDocument();
});
it('sends the loaded record version when updating merchant contacts',async()=>{
  const request=vi.fn(async(path)=>path.endsWith('/registration')?business:path.includes('/orders?')?{items:[],page:0,totalPages:0,totalElements:0}:[]);renderPage(request);
  fireEvent.click(await screen.findByRole('button',{name:'Edit contact details'}));
  fireEvent.change(screen.getByLabelText('contact Email'),{target:{value:'new@example.test'}});fireEvent.click(screen.getByRole('button',{name:'Save contacts'}));
  await waitFor(()=>expect(request.mock.calls.some(([,options])=>options?.method==='PATCH')).toBe(true));
  const [,options]=request.mock.calls.find(([,options])=>options?.method==='PATCH');
  expect(JSON.parse(options.body)).toEqual({expectedVersion:2,profile:{businessName:'Fixture shop',contactEmail:'new@example.test',contactPhone:'0771234567',businessAddress:'Shop address'}});
});
