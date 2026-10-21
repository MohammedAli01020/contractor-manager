import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/error/app_failure.dart';
import '../../data/models/shift_model.dart';
import '../../data/repositories/shifts_repository_impl.dart';

abstract class ShiftsEvent extends Equatable {
  const ShiftsEvent();

  @override
  List<Object?> get props => [];
}

class ShiftsRequested extends ShiftsEvent {
  const ShiftsRequested();
}

abstract class ShiftsState extends Equatable {
  const ShiftsState();

  @override
  List<Object?> get props => [];
}

class ShiftsInitial extends ShiftsState {
  const ShiftsInitial();
}

class ShiftsLoading extends ShiftsState {
  const ShiftsLoading();
}

class ShiftsLoaded extends ShiftsState {
  const ShiftsLoaded(this.items);

  final List<ShiftModel> items;

  @override
  List<Object?> get props => [items];
}

class ShiftsFailed extends ShiftsState {
  const ShiftsFailed(this.message);

  final String message;

  @override
  List<Object?> get props => [message];
}

class ShiftsBloc extends Bloc<ShiftsEvent, ShiftsState> {
  ShiftsBloc(this._repository) : super(const ShiftsInitial()) {
    on<ShiftsRequested>(_onRequested);
  }

  final ShiftsRepositoryImpl _repository;

  Future<void> _onRequested(
    ShiftsRequested event,
    Emitter<ShiftsState> emit,
  ) async {
    emit(const ShiftsLoading());
    try {
      emit(ShiftsLoaded(await _repository.fetchShifts()));
    } on AppFailure catch (failure) {
      emit(ShiftsFailed(failure.message));
    } catch (_) {
      emit(const ShiftsFailed('حدث خطأ غير متوقع أثناء تحميل الورديات.'));
    }
  }
}
