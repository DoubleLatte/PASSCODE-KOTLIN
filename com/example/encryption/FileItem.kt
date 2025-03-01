package com.example.encryption

import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import java.io.File
import kotlin.math.log
import kotlin.math.pow

class FileItem(file: File) {
    val name: StringProperty = SimpleStringProperty(file.name)
    val type: StringProperty = SimpleStringProperty(getFileExtension(file))
    val size: StringProperty = SimpleStringProperty(formatSize(file.length()))
    val status: StringProperty = SimpleStringProperty("준비")

    fun nameProperty(): StringProperty = name
    fun typeProperty(): StringProperty = type
    fun sizeProperty(): StringProperty = size
    fun statusProperty(): StringProperty = status

    fun getName(): String = name.value
    fun setStatus(status: String) {
        this.status.value = status
    }

    private fun getFileExtension(file: File): String {
        val name = file.name
        val lastDot = name.lastIndexOf('.')
        return if (lastDot == -1) "" else name.substring(lastDot + 1)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (log(bytes.toDouble()) / log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / 1024.0.pow(exp), pre)
    }
}