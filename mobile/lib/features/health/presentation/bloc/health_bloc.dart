import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/error/app_failure.dart';
import '../../domain/repositories/health_repository.dart';
import 'health_event.dart';
import 'health_state.dart';

export 'health_event.dart';
export 'health_state.dart';

class HealthBloc extends Bloc<HealthEvent, HealthState> {
  HealthBloc(this._repository) : super(const HealthInitial()) {
    on<HealthCheckRequested>(_onCheckRequested);
  }

  final HealthRepository _repository;

  Future<void> _onCheckRequested(
    HealthCheckRequested event,
    Emitter<HealthState> emit,
  ) async {
    emit(const HealthLoading());
    try {
      final info = await _repository.check();
      emit(HealthLoaded(info));
    } on AppFailure catch (failure) {
      emit(HealthFailed(failure.message));
    }
  }
}
