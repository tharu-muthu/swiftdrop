import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import RegisterPage from './RegisterPage.jsx';

afterEach(() => vi.unstubAllGlobals());
const password = '  an exact long passphrase 🔐  ';
function fill(confirm = password) {
  fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Test Customer' } });
  fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'person@example.test' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
  fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: confirm } });
}
function csrf() { return { ok: true, json: async () => ({ token: 'csrf-fixture', headerName: 'X-XSRF-TOKEN' }) }; }
describe('customer registration', () => {
  it('validates confirmation before network activity and toggles visibility', () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    render(<RegisterPage />); fill('different');
    const toggle = screen.getByRole('button', { name: 'Show password', exact: true });
    expect(toggle).toHaveTextContent('');
    expect(toggle.querySelector('svg')).toBeInTheDocument();
    fireEvent.click(toggle);
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Hide password', exact: true })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: 'Hide password', exact: true }));
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByText('Passwords must match.')).toBeVisible();
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each([
    ['', 'Enter a password.'],
    ['a'.repeat(7), 'Password must contain at least 8 characters.'],
    ['🔐'.repeat(7), 'Password must contain at least 8 characters.'],
    ['a'.repeat(129), 'Password must contain no more than 128 characters.'],
  ])('explains the specific password failure without sending a request (%#)', (value, message) => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    render(<RegisterPage />); fill(value);
    fireEvent.change(screen.getByLabelText('Password'), { target: { value } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByText(message)).toBeVisible();
    expect(screen.getByText(`8–128 characters (${[...value].length} entered).`)).toBeVisible();
    expect(screen.getByLabelText('Password')).toHaveFocus();
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each([8, 128])('accepts %i Unicode characters unchanged after correcting a short password', async length => {
    const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce({ status: 201, json: async () => ({}) });
    vi.stubGlobal('fetch', fetch);
    render(<RegisterPage />); fill('short');
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByText('Password must contain at least 8 characters.')).toBeVisible();
    const value = '🔐'.repeat(length);
    fireEvent.change(screen.getByLabelText('Password'), { target: { value } });
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value } });
    expect(screen.queryByText('Password must contain at least 8 characters.')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Check your email')).toBeVisible();
    expect(JSON.parse(fetch.mock.calls[1][1].body).password).toBe(value);
  });
  it('clears the confirmation mismatch when editing the password makes them match', () => {
    render(<RegisterPage />); fill('another long passphrase');
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByText('Passwords must match.')).toBeVisible();
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'another long passphrase' } });
    expect(screen.queryByText('Passwords must match.')).not.toBeInTheDocument();
  });
  it('submits only permitted fields with exact password and CSRF, then clears sensitive inputs', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce({ status: 201, json: async () => ({ id: 'fixture', roles: ['CUSTOMER'] }) });
    vi.stubGlobal('fetch', fetch);
    render(<RegisterPage />); fill(); fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('heading', { name: 'Check your email' })).toBeVisible();
    const [url, options] = fetch.mock.calls[1];
    expect(url).toBe('/api/v1/auth/register');
    expect(JSON.parse(options.body)).toEqual({ fullName: 'Test Customer', email: 'person@example.test', password });
    expect(options.headers['X-XSRF-TOKEN']).toBe('csrf-fixture');
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
    expect(screen.getByText('Your account was created. Open the verification link in your email before signing in.')).toBeVisible();
  });
  it('disables resubmission while pending', async () => {
    let resolve;
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(csrf()).mockImplementationOnce(() => new Promise(done => { resolve = done; })));
    render(<RegisterPage />); fill(); fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByRole('button', { name: 'Creating account…' })).toBeDisabled();
    await waitFor(() => expect(resolve).toBeTypeOf('function'));
    resolve({ status: 201, json: async () => ({}) });
    expect(await screen.findByText('Check your email')).toBeVisible();
  });
  it('shows backend validation and duplicate errors without navigating', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(csrf()).mockResolvedValueOnce({ status: 400, json: async () => ({ message: 'Check the highlighted fields.', fieldErrors: [{ field: 'email', message: 'Enter a valid email address.' }] }) });
    vi.stubGlobal('fetch', fetch); render(<RegisterPage />); fill();
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Check the highlighted fields.');
    expect(screen.getByLabelText('Email address')).toHaveAttribute('aria-invalid', 'true');
    fetch.mockResolvedValueOnce(csrf()).mockResolvedValueOnce({ status: 409, json: async () => ({ message: 'An account could not be created with these details.', fieldErrors: [] }) });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('An account could not be created with these details.')).toBeVisible();
  });
  it('announces network failure and allows retry', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')));
    render(<RegisterPage />); fill(); fireEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to connect.');
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled();
  });
});
