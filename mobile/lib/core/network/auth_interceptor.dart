import 'package:dio/dio.dart';

import '../storage/auth_token_storage.dart';

class AuthInterceptor extends Interceptor {
  AuthInterceptor({
    required Dio dio,
    required AuthTokenStorage storage,
    Dio? refreshDio,
  })  : _dio = dio,
        _storage = storage,
        _refreshDio = refreshDio ?? Dio(BaseOptions(
          baseUrl: dio.options.baseUrl,
          connectTimeout: dio.options.connectTimeout,
          receiveTimeout: dio.options.receiveTimeout,
          sendTimeout: dio.options.sendTimeout,
          contentType: 'application/json',
          headers: const {'Accept': 'application/json'},
        ));

  final Dio _dio;
  final AuthTokenStorage _storage;
  final Dio _refreshDio;
  Future<String?>? _refreshing;

  bool _isPublic(String path) {
    const publicPaths = [
      '/auth/login',
      '/auth/register-tenant',
      '/auth/refresh',
      '/auth/logout',
      '/health',
    ];
    final normalized = path.split('?').first;
    return publicPaths.contains(normalized);
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    if (!_isPublic(options.path)) {
      try {
        final token = await _storage.readAccessToken();
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer ' + token;
        }
      } catch (_) {}
    }
    handler.next(options);
  }

  @override
  void onError(DioError error, ErrorInterceptorHandler handler) async {
    final request = error.requestOptions;
    if (error.response?.statusCode != 401 ||
        _isPublic(request.path) ||
        request.extra['authRetry'] == true) {
      handler.next(error);
      return;
    }
    final token = await _refreshToken();
    if (token == null) {
      handler.next(error);
      return;
    }
    request.headers['Authorization'] = 'Bearer ' + token;
    request.extra['authRetry'] = true;
    try {
      handler.resolve(await _dio.fetch<dynamic>(request));
    } on DioError catch (retryError) {
      handler.next(retryError);
    } catch (_) {
      handler.next(error);
    }
  }

  Future<String?> _refreshToken() {
    final existing = _refreshing;
    if (existing != null) return existing;
    final future = _doRefresh();
    _refreshing = future;
    return future.whenComplete(() {
      if (identical(_refreshing, future)) _refreshing = null;
    });
  }

  Future<String?> _doRefresh() async {
    try {
      final refresh = await _storage.readRefreshToken();
      if (refresh == null || refresh.isEmpty) {
        await _storage.clear();
        return null;
      }
      final response = await _refreshDio.post<Map<String, dynamic>>(
        '/auth/refresh',
        data: {'refreshToken': refresh},
      );
      final data = response.data;
      final access = data?['accessToken'];
      final nextRefresh = data?['refreshToken'];
      if (access is! String || access.isEmpty ||
          nextRefresh is! String || nextRefresh.isEmpty) {
        await _storage.clear();
        return null;
      }
      await _storage.save(accessToken: access, refreshToken: nextRefresh);
      return access;
    } catch (_) {
      try {
        await _storage.clear();
      } catch (_) {}
      return null;
    }
  }
}
