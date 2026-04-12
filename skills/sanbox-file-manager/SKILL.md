---
name: file-manager
description: Gestiona archivos dentro de la sandbox de la app. Permite listar, leer, editar y eliminar archivos.
metadata:
  homepage: https://github.com/tuusuario/file-manager-skill
---

# Administrador de archivos sandbox

## Instrucciones

Este skill muestra un explorador de archivos interactivo. El usuario puede:

- Ver la lista de archivos y carpetas en `/sandbox/`
- Crear, editar y eliminar archivos de texto
- Ver el contenido y estadísticas (tamaño, líneas, etc.)

Para usarlo, llama a `run_js` con:

- script name: index.html (o directamente muestra el webview)
- data: JSON vacío `{}`

El skill retornará un webview con la interfaz completa.
