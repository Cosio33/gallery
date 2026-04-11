/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ai.edge.gallery.customtasks.agentchat

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File
import java.lang.Exception

// Parámetros para enviar email
@JsonClass(generateAdapter = true)
data class SendEmailParams(
    val extra_email: String,
    val extra_subject: String,
    val extra_text: String,
)

// Parámetros para enviar SMS
@JsonClass(generateAdapter = true)
data class SendSmsParams(val phone_number: String, val sms_body: String)

// Parámetros para operaciones con archivos en la sandbox
@JsonClass(generateAdapter = true)
data class FileOperationParams(
    val path: String,              // ruta relativa dentro de sandbox/
    val content: String = "",      // usado en write_file
    val encoding: String = "utf-8" // reservado para futuro
)

object IntentHandler {
    private const val TAG = "IntentHandler"

    // Obtiene el directorio raíz de la sandbox (dentro de filesDir de la app)
    private fun getSandboxRoot(context: Context): File {
        val sandbox = File(context.filesDir, "sandbox")
        if (!sandbox.exists()) sandbox.mkdirs()
        return sandbox
    }

    // Resuelve una ruta relativa a un File absoluto dentro de la sandbox
    // Previene directory traversal (../)
    private fun resolvePath(context: Context, relativePath: String): File? {
        val sandboxRoot = getSandboxRoot(context)
        val canonicalSandbox = sandboxRoot.canonicalFile
        val target = File(sandboxRoot, relativePath).canonicalFile
        return if (target.path.startsWith(canonicalSandbox.path)) target else null
    }

    fun handleAction(context: Context, action: String, parameters: String): Boolean {
        // --- Acciones originales ---
        if (action == "send_email") {
            try {
                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(SendEmailParams::class.java)
                val params = jsonAdapter.fromJson(parameters)
                if (params != null) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        data = "mailto:".toUri()
                        type = "text/plain"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(params.extra_email))
                        putExtra(Intent.EXTRA_SUBJECT, params.extra_subject)
                        putExtra(Intent.EXTRA_TEXT, params.extra_text)
                    }
                    context.startActivity(intent)
                    return true
                } else {
                    Log.e(TAG, "Failed to parse send_email parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse send_email parameters: $parameters", e)
                return false
            }
        } else if (action == "send_sms") {
            try {
                val moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(SendSmsParams::class.java)
                val params = jsonAdapter.fromJson(parameters)
                if (params != null) {
                    val uri = "smsto:${params.phone_number}".toUri()
                    val intent = Intent(Intent.ACTION_SENDTO, uri)
                    intent.putExtra("sms_body", params.sms_body)
                    context.startActivity(intent)
                    return true
                } else {
                    Log.e(TAG, "Failed to parse send_sms parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse send_sms parameters: $parameters", e)
                return false
            }
        }

        // --- NUEVAS ACCIONES PARA MANEJO DE ARCHIVOS EN SANDBOX ---
        else if (action == "write_file") {
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(FileOperationParams::class.java)
                val params = adapter.fromJson(parameters)
                if (params != null) {
                    val targetFile = resolvePath(context, params.path)
                    if (targetFile == null) {
                        Log.e(TAG, "Path traversal detected: ${params.path}")
                        Toast.makeText(context, "Ruta no válida", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    // Crear directorios padres si no existen
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(params.content)
                    Log.d(TAG, "File written: ${targetFile.absolutePath}")
                    Toast.makeText(context, "Archivo guardado: ${params.path}", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    Log.e(TAG, "Failed to parse write_file parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in write_file: $parameters", e)
                return false
            }
        }
        else if (action == "delete_file") {
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(FileOperationParams::class.java)
                val params = adapter.fromJson(parameters)
                if (params != null) {
                    val targetFile = resolvePath(context, params.path)
                    if (targetFile == null || !targetFile.exists()) {
                        Log.e(TAG, "File not found or invalid path: ${params.path}")
                        return false
                    }
                    val deleted = targetFile.delete()
                    if (deleted) {
                        Log.d(TAG, "File deleted: ${targetFile.absolutePath}")
                        Toast.makeText(context, "Eliminado: ${params.path}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                    }
                    return deleted
                } else {
                    Log.e(TAG, "Failed to parse delete_file parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in delete_file: $parameters", e)
                return false
            }
        }
        else if (action == "create_directory") {
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(FileOperationParams::class.java)
                val params = adapter.fromJson(parameters)
                if (params != null) {
                    val targetDir = resolvePath(context, params.path)
                    if (targetDir == null) {
                        Log.e(TAG, "Invalid directory path: ${params.path}")
                        return false
                    }
                    val created = targetDir.mkdirs()
                    if (created) {
                        Log.d(TAG, "Directory created: ${targetDir.absolutePath}")
                        Toast.makeText(context, "Carpeta creada: ${params.path}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No se pudo crear la carpeta", Toast.LENGTH_SHORT).show()
                    }
                    return created
                } else {
                    Log.e(TAG, "Failed to parse create_directory parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in create_directory: $parameters", e)
                return false
            }
        }
        else if (action == "list_files") {
            // Nota: list_files NO puede devolver datos al LLM mediante run_intent.
            // Esta implementación muestra un Toast con la cantidad de archivos,
            // pero para obtener el listado real el LLM debe usar una JS skill con webview.
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(FileOperationParams::class.java)
                val params = adapter.fromJson(parameters)
                if (params != null) {
                    val targetDir = resolvePath(context, params.path) ?: getSandboxRoot(context)
                    val files = targetDir.listFiles()?.map { it.name } ?: emptyList()
                    val message = "📁 ${files.size} archivos en ${params.path.ifEmpty { "/sandbox" }}"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    Log.d(TAG, "Files in ${targetDir.absolutePath}: $files")
                    // Para depuración, se puede lanzar un intent con el listado (pero no llega al LLM)
                    // Recomendación: usar webview con puente JavaScript.
                    return true
                } else {
                    Log.e(TAG, "Failed to parse list_files parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in list_files: $parameters", e)
                return false
            }
        }
        else if (action == "read_file") {
            // read_file tampoco puede devolver contenido al LLM. Se muestra el contenido en un Toast
            // truncado o en un visor. Para análisis real, usar webview.
            try {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(FileOperationParams::class.java)
                val params = adapter.fromJson(parameters)
                if (params != null) {
                    val targetFile = resolvePath(context, params.path)
                    if (targetFile?.exists() == true && targetFile.isFile) {
                        val content = targetFile.readText()
                        val preview = if (content.length > 200) content.take(200) + "..." else content
                        Toast.makeText(context, "Contenido de ${params.path}:\n$preview", Toast.LENGTH_LONG).show()
                        Log.d(TAG, "File content: $content")
                        return true
                    } else {
                        Toast.makeText(context, "Archivo no encontrado: ${params.path}", Toast.LENGTH_SHORT).show()
                        return false
                    }
                } else {
                    Log.e(TAG, "Failed to parse read_file parameters: $parameters")
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in read_file: $parameters", e)
                return false
            }
        }

        return false
    }
}