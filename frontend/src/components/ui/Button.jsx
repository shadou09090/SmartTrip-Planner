import React from 'react';

export default function Button({
  children,
  variant = 'primary', // 'primary' | 'secondary' | 'submit' | '3d'
  type = 'button',
  onClick,
  disabled = false,
  className = '',
  style = {}
}) {
  let baseClass = 'nav-btn';
  if (variant === 'primary') baseClass = 'nav-btn nav-btn-primary';
  if (variant === 'secondary') baseClass = 'nav-btn nav-btn-secondary';
  if (variant === 'submit') baseClass = 'submit';
  if (variant === '3d') baseClass = 'btn-3d';

  return (
    <button
      type={type}
      className={`${baseClass} ${className}`.trim()}
      onClick={onClick}
      disabled={disabled}
      style={style}
    >
      {children}
    </button>
  );
}
