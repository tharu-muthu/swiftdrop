export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 128;

// Count Unicode code points, just like the backend. Never change the password.
export function passwordLength(password) {
  return [...password].length;
}

export function passwordError(password) {
  const length = passwordLength(password);
  if (length === 0) return 'Enter a password.';
  if (length < MIN_PASSWORD_LENGTH) return `Password must contain at least ${MIN_PASSWORD_LENGTH} characters.`;
  if (length > MAX_PASSWORD_LENGTH) return `Password must contain no more than ${MAX_PASSWORD_LENGTH} characters.`;
  return undefined;
}
