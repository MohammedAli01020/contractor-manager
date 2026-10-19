import 'package:bloc_test/bloc_test.dart';
import 'package:contractor_mobile/core/error/app_failure.dart';
import 'package:contractor_mobile/features/health/domain/entities/health_info.dart';
import 'package:contractor_mobile/features/health/domain/repositories/health_repository.dart';
import 'package:contractor_mobile/features/health/presentation/bloc/health_bloc.dart';
import 'package:mocktail/mocktail.dart';

class MockHealthRepository extends Mock implements HealthRepository {}

void main() {
  late MockHealthRepository repository;
  final info = HealthInfo(
    status: 'UP',
    database: 'UP',
    timestamp: DateTime.utc(2026, 10, 15),
  );

  setUp(() {
    repository = MockHealthRepository();
  });

  blocTest<HealthBloc, HealthState>(
    'emits [loading, loaded] when the repository succeeds',
    build: () {
      when(() => repository.check()).thenAnswer((_) async => info);
      return HealthBloc(repository);
    },
    act: (bloc) => bloc.add(const HealthCheckRequested()),
    expect: () => [const HealthLoading(), HealthLoaded(info)],
  );

  blocTest<HealthBloc, HealthState>(
    'emits [loading, failed] when the repository throws AppFailure',
    build: () {
      when(() => repository.check()).thenThrow(
        const AppFailure(FailureType.noConnection, 'لا يوجد اتصال'),
      );
      return HealthBloc(repository);
    },
    act: (bloc) => bloc.add(const HealthCheckRequested()),
    expect: () => [const HealthLoading(), const HealthFailed('لا يوجد اتصال')],
  );
}
