import 'package:equatable/equatable.dart';

abstract class HealthEvent extends Equatable {
  const HealthEvent();

  @override
  List<Object?> get props => [];
}

class HealthCheckRequested extends HealthEvent {
  const HealthCheckRequested();
}
