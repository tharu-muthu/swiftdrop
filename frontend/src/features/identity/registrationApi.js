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
      const error = new Error(data.message || 'Unable to create your account. Please try again.');
      error.fieldErrors = data.fieldErrors;
      throw error;
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}
