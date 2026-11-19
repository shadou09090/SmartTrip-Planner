# SmartTrip Planner: ejecución y despliegue

## Estado de esta etapa

El backend tiene persistencia relacional para usuarios/viajes y persistencia del documento integral del paquete en `PAQUETE_DOCUMENTO`/`PAQUETE_SOLICITUD`, bajo el perfil `oracle`. El perfil predeterminado sigue siendo `local-no-db`; para usar Oracle se debe activar explícitamente `oracle`. Hibernate está configurado con `ddl-auto=none` y la inicialización SQL permanece desactivada. El paquete solo se habilita con `SMARTTRIP_PACKAGE_PERSISTENCE_ENABLED=true` en el backend y `VITE_SMARTTRIP_PACKAGE_PERSISTENCE=true` en el build del frontend, una vez que las tablas están disponibles.

El frontend usa `VITE_API_BASE_URL` para llamar al backend. Esa variable solo contiene una URL pública. No se deben definir variables `VITE_*` para la contraseña de Oracle, el Wallet ni la clave JWT.

## Contratos HTTP

Base URL: la URL pública del servicio Spring Boot.

| Método y ruta | Acceso | Solicitud / resultado |
| --- | --- | --- |
| `POST /api/auth/signup` | Público | `{ "name": "Nombre completo", "email": "...", "password": "..." }`; devuelve `201` y texto de confirmación. El frontend combina nombre y apellido. Correo duplicado devuelve `409` genérico. |
| `POST /api/auth/login` | Público | `{ "email": "...", "password": "..." }`; devuelve `{ "accessToken": "…", "tokenType": "Bearer", "expiresIn": 1200, "user": { "id", "name", "email" } }`. Credenciales incorrectas devuelven `401` genérico. |
| `POST /api/viajes` | Bearer JWT | `{ "origen": "…", "destino": "…", "fechaInicio": "YYYY-MM-DD", "fechaFin": "YYYY-MM-DD", "presupuesto": 0 }`. El cliente no envía `ID_USUARIO`; el backend lo toma del token. Devuelve `201` con el viaje persistido. |
| `GET /api/viajes` | Bearer JWT | Lista únicamente los viajes del usuario autenticado. |
| `GET /api/viajes/{id}` | Bearer JWT | Detalle del viaje si pertenece al usuario; otro propietario y un ID inexistente reciben el mismo `404`. |
| `PUT /api/viajes/{id}/paquete` | Bearer JWT + `Idempotency-Key` | Guarda el snapshot del paquete con revisión esperada e idempotencia. El cliente lo verifica con GET antes de confirmar éxito. |
| `GET /api/viajes/{id}/paquete` | Bearer JWT | Recupera el snapshot y la revisión del paquete; 404 significa que aún no existe snapshot. |

Los tokens HS256 duran 20 minutos y el frontend los conserva solo en memoria. Al recargar la página se inicia sesión de nuevo. No hay endpoint de actualización ni eliminación de viajes en esta etapa. Los intereses seleccionados siguen siendo locales al formulario; no se persisten en `VIAJE_INTERES`. Los borradores locales protegen cambios no guardados; no equivalen a persistencia en Oracle.

## Variables privadas del backend

Configurar solo en el entorno del backend:

- `SPRING_PROFILES_ACTIVE=oracle`
- `ORACLE_WALLET_PATH`: carpeta que contiene `tnsnames.ora`, `sqlnet.ora` y `cwallet.sso`.
- `ORACLE_TNS_ALIAS=smarttripdb_high`
- `ORACLE_DB_USERNAME=ADMIN`
- `ORACLE_DB_PASSWORD`: credencial de Oracle, nunca en Git.
- `SMARTTRIP_JWT_SECRET`: clave Base64 aleatoria de al menos 32 bytes decodificados; no reutilizar la contraseña Oracle.
- `SMARTTRIP_CORS_ALLOWED_ORIGINS`: lista separada por comas de orígenes exactos autorizados.
- `SMARTTRIP_PACKAGE_PERSISTENCE_ENABLED=true`: habilitar el snapshot solo tras comprobar las tablas en el esquema destino y activar el build frontend correspondiente.
- `PORT`: la plataforma lo proporciona; localmente el perfil usa `8080` por defecto.

Generar una clave JWT temporal para una consola PowerShell local:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:SMARTTRIP_JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

En IntelliJ, definirla como variable de entorno privada en la configuración de ejecución `Backend Oracle`; no pegarla en XML versionado. Las cuatro variables Oracle ya existentes deben seguir heredándose de forma segura. El origen local de Vite es `http://localhost:3000`.

## Frontend local y Vercel

Desde `frontend/`:

```powershell
npm ci
npm run dev
```

El servidor Vite usa `http://localhost:3000`. `frontend/.env.example` muestra la variable local. Para Vercel, conectar el repositorio y configurar **Root Directory** como `frontend`, framework Vite, comando de instalación `npm ci`, comando de build `npm run build` y salida `dist`. `frontend/vercel.json` contiene el fallback SPA.

Crear en Vercel `VITE_API_BASE_URL=https://<host-publico-del-backend>` para Preview y Production. Este valor queda incorporado al JavaScript público, por lo que debe ser únicamente la URL HTTPS del backend. Nunca configurar en Vercel variables Oracle, el Wallet o `SMARTTRIP_JWT_SECRET`.

## Backend en Render

El repositorio incluye `backend/Dockerfile` (Java 17), un entrypoint que desempaqueta un Wallet temporal desde un secret file de Render, y `render.yaml` fijado a `deploy/smartrip-oracle-vercel` con auto-deploy desactivado. El endpoint `/healthz` consulta Oracle (`SELECT 1 FROM DUAL`) y devuelve solo `UP` o `DOWN`; Render lo usa como health check. El blueprint usa el plan `starter`, que es de pago. **No se creó ni se desplegó ningún servicio**; importar ese blueprint puede crear un servicio facturable, así que requiere autorización y revisión manual antes de continuar.

Antes de crear el servicio:

1. En Render, revisar el blueprint, región y plan. Confirmar el costo vigente en el panel antes de crearlo.
2. En el servicio, agregar como Secret File `oracle-wallet.zip.b64` y pegar el Base64 de un ZIP que contenga los archivos del Wallet en su raíz. Render monta ese archivo como `/etc/secrets/oracle-wallet.zip.b64`; el entrypoint lo extrae a la ruta temporal de `ORACLE_WALLET_PATH` y borra el ZIP temporal. El secreto no se copia a la imagen Docker.
3. Rotar primero todas las credenciales expuestas en la antigua configuración local. Configurar los nuevos valores de `ORACLE_DB_PASSWORD`, `SMARTTRIP_JWT_SECRET` y `SMARTTRIP_CORS_ALLOWED_ORIGINS` como valores privados en Render; no reutilizar secretos antiguos. Mantener `ORACLE_TNS_ALIAS=smarttripdb_high` y `ORACLE_DB_USERNAME=ADMIN`. Mantener `SMARTTRIP_PACKAGE_PERSISTENCE_ENABLED=false` hasta que las tablas/columnas se hayan comprobado; cambiar a `true` solo tras verificar que el servicio conecta al mismo esquema Oracle y que el CLOB de paquete está listo.
4. En `SMARTTRIP_CORS_ALLOWED_ORIGINS`, incluir solo el dominio Vercel de producción y, si se van a probar, los dominios preview específicos. No usar `*`.
5. Confirmar que la lista de acceso de red de Autonomous Database permite las IPs de salida de Render. El alias usa TCPS por el puerto 1522. Render muestra los rangos de salida asociados a la región/servicio; no abrir la base a todo Internet.
6. Revisar los logs del primer arranque: se deben ver el perfil `oracle`, Hikari y la inicialización de Hibernate, sin habilitar generación de esquema.

Render permite secret files en el sistema de archivos del servicio y limita el tamaño combinado de esos archivos; verificar que el Base64 del Wallet cumpla el límite vigente (actualmente 1 MB) antes de configurarlo. Si no cabe, no guardar el ZIP en Git ni en una imagen Docker: elegir un mecanismo privado de montaje/secretos compatible con el host.

Preparar el archivo Base64 desde PowerShell, guardándolo fuera del repositorio y sin mostrarlo en terminal:

```powershell
$walletPath = 'C:\ruta\local\al\Wallet'
$zipPath = Join-Path $env:TEMP 'oracle-wallet-deploy.zip'
$base64Path = Join-Path $env:TEMP 'oracle-wallet-deploy.zip.b64'
Compress-Archive -Path (Join-Path $walletPath '*') -DestinationPath $zipPath -Force
$encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes($zipPath))
[IO.File]::WriteAllText($base64Path, $encoded)
(Get-Item $base64Path).Length
```

Solo pegar el contenido del archivo `.b64` en el campo Secret File de Render. No añadir ninguno de esos archivos temporales al repositorio.

## Pruebas

Pruebas sin Oracle:

```powershell
mvn -f backend/pom.xml -B test
```

La prueba real de integración es opt-in y solo se ejecuta con `-Dsmarttrip.oracle.integration=true`, el perfil `oracle`, las variables privadas disponibles y la aprobación de escribir datos de prueba. Genera correos aleatorios, verifica registro/hash/login/viaje/lista/detalle/aislamiento y elimina por IDs exactos los viajes y usuarios que creó. No ejecuta DDL ni modifica datos preexistentes.

## Aún pendiente antes de publicar

- Crear manualmente el servicio backend en Render después de aprobar el plan/costo.
- Cargar el Wallet como secret file y configurar credenciales/clave JWT/CORS en Render.
- Confirmar acceso de red desde Render a Autonomous Database.
- Crear el proyecto Vercel con raíz `frontend` y configurar solo la URL pública del backend.
- Probar desde los dominios reales de Vercel. Ningún despliegue se realizó desde este trabajo.
