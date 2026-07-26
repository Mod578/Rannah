<div align="center">

<img src="Rannah/docs/assets/rannah-logo.png" alt="رَنّة" width="132">

# رَنّة

**لكل موعد رَنّة**

[![الإصدار](https://img.shields.io/badge/%D8%A7%D9%84%D8%A5%D8%B5%D8%AF%D8%A7%D8%B1-1.0.0-0B6B5F?style=flat-square)](https://github.com/Mod578/Rannah/releases/latest)
[![أندرويد](https://img.shields.io/badge/Android-8.0%2B-9A6B1E?style=flat-square)](#متطلبات-التشغيل)
[![بلا إنترنت](https://img.shields.io/badge/%D8%A8%D9%84%D8%A7%20%D8%A5%D9%86%D8%AA%D8%B1%D9%86%D8%AA-100%25-151436?style=flat-square)](#الخصوصية)

</div>

---

<div dir="rtl" align="right">

## ما هي رَنّة

تطبيق تذكيرات عربي يعمل بالكامل على جهازك. تكتب ما تريد تذكّره، تختار وقته، ثم
تؤكّد إنجازه حين تنجزه. لا حساب، ولا إنترنت، ولا إعدادات لا تحتاجها.

كل تذكير **يرنّ** فعلًا — منبّه بصوت مستمر فوق شاشة القفل، لا إشعار صامت يمرّ دون
أن تنتبه له. الاسم هو الوعد.

## المزايا

- **منبّه حقيقي** — يرنّ فوق شاشة القفل بصوت متصاعد حتى تؤجّله أو تؤكّد إنجازه.
- **تأكيد بالسحب** — «اسحب للتأكيد»، فلا تُنهي ضغطة عابرة التزامًا.
- **تكرار كما تحتاجه** — مرة واحدة، أو يوميًا، أو أيامًا من الأسبوع، أو شهريًا،
  أو سنويًا.
- **التقويم الهجري** — تذكيرات شهرية وسنوية بالتاريخ الهجري إلى جانب الميلادي.
- **تخطّي اليوم** دون إنهاء التكرار، و**إيقاف مؤقت** و**استئناف** متى شئت.
- **تراجع** عن الإنجاز أو التخطي.
- **تأجيل** بمدة تختارها أنت.
- **ودجت للشاشة الرئيسية** — تاريخ اليوم ورنّتك القادمة.
- **عربية بالكامل** — واجهة من اليمين إلى اليسار، أرقام عربية، وخط تجوّل.
- **مظهر نهاري وليلي وتلقائي.**
- **يصمد أمام النظام** — تُستعاد التنبيهات بعد إعادة التشغيل، وبعد تحديث التطبيق،
  وعند تغيّر الوقت أو المنطقة الزمنية.

## التنزيل

نزّل ملف `.apk` مباشرة من صفحة الإصدارات:

<div align="center">

### [⬇ تنزيل أحدث إصدار](https://github.com/Mod578/Rannah/releases/latest)

</div>

### تثبيت ملف APK

التطبيق يُثبَّت يدويًا، فلا بد من السماح بذلك مرة واحدة:

1. نزّل `rannah-1.0.0.apk` من صفحة الإصدار.
2. افتحه من الإشعار أو من مدير الملفات.
3. إن سأل أندرويد عن التثبيت من مصدر غير معروف، اسمح للتطبيق الذي تفتح منه الملف
   (المتصفح أو مدير الملفات) ثم أعد المحاولة.

> **للتحقق قبل التثبيت:** قارن بصمة الملف مع `SHA256SUMS.txt` المرفق في صفحة
> الإصدار نفسها.

> **ملف `.aab` المرفق مخصّص للنشر في المتاجر ولا يُثبَّت على الجهاز.** الملف
> المخصّص للتثبيت المباشر هو `.apk`.

## الخصوصية

- **لا تطلب رَنّة إذن الإنترنت** — لا تستطيع إرسال شيء، ولا ترسل.
- **لا حساب، ولا إعلانات، ولا تتبّع، ولا تحليلات.**
- **بياناتك في قاعدة بيانات على جهازك** وحده.
- **نسخ أندرويد الاحتياطي قد يشمل بيانات التطبيق** — بما فيها عناوين تذكيراتك
  وملاحظاتها — لأنه ميزة من النظام نفسه، ويمكنك إيقافها من إعدادات أندرويد.

التفاصيل في [بيان الخصوصية](Rannah/docs/PRIVACY.md).

## متطلبات التشغيل

| | |
|---|---|
| أقل إصدار أندرويد | 8.0 — `API 26` |
| إصدار الاستهداف | `API 35` |
| معرّف التطبيق | `com.bal.reminders` |
| اللغة | العربية |

لكي يرنّ التطبيق في وقته بدقّة، يطلب منك أندرويد أذونات **الإشعارات**
و**المنبّهات الدقيقة** و**التنبيه فوق شاشة القفل**. تجدها كلها في «الإعدادات ←
الأذونات والتنبيهات» داخل التطبيق.

## البناء من المصدر

</div>

```bash
git clone https://github.com/Mod578/Rannah.git
cd Rannah/Rannah

./gradlew :app:assembleDebug     # نسخة تطوير
./gradlew :app:test :app:lint    # الاختبارات والتدقيق
```

<div dir="rtl" align="right">

يحتاج البناء إلى **JDK 17** و**Android SDK 35**. أنشئ ملف `local.properties`
داخل مجلد `Rannah/` وضع فيه مسار الـ SDK:

</div>

```properties
sdk.dir=/path/to/Android/Sdk
```

<div dir="rtl" align="right">

بناء نسخة الإصدار (`assembleRelease`) يحتاج مفتاح توقيع خاصًّا لا يوجد في هذا
المستودع. بدونه تُبنى النسخة **غير موقّعة**، وهو ما يكفي للتحقق من الترجمة
والتصغير والتدقيق.

**تنظيم المستودع:** التطبيق كله داخل مجلد `Rannah/`؛ الجذر يحمل هذا الملف فقط.

## حالة الإصدار

الإصدار **1.0.0** هو أول إصدار موزّع من رَنّة: موقّع بمفتاح خاص، غير قابل
للتنقيح، ومصغَّر بـ R8. يُبنى ويُختبر آليًا قبل كل إصدار.

## المطوّر

**محمد المطيري**
مهندس ذكاء اصطناعي ومطوّر برمجيات

## الحقوق والتراخيص

جميع الحقوق محفوظة © 2026 محمد المطيري. المصدر منشور للاطّلاع والمراجعة، ولم
يُمنح بعد ترخيص مفتوح لإعادة الاستخدام أو التوزيع.

يعتمد التطبيق على أعمال مفتوحة المصدر، ونصوص تراخيصها مرفقة داخل التطبيق:

- خط **تجوّل (Tajawal)** — رخصة الخطوط المفتوحة SIL OFL 1.1
- **AndroidX** و**Jetpack Compose** و**Room** و**Hilt** — رخصة Apache 2.0

</div>

---

<div align="center">

## English summary

**رَنّة (*Rannah*)** — an offline-first Arabic reminders app for Android.

</div>

Reminders that actually **ring**: a real looping alarm over the lock screen, not a
notification that slips past you. One-time or recurring (daily, weekly, monthly,
yearly), in both the Gregorian and Hijri calendars. Completion is a deliberate
slide, never a stray tap. Skip a single day without ending a series, pause and
resume, undo anything. A home-screen widget shows today's date and your next ring.

Fully Arabic and RTL. **No `INTERNET` permission, no account, no ads, no
analytics** — everything stays in a database on the device. Android's own system
backup may still include that data; the app says so plainly.

Kotlin · Jetpack Compose · Room · Hilt · WorkManager · `minSdk 26` / `targetSdk 35`

**[Download the latest release →](https://github.com/Mod578/Rannah/releases/latest)**

<div align="center">

<sub>All rights reserved © 2026 Mohammed Almutairi · Tajawal font under SIL OFL 1.1 · AndroidX under Apache 2.0</sub>

</div>
