import 'package:equatable/equatable.dart';

class HealthInfo extends Equatable {
  const HealthInfo({
    required this.status,
    required this.database,
    required this.timestamp,
  });

  final String status;
  final String database;
  final DateTime timestamp;

  bool get isHealthy => status == 'UP' && database == 'UP';

  @override
  List<Object?> get props => [status, database, timestamp];
}
