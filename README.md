# Gestor de Catálogos de Procesos

Aplicación de escritorio construida con **JavaFX** que captura procesos del sistema operativo,
normaliza sus datos, los guarda en **MySQL** y expone catálogos y procesos mediante un
**servicio REST JSON** ligero con **Javalin**. Incluye una interfaz para capturar, buscar y
administrar los catálogos, además de exportar e importar la información en formato JSON.

## Requisitos

- Java 21
- Maven 3.9+
- Servidor MySQL accesible

Configura la conexión en `src/main/resources/application.properties`:

```properties
app.db.url=jdbc:mysql://localhost:3306/processdb
app.db.user=process_user
app.db.password=process_pass
app.rest.port=8080
app.rest.cors.allowedOrigin=http://localhost:5173
app.files.baseDir=data/process-files
app.export.dir=export
app.import.dir=import
```

El archivo también permite ajustar el tamaño del pool JDBC, el muestreo de captura y las
heurísticas usadas para marcar procesos como expulsivos.

## Cómo ejecutar

```bash
mvn clean javafx:run
```

Al iniciar, la aplicación:

1. Crea (si no existen) las tablas `catalog` y `process` en MySQL, junto con índices por catálogo y PID.
2. Inicializa el servicio de captura basado en `ProcessHandle`, complementado con datos específicos por SO.
3. Genera los directorios configurados para archivos de actividad, exportaciones e importaciones.
4. Arranca el servidor REST en el puerto configurado (por defecto `http://localhost:8080`).
5. Abre la interfaz JavaFX para administrar catálogos y procesos.

## Recorrido por la aplicación de escritorio

- **Captura Top-N**: desde la vista principal se ingresan `N`, el criterio (`CPU` o `MEMORY`),
  nombre y descripción del catálogo. Se crea el lote con normalización de nombre/usuario y evaluación
  de expulsividad (`false` para procesos del sistema).
- **Archivos de actividad**: cada proceso genera un `.txt` con la descripción dentro de
  `app.files.baseDir`, permitiendo calcular ráfagas a partir del número de caracteres.
- **Catálogos persistidos**: la tabla de la izquierda permite buscar por nombre/descripción,
  filtrar por criterio y cargar hasta 100 registros ordenados por fecha descendente.
- **Procesos con filtros avanzados**: la tabla derecha ofrece búsqueda por nombre,
  filtrado por usuario y expulsivo/no expulsivo, además de ordenamientos rápidos
  (recientes, CPU, memoria, prioridad o nombre).
- **Gestión CRUD**: se actualizan descripción/prioridad/expulsivo de cada proceso,
  se eliminan procesos individuales y también catálogos completos (con cascada).
- **Exportar/Importar**: el catálogo seleccionado se exporta como JSON formateado a
  `app.export.dir`; desde el diálogo de importación se cargan archivos válidos que
  recrean catálogos y sus archivos de actividad.

## Servicio REST JSON

Los datos capturados se publican en JSON para que otras aplicaciones los consuman.

| Método | Ruta                                 | Descripción                                           |
| ------ | ------------------------------------ | ----------------------------------------------------- |
| GET    | `/api/health`                        | Estado básico del servicio y sello de tiempo.         |
| GET    | `/api/catalogos`                     | Lista paginada de catálogos (búsqueda y orden).       |
| POST   | `/api/catalogos`                     | Captura Top-N y crea un nuevo catálogo.               |
| GET    | `/api/catalogos/{id}`                | Detalle de un catálogo con metadatos.                 |
| DELETE | `/api/catalogos/{id}`                | Elimina un catálogo y sus procesos asociados.         |
| GET    | `/api/catalogos/{id}/procesos`       | Lista de procesos con filtros por usuario, expulsivo. |
| GET    | `/api/catalogos/{id}/procesos/{idp}` | Detalle individual de un proceso.                     |
| PATCH  | `/api/catalogos/{id}/procesos/{idp}` | Actualiza descripción, prioridad y expulsivo.         |
| GET    | `/api/catalogos/{id}/export`         | Exporta catálogo y procesos como JSON.                |
| POST   | `/api/catalogos/import`              | Importa un catálogo desde un JSON previamente exportado. |

Los errores se devuelven con el formato:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Detalle",
  "details": ["lista de errores"]
}
```

## Directorios y logging

- `app.files.baseDir`: archivos de actividad (un TXT por proceso).
- `app.export.dir`: destino de exportaciones JSON desde la UI o el API.
- `app.import.dir`: ubicación sugerida al importar catálogos.

`logback.xml` configura salida por consola en nivel `INFO` para toda la aplicación y
habilita `DEBUG` para la captura de procesos, facilitando el diagnóstico durante el muestreo.
