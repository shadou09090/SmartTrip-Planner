import React, { useState } from 'react';
import { UserPlus } from 'lucide-react';

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
          <label>
            <input
              required
              type="text"
              name="firstName"
              value={formData.firstName}
              onChange={handleChange}
              className={`input ${formData.firstName ? 'input-has-value' : ''}`}
            />
            <span>Firstname</span>
          </label>

          <label>
            <input
              required
              type="text"
              name="lastName"
              value={formData.lastName}
              onChange={handleChange}
              className={`input ${formData.lastName ? 'input-has-value' : ''}`}
            />
            <span>Lastname</span>
          </label>
        </div>

        <label>
          <input
            required
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            className={`input ${formData.email ? 'input-has-value' : ''}`}
            autoComplete="email"
          />
          <span>Email</span>
        </label>

        <label>
          <input
            required
            type="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            className={`input ${formData.password ? 'input-has-value' : ''}`}
            autoComplete="new-password"
          />
          <span>Password</span>
        </label>

        <label>
          <input
            required
            type="password"
            name="confirmPassword"
            value={formData.confirmPassword}
            onChange={handleChange}
            className={`input ${formData.confirmPassword ? 'input-has-value' : ''}`}
            autoComplete="new-password"
          />
          <span>Confirm password</span>
        </label>

        <button type="submit" className="submit" disabled={submitted}>
          <UserPlus size={18} />
          {submitted ? 'Registrando...' : 'Submit'}
        </button>

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
