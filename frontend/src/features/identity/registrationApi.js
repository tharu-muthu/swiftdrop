import { ApiError } from './apiClient.js';

export async function registerCustomer(fields) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', signal: controller.signal });
    if (!csrfResponse.ok) throw new Error('Unable to prepare registration. Please try again.');
    const csrf = await csrfResponse.json();
    const response = await fetch('/api/v1/auth/register', {
      method: 'POST', credentials: 'same-origin', signal: controller.signal,
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify({ fullName: fields.fullName, email: fields.email, password: fields.password }),
    });
    const data = await response.json();
    if (response.status !== 201) {
      throw new ApiError(data.message || 'Unable to create your account. Please try again.', response.status,
        data.code, data.fieldErrors);
    }
    return data;
  } catch (error) {
    if (error?.name === 'AbortError') {
      throw new ApiError('The request timed out. Please check before trying again.', 0, 'REQUEST_TIMEOUT');
    }
    if (error?.name === 'TypeError') {
      throw new ApiError('Unable to connect. Check your connection and try again.', 0, 'NETWORK_ERROR');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
