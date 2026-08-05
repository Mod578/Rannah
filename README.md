<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="Rannah/docs/assets/rannah-mark-dark.png">
  <img src="Rannah/docs/assets/rannah-mark.png" alt="رَنّة" width="96">
</picture>

# رَنّة

لكل موعد رَنّة

[![CI](https://github.com/Mod578/Rannah/actions/workflows/ci.yml/badge.svg)](https://github.com/Mod578/Rannah/actions/workflows/ci.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)

</div>

<div dir="rtl">

رَنّة تطبيق عربي للتذكيرات يتيح إنشاء تذكير لمرة واحدة أو يومي أو متكرر، مع التأجيل وتأكيد الإنجاز وإدارة موعد اليوم بوضوح.

## المزايا

- تذكير لمرة واحدة أو يومي أو متكرر
- تأجيل بمدة افتراضية، أو بمدة تختارها لموعد واحد
- تخطي موعد اليوم مع استمرار التكرار
- تأكيد الإنجاز بالسحب
- إيقاف التذكير المتكرر واستئنافه
- عرض التاريخ الميلادي والهجري
- واجهة عربية بالكامل
- حفظ البيانات محليًا على الجهاز

## التنزيل

[تنزيل أحدث إصدار](https://github.com/Mod578/Rannah/releases/latest)

ملف `APK` هو الملف المستخدم للتثبيت المباشر على أندرويد. قد يطلب النظام السماح بالتثبيت من هذا المصدر.

## الخصوصية

- بلا حساب
- بلا إعلانات
- بلا تحليلات أو تتبع
- تُحفظ بيانات التذكيرات محليًا على الجهاز
- قد يشمل النسخ الاحتياطي في أندرويد بيانات التطبيق وفق إعدادات الجهاز

[بيان الخصوصية](Rannah/docs/PRIVACY.md)

## المتطلبات

أندرويد 8.0 أو أحدث.

## البناء من المصدر

يحتاج البناء إلى JDK 17 و Android SDK بمستوى 35.

```bash
cd Rannah
./gradlew testDebugUnitTest   # الاختبارات
./gradlew lintDebug           # الفحص
./gradlew assembleDebug       # نسخة تجريبية للتثبيت
```

الناتج في `app/build/outputs/apk/debug/`. نسخة الإصدار تحتاج مفتاح توقيع خارج المستودع، وتفاصيله في [ARCHITECTURE.md](Rannah/docs/ARCHITECTURE.md).

## التقنيات

Kotlin، Jetpack Compose، Material 3، Room، Hilt، WorkManager، AlarmManager.

البنية الداخلية وقرارات التصميم في [ARCHITECTURE.md](Rannah/docs/ARCHITECTURE.md).

## المطوّر

محمد المطيري

[LinkedIn](https://www.linkedin.com/in/mutiri) · [البريد الإلكتروني](mailto:mutirieng@gmail.com)

</div>
