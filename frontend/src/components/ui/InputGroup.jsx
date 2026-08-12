import React from 'react';

export default function InputGroup({
  label,
  type = 'text',
  name,
  value,
  onChange,
  required = true,
  autoComplete
}) {
  return (
    <label>
      <input
        required={required}
        type={type}
        name={name}
        value={value}
        onChange={onChange}
        autoComplete={autoComplete}
        className={`input ${value ? 'input-has-value' : ''}`}
      />
      <span>{label}</span>
    </label>
  );
}
