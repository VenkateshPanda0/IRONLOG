package app.ironlog.personal.ui.train

enum class CardTemplate(val label: String) {
    MONO("Mono"),
    ACCENT("Volt"),
}

data class ShareCardData(
    val title: String,
    val date: String,
    val duration: String,
    val volume: String,
    val sets: String,
    val bests: List<String>,
    val lines: List<Pair<String, String>>,
)
