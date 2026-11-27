import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import AdminUsersPage from './AdminUsersPage.jsx';

const actor = { id: 'admin-1', fullName: 'Admin One', roles: ['DISPATCHER_ADMIN'] };
const account = { id: 'customer-1', fullName: 'Customer One', email: 'one@example.test', phone: null,
  roles: ['CUSTOMER'], active: true, emailVerified: true, createdAt: '2026-11-01T00:00:00Z', version: 3 };

describe('admin user management', () => {
  it('filters by role and assigns selected users with an audit reason', async () => {
    const candidate = { ...account, id: 'candidate-1', roles: ['CUSTOMER'], version: 7 };
    const request = vi.fn().mockImplementation((path) => {
      if (path.endsWith('/candidate-1/roles/grant')) return Promise.resolve({ id: candidate.id, roles: ['CUSTOMER', 'COURIER'], version: 8 });
      if (path.includes('query=candidate') && !path.includes('role=')) return Promise.resolve({ items: [candidate], page: 0, totalElements: 1, totalPages: 1 });
      return Promise.resolve({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);

    fireEvent.click(screen.getByRole('button', { name: /Couriers/ }));
    await waitFor(() => expect(request).toHaveBeenCalledWith(expect.stringContaining('role=COURIER')));
    fireEvent.click(screen.getByRole('button', { name: /Assign user/ }));
    expect(screen.getByText('Enter at least 3 characters to search.')).toBeVisible();
    fireEvent.change(screen.getByLabelText(/Find a user/), { target: { value: 'ca' } });
    expect(request).not.toHaveBeenCalledWith(expect.stringContaining('query=ca'));
    fireEvent.change(screen.getByLabelText(/Find a user/), { target: { value: 'candidate' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Select user' }));
    fireEvent.change(screen.getByLabelText('Vehicle type *'), { target: { value: 'VAN' } });
    fireEvent.click(screen.getByRole('button', { name: 'Assign Courier' }));

    await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/admin/users/candidate-1/roles/grant',
      expect.objectContaining({ body: JSON.stringify({ role: 'COURIER', reason: 'Assigned COURIER through user management', expectedVersion: 7 }) })));
    await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/dispatch/couriers/candidate-1',
      expect.objectContaining({ body: JSON.stringify({ vehicleType: 'VAN', licensePlate: '', active: true }) })));
  });

  it('searches and disables a user from the active toggle', async () => {
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
    expect(screen.getByRole('switch', { name: 'Disable Customer One' }).parentElement).toHaveAttribute('title', 'Disable Customer One and end their sessions');
    expect(screen.getByRole('button', { name: 'View' })).toHaveAttribute('title', "View Customer One's account details and role information");
    fireEvent.click(screen.getByRole('switch', { name: 'Disable Customer One' }));
    await waitFor(() => expect(request).toHaveBeenCalledWith('/api/v1/admin/users/customer-1/suspend',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ reason: 'Disabled through user management', expectedVersion: 3 }) })));
    expect(await screen.findByRole('status')).toHaveTextContent('Customer One was disabled.');
  });
  it('prevents the administrator from removing their own administrator role', async () => {
    const self = { ...account, id: actor.id, roles: ['CUSTOMER', 'DISPATCHER_ADMIN'] };
    const request = vi.fn().mockImplementation(path => path.endsWith('/admin-1') ? Promise.resolve(self)
      : Promise.resolve({ items: [self], page: 0, totalElements: 1, totalPages: 1 }));
    const snapshot = { status: 'authenticated', user: actor };
    const client = { subscribe: () => () => {}, getSnapshot: () => snapshot, bootstrap: vi.fn(), request };
    render(<AuthProvider client={client}><AdminUsersPage /></AuthProvider>);
    expect(await screen.findByRole('button', { name: 'You cannot remove your own administrator role for Customer One' })).toBeDisabled();
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
    expect(screen.getByRole('status', { name: '' })).toHaveTextContent('Loading account details');

    // When request resolves, details are shown in the modal
    delayResolve();
    const modal = await screen.findByRole('dialog', { name: 'Account details modal' });
    expect(await within(modal).findByRole('region', { name: 'Account details' })).toBeVisible();
    expect(within(modal).getByText('Roles')).toBeVisible();
    expect(within(modal).getByText('CUSTOMER')).toBeVisible();
    expect(within(modal).queryByLabelText('Role')).not.toBeInTheDocument();
    expect(screen.queryByText('Loading account details…')).not.toBeInTheDocument();

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
