import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import AdminUsersPage from './AdminUsersPage.jsx';

const actor = { id: 'admin-1', fullName: 'Admin One', roles: ['DISPATCHER_ADMIN'] };
const account = { id: 'customer-1', fullName: 'Customer One', email: 'one@example.test', phone: null,
  roles: ['CUSTOMER'], active: true, emailVerified: true, createdAt: '2026-11-01T00:00:00Z', version: 3 };

describe('admin user management', () => {
  it('searches, requires a reason, and submits the current record version', async () => {
    const request = vi.fn().mockImplementation(path => {
      if (path.includes('/suspend')) return Promise.resolve({ ...account, active: false, version: 4 });
      if (path.endsWith('/customer-1')) return Promise.resolve(account);
      return Promise.resolve({ items: [account], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    });
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    fireEvent.change(screen.getByLabelText('Search by name or email'), { target: { value: 'one@example.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await screen.findByText('Customer One')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'View' }));
    expect(await screen.findByRole('button', { name: 'Suspend account' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Reason for suspension *'), { target: { value: 'Access review' } });
    fireEvent.click(screen.getByRole('button', { name: 'Suspend account' }));
    await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/admin/users/customer-1/suspend',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ reason: 'Access review', expectedVersion: 3 }) })));
    expect(await screen.findByRole('status')).toHaveTextContent('Account suspended.');
  });
  it('grants a permitted role with a reason and version', async () => {
    const request = vi.fn().mockImplementation(path => path.includes('/roles/grant')
      ? Promise.resolve({ id: account.id, roles: ['CUSTOMER', 'DISPATCHER_ADMIN'], version: 4 })
      : path.endsWith('/customer-1') ? Promise.resolve(account)
        : Promise.resolve({ items: [account], page: 0, size: 20, totalElements: 1, totalPages: 1 }));
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'View' }));
    fireEvent.change(await screen.findByLabelText('Role'), { target: { value: 'DISPATCHER_ADMIN' } });
    fireEvent.change(screen.getByLabelText('Reason *'), { target: { value: 'Approved second admin' } });
    fireEvent.click(screen.getByRole('button', { name: 'Grant role' }));
    await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/admin/users/customer-1/roles/grant',
      expect.objectContaining({ body: JSON.stringify({ role: 'DISPATCHER_ADMIN', reason: 'Approved second admin', expectedVersion: 3 }) })));
  });
  it('connects courier role assignment to the selected account profile', async () => {
    const request = vi.fn().mockImplementation(path => {
      if (path.endsWith('/roles/grant')) return Promise.resolve({ id: account.id, roles: ['CUSTOMER', 'COURIER'], version: 4 });
      if (path === '/api/v1/dispatch/couriers') return Promise.resolve([]);
      if (path === `/api/v1/dispatch/couriers/${account.id}`) return Promise.resolve(null);
      if (path.endsWith('/customer-1')) return Promise.resolve(account);
      return Promise.resolve({ items: [account], page: 0, totalElements: 1, totalPages: 1 });
    });
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'View' }));
    fireEvent.change(await screen.findByLabelText('Reason *'), { target: { value: 'Courier onboarding approved' } });
    fireEvent.click(screen.getByRole('button', { name: 'Grant role' }));
    fireEvent.change(await screen.findByLabelText('Vehicle type *'), { target: { value: 'Van' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save courier profile' }));
    await waitFor(() => expect(request).toHaveBeenCalledWith(`/api/v1/dispatch/couriers/${account.id}`, expect.objectContaining({ method: 'PUT' })));
    expect(await screen.findByText(/Courier setup complete/)).toBeVisible();
  });
  it('requires reload after a stale role edit without changing the displayed roles', async () => {
    const request = vi.fn().mockImplementation(path => path.endsWith('/roles/grant')
      ? Promise.reject(Object.assign(new Error('Conflict'), { status: 409 }))
      : path.endsWith('/customer-1') ? Promise.resolve(account)
        : Promise.resolve({ items: [account], page: 0, totalElements: 1, totalPages: 1 }));
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'View' }));
    fireEvent.change(await screen.findByLabelText('Reason *'), { target: { value: 'Access review' } });
    fireEvent.click(screen.getByRole('button', { name: 'Grant role' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Reload this account');
    expect(screen.queryByRole('heading', { name: 'Courier setup' })).not.toBeInTheDocument();
  });
  it('prevents the administrator from removing their own administrator role', async () => {
    const self = { ...account, id: actor.id, roles: ['CUSTOMER', 'DISPATCHER_ADMIN'] };
    const request = vi.fn().mockImplementation(path => path.endsWith('/admin-1') ? Promise.resolve(self)
      : Promise.resolve({ items: [self], page: 0, totalElements: 1, totalPages: 1 }));
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    fireEvent.click(await screen.findByRole('button', { name: 'View' }));
    fireEvent.change(await screen.findByLabelText('Role'), { target: { value: 'DISPATCHER_ADMIN' } });
    fireEvent.change(screen.getByLabelText('Action'), { target: { value: 'revoke' } });
    fireEvent.change(screen.getByLabelText('Reason *'), { target: { value: 'Self removal' } });
    expect(screen.getByRole('button', { name: 'Remove role' })).toBeDisabled();
  });
  it('opens details in a centered pop-up window modal, shows skeleton loading, and closes back to the original list', async () => {
    let delayResolve = null;
    const request = vi.fn().mockImplementation(path => {
      if (path.endsWith('/customer-1')) {
        return new Promise(resolve => { delayResolve = () => resolve(account); });
      }
      return Promise.resolve({ items: [account], page: 0, totalElements: 1, totalPages: 1 });
    });
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    expect(await screen.findByText('Customer One')).toBeVisible();
    expect(screen.queryByRole('dialog', { name: 'Account details modal' })).not.toBeInTheDocument();

    // Click view: centered pop-up modal opens immediately with skeleton loading
    fireEvent.click(screen.getByRole('button', { name: 'View' }));
    expect(screen.getByRole('dialog', { name: 'Account details modal' })).toBeVisible();
    expect(screen.getByLabelText('Loading account details')).toBeVisible();

    // When request resolves, details are shown in the modal
    delayResolve();
    const modal = await screen.findByRole('dialog', { name: 'Account details modal' });
    expect(await within(modal).findByRole('region', { name: 'Account details' })).toBeVisible();
    expect(within(modal).getByText('Account roles')).toBeVisible();
    expect(screen.queryByLabelText('Loading account details')).not.toBeInTheDocument();

    // Close via close button: modal closes, original list is intact
    fireEvent.click(screen.getByRole('button', { name: 'Close modal' }));
    expect(screen.queryByRole('dialog', { name: 'Account details modal' })).not.toBeInTheDocument();
    expect(screen.getByText('Customer One')).toBeVisible();

    // Reopen and close via Escape key
    fireEvent.click(screen.getByRole('button', { name: 'View' }));
    expect(await screen.findByRole('dialog', { name: 'Account details modal' })).toBeVisible();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Account details modal' })).not.toBeInTheDocument();
    expect(screen.getByText('Customer One')).toBeVisible();

    // Reopen and close via backdrop click
    fireEvent.click(screen.getByRole('button', { name: 'View' }));
    expect(await screen.findByRole('dialog', { name: 'Account details modal' })).toBeVisible();
    fireEvent.click(document.querySelector('.admin-modal-backdrop'));
    expect(screen.queryByRole('dialog', { name: 'Account details modal' })).not.toBeInTheDocument();
    expect(screen.getByText('Customer One')).toBeVisible();
  });
});
