# Gestor de Catálogos de Procesos

Aplicación de escritorio desarrollada con **JavaFX** que captura procesos del sistema operativo,
los normaliza, los persiste en **MySQL** mediante JDBC/HikariCP y expone un servicio REST
ligero implementado con **Javalin**. Además, incorpora exportación/importación JSON y una UI
para administrar catálogos y procesos asociados.

## Requisitos

- Java 21
- Maven 3.9+
- Servidor MySQL accesible

Configura la conexión en `src/main/resources/application.properties`:

```properties
app.db.url=jdbc:mysql://localhost:3306/processdb
app.db.user=process_user
app.db.password=process_pass
```

El archivo también permite ajustar el puerto REST (`app.rest.port`), dominios permitidos para CORS,
directorios de trabajo y heurísticas de expulsión.

## Ejecución

```bash
mvn clean javafx:run
```

Al iniciar se realizan estos pasos:

1. Verificación/creación de tablas `catalog` y `process` en MySQL.
2. Inicialización del capturador de procesos (`ProcessHandle` + heurísticas por SO).
3. Arranque del servidor REST (por defecto en `http://localhost:8080`).
4. Apertura de la UI JavaFX para gestionar catálogos y procesos.

## Funcionalidades destacadas

- **Captura Top-N** por CPU o MEM con limpieza de nombre/usuario.
- **Creación y edición de catálogos** (`nombre`, `descripción`, `origen`, `N`, `fecha`).
- **Persistencia MySQL** con integridad referencial y auditoría (`created_at`).
- **Gestión de procesos**: listado, detalle, edición de `descripcion`/`prioridad`/`expulsivo` y eliminación.
- **Archivos de actividad** por proceso almacenados en el directorio `app.files.baseDir`.
- **Exportación JSON** (`GET /api/catalogos/{id}/export` o desde la UI) hacia `app.export.dir`.
- **Importación JSON** desde `app.import.dir`, recreando archivos de actividad.
- **Servicio REST JSON** con paginación, ordenamiento y errores estandarizados.
- **Health check** en `GET /api/health` para monitoreo.

## Resumen de endpoints REST

| Método | Ruta                                 | Descripción                                    |
| ------ | ------------------------------------ | ---------------------------------------------- |
| GET    | `/api/health`                        | Estado general del servicio.                    |
| GET    | `/api/catalogos`                     | Lista paginada de catálogos (`page`, `size`).   |
| POST   | `/api/catalogos`                     | Captura Top-N y crea un nuevo catálogo.         |
| GET    | `/api/catalogos/{id}`                | Detalle del catálogo.                           |
| DELETE | `/api/catalogos/{id}`                | Elimina catálogo y procesos asociados.          |
| GET    | `/api/catalogos/{id}/procesos`       | Lista de procesos con filtros opcionales.       |
| GET    | `/api/catalogos/{id}/procesos/{idp}` | Detalle de un proceso.                          |
| PATCH  | `/api/catalogos/{id}/procesos/{idp}` | Actualiza `descripcion`, `prioridad`, `expulsivo`. |
| GET    | `/api/catalogos/{id}/export`         | Exporta catálogo y procesos (JSON).             |
| POST   | `/api/catalogos/import`              | Importa catálogo desde JSON.                    |

Los errores se devuelven en JSON con la forma:

```json
{ "code": "VALIDATION_ERROR", "message": "Detalle", "details": ["..."] }
```

## Directorios y logging

- `app.files.baseDir`: archivos de actividad (un TXT por proceso).
- `app.export.dir`: destino de exportaciones JSON.
- `app.import.dir`: directorio sugerido en el diálogo de importación.

`logback.xml` configura salida por consola (`INFO`) para todos los paquetes `com.pm`.
Los eventos importantes (capturas, importaciones, exportaciones) quedan registrados vía SLF4J.

## Próximos pasos sugeridos

- Añadir autenticación al API REST.
- Publicar binarios nativos (jlink/jpackage) con configuración incluida.
- Integrar filtros avanzados (usuario, expulsivo) directamente en la interfaz.
