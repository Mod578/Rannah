package com.bal.reminders.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * علامة «رَنّة» موجودة في ستة ملفات: ثابت Kotlin واحد، وأربعة Vector Drawable،
 * وأصلَي SVG. هذا الاختبار يثبت أنها الشكل نفسه في كلّها.
 *
 * قبل هذا كانت كل نسخة تُحرَّر يدويًا، فانتهى الأمر إلى أيقونة إشعار تحمل هندسة
 * وأيقونة تطبيق تحمل غيرها، وتعليق يصف مقياسًا لا يستخدمه ملفه. الانحراف هنا
 * فشلٌ في الاختبار لا اكتشافٌ متأخر على جهاز.
 */
class MarkGeometryTest {

    private val resDir: File? = System.getProperty("rannah.resDir")?.let(::File)
    private val docsDir: File? = System.getProperty("rannah.docsDir")?.let(::File)

    private fun drawable(name: String) = File(resDir, "drawable/$name.xml")

    /** يستخرج قيمة السمة، مع فكّ ما يعترضه من أسطر. */
    private fun attribute(file: File, name: String): String {
        val text = file.readText()
        val marker = "$name=\""
        val start = text.indexOf(marker)
        assertTrue("${file.name}: لا يحمل $name", start >= 0)
        val from = start + marker.length
        return text.substring(from, text.indexOf('"', from))
    }

    private val variants = listOf(
        "ic_launcher_foreground",
        "ic_launcher_monochrome",
        "ic_notification",
        "ic_splash",
    )

    @Test
    fun `every drawable carries the canonical geometry`() {
        assumeTrue("rannah.resDir غير مضبوط", resDir?.isDirectory == true)
        for (name in variants) {
            assertEquals(
                "$name.xml انحرف عن الهندسة المولَّدة؛ أعد تشغيل tools/brand/build_brand.py",
                MARK_PATH_DATA,
                attribute(drawable(name), "android:pathData"),
            )
        }
    }

    @Test
    fun `repository mark assets carry the canonical geometry`() {
        assumeTrue("rannah.docsDir غير مضبوط", docsDir?.isDirectory == true)
        for (name in listOf("rannah-mark.svg", "rannah-mark-dark.svg")) {
            val svg = File(docsDir, "assets/$name")
            assertTrue("$name مفقود", svg.isFile)
            val d = svg.readText().substringAfter(" d=\"").substringBefore('"')
            assertEquals("$name انحرف عن الهندسة المولَّدة", MARK_PATH_DATA, d)
        }
    }

    @Test
    fun `the notification icon is a single-colour silhouette on transparency`() {
        assumeTrue("rannah.resDir غير مضبوط", resDir?.isDirectory == true)
        val file = drawable("ic_notification")
        // النظام يقرأ قناة الشفافية ويضع تلوينه؛ أي لون مرجعي أو غير أبيض
        // يجعل أندرويد يرسم مربّعًا أبيض بدل الشكل.
        assertEquals("#FFFFFFFF", attribute(file, "android:fillColor"))
        assertEquals("24", attribute(file, "android:viewportWidth"))
        assertEquals("24", attribute(file, "android:viewportHeight"))
        // شكل واحد، بلا مستطيل خلفية ولا طبقة ثانية تحمل لونًا.
        assertEquals(1, Regex("<path").findAll(file.readText()).count())
    }

    @Test
    fun `the monochrome layer is its own file, not the coloured foreground`() {
        assumeTrue("rannah.resDir غير مضبوط", resDir?.isDirectory == true)
        val mono = attribute(drawable("ic_launcher_monochrome"), "android:fillColor")
        val fg = attribute(drawable("ic_launcher_foreground"), "android:fillColor")
        assertEquals("#FFFFFFFF", mono)
        assertTrue("الطبقة أحادية اللون تكرّر لون الواجهة", mono != fg)

        val launcher = File(resDir, "mipmap-anydpi-v26/ic_launcher.xml").readText()
        assertTrue("الأيقونة التكيفية لا تعلن طبقة monochrome", "<monochrome" in launcher)
        assertTrue(
            "الواجهة تُستخدَم طبقةً أحادية اللون",
            "monochrome android:drawable=\"@drawable/ic_launcher_monochrome\"" in launcher,
        )
    }

    @Test
    fun `the adaptive foreground keeps the mark inside the safe zone`() {
        assumeTrue("rannah.resDir غير مضبوط", resDir?.isDirectory == true)
        val file = drawable("ic_launcher_foreground")
        assertEquals("108", attribute(file, "android:viewportWidth"))

        // المنحنى التكعيبي محصور داخل غلاف نقاط تحكّمه، فأبعد نقطة تحكّم عن
        // مركز الشبكة حدّ أعلى لأبعد نقطة حبر. عند مقياس الواجهة يجب أن تبقى
        // داخل نصف قطر ٣٣dp، وهو المنطقة الآمنة (دائرة ٦٦dp من لوحة ١٠٨dp).
        val scale = attribute(file, "android:scaleX").toDouble()
        val reach = maxControlPointRadius() * scale
        assertTrue(
            "العلامة تتجاوز المنطقة الآمنة: %.1f dp والحدّ 33dp".format(reach),
            reach <= 33.0,
        )

        // القناع من النظام: لا مستطيل ولا دائرة مرسومة داخل الواجهة.
        val body = file.readText()
        assertEquals("طبقة الواجهة تحمل أكثر من مسار واحد", 1, Regex("<path").findAll(body).count())
    }

    /** أبعد نقطة تحكّم في المسار عن مركز الشبكة (12, 12)، بوحدات الشبكة. */
    private fun maxControlPointRadius(): Double =
        Regex("(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)").findAll(MARK_PATH_DATA).maxOf { m ->
            Math.hypot(m.groupValues[1].toDouble() - 12.0, m.groupValues[2].toDouble() - 12.0)
        }
}
