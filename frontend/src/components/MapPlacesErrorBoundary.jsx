import React from 'react';
import { MapPin } from 'lucide-react';

export default class MapPlacesErrorBoundary extends React.Component {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error) {
    console.error('Map places view failed to render.', error?.name || 'UnknownError');
  }

  componentDidUpdate(previousProps) {
    if (previousProps.tripKey !== this.props.tripKey && this.state.hasError) {
      this.setState({ hasError: false });
    }
  }

  render() {
    if (!this.state.hasError) return this.props.children;
    return (
      <section className="map-view-error" role="alert">
        <MapPin size={28} aria-hidden="true" />
        <h2>No pudimos abrir la vista del mapa</h2>
        <p>La planificación y los datos del viaje siguen disponibles. Puedes volver al viaje o intentar cargar el mapa de nuevo.</p>
        <div className="map-view-error__actions">
          <button type="button" onClick={this.props.onBack}>Volver al viaje</button>
          <button type="button" onClick={() => this.setState({ hasError: false })}>Reintentar</button>
        </div>
      </section>
    );
  }
}
