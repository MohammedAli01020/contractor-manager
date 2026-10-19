import '../entities/health_info.dart';

abstract class HealthRepository {
  /// يرمي AppFailure عند أي فشل.
  Future<HealthInfo> check();
}
