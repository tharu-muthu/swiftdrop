import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import LoginPage from './LoginPage.jsx';

function setup(login = vi.fn()) {
  const state = { status: 'anonymous', user: null, error: '' };
  const client = { subscribe: () => () => {}, getSnapshot: () => state, bootstrap: vi.fn(), login };
  render(<AuthProvider client={client}><LoginPage /></AuthProvider>);
  return client;
}
describe('password login screen', () => {
  beforeEach(()=>vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,json:async()=>({googleEnabled:true})})));
  afterEach(()=>vi.unstubAllGlobals());
  it('uses neutral wording, accessible visibility and registration navigation', async () => {
    setup(); expect(screen.getByText('Log in to your SwiftDrop account')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Sign up' })).toHaveAttribute('href', '/register');
    fireEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text');
    expect(await screen.findByRole('button', { name: 'Continue with Google' }))
      .toHaveAttribute('href', '/oauth2/authorization/google');
  });
  it('announces generic login errors without changing the password', async () => {
    const login = vi.fn().mockRejectedValue(new Error('Email or password is incorrect.')); setup(login);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'Person@example.test' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: ' exact password ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Log in', exact: true }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
    expect(login).toHaveBeenCalledWith('Person@example.test', ' exact password ');
  });
  it('requires both fields and prevents repeated submission while pending', async () => {
    let finish; const login = vi.fn(() => new Promise(resolve => { finish = resolve; })); setup(login);
    fireEvent.click(screen.getByRole('button', { name: 'Log in', exact: true }));
    expect(screen.getByText('Enter your password.')).toBeVisible(); expect(login).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'test@example.test' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Log in', exact: true }));
    expect(screen.getByRole('button', { name: 'Logging in…' })).toBeDisabled();
    finish(); expect(await screen.findByRole('button', { name: 'Log in', exact: true })).toBeEnabled();
  });
});
