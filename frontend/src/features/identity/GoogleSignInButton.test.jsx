import { render,screen } from '@testing-library/react';
import { afterEach,it,expect,vi } from 'vitest';
import GoogleSignInButton from './GoogleSignInButton.jsx';
afterEach(()=>vi.unstubAllGlobals());
it('disables Google when the backend provider is not configured',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,json:async()=>({googleEnabled:false})}));
  render(<GoogleSignInButton />);
  expect(await screen.findByRole('button',{name:'Google sign-in is unavailable'})).toBeDisabled();
  expect(screen.queryByRole('button',{name:'Continue with Google'})).not.toBeInTheDocument();
});
it('does not expose an OAuth link when provider discovery fails',async()=>{
  vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new Error('Offline')));render(<GoogleSignInButton />);
  expect(await screen.findByRole('button',{name:'Google sign-in is unavailable'})).toBeDisabled();
});
