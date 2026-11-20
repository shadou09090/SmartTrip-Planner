import React, { useState } from 'react';
import { LogIn } from 'lucide-react';
import { InputGroup, Button } from '../components/ui';
import { apiRequest } from '../lib/api';

export default function Login({ onNavigate, onLogin }) {
  const localDevMode = import.meta.env.VITE_DUFFEL_MODE === 'local-no-db';
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    bootstrapToken: '',
  });
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSubmitted(true);
    try {
      const loginResponse = localDevMode
        ? await apiRequest('/api/local/dev-auth/token', {
          method: 'POST',
          body: JSON.stringify({ bootstrapToken: formData.bootstrapToken }),
        })
        : await apiRequest('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ email: formData.email.trim(), password: formData.password }),
        });
      setFormData({ email: '', password: '', bootstrapToken: '' });
      onLogin(localDevMode ? { ...loginResponse, localDevSession: true } : loginResponse);
    } catch (requestError) {
      const msg = requestError?.message || '';
      if (msg.includes('Failed to fetch') || msg.includes('NetworkError')) {
        setError('No se pudo conectar con el servidor backend (http://localhost:8080). Verifica que el backend esté iniciado.');
      } else {
        setError(msg);
      }
    } finally {
      setSubmitted(false);
    }
  };

  return (
    <div className="form-container">
      <form className="form" onSubmit={handleSubmit}>
        <p className="title">{localDevMode ? 'Acceso temporal de desarrollo' : 'Iniciar sesión'}</p>
        <p className="message">{localDevMode
          ? 'Este acceso solo permite probar la búsqueda Duffel; no inicia sesión en una cuenta ni habilita viajes.'
          : 'Ingresa para comenzar a planificar tu viaje.'}</p>
        {error && <p className="form-error" role="alert">{error}</p>}

        {localDevMode ? (
          <InputGroup
            label="Clave local de desarrollo"
            type="password"
            name="bootstrapToken"
            value={formData.bootstrapToken}
            onChange={handleChange}
            autoComplete="off"
          />
        ) : <>
          <InputGroup
            label="Email"
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            autoComplete="email"
          />
          <InputGroup
            label="Contraseña"
            type="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            autoComplete="current-password"
          />
        </>}

        <Button type="submit" variant="submit" disabled={submitted}>
          <LogIn size={18} />
          {submitted ? 'Validando...' : localDevMode ? 'Abrir búsqueda Duffel' : 'Ingresar'}
        </Button>

        {!localDevMode && <p className="signin">
          ¿No tienes cuenta?{' '}
          <button type="button" onClick={() => onNavigate('register')}>
            Regístrate
          </button>
        </p>}
      </form>
    </div>
  );
}
