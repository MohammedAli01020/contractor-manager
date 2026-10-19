import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';

/// ينشئ كائن Dio الوحيد المستخدم في التطبيق.
/// المرحلة 1 ستضيف إليه Interceptor للمصادقة.
Dio createDio() {
  final dio = Dio(
    BaseOptions(
      baseUrl: '${AppConfig.apiBaseUrl}${AppConfig.apiPrefix}',
      connectTimeout: AppConfig.connectTimeoutMs,
      receiveTimeout: AppConfig.receiveTimeoutMs,
      sendTimeout: AppConfig.sendTimeoutMs,
      contentType: 'application/json',
      headers: const {'Accept': 'application/json'},
    ),
  );

  if (kDebugMode) {
    // بدون محتوى الطلبات أو الترويسات كي لا تظهر كلمات المرور والرموز في السجل.
    dio.interceptors.add(
      LogInterceptor(
        requestHeader: false,
        requestBody: false,
        responseHeader: false,
        responseBody: false,
      ),
    );
  }
  return dio;
}
