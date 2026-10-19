/// المكان الوحيد الذي يُعرَّف فيه عنوان الخادم وإعدادات الاتصال.
///
/// يُمرَّر العنوان عند التشغيل:
///   flutter run --dart-define=API_BASE_URL=http://192.168.1.10:8080
/// والقيمة الافتراضية 10.0.2.2 تصل من محاكي Android إلى جهازك.
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  static const String apiPrefix = '/api/v1';

  static const int connectTimeoutMs = 10000;
  static const int receiveTimeoutMs = 15000;
  static const int sendTimeoutMs = 15000;
}
