import React, { useState } from 'react';
import { LogIn } from 'lucide-react';
import { InputGroup, Button } from '../components/ui';

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
          autoComplete="current-password"
        />

        <Button type="submit" variant="submit" disabled={submitted}>
          <LogIn size={18} />
          {submitted ? 'Ingresando...' : 'Submit'}
        </Button>

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
