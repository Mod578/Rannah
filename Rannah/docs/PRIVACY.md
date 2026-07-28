<div dir="rtl" align="right">

# بيان الخصوصية — رَنّة

آخر تحديث: الإصدار `1.1.0`

هذا البيان يصف ما يفعله التطبيق فعلًا، لا ما يحسن قوله. كل جملة فيه قابلة
للتحقق من ملف `AndroidManifest.xml` ومن مصدر التطبيق في هذا المستودع.

## لا نجمع عنك شيئًا

رَنّة **لا تطلب إذن الإنترنت** (`android.permission.INTERNET` غير موجود في
البيان). التطبيق لا يستطيع فتح اتصال بالشبكة أصلًا، فلا يرسل بياناتك إلى أي
جهة، ولا إلى المطوّر.

لا يوجد **حساب**، ولا **تسجيل دخول**، ولا **إعلانات**، ولا **تتبّع**، ولا
**تحليلات**، ولا أي حزمة برمجية لجمع الاستخدام أو تقارير الأعطال.

## أين تُحفظ بياناتك

تُحفظ تذكيراتك — عناوينها وملاحظاتها ومواعيدها وسجل إنجازها — في قاعدة بيانات
باسم `bal.db` داخل مساحة التطبيق الخاصة على جهازك. لا يصل إليها تطبيق آخر.

يُحتفظ بسجل الرنّات ١٨٠ يومًا ثم يُحذف تلقائيًا. التذكيرات التي أنجزتَها لمرة
واحدة تُحذف مع سجلّها بعد انتهاء يومها.

## النسخ الاحتياطي من أندرويد

هذه هي النقطة الوحيدة التي تغادر فيها بياناتك جهازك، ونذكرها صراحة:

التطبيق يسمح بنسخ أندرويد الاحتياطي (`allowBackup="true"`)، ولم تُستثنَ منه أي
بيانات. هذا يعني أن نظام أندرويد **قد ينسخ بيانات التطبيق — بما فيها عناوين
تذكيراتك وملاحظاتها — إلى نسختك الاحتياطية في حسابك على Google، أو عند نقل
بياناتك إلى جهاز جديد.**

هذه ميزة من النظام لا من التطبيق، والغرض منها ألّا تفقد تذكيراتك حين تغيّر
جهازك. يمكنك إيقافها من إعدادات أندرويد نفسه (النسخ الاحتياطي).

## الأذونات ولماذا

| الإذن | لماذا |
|---|---|
| `POST_NOTIFICATIONS` | إظهار التذكير حين يحين وقته |
| `SCHEDULE_EXACT_ALARM` | أن يرنّ التذكير في وقته بالضبط، لا بعده بساعة |
| `USE_FULL_SCREEN_INTENT` | إظهار شاشة المنبّه فوق شاشة القفل |
| `FOREGROUND_SERVICE` و`FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | إبقاء الرنّة مستمرة حتى تؤجّلها أو تؤكّد إنجازها |
| `WAKE_LOCK` | إيقاظ الجهاز لحظة الرنّة |
| `VIBRATE` | الاهتزاز مع الرنّة |
| `RECEIVE_BOOT_COMPLETED` | إعادة جدولة تذكيراتك بعد إعادة تشغيل الجهاز |
| `ACCESS_NETWORK_STATE` | تضيفه مكتبة `WorkManager` من النظام؛ لا يفيد في إرسال شيء ما دام إذن الإنترنت غير موجود |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | إذن يعرّفه التطبيق لنفسه بمستوى التوقيع، تضيفه مكتبات AndroidX لحماية مستقبِلاته الداخلية من تطبيقات أخرى |

## سجلّات التشخيص

لا يكتب التطبيق أي سطر في سجلّ النظام (`logcat`)، فلا يظهر عنوان تذكير في
سجلّات الجهاز.

## التغييرات

أي تغيير في سلوك التطبيق تجاه بياناتك سيظهر في هذا الملف وفي شاشة «الخصوصية»
داخل التطبيق معًا. الشاشتان تقولان الشيء نفسه عمدًا.

## للتواصل

محمد المطيري — عبر صفحة المشكلات في هذا المستودع.

</div>

---

## English summary

رَنّة collects nothing. The app has **no `INTERNET` permission**, so it cannot
send data anywhere. There is no account, no sign-in, no ads, no tracking, no
analytics and no crash reporting. Reminders live in a local database (`bal.db`)
inside the app's private storage.

One exception is stated plainly: `allowBackup` is enabled with no exclusions, so
**Android's own system backup and device-transfer may include app data, including
reminder titles and notes**, in the user's Google account backup. That is a
platform feature, it exists so reminders survive a change of phone, and it can be
turned off in Android's own settings.

The app writes nothing to `logcat`, so no reminder content reaches device logs.
