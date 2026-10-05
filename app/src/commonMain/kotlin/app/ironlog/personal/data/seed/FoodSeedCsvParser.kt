package app.ironlog.personal.data.seed

internal data class FoodSeedRecord(
    val name: String,
    val sourceRef: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double?,
    val brand: String? = null,
    val source: String? = null,
    val cuisine: String? = null,
    val popularity: Int = 0,
)

internal data class FoodServingRecord(
    val sourceRef: String,
    val label: String,
    val grams: Double,
)

internal object FoodSeedCsvParser {
    fun parseFoods(csv: String): List<FoodSeedRecord> =
        parseRows(csv).drop(1).mapNotNull(::parseFoodRow)

    fun parseServings(csv: String): List<FoodServingRecord> =
        parseRows(csv).drop(1).mapNotNull(::parseServingRow)

    private fun parseFoodRow(columns: List<String>): FoodSeedRecord? {
        if (columns.size < 8) return null
        val name = columns[0].trim()
        val sourceRef = columns[7].trim().takeIf(String::isNotEmpty) ?: return null
        val kcal = columns[2].toDoubleOrNull() ?: return null
        val protein = columns[3].toDoubleOrNull() ?: return null
        val carbs = columns[4].toDoubleOrNull() ?: return null
        val fat = columns[5].toDoubleOrNull() ?: return null
        if (name.isBlank() || listOf(kcal, protein, carbs, fat).any { !it.isFinite() || it < 0 }) {
            return null
        }
        val fiber = columns[6].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        // Optional columns in the extra bundles: source, cuisine, popularity.
        return FoodSeedRecord(
            name,
            sourceRef,
            kcal,
            protein,
            carbs,
            fat,
            fiber,
            brand = columns[1].trim().takeIf(String::isNotEmpty),
            source = columns.getOrNull(8)?.trim()?.takeIf(String::isNotEmpty),
            cuisine = columns.getOrNull(9)?.trim()?.takeIf(String::isNotEmpty),
            popularity = columns.getOrNull(10)?.trim()?.toIntOrNull() ?: 0,
        )
    }

    private fun parseServingRow(columns: List<String>): FoodServingRecord? {
        if (columns.size < 3) return null
        val sourceRef = columns[0].trim()
        val label = columns[1].trim()
        val grams = columns[2].toDoubleOrNull() ?: return null
        if (sourceRef.isBlank() || label.isBlank() || !grams.isFinite() || grams <= 0) return null
        return FoodServingRecord(sourceRef, label, grams)
    }

    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' && quoted && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index += 1
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    row += cell.toString()
                    cell.setLength(0)
                }
                (character == '\n' || character == '\r') && !quoted -> {
                    if (character == '\r' && text.getOrNull(index + 1) == '\n') index += 1
                    row += cell.toString()
                    rows += row.toList()
                    row.clear()
                    cell.setLength(0)
                }
                else -> cell.append(character)
            }
            index += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row.toList()
        }
        return rows
    }
}
