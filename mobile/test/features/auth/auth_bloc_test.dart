import 'package:bloc_test/bloc_test.dart';
import 'package:contractor_mobile/core/storage/auth_token_storage.dart';
import 'package:contractor_mobile/features/auth/auth_bloc.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockDio extends Mock implements Dio {}

class MockAuthTokenStorage extends Mock implements AuthTokenStorage {}

void main() {
  late MockDio dio;
  late MockAuthTokenStorage storage;

  final userJson = <String, dynamic>{
    'id': 7,
    'tenantId': 3,
    'username': 'demo',
    'fullName': 'Demo User',
    'role': 'ADMIN',
    'active': true,
  };

  setUp(() {
    dio = MockDio();
    storage = MockAuthTokenStorage();
  });

  group('AuthStarted', () {
    blocTest<AuthBloc, AuthState>(
      'emits unauthenticated when neither token exists',
      setUp: () {
        when(() => storage.readAccessToken()).thenAnswer((_) async => null);
        when(() => storage.readRefreshToken()).thenAnswer((_) async => null);
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthStarted()),
      expect: () => [
        const AuthChecking(),
        const AuthUnauthenticated(),
      ],
      verify: (_) {
        verifyNever(() => dio.get<Map<String, dynamic>>('/auth/me'));
        verifyNever(() => storage.clear());
      },
    );

    blocTest<AuthBloc, AuthState>(
      'loads the authenticated user when the session is valid',
      setUp: () {
        when(() => storage.readAccessToken())
            .thenAnswer((_) async => 'access');
        when(() => storage.readRefreshToken())
            .thenAnswer((_) async => 'refresh');
        when(() => dio.get<Map<String, dynamic>>('/auth/me')).thenAnswer(
          (_) async => Response<Map<String, dynamic>>(
            requestOptions: RequestOptions(path: '/auth/me'),
            data: userJson,
          ),
        );
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthStarted()),
      expect: () => [
        const AuthChecking(),
        AuthAuthenticated(AuthUser.fromJson(userJson)),
      ],
      verify: (_) => verify(() => dio.get<Map<String, dynamic>>('/auth/me'))
          .called(1),
    );

    blocTest<AuthBloc, AuthState>(
      'clears stored tokens when the session endpoint returns 401',
      setUp: () {
        when(() => storage.readAccessToken())
            .thenAnswer((_) async => 'expired');
        when(() => storage.readRefreshToken())
            .thenAnswer((_) async => 'refresh');
        when(() => dio.get<Map<String, dynamic>>('/auth/me')).thenThrow(
          DioError(
            requestOptions: RequestOptions(path: '/auth/me'),
            response: Response<dynamic>(
              requestOptions: RequestOptions(path: '/auth/me'),
              statusCode: 401,
            ),
          ),
        );
        when(() => storage.clear()).thenAnswer((_) async {});
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthStarted()),
      expect: () => [
        const AuthChecking(),
        const AuthUnauthenticated(),
      ],
      verify: (_) => verify(() => storage.clear()).called(1),
    );

    blocTest<AuthBloc, AuthState>(
      'preserves tokens and emits an error on a connection failure',
      setUp: () {
        when(() => storage.readAccessToken())
            .thenAnswer((_) async => 'access');
        when(() => storage.readRefreshToken())
            .thenAnswer((_) async => 'refresh');
        when(() => dio.get<Map<String, dynamic>>('/auth/me')).thenThrow(
          DioError(
            requestOptions: RequestOptions(path: '/auth/me'),
            type: DioErrorType.connectTimeout,
          ),
        );
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthStarted()),
      expect: () => [
        const AuthChecking(),
        isA<AuthError>().having((state) => state.message, 'message', isNotEmpty),
      ],
      verify: (_) => verifyNever(() => storage.clear()),
    );
  });

  group('AuthLoginSubmitted', () {
    blocTest<AuthBloc, AuthState>(
      'saves both tokens and authenticates after a successful login',
      setUp: () {
        when(
          () => dio.post<Map<String, dynamic>>(
            '/auth/login',
            data: {'username': 'demo', 'password': 'secret'},
          ),
        ).thenAnswer(
          (_) async => Response<Map<String, dynamic>>(
            requestOptions: RequestOptions(path: '/auth/login'),
            data: {
              'user': userJson,
              'accessToken': 'new-access',
              'refreshToken': 'new-refresh',
            },
          ),
        );
        when(
          () => storage.save(
            accessToken: 'new-access',
            refreshToken: 'new-refresh',
          ),
        ).thenAnswer((_) async {});
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthLoginSubmitted('demo', 'secret')),
      expect: () => [
        const AuthLoginLoading(),
        AuthAuthenticated(AuthUser.fromJson(userJson)),
      ],
      verify: (_) => verify(
        () => storage.save(
          accessToken: 'new-access',
          refreshToken: 'new-refresh',
        ),
      ).called(1),
    );

    blocTest<AuthBloc, AuthState>(
      'does not save tokens when the login response is incomplete',
      setUp: () {
        when(
          () => dio.post<Map<String, dynamic>>(
            '/auth/login',
            data: {'username': 'demo', 'password': 'secret'},
          ),
        ).thenAnswer(
          (_) async => Response<Map<String, dynamic>>(
            requestOptions: RequestOptions(path: '/auth/login'),
            data: {'user': userJson, 'accessToken': 'only-access'},
          ),
        );
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthLoginSubmitted('demo', 'secret')),
      expect: () => [
        const AuthLoginLoading(),
        isA<AuthError>().having(
          (state) => state.message,
          'message',
          'استجابة تسجيل الدخول من الخادم غير مكتملة.',
        ),
      ],
      verify: (_) => verifyNever(
        () => storage.save(
          accessToken: any(named: 'accessToken'),
          refreshToken: any(named: 'refreshToken'),
        ),
      ),
    );
  });

  group('AuthLogoutRequested', () {
    blocTest<AuthBloc, AuthState>(
      'clears the local session even when the logout request fails',
      setUp: () {
        when(() => storage.readRefreshToken())
            .thenAnswer((_) async => 'refresh');
        when(
          () => dio.post<void>(
            '/auth/logout',
            data: {'refreshToken': 'refresh'},
          ),
        ).thenThrow(
          DioError(
            requestOptions: RequestOptions(path: '/auth/logout'),
            type: DioErrorType.connectTimeout,
          ),
        );
        when(() => storage.clear()).thenAnswer((_) async {});
      },
      build: () => AuthBloc(dio: dio, storage: storage),
      act: (bloc) => bloc.add(const AuthLogoutRequested()),
      expect: () => [const AuthLoggingOut(), const AuthUnauthenticated()],
      verify: (_) => verify(() => storage.clear()).called(1),
    );
  });
}
