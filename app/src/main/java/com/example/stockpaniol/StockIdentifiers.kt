package com.example.stockpaniol

import java.text.Normalizer
import java.util.Locale

/** Keep three letters, replacing the third with later letters when a prefix is taken. */
internal fun chooseStockPrefix(name: String, usedPrefixes: Set<String>): String {
    val letters = Normalizer.normalize(name, Normalizer.Form.NFD)
        .uppercase(Locale.ROOT).filter { it in 'A'..'Z' }.ifBlank { "ART" }.padEnd(3, 'X')
    val used = usedPrefixes.map { it.uppercase(Locale.ROOT) }.toSet()
    val candidates = sequence {
        for (third in 2 until letters.length) yield(letters.take(2) + letters[third])
        // If all third-letter choices are taken, continue with later second letters.
        for (second in 2 until letters.lastIndex) {
            for (third in second + 1 until letters.length) yield("${letters[0]}${letters[second]}${letters[third]}")
        }
        // Short names and repeated letters still need a unique three-letter code.
        for (first in 'A'..'Z') for (second in 'A'..'Z') for (third in 'A'..'Z') yield("$first$second$third")
    }
    return candidates.firstOrNull { it !in used } ?: error("No quedan códigos de tres letras disponibles.")
}

internal fun formatStockIdentifier(prefix: String, number: Long): String =
    "$prefix ${number.toString().padStart(3, '0')}"
