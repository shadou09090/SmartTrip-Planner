# SmartTrip Planner

## Descripción

**SmartTrip Planner** es una aplicación web integral diseñada para ayudar a los usuarios a planificar viajes de manera eficiente, inteligente y personalizada.

El sistema centraliza en una sola plataforma la creación de viajes, la generación automática de itinerarios estructurados día a día según el presupuesto, fechas e intereses del viajero, la visualización geográfica interactiva en mapas, el cálculo preciso de rutas y tiempos de traslado entre actividades, y la consulta de opciones de vuelos y alojamiento en tiempo real.

A través del uso de servicios e integraciones externas modernas (**OpenStreetMap**, **Leaflet**, **OpenRouteService**, **Geoapify**, **Duffel API**, **OBO Travel** y **Wikipedia / Wikidata**), SmartTrip Planner elimina la dispersión de información y simplifica la toma de decisiones al momento de organizar un viaje.

---

## Objetivo del proyecto

Desarrollar una aplicación web que permita a los usuarios organizar sus viajes de forma sencilla, personalizada y centralizada, reduciendo la necesidad de consultar múltiples plataformas para tomar decisiones durante la planificación.

### Propuesta de valor

SmartTrip Planner permite planificar un viaje personalizado desde un solo lugar, integrando destinos, cálculo automático de traslados, detección de tiempos excesivos de viaje, cotizaciones de vuelos y hoteles, mapas interactivos y ordenamiento flexible de actividades para ofrecer una experiencia de usuario fluida y completa.

---

## Equipo del proyecto

El proyecto es desarrollado por el grupo **Modo Avión**, conformado por estudiantes de Ingeniería de Sistemas de la Pontificia Universidad Javeriana.

| Integrante | GitHub | Rol principal | Responsabilidades |
|---|---|---|---|
| **Daniel Mauricio Plazas López** | [@danielopez1806](https://github.com/danielopez1806) | Base de Datos | Diseño y gestión de la base de datos, persistencia de información y manejo de Oracle Autonomous Database. |
| **Nikolas Hannee García Cure** | [@shadou09090](https://github.com/shadou09090) | Frontend | Desarrollo de la interfaz, creación de vistas y componentes, integración de mapas y consumo de endpoints. |
| **Juan Felipe Chaparro Jurado** | [@Juanchap](https://github.com/Juanchap) | Backend | Desarrollo de endpoints, lógica de negocio, seguridad con JWT, validaciones y pruebas de la API. |
| **Laura Salomé Carrero Guevara** | [@KURAPlRA](https://github.com/KURAPlRA) | Líder | Coordinación del equipo, seguimiento del proyecto, gestión de requisitos, integración y organización del trabajo. |

---

## Roles de gestión

Para la organización del proyecto se distribuyeron los roles de gestión de acuerdo con la metodología de trabajo establecida:

| Integrante | Rol de gestión |
|---|---|
| **Laura Salomé Carrero Guevara** | Scrum Master |
| **Daniel Mauricio Plazas López** | Sprint Planner / QA Lead |
| **Nikolas Hannee García Cure** | Configuration Manager / DevOps Engineer |
| **Juan Felipe Chaparro Jurado** | Product Owner |

---

## Funcionalidades principales

El sistema cuenta con las siguientes funcionalidades implementadas:

- **Autenticación y Gestión de Usuarios:** Registro e inicio de sesión seguros mediante tokens JWT (JSON Web Tokens) stateless, con contraseñas cifradas en BCrypt.
- **Creación y Gestión de Viajes:** Registro de viajes especificando origen, destino, fechas (inicio y fin), presupuesto asignado y selección de intereses turísticos.
- **Generación Automática de Itinerarios:** Creación inteligente y estructurada de días de viaje con actividades sugeridas acordes a las preferencias y presupuesto del usuario.
- **Reordenamiento y Edición de Actividades:** Capacidad de reordenar actividades dentro de un día o entre días del itinerario.
- **Cálculo de Tiempos y Rutas (OpenRouteService):** Estimación precisa de tiempos y distancias entre paradas sucesivas, incluyendo alertas visuales si un traslado supera los 120 minutos.
- **Visualización en Mapa Interactivo (Leaflet & OpenStreetMap):** Mapa interactivo que muestra las ubicaciones de los destinos, atractivos turísticos y alojamientos.
- **Consulta de Vuelos en Tiempo Real (Duffel API):** Búsqueda de ofertas y tarifas de transporte aéreo entre las ciudades de origen y destino.
- **Consulta de Alojamiento (OBO Travel):** Visualización de opciones de hospedaje y cotizaciones de hoteles en el destino seleccionado.
- **Información Enriquecida de Destinos:** Integración con Wikipedia y Wikidata para complementar actividades con descripciones culturales e imágenes georreferenciadas.
- **Persistencia Flexible:** Soporte para base de datos H2 en desarrollo local (sin dependencias externas) y Oracle Autonomous Database (ATP) con Oracle Wallet en producción.

---

## Cómo iniciar el Backend

El backend está desarrollado en **Java 17** con el framework **Spring Boot 4.1.0** y **Maven**.

### Requisitos previos

- **Java JDK 17** o superior instalado y configurado en el sistema (`JAVA_HOME`).
- **Maven 3.8+** (opcional, el repositorio incluye los ejecutables del Maven Wrapper `mvnw` y `mvnw.cmd`).
- Git.

### Pasos para ejecutar en desarrollo local (Base de datos H2)

El entorno local por defecto utiliza una base de datos embebida **H2** en archivo local (`./data/smarttrip`), lo que permite ejecutar y probar todo el backend inmediatamente sin requerir credenciales ni instalación de bases de datos externas.

#### 1. Navegar al directorio del backend

```bash
cd backend
```

#### 2. Iniciar el servidor

- En **Windows (PowerShell o CMD)**:

```powershell
.\mvnw.cmd spring-boot:run
```

- En **Linux o macOS**:

```bash
./mvnw spring-boot:run
```

*(Si tienes Maven instalado globalmente, también puedes ejecutar `mvn spring-boot:run`).*

#### 3. Verificar el estado del backend

El backend iniciará en el puerto **8080**. Puedes validar su estado consultando el endpoint de verificación:

- **Health check:** `http://localhost:8080/healthz`
- **Respuesta esperada:** `{"status":"UP"}`

### Ejecución con Oracle Autonomous Database (Perfil de producción)

Para conectar el backend a la base de datos de producción en Oracle Cloud (ATP) utilizando el Oracle Wallet, se debe activar el perfil `oracle` y suministrar las variables de entorno correspondientes:

#### Variables de entorno requeridas:

| Variable | Descripción | Ejemplo / Valor |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring Boot | `oracle` |
| `ORACLE_WALLET_PATH` | Ruta absoluta al directorio del Wallet descomprimido | `/app/wallet` o `C:\wallets\SmartTrip` |
| `ORACLE_DB_USERNAME` | Usuario de la base de datos Oracle | `ADMIN` o usuario de aplicación |
| `ORACLE_DB_PASSWORD` | Contraseña del usuario Oracle | `TuPassword123` |
| `ORACLE_TNS_ALIAS` | Alias de conexión TNS definido en `tnsnames.ora` | `smarttripdb_high` |
| `SMARTTRIP_JWT_SECRET` | Clave secreta para firmar tokens JWT (mínimo 32 caracteres) | `TuClaveSecretaDeAlMenos32Caracteres!` |
| `SMARTTRIP_CORS_ALLOWED_ORIGINS` | Orígenes permitidos para peticiones CORS | `http://localhost:3000,https://smartrip-planner.vercel.app` |

#### Ejecución con perfil Oracle:

- **Windows (PowerShell):**

```powershell
$env:SPRING_PROFILES_ACTIVE="oracle"
$env:ORACLE_WALLET_PATH="C:\ruta\al\wallet"
$env:ORACLE_DB_USERNAME="ADMIN"
$env:ORACLE_DB_PASSWORD="TuPassword"
$env:SMARTTRIP_JWT_SECRET="ClaveSecretaDeAlMenos32Caracteres12345"
.\mvnw.cmd spring-boot:run
```

- **Linux / macOS:**

```bash
export SPRING_PROFILES_ACTIVE=oracle
export ORACLE_WALLET_PATH=/ruta/al/wallet
export ORACLE_DB_USERNAME=ADMIN
export ORACLE_DB_PASSWORD=TuPassword
export SMARTTRIP_JWT_SECRET=ClaveSecretaDeAlMenos32Caracteres12345
./mvnw spring-boot:run
```

### Ejecutar pruebas del Backend

El backend cuenta con una completa suite de más de 250 pruebas automatizadas que validan controladores, servicios de lógica de negocio, seguridad y persistencia:

- En **Windows**:

```powershell
.\mvnw.cmd test
```

- En **Linux / macOS**:

```bash
./mvnw test
```

---

## Cómo iniciar el Frontend

El frontend está desarrollado utilizando **React 18** y **Vite 6**.

### Requisitos previos

- **Node.js** v18 o superior.
- **npm** v9 o superior.
- Git.

### Pasos para ejecutar en desarrollo

#### 1. Navegar al directorio del frontend

```bash
cd frontend
```

#### 2. Instalar dependencias

```bash
npm install
```

#### 3. Iniciar el servidor de desarrollo

```bash
npm run dev
```

#### 4. Abrir la aplicación

La aplicación web estará disponible en:

**http://localhost:3000**

*Nota: Durante el desarrollo local, Vite redirige automáticamente las peticiones dirigidas a `/api/*` hacia `http://localhost:8080`, por lo que el frontend se comunica de forma transparente con el backend sin necesidad de configuración adicional.*

### Ejecutar pruebas del Frontend

El frontend cuenta con una suite de pruebas unitarias que validan el renderizado de vistas, cálculo de presupuestos, validaciones de formularios y manejo de itinerarios:

```bash
npm test
```

---

## Comandos disponibles

### Comandos del Backend (`backend/`)

| Comando (Windows) | Comando (Linux/macOS) | Descripción |
|---|---|---|
| `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` | Inicia la API de Spring Boot en modo desarrollo local (puerto 8080). |
| `.\mvnw.cmd test` | `./mvnw test` | Ejecuta la suite de pruebas automatizadas con JUnit y Mockito. |
| `.\mvnw.cmd clean package` | `./mvnw clean package` | Compila el proyecto y genera el artefacto ejecutable JAR en `target/`. |
| `.\mvnw.cmd clean compile` | `./mvnw clean compile` | Limpia y compila el código fuente Java. |

### Comandos del Frontend (`frontend/`)

| Comando | Descripción |
|---|---|
| `npm run dev` | Inicia el servidor de desarrollo de Vite en `http://localhost:3000` con Hot Module Replacement (HMR). |
| `npm test` | Ejecuta las pruebas unitarias del frontend mediante el test runner nativo de Node.js (`node --test`). |
| `npm run build` | Compila y optimiza la aplicación para producción en la carpeta `dist/`. |
| `npm run preview` | Previsualiza localmente la compilación de producción generada en `dist/`. |

---

## Tecnologías del proyecto

### Frontend

- **React 18** — Biblioteca principal para la construcción de interfaces de usuario.
- **Vite 6** — Herramienta de compilación y servidor de desarrollo.
- **Leaflet & React-Leaflet** — Renderizado e interacción de mapas geográficos basados en OpenStreetMap.
- **Lucide React** — Biblioteca de iconografía moderna.
- **CSS Modules & Vanilla CSS** — Estilos modulares, diseño adaptable y responsivo.
- **Node.js Test Runner (`node --test`) & JSDOM** — Ejecución de pruebas unitarias de componentes e interfaces.

### Backend

- **Java 17 (LTS)** — Lenguaje de programación.
- **Spring Boot 4.1.0** — Framework backend empresarial.
- **Spring Security & OAuth2 Resource Server** — Control de acceso, protección de rutas y autenticación stateless con JWT.
- **Spring Data JPA & Hibernate** — Capa de persistencia, mapeo objeto-relacional y repositorios.
- **Nimbus JOSE + JWT** — Generación, firmado y verificación criptográfica de tokens JWT.
- **H2 Database** — Motor de base de datos relacional ligero en archivo para desarrollo local.
- **Oracle Autonomous Database (ATP 21c)** — Motor de base de datos en la nube para entornos de producción.
- **JUnit 5 & Mockito** — Pruebas unitarias y de integración automatizadas.
- **Maven Wrapper (`mvnw`)** — Construcción reproducible del proyecto.

### Despliegue e Infraestructura

- **Frontend:** Desplegado en **Vercel** con integración y entrega continua (CI/CD).
- **Backend:** Desplegado en una máquina virtual de **Oracle Cloud Infrastructure (OCI Compute)** con Oracle Linux y gestionado como servicio `systemd`.
- **Base de datos:** **Oracle Autonomous Transaction Processing (ATP)** con autenticación segura mTLS mediante Oracle Wallet.

---

## Servicios e integraciones externas

El sistema integra múltiples servicios externos especializados para enriquecer la experiencia de viaje:

1. **OpenRouteService (ORS):** Cálculo de matrices de distancia y tiempos estimados de desplazamiento entre actividades sucesivas del itinerario (a pie o en vehículo), permitiendo alertar al usuario si una ruta excede los 120 minutos.
2. **Geoapify:** Autocompletado y geocodificación de ciudades y destinos turísticos.
3. **OpenStreetMap & Overpass API:** Consulta y georreferenciación de lugares de interés (POI), atractivos turísticos, actividades culturales y alojamientos.
4. **Duffel API:** Búsqueda en tiempo real de itinerarios, disponibilidad y tarifas de vuelos comerciales.
5. **OBO Travel:** Consulta de opciones hoteleras y cotizaciones de hospedaje en el destino seleccionado.
6. **Wikipedia / Wikidata Geosearch:** Extracción de descripciones turísticas, reseñas culturales e imágenes representativas de los puntos de interés.

*Nota: Los servicios externos cuentan con mecanismos de tolerancia a fallos y datos de respaldo para garantizar la operatividad de la aplicación en entornos de desarrollo sin conectividad o claves API activas.*

---

## Estructura del proyecto

```text
SmartTrip-Planner/
│
├── frontend/                     # Aplicación cliente en React + Vite
│   ├── src/
│   │   ├── components/           # Componentes reutilizables (Navbar, MapPlaces, etc.)
│   │   ├── pages/                # Vistas principales (Login, Register, Dashboard, Trips, etc.)
│   │   ├── services/             # Clientes de API y servicios de comunicación HTTP
│   │   ├── styles/               # Archivos de estilos globales y modulares
│   │   └── App.jsx               # Enrutador principal de la aplicación
│   ├── public/                   # Recursos estáticos
│   ├── tests/                    # Pruebas unitarias con node:test y JSDOM
│   ├── package.json              # Dependencias y scripts del frontend
│   └── vite.config.js            # Configuración de Vite y proxy reverso
│
├── backend/                      # API REST en Spring Boot
│   ├── src/
│   │   ├── main/java/com/project/Back_end_proyecto/
│   │   │   ├── controller/       # Controladores REST (Auth, Viaje, Itinerario, etc.)
│   │   │   ├── service/          # Lógica de negocio y servicios externos
│   │   │   ├── repository/       # Repositorios Spring Data JPA
│   │   │   ├── model/            # Entidades JPA (Usuario, Viaje, Itinerario, etc.)
│   │   │   ├── dto/              # Objetos de transferencia de datos (DTOs)
│   │   │   └── security/         # Configuración de seguridad, filtros y JWT
│   │   └── resources/
│   │       ├── application.properties          # Configuración base y perfil local (H2)
│   │       └── application-oracle.properties   # Configuración de producción (Oracle Cloud)
│   ├── pom.xml                   # Configuración de dependencias Maven
│   ├── mvnw                      # Maven Wrapper para Linux/macOS
│   └── mvnw.cmd                  # Maven Wrapper para Windows
│
├── Docs/                         # Documentación del proyecto
│   ├── deployment/               # Guías detalladas de despliegue en OCI y Vercel
│   └── ...                       # Historias de usuario y artefactos de gestión
│
└── README.md                     # Documentación general del repositorio
```

---

## Contexto académico

Este proyecto es desarrollado como parte de la asignatura:

**Fundamentos de Ingeniería de Software**

**Pontificia Universidad Javeriana**

El proyecto aplica y consolida conceptos clave de la ingeniería de software:

- Ingeniería de requisitos e historias de usuario (HU).
- Metodologías ágiles y marco de trabajo Scrum.
- Control de versiones y flujo de trabajo colaborativo en Git.
- Arquitectura desacoplada Frontend / Backend.
- Seguridad en APIs REST y autenticación con tokens JWT.
- Integración continua y despliegue continuo (CI/CD) en la nube.
- Pruebas automatizadas unitarias y de integración.
- Persistencia políglota y gestión de bases de datos relacionales en la nube.

---

## Repositorio

El código fuente, issues, seguimiento de sprints y lanzamientos se encuentran disponibles en:

[SmartTrip Planner - GitHub](https://github.com/shadou09090/SmartTrip-Planner)

---

## Licencia

Este proyecto está bajo la **Licencia Apache 2.0**.
