# SmartTrip Planner

## Descripción

**SmartTrip Planner** es una aplicación web diseñada para ayudar a los usuarios a planificar viajes de manera eficiente y personalizada.

A través del uso de APIs externas como **Google Maps** y servicios de vuelos, el sistema busca generar itinerarios inteligentes basados en los intereses, presupuesto y tiempo disponible del usuario.

El objetivo principal es **centralizar la información necesaria para planear un viaje en una sola plataforma**, facilitando la toma de decisiones y mejorando la experiencia del usuario.

Entre las funcionalidades planteadas para el proyecto se encuentran la creación de viajes, generación de itinerarios personalizados, visualización de lugares mediante mapas y consulta de opciones de vuelos.

---

## Objetivo del proyecto

Desarrollar una aplicación web que permita a los usuarios organizar sus viajes de forma sencilla, personalizada y centralizada, reduciendo la necesidad de consultar múltiples plataformas para tomar decisiones durante la planificación.

### Propuesta de valor

SmartTrip Planner permite planificar un viaje personalizado desde un solo lugar, integrando información sobre destinos, actividades, mapas y opciones de transporte para facilitar la organización del viaje.

---

# Equipo del proyecto

El proyecto es desarrollado por el grupo **Modo Avión**, conformado por estudiantes de Ingeniería de Sistemas de la Pontificia Universidad Javeriana.

| Integrante | GitHub | Rol principal | Responsabilidades |
|---|---|---|---|
| **Daniel Mauricio Plazas López** | [@danielopez1806](https://github.com/danielopez1806) | Base de Datos | Diseño y gestión de la base de datos, persistencia de información y manejo de Oracle SQL Developer. |
| **Nikolas Hannee García Cure** | [@shadou09090](https://github.com/shadou09090) | Frontend | Desarrollo de la interfaz, creación de vistas y componentes, y consumo de endpoints. |
| **Juan Felipe Chaparro Jurado** | [@Juanchap](https://github.com/Juanchap) | Backend | Desarrollo de endpoints, lógica de negocio, validaciones, manejo de errores y pruebas de la API. |
| **Laura Salomé Carrero Guevara** | [@KURAPlRA](https://github.com/KURAPlRA) | Líder | Coordinación del equipo, seguimiento del proyecto, gestión de requisitos, integración y organización del trabajo. |

---

## Roles de gestión

Para la organización del proyecto se distribuyeron los roles de gestión de acuerdo con la metodología de trabajo establecida:

| Integrante | Rol de gestión |
|---|---|
| **Laura Salomé Carrero Guevara** | Scrum Master |
| **Daniel Mauricio Plazas López** | Sprint Planner | QA lead |
| **Nikolas Hannee García Cure** | Configuration Manager | DevOps Engineer |
| **Juan Felipe Chaparro Jurado** | Product owner |

Estos roles permiten distribuir las responsabilidades relacionadas con la planeación, gestión, calidad, configuración y desarrollo del proyecto.

---

# Funcionalidades principales

Las funcionalidades principales planteadas para SmartTrip Planner incluyen:

-  Registro de usuarios.
-  Inicio de sesión.
-  Creación de viajes.
-  Definición de fechas y tiempo disponible.
-  Consideración del presupuesto.
-  Selección de intereses y preferencias.
-  Generación de itinerarios personalizados.
-  Visualización de lugares de interés.
-  Integración con mapas.
-  Consulta de opciones de vuelos.
-  Guardado de itinerarios.

Las funcionalidades pueden evolucionar durante el desarrollo de acuerdo con los requisitos y las restricciones técnicas encontradas.


##  Cómo iniciar el Frontend

El frontend está desarrollado utilizando **React 18 y Vite**.

### Requisitos previos

Antes de ejecutar el proyecto es necesario contar con:

- **Node.js** v18 o superior.
- **npm** v9 o superior.
- Git.

### Pasos para ejecutar en desarrollo

#### 1. Clonar el repositorio

```bash
git clone https://github.com/shadou09090/SmartTrip-Planner.git
```

#### 2. Ingresar al directorio del proyecto

```bash
cd SmartTrip-Planner
```

#### 3. Navegar al directorio del frontend

```bash
cd frontend
```

#### 4. Instalar las dependencias

Si es la primera vez que se ejecuta el proyecto:

```bash
npm install
```

#### 5. Iniciar el servidor de desarrollo

```bash
npm run dev
```

#### 6. Abrir la aplicación

La aplicación estará disponible en:

**http://localhost:3000**

---

## Comandos disponibles

Dentro de la carpeta `frontend/` se encuentran los siguientes comandos:

| Comando | Descripción |
|---|---|
| `npm run dev` | Inicia el servidor de desarrollo de Vite con recarga rápida (HMR). |
| `npm run build` | Compila la aplicación optimizada para producción en la carpeta `dist/`. |
| `npm run preview` | Permite previsualizar localmente la compilación de producción. |

---

## Tecnologías del proyecto

### Frontend

- **React 18**
- **Vite**
- **Lucide React** — Biblioteca utilizada para los iconos.
- **CSS Modules** — Para la organización y estilos de los componentes.

### Backend

- **Java**
- **Spring Boot**
- **Maven**

El backend se encuentra ubicado en la carpeta:

```text
backend/
```

### Servicios externos

El proyecto contempla el uso de servicios externos para complementar las funcionalidades de planificación:

- **Google Maps API** — mapas, ubicaciones y rutas.
- **Servicios/API de vuelos** — consulta de opciones de transporte aéreo.

---

## Estructura del proyecto

La estructura general del repositorio se organiza de la siguiente manera:

```text
SmartTrip-Planner/
│
├── frontend/
│   ├── src/
│   ├── public/
│   ├── package.json
│   └── ...
│
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── ...
│
├── docs/
│   └── Documentación del proyecto
│
└── README.md
```

La estructura puede modificarse a medida que avance el desarrollo del proyecto.

---

# Integraciones externas

## Google Maps

La integración con **Google Maps** permitirá complementar la planificación de viajes mediante funcionalidades relacionadas con:

- Visualización de mapas.
- Ubicación de destinos.
- Lugares de interés.
- Cálculo de rutas.
- Información relacionada con desplazamientos.

## Servicios de vuelos

La aplicación contempla la integración con servicios o APIs de vuelos para consultar información como:

- Opciones de vuelos.
- Horarios.
- Precios.
- Aeropuertos.
- Alternativas de transporte.

En caso de que alguna integración externa no se encuentre disponible durante una etapa del desarrollo, se podrán utilizar datos simulados para implementar y probar la funcionalidad.

---

# Contexto académico

Este proyecto es desarrollado como parte de la asignatura:

**Fundamentos de Ingeniería de Software**

**Pontificia Universidad Javeriana**

El proyecto busca aplicar conceptos relacionados con:

- Ingeniería de requisitos.
- Historias de usuario.
- Casos de uso.
- Metodologías ágiles.
- Scrum.
- Estimación de software.
- Gestión de proyectos.
- Control de versiones.
- Desarrollo frontend y backend.
- Bases de datos.
- Pruebas de software.
- Integración de servicios externos.

---

# Repositorio

El código fuente, Issues, gestión del proyecto y demás elementos relacionados con el desarrollo se encuentran disponibles en:

[SmartTrip Planner - GitHub](https://github.com/shadou09090/SmartTrip-Planner)

---

# Grupo SmartTrip Planner

**Integrantes:**

- Daniel Mauricio Plazas López — `danielopez1806`
- Nikolas Hannee García Cure — `shadou09090`
- Juan Felipe Chaparro Jurado — `Juanchap`
- Laura Salomé Carrero Guevara — `KURAPlRA`

---

# Licencia

Este proyecto está bajo la **Licencia Apache 2.0**.

El proyecto fue desarrollado con fines académicos para la asignatura **Fundamentos de Ingeniería de Software** de la **Pontificia Universidad Javeriana**.
