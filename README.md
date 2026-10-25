# Contractor Manager

إدارة الورديات والسائقين والمعدات والمصروفات للمقاولين.
Flutter (Android) + Spring Boot 2.7 (Java 11) + MySQL 8 + Docker Compose.

## التخطيط ومتابعة المراحل

- [خريطة المنتج والقرارات وحالة المراحل](docs/PRODUCT_ROADMAP.md) — المصدر الوحيد لترتيب المراحل وحالتها.
- [خطط التنفيذ التفصيلية](docs/phases/) — نطاق المرحلة وشروط قبولها والأدلة المطلوبة.
- [صفحة تفعيل مدير الشركة](backend/src/main/resources/static/activate-admin/index.html) — تُقدَّم من نفس تطبيق Spring Boot على Railway.
- [إعداد Platform Owner وتفعيل الأدمن](docs/platform-owner-activation.md) — متطلبات الإعداد والأمان والنشر.

هذا الملف مخصص للتثبيت والتشغيل والاختبارات؛ تفاصيل المراحل لا تُدار هنا.

## تشغيل المشروع

### 1) الـ Backend وقاعدة البيانات
```powershell
copy .env.example .env      # ثم عدّل كلمات المرور داخل .env
docker compose up -d --build
docker compose logs -f api  # انتظر ظهور Started ContractorApplication
```
- فحص الخادم: http://localhost:8080/api/v1/health
- Swagger: http://localhost:8080/swagger-ui.html
- إيقاف دون حذف البيانات: `docker compose down`  (لا تضف `-v` لأنه يحذف قاعدة البيانات)

### 2) تشغيل الـ Backend من IntelliJ (اختياري للتطوير)
```powershell
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db
```
افتح مجلد `backend` كمشروع Maven، واضبط متغيرات البيئة في Run Configuration:
`DB_USERNAME` و`DB_PASSWORD` (من ملف .env). المنفذ المحلي لقاعدة البيانات 3307.

### 3) اختبارات الـ Backend (لا تحتاج Maven مثبّتًا)
```powershell
docker run --rm -v "${PWD}/backend:/w" -w /w maven:3.8.8-eclipse-temurin-11 mvn -B test
```

### 4) تطبيق Flutter
```powershell
mkdir mobile   # إن لم يكن موجودًا
cd mobile
flutter create --org com.contractor --project-name contractor_mobile --platforms android .
```
ثم انسخ ملفات مجلد `mobile` من الـ zip فوقه (تستبدل pubspec.yaml وlib وtest). بعد ذلك عدّل ملفين:

1. `android/app/src/main/AndroidManifest.xml`: أضف قبل وسم `<application` السطر:
   `<uses-permission android:name="android.permission.INTERNET"/>`
2. `android/app/src/debug/AndroidManifest.xml`: أضف قبل `</manifest>` السطر:
   `<application android:usesCleartextTraffic="true"/>`
   (للتطوير عبر HTTP فقط. يعمل في نسخة debug ولا يؤثر على release.)

```powershell
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```
- محاكي Android: `http://10.0.2.2:8080`
- هاتف حقيقي على نفس الشبكة: `http://<IP جهازك>:8080` (اسمح بالمنفذ 8080 في جدار حماية Windows).
