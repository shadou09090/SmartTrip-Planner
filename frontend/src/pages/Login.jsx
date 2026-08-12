import React, { useState } from 'react';
import { LogIn, Mail, Lock } from 'lucide-react';

export default function Login({ onNavigate }) {
  const [formData, setFormData] = useState({
    email: '',
    password: ''
  });
  const [submitted, setSubmitted] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => {
      alert(`¡Bienvenido de nuevo! Email: ${formData.email}`);
      setSubmitted(false);
    }, 400);
  };

  return (
    <div className="form-container">
      <form className="form" onSubmit={handleSubmit}>
        <p className="title">Login</p>
        <p className="message">Ingresa ahora y accede a tu cuenta.</p>

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
            autoComplete="current-password"
          />
          <span>Password</span>
        </label>

        <button type="submit" className="submit" disabled={submitted}>
          <LogIn size={18} />
          {submitted ? 'Ingresando...' : 'Submit'}
        </button>

        <p className="signin">
          ¿No tienes cuenta?{' '}
          <button type="button" onClick={() => onNavigate('register')}>
            Regístrate
          </button>
        </p>
      </form>
    </div>
  );
}
