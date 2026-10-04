# bytemarket-support-service

Libro de reclamaciones, obligatorio para comercios en Perú.

Parte del sistema **ByteMarket**, una tienda de repuestos y accesorios para
celulares construida con microservicios Spring Boot y un frontend Nuxt.

**Puerto 8084** · Base de datos `bytemarket_support`

## API

Todo entra por el gateway (`http://localhost:8085`), no directamente al 8084.

### Público

| Método | Ruta | Qué hace |
|---|---|---|
| `POST` | `/api/reclamaciones` | Registra el reclamo. Acepta `application/json` o `multipart/form-data` con adjunto |
| `GET` | `/api/reclamaciones/adjuntos/{nombre}` | Descarga un adjunto |

Campos del reclamo: `customerName`, `tipoDocumento` (DNI, CE, Pasaporte),
`numeroDocumento`, `direccion`, `telefono`, `email`, `tipoBien` (Producto o
Servicio), `descripcionBien`, `monto?`, `tipoReclamo` (Reclamo o Queja),
`descripcion`, `pedido?`.

Adjunto: hasta **5 MB**. Al registrarse se genera un **código correlativo**
con el año, que es lo que el cliente usa para dar seguimiento.

### Panel — requiere rol `admin` o `superadmin`

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/admin/reclamaciones` | Listado |
| `GET` | `/api/admin/reclamaciones/{id}` | Detalle |
| `PUT` | `/api/admin/reclamaciones/{id}` | Responde o cambia el estado. Body: `estado?`, `respuesta?` |

Estados: `pendiente`, `en_proceso`, `respondido`, `cerrado`.

### El correo al cliente

Al guardar una `respuesta` se envía un correo al cliente, **solo si el texto
cambió**: reabrir y volver a guardar lo mismo no reenvía el aviso.

La respuesta dice lo que de verdad pasó:

```json
{ "status": 200, "message": "Actualizado", "emailEnviado": true }
```

Si el envío falla, el reclamo **igualmente queda guardado** y responde
`emailEnviado: false` con un `emailAviso`. Decir "enviado" cuando no salió
sería peor que no avisar: el encargado creería que el cliente ya lo sabe.

> Gmail en el puerto 587 exige **STARTTLS** (ya configurado) y una
> **contraseña de aplicación** de Google, no la del correo.

## Cómo levantarlo

Requisitos: **Java 17+**, **MySQL 8** en `localhost:3306` y el
`bytemarket-eureka-server` ya arrancado (salvo que este repo *sea* Eureka).

```bash
cp .env.example .env     # y rellena los valores
./mvnw spring-boot:run
```

Queda escuchando en el puerto **8084**. El esquema de base de datos se crea
solo al arrancar (`createDatabaseIfNotExist=true`).

## Configuración

Las credenciales se leen del `.env`, que **no se versiona**. Los
`application*.yml` solo traen marcadores: si falta el `.env`, los valores
sensibles quedan vacíos. Mira `.env.example` para saber qué rellenar.

> El `JWT_SECRET` debe ser **idéntico** en user, catalog, order y support:
> user-service firma el token y los demás verifican la firma. Si difieren,
> todas las peticiones autenticadas fallan con 401 sin dejar rastro en el log.

## El sistema completo

| Repositorio | Puerto | Función |
|---|---|---|
| `bytemarket-eureka-server` | 8761 | Registro de servicios |
| `bytemarket-api-gateway` | 8085 | Punto de entrada único; enruta a los demás |
| `bytemarket-user-service` | 8081 | Cuentas, autenticación JWT, perfiles |
| `bytemarket-catalog-service` | 8082 | Productos, categorías, banners, inventario |
| `bytemarket-order-service` | 8083 | Pedidos, métodos de pago, cupones, reportes |
| `bytemarket-support-service` | 8084 | Libro de reclamaciones |
| `frontend-bytemarket` | 3000 | Tienda y panel de administración (Nuxt 3) |

Orden de arranque: **Eureka primero**, luego los servicios de negocio, el
gateway al final y el frontend cuando el gateway responda.
