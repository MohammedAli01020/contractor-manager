import '../../domain/entities/health_info.dart';

class HealthInfoModel {
  const HealthInfoModel({
    required this.status,
    required this.database,
    required this.timestamp,
  });

  factory HealthInfoModel.fromJson(Map<String, dynamic> json) {
    return HealthInfoModel(
      status: json['status'] as String,
      database: json['database'] as String,
      timestamp: DateTime.parse(json['timestamp'] as String),
    );
  }

  final String status;
  final String database;
  final DateTime timestamp;

  HealthInfo toEntity() =>
      HealthInfo(status: status, database: database, timestamp: timestamp);
}
