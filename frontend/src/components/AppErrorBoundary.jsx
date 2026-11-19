import React from 'react';

export default class AppErrorBoundary extends React.Component {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // Preserve diagnostics for developers while keeping the user on a recoverable screen.
    console.error('SmartTrip root render failed.', error, info?.componentStack || '');
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <main className="app-render-error" role="alert">
        <section>
          <p className="eyebrow">SmartTrip Planner</p>
          <h1>No pudimos mostrar esta pantalla</h1>
          <p>La aplicación encontró un error de interfaz. Tus datos guardados no se modificaron. Recarga para volver a intentarlo.</p>
          <button type="button" onClick={() => window.location.reload()}>Recargar aplicación</button>
        </section>
      </main>
    );
  }
}
