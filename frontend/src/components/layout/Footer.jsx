import React from 'react';

export default function Footer() {
  return (
    <footer className="footer">
      © {new Date().getFullYear()} SmartTrip Planner. Todos los derechos reservados.
    </footer>
  );
}
