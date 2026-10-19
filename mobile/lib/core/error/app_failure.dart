import 'dart:io';

import 'package:dio/dio.dart';

enum FailureType { noConnection, timeout, unauthorized, validation, server, unknown }

/// الخطأ الوحيد الذي تراه طبقات المنطق والواجهة، برسالة عربية جاهزة للعرض.
class AppFailure implements Exception {
  const AppFailure(this.type, this.message, {this.statusCode});

  final FailureType type;
  final String message;
  final int? statusCode;

  factory AppFailure.fromDio(DioError error) {
    switch (error.type) {
      case DioErrorType.connectTimeout:
      case DioErrorType.sendTimeout:
      case DioErrorType.receiveTimeout:
        return const AppFailure(
          FailureType.timeout,
          'انتهت مهلة الاتصال بالخادم. حاول مرة أخرى.',
        );
      case DioErrorType.response:
        return _fromResponse(error.response);
      case DioErrorType.cancel:
        return const AppFailure(FailureType.unknown, 'تم إلغاء الطلب.');
      case DioErrorType.other:
        if (error.error is SocketException) {
          return const AppFailure(
            FailureType.noConnection,
            'لا يوجد اتصال بالخادم. تحقق من الإنترنت أو من عنوان الخادم.',
          );
        }
        return const AppFailure(FailureType.unknown, 'حدث خطأ غير متوقع.');
    }
  }

  static AppFailure _fromResponse(Response<dynamic>? response) {
    final code = response?.statusCode;
    final serverMessage = _extractMessage(response?.data);
    if (code == 401) {
      return AppFailure(
        FailureType.unauthorized,
        serverMessage ?? 'انتهت الجلسة. سجّل الدخول من جديد.',
        statusCode: code,
      );
    }
    if (code == 400 || code == 409 || code == 422) {
      return AppFailure(
        FailureType.validation,
        serverMessage ?? 'البيانات المدخلة غير صحيحة.',
        statusCode: code,
      );
    }
    return AppFailure(
      FailureType.server,
      serverMessage ?? 'الخادم يواجه مشكلة حاليًا. حاول لاحقًا.',
      statusCode: code,
    );
  }

  static String? _extractMessage(dynamic data) {
    if (data is Map && data['message'] is String) {
      return data['message'] as String;
    }
    return null;
  }

  @override
  String toString() => 'AppFailure($type, $message, $statusCode)';
}
