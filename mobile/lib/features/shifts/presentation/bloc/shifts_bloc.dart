import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../../core/error/app_failure.dart';
import '../../data/datasources/shifts_api.dart';
import '../../data/models/shift_model.dart';
import '../../data/repositories/shifts_repository_impl.dart';

enum ShiftListTab { running, today, all }

abstract class ShiftsEvent extends Equatable {
  const ShiftsEvent();

  @override
  List<Object?> get props => [];
}

class ShiftsRequested extends ShiftsEvent {
  const ShiftsRequested();
}

class ShiftTabChanged extends ShiftsEvent {
  const ShiftTabChanged(this.tab);
  final ShiftListTab tab;

  @override
  List<Object?> get props => [tab];
}

class AllShiftsLoadMoreRequested extends ShiftsEvent {
  const AllShiftsLoadMoreRequested();
}

class AllShiftsStatusFilterChanged extends ShiftsEvent {
  const AllShiftsStatusFilterChanged(this.status);
  final String? status;

  @override
  List<Object?> get props => [status];
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
  const ShiftsLoaded({
    required this.runningItems,
    required this.todayItems,
    required this.allItems,
    required this.selectedTab,
    required this.allPage,
    required this.allTotalPages,
    required this.allStatus,
    this.loadingMore = false,
  });

  final List<ShiftModel> runningItems;
  final List<ShiftModel> todayItems;
  final List<ShiftModel> allItems;
  final ShiftListTab selectedTab;
  final int allPage;
  final int allTotalPages;
  final String? allStatus;
  final bool loadingMore;

  bool get hasMore => allPage + 1 < allTotalPages;

  List<ShiftModel> get visibleItems {
    switch (selectedTab) {
      case ShiftListTab.running:
        return runningItems;
      case ShiftListTab.today:
        return todayItems;
      case ShiftListTab.all:
        return allItems;
    }
  }

  ShiftsLoaded copyWith({
    List<ShiftModel>? runningItems,
    List<ShiftModel>? todayItems,
    List<ShiftModel>? allItems,
    ShiftListTab? selectedTab,
    int? allPage,
    int? allTotalPages,
    String? allStatus,
    bool clearAllStatus = false,
    bool? loadingMore,
  }) {
    return ShiftsLoaded(
      runningItems: runningItems ?? this.runningItems,
      todayItems: todayItems ?? this.todayItems,
      allItems: allItems ?? this.allItems,
      selectedTab: selectedTab ?? this.selectedTab,
      allPage: allPage ?? this.allPage,
      allTotalPages: allTotalPages ?? this.allTotalPages,
      allStatus: clearAllStatus ? null : (allStatus ?? this.allStatus),
      loadingMore: loadingMore ?? this.loadingMore,
    );
  }

  @override
  List<Object?> get props => [
        runningItems,
        todayItems,
        allItems,
        selectedTab,
        allPage,
        allTotalPages,
        allStatus,
        loadingMore,
      ];
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
    on<ShiftTabChanged>(_onTabChanged);
    on<AllShiftsLoadMoreRequested>(_onLoadMore);
    on<AllShiftsStatusFilterChanged>(_onStatusFilterChanged);
  }

  final ShiftsRepositoryImpl _repository;

  String _today() {
    final now = DateTime.now();
    final month = now.month.toString().padLeft(2, '0');
    final day = now.day.toString().padLeft(2, '0');
    return '${now.year}-$month-$day';
  }

  Future<void> _onRequested(
    ShiftsRequested event,
    Emitter<ShiftsState> emit,
  ) async {
    final previous = state;
    if (previous is! ShiftsLoaded) emit(const ShiftsLoading());
    try {
      final today = _today();
      final results = await Future.wait<dynamic>([
        _repository.fetchRunningShifts(),
        _repository.fetchShifts(from: today, to: today, page: 0, size: 50),
        _repository.fetchShifts(
          page: 0,
          size: 50,
          status: previous is ShiftsLoaded ? previous.allStatus : null,
        ),
      ]);
      emit(ShiftsLoaded(
        runningItems: results[0] as List<ShiftModel>,
        todayItems: (results[1] as ShiftPage).items,
        allItems: (results[2] as ShiftPage).items,
        selectedTab: previous is ShiftsLoaded
            ? previous.selectedTab
            : ShiftListTab.running,
        allPage: (results[2] as ShiftPage).page,
        allTotalPages: (results[2] as ShiftPage).totalPages,
        allStatus: previous is ShiftsLoaded ? previous.allStatus : null,
      ));
    } on AppFailure catch (failure) {
      emit(ShiftsFailed(failure.message));
    } catch (_) {
      emit(const ShiftsFailed('حدث خطأ غير متوقع أثناء تحميل الورديات.'));
    }
  }

  void _onTabChanged(ShiftTabChanged event, Emitter<ShiftsState> emit) {
    if (state is ShiftsLoaded) {
      emit((state as ShiftsLoaded).copyWith(selectedTab: event.tab));
    }
  }

  Future<void> _onLoadMore(
    AllShiftsLoadMoreRequested event,
    Emitter<ShiftsState> emit,
  ) async {
    final current = state;
    if (current is! ShiftsLoaded || !current.hasMore || current.loadingMore) {
      return;
    }
    emit(current.copyWith(loadingMore: true));
    try {
      final nextPage = current.allPage + 1;
      final result = await _repository.fetchShifts(
        page: nextPage,
        size: 50,
        from: null,
        to: null,
        status: current.allStatus,
      );
      final refreshed = state;
      if (refreshed is ShiftsLoaded) {
        emit(refreshed.copyWith(
          allItems: [...refreshed.allItems, ...result.items],
          allPage: result.page,
          allTotalPages: result.totalPages,
          loadingMore: false,
        ));
      }
    } on AppFailure catch (failure) {
      final refreshed = state;
      if (refreshed is ShiftsLoaded) {
        emit(refreshed.copyWith(loadingMore: false));
      } else {
        emit(ShiftsFailed(failure.message));
      }
    } catch (_) {
      final refreshed = state;
      if (refreshed is ShiftsLoaded) {
        emit(refreshed.copyWith(loadingMore: false));
      }
    }
  }

  Future<void> _onStatusFilterChanged(
    AllShiftsStatusFilterChanged event,
    Emitter<ShiftsState> emit,
  ) async {
    final current = state;
    if (current is! ShiftsLoaded) return;
    emit(current.copyWith(loadingMore: true));
    try {
      final result = await _repository.fetchShifts(
        page: 0,
        size: 50,
        status: event.status,
      );
      final refreshed = state;
      if (refreshed is ShiftsLoaded) {
        emit(refreshed.copyWith(
          allItems: result.items,
          allPage: result.page,
          allTotalPages: result.totalPages,
          allStatus: event.status,
          clearAllStatus: event.status == null,
          loadingMore: false,
        ));
      }
    } catch (_) {
      final refreshed = state;
      if (refreshed is ShiftsLoaded) {
        emit(refreshed.copyWith(loadingMore: false));
      }
    }
  }
}
