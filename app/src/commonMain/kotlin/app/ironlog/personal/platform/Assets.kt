package app.ironlog.personal.platform

/** Files shipped inside the app (seed data, exercise photos), by path such as "seed/exercises.json". */
interface AssetReader {
    fun readBytes(path: String): ByteArray

    fun readText(path: String): String = readBytes(path).decodeToString()
}
