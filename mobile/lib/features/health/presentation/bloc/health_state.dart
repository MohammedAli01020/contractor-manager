import 'package:equatable/equatable.dart';

import '../../domain/entities/health_info.dart';

abstract class HealthState extends Equatable {
  const HealthState();

  @override
  List<Object?> get props => [];
}

class HealthInitial extends HealthState {
  const HealthInitial();
}

class HealthLoading extends HealthState {
  const HealthLoading();
}

class HealthLoaded extends HealthState {
  const HealthLoaded(this.info);

  final HealthInfo info;

  @override
  List<Object?> get props => [info];
}

class HealthFailed extends HealthState {
  const HealthFailed(this.message);

  final String message;

  @override
  List<Object?> get props => [message];
}
