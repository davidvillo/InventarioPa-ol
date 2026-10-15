package com.example.stockpaniol

import org.json.JSONArray

/** Single-photo records keep their original representation for existing installations. */
object StockPhotos {
    const val LIMIT = 5

    fun decode(value: String): List<String> = when {
        value.isBlank() -> emptyList()
        value.startsWith("[") -> JSONArray(value).let { array -> List(array.length()) { array.getString(it) } }
        else -> listOf(value)
    }

    fun encode(images: List<String>): String {
        require(images.size <= LIMIT) { "Se permiten hasta $LIMIT fotos por artículo." }
        require(images.all { it.isNotBlank() } && images.distinct().size == images.size) { "Las fotos no pueden estar vacías ni repetidas." }
        return when (images.size) {
            0 -> ""
            1 -> images.single()
            else -> JSONArray(images).toString()
        }
    }
}
