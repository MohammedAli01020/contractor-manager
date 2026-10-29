import 'package:dio/dio.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/error/app_failure.dart';
import '../../core/storage/auth_token_storage.dart';

class AuthUser extends Equatable {
  const AuthUser({
    required this.id,
    required this.tenantId,
    required this.username,
    required this.fullName,
    required this.role,
    required this.active,
  });
  final int id;
  final int tenantId;
  final String username;
  final String fullName;
  final String role;
  final bool active;

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    final id = _asPositiveInt(json['id'], 'id');
    final tenantId = _asPositiveInt(json['tenantId'], 'tenantId');
    final username = _asRequiredString(json['username'], 'username');
    final fullName = _asRequiredString(json['fullName'], 'fullName');
    final role = _asRequiredString(json['role'], 'role');
    final active = json['active'];
    if (active is! bool) {
      throw const FormatException('Invalid user field: active');
    }
    return AuthUser(
      id: id,
      tenantId: tenantId,
      username: username,
      fullName: fullName,
      role: role,
      active: active,
    );
  }

  static int _asPositiveInt(dynamic value, String field) {
    final parsed = value is int ? value : int.tryParse('$value');
    if (parsed == null || parsed <= 0) {
      throw FormatException('Invalid user field: $field');
    }
    return parsed;
  }

  static String _asRequiredString(dynamic value, String field) {
    if (value is! String || value.trim().isEmpty) {
      throw FormatException('Invalid user field: $field');
    }
    return value;
  }

  @override
  List<Object?> get props => [id, tenantId, username, fullName, role, active];
}

abstract class AuthEvent extends Equatable {
  const AuthEvent();
  @override
  List<Object?> get props => [];
}
class AuthStarted extends AuthEvent { const AuthStarted(); }
class AuthLoginSubmitted extends AuthEvent {
  const AuthLoginSubmitted(this.username, this.password);
  final String username;
  final String password;
  @override
  List<Object?> get props => [username];
}
class AuthLogoutRequested extends AuthEvent { const AuthLogoutRequested(); }

abstract class AuthState extends Equatable {
  const AuthState();
  @override
  List<Object?> get props => [];
}
class AuthChecking extends AuthState { const AuthChecking(); }
class AuthUnauthenticated extends AuthState { const AuthUnauthenticated(); }
class AuthLoginLoading extends AuthState { const AuthLoginLoading(); }
class AuthLoggingOut extends AuthState { const AuthLoggingOut(); }
class AuthAuthenticated extends AuthState {
  const AuthAuthenticated(this.user);
  final AuthUser user;
  @override
  List<Object?> get props => [user];
}
class AuthError extends AuthState {
  const AuthError(this.message);
  final String message;
  @override
  List<Object?> get props => [message];
}

class AuthBloc extends Bloc<AuthEvent, AuthState> {
  AuthBloc({required Dio dio, required AuthTokenStorage storage})
      : _dio = dio,
        _storage = storage,
        super(const AuthChecking()) {
    on<AuthStarted>(_started);
    on<AuthLoginSubmitted>(_login);
    on<AuthLogoutRequested>(_logout);
  }

  final Dio _dio;
  final AuthTokenStorage _storage;

  Future<void> _started(AuthStarted event, Emitter<AuthState> emit) async {
    emit(const AuthChecking());
    try {
      final access = await _storage.readAccessToken();
      final refresh = await _storage.readRefreshToken();
      if ((access == null || access.isEmpty) &&
          (refresh == null || refresh.isEmpty)) {
        emit(const AuthUnauthenticated());
        return;
      }
      final response = await _dio.get<Map<String, dynamic>>('/auth/me');
      if (response.data == null) {
        await _storage.clear();
        emit(const AuthUnauthenticated());
        return;
      }
      try {
        emit(AuthAuthenticated(AuthUser.fromJson(response.data!)));
      } on FormatException {
        await _storage.clear();
        emit(const AuthUnauthenticated());
      }
    } on DioError catch (error) {
      if (error.response?.statusCode == 401 &&
          error.requestOptions.extra['authRefreshUnavailable'] != true) {
        await _storage.clear();
        emit(const AuthUnauthenticated());
      } else if (error.requestOptions.extra['authRefreshUnavailable'] == true) {
        emit(const AuthError('تعذر التحقق من الجلسة. حاول مرة أخرى.'));
      } else {
        emit(AuthError(AppFailure.fromDio(error).message));
      }
    } catch (_) {
      emit(const AuthError('تعذر التحقق من الجلسة. حاول مرة أخرى.'));
    }
  }

  Future<void> _login(
    AuthLoginSubmitted event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoginLoading());
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/auth/login',
        data: {'username': event.username, 'password': event.password},
      );
      final data = response.data;
      if (data == null || data['user'] is! Map ||
          data['accessToken'] is! String || data['refreshToken'] is! String) {
        emit(const AuthError('استجابة تسجيل الدخول من الخادم غير مكتملة.'));
        return;
      }
      final access = data['accessToken'] as String;
      final refresh = data['refreshToken'] as String;
      if (access.isEmpty || refresh.isEmpty) {
        emit(const AuthError('استجابة تسجيل الدخول من الخادم غير مكتملة.'));
        return;
      }
      final user = AuthUser.fromJson(
        Map<String, dynamic>.from(data['user'] as Map),
      );
      await _storage.save(accessToken: access, refreshToken: refresh);
      emit(AuthAuthenticated(user));
    } on DioError catch (error) {
      emit(AuthError(AppFailure.fromDio(error).message));
    } on FormatException {
      emit(const AuthError('تعذر قراءة استجابة تسجيل الدخول.'));
    } catch (_) {
      emit(const AuthError('تعذر تسجيل الدخول. حاول مرة أخرى.'));
    }
  }

  Future<void> _logout(
    AuthLogoutRequested event,
    Emitter<AuthState> emit,
  ) async {
    emit(const AuthLoggingOut());
    try {
      final refresh = await _storage.readRefreshToken();
      if (refresh != null && refresh.isNotEmpty) {
        await _dio.post<void>(
          '/auth/logout',
          data: {'refreshToken': refresh},
        );
      }
    } catch (_) {
      // مسح الجلسة المحلية مهم حتى عند انقطاع الاتصال.
    } finally {
      try {
        await _storage.clear();
      } catch (_) {}
    }
    emit(const AuthUnauthenticated());
  }
}
