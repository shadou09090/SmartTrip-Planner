import React, { useState } from 'react';
import { UserPlus } from 'lucide-react';
import { InputGroup, Button } from '../components/ui';
import { apiRequest } from '../lib/api';

export default function Register({ onNavigate }) {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [error, setError] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [success, setSuccess] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    if (error) setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    if (formData.password !== formData.confirmPassword) {
      setError('Las contraseñas no coinciden');
      return;
    }

    setSubmitted(true);
    try {
      const message = await apiRequest('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
          name: `${formData.firstName.trim()} ${formData.lastName.trim()}`.trim(),
          email: formData.email.trim(),
          password: formData.password,
        }),
      });
      setSuccess(typeof message === 'string'
        ? message
        : 'Solicitud recibida. Puedes intentar iniciar sesión con ese correo.');
      setSubmitted(false);
    } catch (requestError) {
      setError(requestError.message);
      setSubmitted(false);
    }
  };

  return (
    <div className="form-container">
      <form className="form" onSubmit={handleSubmit}>
        <p className="title">Register</p>
        <p className="message">Regístrate ahora y obtén acceso a nuestra app.</p>

        {error && (
          <div role="alert" style={{ color: '#ef4444', fontSize: '0.85rem', fontWeight: 600 }}>
            {error}
          </div>
        )}
        {success && (
          <div role="status" style={{ color: '#16a34a', fontSize: '0.85rem', fontWeight: 600 }}>
            {success}{' '}
            <button type="button" onClick={() => onNavigate('login')}>Inicia sesión</button>
          </div>
        )}

        <div className="flex">
          <InputGroup
            label="Firstname"
            name="firstName"
            value={formData.firstName}
            onChange={handleChange}
          />
          <InputGroup
            label="Lastname"
            name="lastName"
            value={formData.lastName}
            onChange={handleChange}
          />
        </div>

        <InputGroup
          label="Email"
          type="email"
          name="email"
          value={formData.email}
          onChange={handleChange}
          autoComplete="email"
        />

        <InputGroup
          label="Password"
          type="password"
          name="password"
          value={formData.password}
          onChange={handleChange}
          autoComplete="new-password"
        />

        <InputGroup
          label="Confirm password"
          type="password"
          name="confirmPassword"
          value={formData.confirmPassword}
          onChange={handleChange}
          autoComplete="new-password"
        />

        <Button type="submit" variant="submit" disabled={submitted}>
          <UserPlus size={18} />
          {submitted ? 'Registrando...' : 'Submit'}
        </Button>

        <p className="signin">
          ¿Ya tienes una cuenta?{' '}
          <button type="button" onClick={() => onNavigate('login')}>
            Inicia sesión
          </button>
        </p>
      </form>
    </div>
  );
}
