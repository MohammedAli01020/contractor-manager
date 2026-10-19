import 'package:dio/dio.dart';
import 'package:get_it/get_it.dart';

import '../../features/health/data/datasources/health_api.dart';
import '../../features/health/data/repositories/health_repository_impl.dart';
import '../../features/health/domain/repositories/health_repository.dart';
import '../../features/health/presentation/bloc/health_bloc.dart';
import '../network/dio_client.dart';

final GetIt getIt = GetIt.instance;

/// نقطة التجميع الوحيدة: كل ميزة جديدة تسجّل مكوناتها هنا.
void configureDependencies() {
  getIt.registerLazySingleton<Dio>(createDio);

  // health
  getIt.registerLazySingleton<HealthApi>(() => HealthApi(getIt<Dio>()));
  getIt.registerLazySingleton<HealthRepository>(
    () => HealthRepositoryImpl(getIt<HealthApi>()),
  );
  getIt.registerFactory<HealthBloc>(() => HealthBloc(getIt<HealthRepository>()));
}
