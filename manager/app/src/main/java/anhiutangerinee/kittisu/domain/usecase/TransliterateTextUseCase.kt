package anhiutangerinee.kittisu.domain.usecase

import anhiutangerinee.kittisu.domain.text.TextTransliterator

class TransliterateTextUseCase(private val transliterator: TextTransliterator) {
    operator fun invoke(value: String): String = transliterator.transliterate(value)
}
