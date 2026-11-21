package io.festicore.domain.auth

import java.text.BreakIterator

/** 닉네임 규칙: 앞뒤 공백을 뺀 1~12자. 길이는 iOS `Character`와 같이 문자소(grapheme) 단위로 센다. iOS `AuthNicknameRule`. */
object AuthNicknameRule {
    val lengthRange = 1..12
    val maxLength: Int get() = lengthRange.last

    fun trimmed(nickname: String): String = nickname.trim()

    fun isValid(nickname: String): Boolean = length(trimmed(nickname)) in lengthRange

    /** 입력 중 쓰는 값. 공백을 뺀 길이가 12자를 넘으면 공백을 뺀 앞 12자로 자르고, 아니면 입력을 그대로 둔다. */
    fun limited(input: String): String {
        val trimmedInput = trimmed(input)
        val ends = graphemeEnds(trimmedInput)
        return if (ends.size > maxLength) trimmedInput.substring(0, ends[maxLength - 1]) else input
    }

    fun counterText(input: String): String = "${length(trimmed(input))} / $maxLength"

    private fun length(text: String): Int = graphemeEnds(text).size

    private fun graphemeEnds(text: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        return generateSequence(iterator.next()) { iterator.next() }.takeWhile { it != BreakIterator.DONE }.toList()
    }
}
