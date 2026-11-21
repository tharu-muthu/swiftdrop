import { fireEvent, render, screen, waitFor } from '@testing-library/react';
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
});
