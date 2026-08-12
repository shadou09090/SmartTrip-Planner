import React, { useState } from 'react';
import { UserPlus } from 'lucide-react';
import { InputGroup, Button } from '../components/ui';

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

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
    if (error) setError('');
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (formData.password !== formData.confirmPassword) {
      setError('Las contraseñas no coinciden');
      return;
    }

    setSubmitted(true);
    setTimeout(() => {
      alert(`¡Registro exitoso para ${formData.firstName} ${formData.lastName}!`);
      setSubmitted(false);
      onNavigate('login');
    }, 400);
  };

  return (
    <div className="form-container">
      <form className="form" onSubmit={handleSubmit}>
        <p className="title">Register</p>
        <p className="message">Regístrate ahora y obtén acceso a nuestra app.</p>

        {error && (
          <div style={{ color: '#ef4444', fontSize: '0.85rem', fontWeight: 600 }}>
            {error}
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
