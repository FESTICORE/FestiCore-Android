package io.festicore.feature.bearer.board

import io.festicore.domain.bearer.entity.BearerActivity

/** 활동 이름. iOS `BearerActivity.title`(`Common/BearerDomain+Title.swift`). */
val BearerActivity.title: String
    get() =
        when (this) {
            BearerActivity.Slam -> "슬램"
            BearerActivity.Sing -> "떼창"
            BearerActivity.Train -> "기차놀이"
            BearerActivity.Calm -> "차분"
        }

/** 마지막 글자의 받침에 맞는 조사를 붙인다. 한글이 아니면 받침 없는 쪽을 쓴다. iOS `withJosa`(`Common/String+Josa.swift`). */
internal fun String.withJosa(
    afterBatchim: String,
    afterVowel: String,
    rieulTakesVowelForm: Boolean = false,
): String {
    val last = lastOrNull()?.code ?: return this + afterVowel
    if (last !in HANGUL_FIRST..HANGUL_LAST) return this + afterVowel
    val jongseong = (last - HANGUL_FIRST) % JONGSEONG_COUNT
    return if (jongseong == 0 || (rieulTakesVowelForm && jongseong == RIEUL)) this + afterVowel else this + afterBatchim
}

/** "…으로/로". 받침 ㄹ 뒤에는 "로"를 쓴다. iOS `withEuro`. */
fun String.withEuro(): String = withJosa("으로", "로", rieulTakesVowelForm = true)

/** "…이/가". iOS `withIga`. */
fun String.withIga(): String = withJosa("이", "가")

private const val HANGUL_FIRST = 0xAC00
private const val HANGUL_LAST = 0xD7A3
private const val JONGSEONG_COUNT = 28

/** 받침 ㄹ의 종성 번호 */
private const val RIEUL = 8
