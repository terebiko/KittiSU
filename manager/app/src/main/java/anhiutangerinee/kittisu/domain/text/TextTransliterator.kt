package anhiutangerinee.kittisu.domain.text

fun interface TextTransliterator {
    fun transliterate(value: String): String
}
