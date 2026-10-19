import 'package:dio/dio.dart';

import '../../../../core/error/app_failure.dart';
import '../../domain/entities/health_info.dart';
import '../../domain/repositories/health_repository.dart';
import '../datasources/health_api.dart';

class HealthRepositoryImpl implements HealthRepository {
  HealthRepositoryImpl(this._api);

  final HealthApi _api;

  @override
  Future<HealthInfo> check() async {
    try {
      final model = await _api.fetch();
      return model.toEntity();
    } on DioError catch (e) {
      throw AppFailure.fromDio(e);
    } on FormatException {
      throw const AppFailure(
        FailureType.server,
        'استجابة غير صالحة من الخادم.',
      );
    } on TypeError {
      throw const AppFailure(
        FailureType.server,
        'استجابة غير صالحة من الخادم.',
      );
    }
  }
}
