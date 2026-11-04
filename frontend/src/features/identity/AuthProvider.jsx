import { createContext, useContext, useEffect, useSyncExternalStore } from 'react';
import { authClient } from './apiClient.js';

const AuthContext = createContext(null);
export function AuthProvider({ children, client = authClient }) {
  const state = useSyncExternalStore(client.subscribe, client.getSnapshot);
  useEffect(() => { client.bootstrap(); }, [client]);
  return <AuthContext.Provider value={{ ...state, client }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('Wrap authentication screens with AuthProvider.');
  return auth;
}
