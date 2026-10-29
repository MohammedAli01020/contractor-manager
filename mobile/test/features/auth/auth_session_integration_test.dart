import 'dart:convert';
import 'dart:typed_data';

import 'package:bloc_test/bloc_test.dart';
import 'package:contractor_mobile/core/network/auth_interceptor.dart';
import 'package:contractor_mobile/core/storage/auth_token_storage.dart';
import 'package:contractor_mobile/features/auth/auth_bloc.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockAuthTokenStorage extends Mock implements AuthTokenStorage {}

class CallbackHttpClientAdapter implements HttpClientAdapter {
  CallbackHttpClientAdapter(this.handle);

  final Future<ResponseBody> Function(RequestOptions options) handle;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) =>
      handle(options);

  @override
  void close({bool force = false}) {}
}

ResponseBody jsonResponse(Map<String, dynamic> data) => ResponseBody.fromString(
      jsonEncode(data),
      200,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );

void main() {
  late MockAuthTokenStorage storage;
  late Dio dio;
  late Dio refreshDio;
  late int sessionRequests;
  late int refreshRequests;
  late String accessToken;
  late String refreshToken;

  final userJson = <String, dynamic>{
    'id': 7,
    'tenantId': 3,
    'username': 'demo',
    'fullName': 'Demo User',
    'role': 'ADMIN',
    'active': true,
  };

  setUp(() {
    storage = MockAuthTokenStorage();
    sessionRequests = 0;
    refreshRequests = 0;
    accessToken = 'expired-access';
    refreshToken = 'old-refresh';

    when(() => storage.readAccessToken())
        .thenAnswer((_) async => accessToken);
    when(() => storage.readRefreshToken())
        .thenAnswer((_) async => refreshToken);
    when(() => storage.save(
          accessToken: any(named: 'accessToken'),
          refreshToken: any(named: 'refreshToken'),
        )).thenAnswer((invocation) async {
      accessToken =
          invocation.namedArguments[#accessToken] as String;
      refreshToken =
          invocation.namedArguments[#refreshToken] as String;
    });
    when(() => storage.clear()).thenAnswer((_) async {
      accessToken = '';
      refreshToken = '';
    });

    dio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    dio.httpClientAdapter = CallbackHttpClientAdapter((options) async {
      if (options.path == '/auth/me') {
        sessionRequests++;
        if (options.headers['Authorization'] != 'Bearer new-access') {
          throw DioError(
            requestOptions: options,
            response: Response<dynamic>(
              requestOptions: options,
              statusCode: 401,
              data: {'message': 'Access token expired'},
            ),
          );
        }
        return jsonResponse(userJson);
      }
      throw StateError('Unexpected API path: ${options.path}');
    });

    refreshDio = Dio(BaseOptions(baseUrl: 'https://api.test'));
    refreshDio.httpClientAdapter = CallbackHttpClientAdapter((options) async {
      if (options.path != '/auth/refresh') {
        throw StateError('Unexpected refresh path: ${options.path}');
      }
      refreshRequests++;
      expect(options.data, {'refreshToken': 'old-refresh'});
      return jsonResponse({
        'accessToken': 'new-access',
        'refreshToken': 'new-refresh',
      });
    });

    dio.interceptors.add(AuthInterceptor(
      dio: dio,
      storage: storage,
      refreshDio: refreshDio,
    ));
  });

  tearDown(() {
    dio.close(force: true);
    refreshDio.close(force: true);
  });

  blocTest<AuthBloc, AuthState>(
    'restores the session after the interceptor refreshes an expired access token',
    build: () => AuthBloc(dio: dio, storage: storage),
    act: (bloc) => bloc.add(const AuthStarted()),
    expect: () => [
      const AuthChecking(),
      AuthAuthenticated(AuthUser.fromJson(userJson)),
    ],
    verify: (_) {
      expect(sessionRequests, 2,
          reason: 'The initial 401 should be followed by one retried request.');
      expect(refreshRequests, 1,
          reason: 'The expired access token should trigger one refresh.');
      expect(accessToken, 'new-access');
      expect(refreshToken, 'new-refresh');
      verify(() => storage.save(
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
          )).called(1);
      verifyNever(() => storage.clear());
    },
  );
}
