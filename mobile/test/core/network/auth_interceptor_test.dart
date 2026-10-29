import 'dart:async';

import 'package:contractor_mobile/core/network/auth_interceptor.dart';
import 'package:contractor_mobile/core/storage/auth_token_storage.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockDio extends Mock implements Dio {}

class MockAuthTokenStorage extends Mock implements AuthTokenStorage {}

class MockRequestInterceptorHandler extends Mock
    implements RequestInterceptorHandler {}

class MockErrorInterceptorHandler extends Mock
    implements ErrorInterceptorHandler {}

class FakeRequestOptions extends Fake implements RequestOptions {}

class FakeDioError extends Fake implements DioError {}

class FakeResponse<T> extends Fake implements Response<T> {}

void main() {
  late MockDio dio;
  late MockDio refreshDio;
  late MockAuthTokenStorage storage;

  setUpAll(() {
    registerFallbackValue(FakeRequestOptions());
    registerFallbackValue(FakeDioError());
    registerFallbackValue(FakeResponse<dynamic>());
  });

  setUp(() {
    dio = MockDio();
    refreshDio = MockDio();
    storage = MockAuthTokenStorage();
    when(() => dio.options).thenReturn(BaseOptions(baseUrl: 'https://api.test'));
    when(() => refreshDio.options)
        .thenReturn(BaseOptions(baseUrl: 'https://api.test'));
  });

  AuthInterceptor buildInterceptor() => AuthInterceptor(
        dio: dio,
        storage: storage,
        refreshDio: refreshDio,
      );

  DioError unauthorized(RequestOptions request) => DioError(
        requestOptions: request,
        response: Response<dynamic>(
          requestOptions: request,
          statusCode: 401,
        ),
      );

  group('onRequest', () {
    test('adds the access token to protected requests', () async {
      final handler = MockRequestInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      when(() => storage.readAccessToken()).thenAnswer((_) async => 'access');

      buildInterceptor().onRequest(request, handler);
      await pumpEventQueue();

      expect(request.headers['Authorization'], 'Bearer access');
      verify(() => handler.next(request)).called(1);
    });

    test('does not read or attach a token for public endpoints', () async {
      final handler = MockRequestInterceptorHandler();
      final request = RequestOptions(path: '/auth/login');

      buildInterceptor().onRequest(request, handler);
      await pumpEventQueue();

      expect(request.headers.containsKey('Authorization'), isFalse);
      verifyNever(() => storage.readAccessToken());
      verify(() => handler.next(request)).called(1);
    });

    test('treats a public endpoint with query parameters as public', () async {
      final handler = MockRequestInterceptorHandler();
      final request = RequestOptions(path: '/auth/login?source=mobile');

      buildInterceptor().onRequest(request, handler);
      await pumpEventQueue();

      expect(request.headers.containsKey('Authorization'), isFalse);
      verifyNever(() => storage.readAccessToken());
      verify(() => handler.next(request)).called(1);
    });

    test('does not treat a longer path ending in a public path as public',
        () async {
      final handler = MockRequestInterceptorHandler();
      final request = RequestOptions(path: '/not-auth/login');
      when(() => storage.readAccessToken()).thenAnswer((_) async => 'access');

      buildInterceptor().onRequest(request, handler);
      await pumpEventQueue();

      expect(request.headers['Authorization'], 'Bearer access');
      verify(() => storage.readAccessToken()).called(1);
      verify(() => handler.next(request)).called(1);
    });
  });

  group('onError', () {
    test('passes through non-401 errors without refreshing', () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = DioError(
        requestOptions: request,
        response: Response<dynamic>(
          requestOptions: request,
          statusCode: 500,
        ),
      );

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verify(() => handler.next(error)).called(1);
      verifyNever(() => storage.readRefreshToken());
      verifyNever(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: any(named: 'data'),
          ));
    });

    test('clears the session and forwards 401 when no refresh token exists',
        () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = unauthorized(request);
      when(() => storage.readRefreshToken()).thenAnswer((_) async => null);
      when(() => storage.clear()).thenAnswer((_) async {});

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verify(() => storage.clear()).called(1);
      verify(() => handler.next(error)).called(1);
      verifyNever(() => dio.fetch<dynamic>(any()));
    });

    test('refreshes tokens and retries a protected request once', () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = unauthorized(request);
      final retriedResponse = Response<dynamic>(
        requestOptions: request,
        data: {'ok': true},
      );

      when(() => storage.readRefreshToken())
          .thenAnswer((_) async => 'old-refresh');
      when(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).thenAnswer(
        (_) async => Response<Map<String, dynamic>>(
          requestOptions: RequestOptions(path: '/auth/refresh'),
          data: {
            'accessToken': 'new-access',
            'refreshToken': 'new-refresh',
          },
        ),
      );
      when(() => storage.save(
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
          )).thenAnswer((_) async {});
      when(() => dio.fetch<dynamic>(request))
          .thenAnswer((_) async => retriedResponse);

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      expect(request.headers['Authorization'], 'Bearer new-access');
      expect(request.extra['authRetry'], isTrue);
      verify(() => storage.save(
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
          )).called(1);
      verify(() => dio.fetch<dynamic>(request)).called(1);
      verify(() => handler.resolve(retriedResponse)).called(1);
    });

    test('preserves the session when refresh fails due to a network error',
        () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = unauthorized(request);
      when(() => storage.readRefreshToken())
          .thenAnswer((_) async => 'old-refresh');
      when(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).thenThrow(DioError(
        requestOptions: RequestOptions(path: '/auth/refresh'),
        type: DioErrorType.other,
      ));

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verifyNever(() => storage.clear());
      verify(() => handler.next(error)).called(1);
      verifyNever(() => dio.fetch<dynamic>(any()));
    });

    test('preserves the session when refresh server returns 503', () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = unauthorized(request);
      when(() => storage.readRefreshToken())
          .thenAnswer((_) async => 'old-refresh');
      when(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).thenThrow(DioError(
        requestOptions: RequestOptions(path: '/auth/refresh'),
        response: Response<dynamic>(
          requestOptions: RequestOptions(path: '/auth/refresh'),
          statusCode: 503,
        ),
      ));

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verifyNever(() => storage.clear());
      verify(() => handler.next(error)).called(1);
      verifyNever(() => dio.fetch<dynamic>(any()));
    });

    test('clears the session when refresh credentials are rejected', () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = unauthorized(request);
      when(() => storage.readRefreshToken())
          .thenAnswer((_) async => 'old-refresh');
      when(() => storage.clear()).thenAnswer((_) async {});
      when(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).thenThrow(DioError(
        requestOptions: RequestOptions(path: '/auth/refresh'),
        response: Response<dynamic>(
          requestOptions: RequestOptions(path: '/auth/refresh'),
          statusCode: 401,
        ),
      ));

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verify(() => storage.clear()).called(1);
      verify(() => handler.next(error)).called(1);
      verifyNever(() => dio.fetch<dynamic>(any()));
    });

    test('clears the session when refresh response tokens are incomplete',
        () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(path: '/drivers');
      final error = unauthorized(request);
      when(() => storage.readRefreshToken())
          .thenAnswer((_) async => 'old-refresh');
      when(() => storage.clear()).thenAnswer((_) async {});
      when(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).thenAnswer(
        (_) async => Response<Map<String, dynamic>>(
          requestOptions: RequestOptions(path: '/auth/refresh'),
          data: {'accessToken': 'new-access'},
        ),
      );

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verify(() => storage.clear()).called(1);
      verify(() => handler.next(error)).called(1);
      verifyNever(() => storage.save(
            accessToken: any(named: 'accessToken'),
            refreshToken: any(named: 'refreshToken'),
          ));
      verifyNever(() => dio.fetch<dynamic>(any()));
    });

    test('shares one refresh across concurrent unauthorized requests', () async {
      final refreshCompleter = Completer<Response<Map<String, dynamic>>>();
      final firstHandler = MockErrorInterceptorHandler();
      final secondHandler = MockErrorInterceptorHandler();
      final firstRequest = RequestOptions(path: '/drivers');
      final secondRequest = RequestOptions(path: '/equipment');
      final firstError = unauthorized(firstRequest);
      final secondError = unauthorized(secondRequest);

      when(() => storage.readRefreshToken())
          .thenAnswer((_) async => 'old-refresh');
      when(() => storage.save(
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
          )).thenAnswer((_) async {});
      when(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).thenAnswer((_) => refreshCompleter.future);
      when(() => dio.fetch<dynamic>(any())).thenAnswer((invocation) async {
        final request = invocation.positionalArguments[0] as RequestOptions;
        return Response<dynamic>(
          requestOptions: request,
          data: {'ok': true},
        );
      });

      final interceptor = buildInterceptor();
      interceptor.onError(firstError, firstHandler);
      interceptor.onError(secondError, secondHandler);
      await pumpEventQueue();

      refreshCompleter.complete(Response<Map<String, dynamic>>(
        requestOptions: RequestOptions(path: '/auth/refresh'),
        data: {
          'accessToken': 'new-access',
          'refreshToken': 'new-refresh',
        },
      ));
      await pumpEventQueue();

      verify(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: {'refreshToken': 'old-refresh'},
          )).called(1);
      verify(() => storage.save(
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
          )).called(1);
      verify(() => dio.fetch<dynamic>(any())).called(2);
      verify(() => firstHandler.resolve(any())).called(1);
      verify(() => secondHandler.resolve(any())).called(1);
    });

    test('does not refresh an already retried request', () async {
      final handler = MockErrorInterceptorHandler();
      final request = RequestOptions(
        path: '/drivers',
        extra: {'authRetry': true},
      );
      final error = unauthorized(request);

      buildInterceptor().onError(error, handler);
      await pumpEventQueue();

      verify(() => handler.next(error)).called(1);
      verifyNever(() => storage.readRefreshToken());
      verifyNever(() => refreshDio.post<Map<String, dynamic>>(
            '/auth/refresh',
            data: any(named: 'data'),
          ));
    });
  });
}
