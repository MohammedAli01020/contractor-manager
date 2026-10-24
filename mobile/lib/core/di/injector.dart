import 'package:dio/dio.dart';
import 'package:get_it/get_it.dart';

import '../../features/auth/auth_bloc.dart';
import '../../features/shifts/data/datasources/shifts_api.dart';
import '../../features/shifts/data/repositories/shifts_repository_impl.dart';
import '../../features/shifts/presentation/bloc/shifts_bloc.dart';
import '../../features/health/data/datasources/health_api.dart';
import '../../features/health/data/repositories/health_repository_impl.dart';
import '../../features/health/domain/repositories/health_repository.dart';
import '../../features/health/presentation/bloc/health_bloc.dart';
import '../network/dio_client.dart';
import '../storage/auth_token_storage.dart';

final GetIt getIt = GetIt.instance;

void configureDependencies() {
  getIt.registerLazySingleton<AuthTokenStorage>(() => AuthTokenStorage());
  getIt.registerLazySingleton<Dio>(
    () => createDio(getIt<AuthTokenStorage>()),
  );
  getIt.registerFactory<AuthBloc>(
    () => AuthBloc(dio: getIt<Dio>(), storage: getIt<AuthTokenStorage>()),
  );

  // تظل ميزة فحص الاتصال متاحة أثناء التطوير.
  getIt.registerLazySingleton<HealthApi>(() => HealthApi(getIt<Dio>()));
  getIt.registerLazySingleton<HealthRepository>(
    () => HealthRepositoryImpl(getIt<HealthApi>()),
  );
  getIt.registerFactory<HealthBloc>(() => HealthBloc(getIt<HealthRepository>()));

  getIt.registerLazySingleton<ShiftsApi>(() => ShiftsApi(getIt<Dio>()));
  getIt.registerLazySingleton<ShiftsRepositoryImpl>(
    () => ShiftsRepositoryImpl(getIt<ShiftsApi>()),
  );
  getIt.registerFactory<ShiftsBloc>(
    () => ShiftsBloc(getIt<ShiftsRepositoryImpl>()),
  );
}
