package com.ojitos369.lumaloop.utilities

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ojitos369.lumaloop.preferences.TagExportData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Ayudante para la exportación e importación de los datos de etiquetas en formato JSON.
 */
object TagBackupHelper {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportTags(context: Context, uri: Uri, data: TagExportData): Boolean {
        return try {
            val json = gson.toJson(data)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importTags(context: Context, uri: Uri): TagExportData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    gson.fromJson(reader, TagExportData::class.java)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
