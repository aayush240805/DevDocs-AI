import { createContext, useContext, useEffect, useState } from 'react';
import { fetchCurrentUser, loginUser, registerUser } from '../api/authApi';
import { TOKEN_STORAGE_KEY } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY));
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let active = true;

    async function verifySession() {
      const storedToken = localStorage.getItem(TOKEN_STORAGE_KEY);
      if (!storedToken) {
        if (active) setInitializing(false);
        return;
      }

      try {
        const currentUser = await fetchCurrentUser();
        if (active) {
          setUser(currentUser);
          setToken(storedToken);
        }
      } catch {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        if (active) {
          setUser(null);
          setToken(null);
        }
      } finally {
        if (active) setInitializing(false);
      }
    }

    verifySession();
    return () => {
      active = false;
    };
  }, []);

  const handleAuthSuccess = (authResponse) => {
    localStorage.setItem(TOKEN_STORAGE_KEY, authResponse.token);
    setToken(authResponse.token);
    setUser(authResponse.user);
    return authResponse.user;
  };

  const login = async (credentials) => {
    const authResponse = await loginUser(credentials);
    return handleAuthSuccess(authResponse);
  };

  const register = async (details) => {
    const authResponse = await registerUser(details);
    return handleAuthSuccess(authResponse);
  };

  const logout = () => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: Boolean(token && user),
        initializing,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return ctx;
}
