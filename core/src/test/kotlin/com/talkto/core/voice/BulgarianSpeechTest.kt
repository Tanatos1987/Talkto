package com.talkto.core.voice

import com.google.common.truth.Truth.assertThat
import com.talkto.core.voice.BulgarianSpeech.Gender
import org.junit.Test

class BulgarianSpeechTest {

    private fun n(text: String) = BulgarianSpeech.normalize(text)

    @Test fun `numbers in words`() {
        assertThat(BulgarianSpeech.cardinal(1878)).isEqualTo("хиляда осемстотин седемдесет и осем")
        assertThat(BulgarianSpeech.cardinal(1900)).isEqualTo("хиляда и деветстотин")
        assertThat(BulgarianSpeech.cardinal(1005)).isEqualTo("хиляда и пет")
        assertThat(BulgarianSpeech.cardinal(2026)).isEqualTo("две хиляди двадесет и шест")
        assertThat(BulgarianSpeech.cardinal(120)).isEqualTo("сто и двадесет")
        assertThat(BulgarianSpeech.cardinal(1120)).isEqualTo("хиляда сто и двадесет")
        assertThat(BulgarianSpeech.cardinal(384000)).isEqualTo("триста осемдесет и четири хиляди")
        assertThat(BulgarianSpeech.cardinal(21000)).isEqualTo("двадесет и една хиляди")
        assertThat(BulgarianSpeech.cardinal(2_000_005)).isEqualTo("два милиона и пет")
        assertThat(BulgarianSpeech.cardinal(21, Gender.F)).isEqualTo("двадесет и една")
        assertThat(BulgarianSpeech.cardinal(2, Gender.N)).isEqualTo("две")
        assertThat(BulgarianSpeech.cardinal(0)).isEqualTo("нула")
    }

    @Test fun `ordinals change only the last word`() {
        assertThat(BulgarianSpeech.ordinal(1878, Gender.F)).isEqualTo("хиляда осемстотин седемдесет и осма")
        assertThat(BulgarianSpeech.ordinal(1961, Gender.F)).isEqualTo("хиляда деветстотин шестдесет и първа")
        assertThat(BulgarianSpeech.ordinal(2026, Gender.F)).isEqualTo("две хиляди двадесет и шеста")
        assertThat(BulgarianSpeech.ordinal(2000, Gender.F)).isEqualTo("двехилядна")
        assertThat(BulgarianSpeech.ordinal(1900, Gender.F)).isEqualTo("хиляда и деветстотна")
        assertThat(BulgarianSpeech.ordinal(681, Gender.F)).isEqualTo("шестстотин осемдесет и първа")
        assertThat(BulgarianSpeech.ordinal(24)).isEqualTo("двадесет и четвърти")
        assertThat(BulgarianSpeech.ordinal(3)).isEqualTo("трети")
        assertThat(BulgarianSpeech.ordinal(11)).isEqualTo("единадесети")
        assertThat(BulgarianSpeech.ordinal(1, Gender.N)).isEqualTo("първо")
        assertThat(BulgarianSpeech.ordinal(100)).isEqualTo("стотен")
        assertThat(BulgarianSpeech.ordinal(19)).isEqualTo("деветнадесети")
    }

    @Test fun `dates and years`() {
        assertThat(n("Днес е 24 май.")).isEqualTo("Днес е двадесет и четвърти май.")
        assertThat(n("На 3 март 1878 г. е подписан договорът."))
            .isEqualTo("На трети март хиляда осемстотин седемдесет и осма година е подписан договорът.")
        assertThat(n("Гагарин полетя на 12 април 1961 година.")).isEqualTo("Гагарин полетя на дванадесети април хиляда деветстотин шестдесет и първа година.")
        assertThat(n("През 1878 година България е освободена.")).isEqualTo("През хиляда осемстотин седемдесет и осма година България е освободена.")
        assertThat(n("Това става през 79 година.")).isEqualTo("Това става през седемдесет и девета година.")
        assertThat(n("От 2026 година се плаща с евро.")).isEqualTo("От две хиляди двадесет и шеста година се плаща с евро.")
        assertThat(n("Роден е през 1990.")).isEqualTo("Роден е през хиляда деветстотин и деветдесета година.")
        assertThat(n("Рожденият ти ден е 15.03")).isEqualTo("Рожденият ти ден е петнадесети март")
        assertThat(n("Убит е през 44 година пр.н.е.")).isEqualTo("Убит е през четиридесет и четвърта година преди новата ера")
        assertThat(n("Бебето е на 1 година.")).isEqualTo("Бебето е на една година.")
        assertThat(n("Днес е 29 септември 2026")).isEqualTo("Днес е двадесет и девети септември две хиляди двадесет и шеста година")
    }

    @Test fun `centuries, classes and ordinal endings`() {
        assertThat(n("През XIX век")).isEqualTo("През деветнадесети век")
        assertThat(n("в 21 век")).isEqualTo("в двадесет и първи век")
        assertThat(n("Задачи за 3 клас")).isEqualTo("Задачи за трети клас")
        assertThat(n("Той е в 7-ми клас.")).isEqualTo("Той е в седми клас.")
        assertThat(n("Зае 1-во място")).isEqualTo("Зае първо място")
        assertThat(n("2-ра част")).isEqualTo("втора част")
        assertThat(n("Цар Борис III покръства българите")).isEqualTo("Цар Борис трети покръства българите")
    }

    @Test fun `units say their words in the right form`() {
        assertThat(n("Тича 5 км на ден")).isEqualTo("Тича пет километра на ден")
        assertThat(n("само 1 км")).isEqualTo("само един километър")
        assertThat(n("Луната е на 384 000 км")).isEqualTo("Луната е на триста осемдесет и четири хиляди километра")
        assertThat(n("Азотът е 78% от въздуха")).isEqualTo("Азотът е седемдесет и осем процента от въздуха")
        assertThat(n("Навън е 20 °C")).isEqualTo("Навън е двадесет градуса по Целзий")
        assertThat(n("Струва 1 лв.")).isEqualTo("Струва един лев")
        assertThat(n("Струва 2 лв.")).isEqualTo("Струва два лева")
        assertThat(n("Струва 5 €")).isEqualTo("Струва пет евро")
        assertThat(n("само €1")).isEqualTo("само едно евро")
        assertThat(n("след 1 мин.")).isEqualTo("след една минута")
        assertThat(n("след 2 мин.")).isEqualTo("след две минути")
        assertThat(n("Висок е 1,5 м")).isEqualTo("Висок е 1,5 метра")
        assertThat(n("Около 1000 км")).isEqualTo("Около хиляда километра")
        assertThat(n("от 1000 до 2000 точки")).isEqualTo("от 1000 до 2000 точки")
    }

    @Test fun `maths is read the way a teacher says it`() {
        assertThat(n("7 − 3 = 4")).isEqualTo("7 минус 3 е равно на 4")
        assertThat(n("2x + 3 = 7")).isEqualTo("2 хикс плюс 3 е равно на 7")
        assertThat(n("x² = 16")).isEqualTo("хикс на квадрат е равно на 16")
        assertThat(n("3 × 4 = 12")).isEqualTo("3 по 4 е равно на 12")
        assertThat(n("3 x 4")).isEqualTo("3 по 4")
        assertThat(n("12 ÷ 3 = 4")).isEqualTo("12 делено на 3 е равно на 4")
        assertThat(n("10 / 2 = 5")).isEqualTo("10 делено на 2 е равно на 5")
        assertThat(n("3/4 от пицата")).isEqualTo("три четвърти от пицата")
        assertThat(n("1/2 от 18 е 9")).isEqualTo("една втора от 18 е 9")
        assertThat(n("2/100")).isEqualTo("две стотни")
        assertThat(n("Колко е 7-3?")).isEqualTo("Колко е 7 минус 3?")
        assertThat(n("7 + 5 = ?")).isEqualTo("7 плюс 5?")
        assertThat(n("5 > 3, а 2 < 4")).isEqualTo("5 е по-голямо от 3, а 2 е по-малко от 4")
        assertThat(n("√16 = 4")).isEqualTo("корен квадратен от 16 е равно на 4")
        assertThat(n("π ≈ 3,14")).isEqualTo("пи е приблизително 3,14")
        assertThat(n("x = -5")).isEqualTo("хикс е равно на минус 5")
        assertThat(n("Навън е -3 °C")).isEqualTo("Навън е минус три градуса по Целзий")
        assertThat(n("a + b = c")).isEqualTo("а плюс бе е равно на це")
        assertThat(n("Кажи и/или")).isEqualTo("Кажи и/или")
        assertThat(n("Езикът C++ е труден")).isEqualTo("Езикът C++ е труден")
    }

    @Test fun `money, times and ranges`() {
        assertThat(n("Струва 2,50 лв.")).isEqualTo("Струва два лева и петдесет стотинки")
        assertThat(n("Струва 3,20 €")).isEqualTo("Струва три евро и двадесет цента")
        assertThat(n("само €0.99")).isEqualTo("само деветдесет и девет цента")
        assertThat(n("Дава 1,00 €")).isEqualTo("Дава едно евро")
        assertThat(n("5 EUR")).isEqualTo("пет евро")
        assertThat(n("Срещата е в 10.30 ч.")).isEqualTo("Срещата е в десет и тридесет часа")
        assertThat(n("Ставам в 7:00 ч.")).isEqualTo("Ставам в седем часа")
        assertThat(n("Тича 1.5 км")).isEqualTo("Тича 1,5 километра")
        assertThat(n("Нивата е 1.500 кв. м")).isEqualTo("Нивата е хиляда и петстотин квадратни метра")
        assertThat(n("Войната е 1941-1945 г.")).isEqualTo("Войната е от хиляда деветстотин четиридесет и първа до хиляда деветстотин четиридесет и пета година")
        assertThat(n("Живял е там 5-6 години.")).isEqualTo("Живял е там 5 до 6 години.")
        assertThat(n("от 1000-2000 точки")).isEqualTo("от 1000 до 2000 точки")
        assertThat(n("Прочети стр. 5-10.")).isEqualTo("Прочети страница 5 до 10.")
        assertThat(n("Колко е 3-7?")).isEqualTo("Колко е 3 минус 7?")
        assertThat(n("През учебната 2025/2026 година")).isEqualTo("През учебната две хиляди двадесет и пета - двадесет и шеста година")
        assertThat(n("Сезон 2025/2026")).isEqualTo("Сезон две хиляди двадесет и пета - двадесет и шеста година")
        assertThat(n("Брои до 1000.")).isEqualTo("Брои до 1000.")
        assertThat(n("Кое число е след 1999?")).isEqualTo("Кое число е след 1999?")
        assertThat(n("Живял е до 1878.")).isEqualTo("Живял е до хиляда осемстотин седемдесет и осма година.")
        assertThat(n("преди 2 века")).isEqualTo("преди два века")
    }

    @Test fun `roman numerals, articles and people`() {
        assertThat(n("Учи в V клас")).isEqualTo("Учи в пети клас")
        assertThat(n("Зае II място")).isEqualTo("Зае второ място")
        assertThat(n("През I световна война")).isEqualTo("През Първата световна война")
        assertThat(n("Екатерина II и Симеон I")).isEqualTo("Екатерина втора и Симеон първи")
        assertThat(n("Луи XIV е крал")).isEqualTo("Луи четиринадесети е крал")
        assertThat(n("Прочети глава IV")).isEqualTo("Прочети глава четвърта")
        assertThat(n("Кажи I love you")).isEqualTo("Кажи I love you")
        assertThat(n("Това е 3-тата задача от 21-вия век")).isEqualTo("Това е третата задача от двадесет и първия век")
        assertThat(n("Дойдоха 2-ма души")).isEqualTo("Дойдоха двама души")
        assertThat(n("Той е 7-ми")).isEqualTo("Той е седми")
        assertThat(n("Сложи 1 глава лук")).isEqualTo("Сложи една глава лук")
        assertThat(n("БРАВО! Позна от ЕС")).isEqualTo("браво! Позна от ЕС")
        assertThat(n("Рожденият ти ден е на 15.03")).isEqualTo("Рожденият ти ден е на петнадесети март")
        assertThat(n("Рожденият ти ден е на 5.3.")).isEqualTo("Рожденият ти ден е на пети март.")
        assertThat(n("Роден е на 15/3/1990")).isEqualTo("Роден е на петнадесети март хиляда деветстотин и деветдесета година")
        assertThat(n("Колко е 12.05 + 3?")).isEqualTo("Колко е 12.05 плюс 3?")
    }

    @Test fun `abbreviations and latin words`() {
        assertThat(n("Т.е. утре, и т.н.")).isEqualTo("тоест утре, и така нататък")
        assertThat(n("Д-р Иванов, напр. в гр. Пловдив")).isEqualTo("доктор Иванов, например в град Пловдив")
        assertThat(n("OK, пусни YouTube")).isEqualTo("окей, пусни Ютюб")
        assertThat(n("Водата е H2O")).isEqualTo("Водата е аш две о")
        assertThat(n("Пиши ми SMS")).isEqualTo("Пиши ми есемес")
    }

    @Test fun `the settings sample says everything in words`() {
        assertThat(n(Speakable.clean(BulgarianSpeech.SAMPLE))).isEqualTo(
            "Здравей! Аз съм Знайко. Днес е двадесет и четвърти май, празникът на буквите. Връх Мусала е висок " +
                "две хиляди деветстотин двадесет и пет метра, а в трети клас знаем, че 7 по 8 е равно на 56.",
        )
    }

    @Test fun `ordinary sentences are left alone`() {
        listOf(
            "Колко е 7 плюс 5?", "Ниво 12, опит 340.", "Болт пробяга 100 метра за 9,58 секунди.", "Имам 3 любими игри.",
            "Среща в 14:30.", "Куче на английски е dog.", "Колко е 2 на квадрат?",
        ).forEach { assertThat(n(it)).isEqualTo(it) }
    }

    @Test fun `clear speech keeps each character but near a natural Bulgarian voice`() {
        for (p in VoicePreset.entries) {
            val (pitch, rate) = p.prosody(com.talkto.core.i18n.Lang.BG, clear = true)
            assertThat(pitch).isIn(com.google.common.collect.Range.closed(0.85f, 1.25f))
            assertThat(rate).isIn(com.google.common.collect.Range.closed(0.85f, 1.0f))
            // Higher characters stay higher, lower ones lower.
            if (p.pitch > 1f) assertThat(pitch).isGreaterThan(1f)
            if (p.pitch < 1f) assertThat(pitch).isLessThan(1f)
            assertThat(p.prosody(com.talkto.core.i18n.Lang.BG, clear = false)).isEqualTo(p.pitch to p.rate)
            assertThat(p.prosody(com.talkto.core.i18n.Lang.EN, clear = true)).isEqualTo(p.pitch to p.rate)
        }
    }
}
