---
# Modificaciones para gestión de archivos en sandbox (AI Edge Gallery)
---

Este documento resume los cambios implementados para añadir capacidades de gestión de archivos dentro de una carpeta sandbox segura en la aplicación AI Edge Gallery. Las modificaciones permiten a las **Agent Skills** (tanto nativas mediante `run_intent` como visuales con webview) crear, leer, escribir, eliminar y analizar archivos en el almacenamiento privado de la app.

## Cambios realizados

### 1. Extensión de `IntentHandler.kt` – Acciones nativas para el LLM

**Archivo modificado:**  
`Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/IntentHandler.kt`

**Nuevas acciones disponibles para `run_intent`:**

- `write_file` – Escribe o sobrescribe un archivo de texto.
- `delete_file` – Elimina un archivo.
- `create_directory` – Crea una o más carpetas.
- `list_files` – Muestra un Toast con el número de archivos (solo depuración, no retorna datos al LLM).
- `read_file` – Muestra un Toast con los primeros 200 caracteres (solo depuración).

**Detalles de implementación:**

- Se añadió el data class `FileOperationParams`.
- Funciones auxiliares `getSandboxRoot()` y `resolvePath()` para prevenir directory traversal (`../`).
- La sandbox se ubica en `context.filesDir/sandbox/` (privado de la app, sin permisos especiales).
- Las acciones `write_file`, `delete_file` y `create_directory` retornan `true/false` y muestran un `Toast` de confirmación.

**Ejemplo de uso desde una skill (SKILL.md):**

```markdown
Call the `run_intent` tool with:
- intent: write_file
- parameters: {"path": "notas/mi_nota.txt", "content": "Hola mundo"}
```

### 2. Inyección del puente nativo en `GalleryWebView.kt` – Para skills visuales (webview)

**Archivo modificado:**  
`Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/GalleryWebView.kt`

**Objeto inyectado en el WebView:** `AndroidFileSystem`

Métodos disponibles desde JavaScript en cualquier `webview.html`:

Método	Descripción	Retorno
listFiles(relativePath)	Lista archivos en la carpeta especificada (vacío = raíz)	string (JSON array)
readFile(relativePath)	Lee el contenido de un archivo de texto	string
writeFile(relativePath, content)	Escribe o sobrescribe un archivo	void
deleteFile(relativePath)	Elimina un archivo	void
getFileSize(relativePath)	Tamaño del archivo en bytes	long
getMimeType(relativePath)	Tipo MIME estimado por extensión	string o null
Seguridad: El método resolvePath() evita que se acceda a rutas fuera de sandbox/ (ej. ../../../etc/hosts).

**Ejemplo de uso desde una skill visual (HTML/JS):**

```javascript
const files = JSON.parse(AndroidFileSystem.listFiles(""));
const content = AndroidFileSystem.readFile("prueba.txt");
AndroidFileSystem.writeFile("nuevo.txt", "contenido");
```

### 3. Skill de ejemplo: `file-manager` (explorador visual)

**Estructura de la skill:**

```
file-manager-skill/
├── SKILL.md
├── scripts/
│   └── index.html          (runner oculto que retorna el webview)
└── assets/
    └── webview.html        (interfaz completa: listar, editar, analizar, eliminar)
```

**Funcionalidades:**

- Listar archivos y carpetas dentro de `/sandbox/`.
- Crear/editar archivos de texto.
- Eliminar archivos.
- Analizar archivos: tamaño, líneas, palabras, tipo MIME.
- Interfaz adaptada a móviles.

**Instalación:** Importar la carpeta `file-manager-skill` desde la opción "Import local skill" en la app, o alojarla en un servidor web y cargar desde URL.

## Compilación e instalación

1. **Reemplazar los archivos modificados** en el proyecto clonado:
   - `IntentHandler.kt`
   - `GalleryWebView.kt`

2. **Agregar import faltante** en `GalleryWebView.kt` si es necesario:
   ```kotlin
   import java.io.IOException
   ```

3. **Sincronizar y compilar** con Android Studio:
   - `File → Sync Project with Gradle Files`
   - `Build → Clean Project`
   - `Build → Rebuild Project`

4. **Instalar en dispositivo físico** (recomendado) o emulador:
   - Conectar dispositivo con depuración USB activada.
   - Hacer clic en el botón verde **Run** (▶️).

5. **Verificar la sandbox** (opcional, mediante ADB):
   ```bash
   adb shell
   run-as com.google.ai.edge.gallery
   ls files/sandbox
   cat files/sandbox/ejemplo.txt
   ```

## Pruebas realizadas

- ✅ Escritura de archivos mediante `run_intent` desde una skill nativa.
- ✅ Creación de directorios.
- ✅ Eliminación de archivos.
- ✅ Skill visual `file-manager` conectada al puente `AndroidFileSystem`:
  - Listado de archivos.
  - Lectura y edición.
  - Análisis de contenido.
- ✅ Seguridad: rutas con `..` son bloqueadas.
- ✅ Persistencia tras reinicio de la app.

## Limitaciones conocidas

- Las acciones `list_files` y `read_file` a través de `run_intent` **no retornan datos al LLM** (solo muestran Toasts). Para obtener contenido o listados procesables por el modelo, es necesario usar la skill visual con `AndroidFileSystem`.
- El puente `AndroidFileSystem` solo está disponible en los webviews creados por `GalleryWebView` (todas las skills visuales). No afecta a otros webviews de la app.

## Contribuciones

Estas modificaciones han sido desarrolladas para extender las capacidades de AI Edge Gallery, permitiendo a los agentes y usuarios gestionar archivos dentro de un entorno controlado y seguro. Si deseas mejorar o añadir más funcionalidades (como soporte para archivos binarios, subida a la nube, etc.), se recomienda seguir el mismo patrón: añadir nuevas acciones en `IntentHandler.kt` para el LLM y/o extender el puente `AndroidFileSystem` en `GalleryWebView.kt`.

## Agradecimientos

- Documentación oficial de AI Edge Gallery Agent Skills.
- Comunidad de Google AI Edge.

---

**Fecha de última modificación:** 2026-04-11  
**Autor:** Cosio33
```
