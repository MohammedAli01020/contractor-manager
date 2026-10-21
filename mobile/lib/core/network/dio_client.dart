import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../config/app_config.dart';
import '../storage/auth_token_storage.dart';
import 'auth_interceptor.dart';

Dio createDio(AuthTokenStorage tokenStorage) {
  final dio = Dio(BaseOptions(
    baseUrl: AppConfig.apiBaseUrl + AppConfig.apiPrefix,
    connectTimeout: AppConfig.connectTimeoutMs,
    receiveTimeout: AppConfig.receiveTimeoutMs,
    sendTimeout: AppConfig.sendTimeoutMs,
    contentType: 'application/json',
    headers: const {'Accept': 'application/json'},
  ));

  dio.interceptors.add(AuthInterceptor(dio: dio, storage: tokenStorage));
  if (kDebugMode) {
    dio.interceptors.add(LogInterceptor(
      requestHeader: false,
      requestBody: false,
      responseHeader: false,
      responseBody: false,
    ));
  }
  return dio;
}
