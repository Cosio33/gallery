
---
# Modificaciones a IntentHandler.kt: Operaciones con archivos en sandbox
---
## Resumen

Se ha extendido el `IntentHandler` original de la app AI Edge Gallery para soportar operaciones básicas de gestión de archivos dentro de una **carpeta sandbox** segura (`/data/data/.../files/sandbox/`). Las nuevas acciones permiten:

- `write_file` – Crear o sobrescribir un archivo de texto.
- `delete_file` – Eliminar un archivo.
- `create_directory` – Crear una o más carpetas.
- `list_files` – Listar archivos (solo para depuración, **no devuelve datos al LLM**).
- `read_file` – Leer contenido (solo previsualización en Toast, **no devuelve datos al LLM**).

> **⚠️ Importante**: Las acciones `list_files` y `read_file` **no pueden devolver el resultado al LLM** a través de `run_intent` debido a limitaciones del diseño actual (los intents solo retornan un booleano de éxito/fracaso). Para obtener listados o contenidos completos y procesarlos con el LLM, se debe utilizar una **JS skill con webview** y el puente `AndroidFileSystem` (descrito al final).

## Cambios realizados en IntentHandler.kt

### 1. Nuevo data class

```kotlin
@JsonClass(generateAdapter = true)
data class FileOperationParams(
    val path: String,              // ruta relativa dentro de sandbox/
    val content: String = "",      // usado en write_file
    val encoding: String = "utf-8" // reservado
)
```

2. Funciones auxiliares de seguridad

```kotlin
private fun getSandboxRoot(context: Context): File { ... }
private fun resolvePath(context: Context, relativePath: String): File? { ... }
```

· resolvePath previene directory traversal (por ejemplo, ../../../etc/passwd). Solo permite rutas que permanezcan dentro de sandbox/.

3. Nuevos bloques en handleAction

write_file

· Parámetros: path (ruta relativa), content (texto a escribir), encoding (opcional).
· Comportamiento: Crea los directorios padres si no existen, escribe el contenido (sobrescribe si ya existe).
· Retorno: true si éxito, false en caso contrario. Muestra un Toast de confirmación.

delete_file

· Parámetros: path.
· Comportamiento: Elimina el archivo si existe y está dentro de la sandbox.
· Retorno: true si se eliminó correctamente.

create_directory

· Parámetros: path.
· Comportamiento: Crea el directorio y todos los padres necesarios (mkdirs).
· Retorno: true si se creó o ya existía.

list_files (limitado)

· Parámetros: path (opcional, carpeta a listar; si está vacío usa la raíz).
· Comportamiento: Muestra un Toast con la cantidad de archivos encontrados. No devuelve la lista al LLM.
· Retorno: Siempre true (si el parseo es correcto), pero sin datos útiles para el modelo.

read_file (limitado)

· Parámetros: path.
· Comportamiento: Lee el archivo, muestra un Toast con los primeros 200 caracteres. No devuelve el contenido al LLM.
· Retorno: true si el archivo existe y se pudo leer.

Cómo usar estas acciones desde una skill (SKILL.md)

Las acciones se invocan a través de la herramienta run_intent que la app expone al LLM. La skill debe incluir instrucciones claras con el nombre exacto del intent y el esquema JSON de los parámetros.

Ejemplo: escribir un archivo

```markdown
---
name: guardar-nota
description: Guarda una nota de texto en la sandbox.
---

# Guardar nota

## Instructions

Call the `run_intent` tool with the following exact parameters:
- intent: write_file
- parameters: A JSON string with the following fields:
  - path: String. Ruta del archivo (ej: "notas/mi_nota.txt").
  - content: String. Contenido del archivo.
```

Ejemplo: eliminar un archivo

```markdown
---
name: borrar-temporal
description: Elimina un archivo temporal.
---

# Borrar archivo

## Instructions

Call the `run_intent` tool with the following exact parameters:
- intent: delete_file
- parameters: A JSON string with the field:
  - path: String. Ruta del archivo a eliminar.
```

Ejemplo: crear una carpeta

```markdown
- intent: create_directory
- parameters: {"path": "fotos/vacaciones"}
```

Nota sobre list_files y read_file

El LLM no podrá obtener el listado ni el contenido a través de run_intent. Si tu skill necesita leer archivos para responder, debes implementar una JS skill con webview y usar el puente AndroidFileSystem.

Solución recomendada para lectura/lista de archivos: JS skill + puente nativo

En lugar de usar run_intent, crea una skill JavaScript que retorne un webview (apuntando a assets/webview.html). En ese webview, inyecta un objeto AndroidFileSystem desde el código nativo (modificando el WebView que renderiza la skill). El objeto expone métodos como:

```kotlin
webView.addJavascriptInterface(object {
    @JavascriptInterface fun listFiles(path: String): String { ... }
    @JavascriptInterface fun readFile(path: String): String { ... }
    // etc.
}, "AndroidFileSystem")
```

Desde el HTML puedes llamar a estos métodos y mostrar los resultados en la UI. El LLM no recibe directamente los datos, pero el usuario sí los ve y puede interactuar.

Si necesitas que el LLM procese el contenido de un archivo (por ejemplo, resumir un texto), la skill JS puede leer el archivo y luego enviar su contenido de vuelta al LLM mediante una nueva llamada a la API de chat (simulando una respuesta del usuario). Eso ya es un patrón más avanzado que no está soportado nativamente en la versión actual de la app.

Seguridad

· Todas las rutas son validadas con resolvePath para evitar .. y salir de la sandbox.
· Los archivos se almacenan en el directorio privado de la app (context.filesDir), inaccesible para otras aplicaciones sin root.
· No se requieren permisos de almacenamiento externo.

Compilación

Sustituye el archivo IntentHandler.kt original por la versión modificada y compila normalmente en Android Studio. Los cambios son compatibles con el resto de la app.

Prueba rápida (usando ADB)

Puedes verificar que la sandbox funciona correctamente usando ADB:

```bash
# Escribir un archivo
adb shell "echo 'Hola mundo' > /data/data/com.google.ai.edge.gallery/files/sandbox/prueba.txt"

# Leerlo (requiere root o depuración)
adb shell cat /data/data/com.google.ai.edge.gallery/files/sandbox/prueba.txt
```

Dentro de la app, invoca una skill que use read_file y deberías ver un Toast con el contenido.

---

Fecha de modificación: 2026-04-11
Autor: Basado en los requerimientos del usuario.

```
