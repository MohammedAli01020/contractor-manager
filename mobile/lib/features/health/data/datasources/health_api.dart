import 'package:dio/dio.dart';

import '../../../../core/error/app_failure.dart';
import '../models/health_info_model.dart';

class HealthApi {
  HealthApi(this._dio);

  final Dio _dio;

  Future<HealthInfoModel> fetch() async {
    final response = await _dio.get<Map<String, dynamic>>('/health');
    final data = response.data;
    if (data == null) {
      throw const AppFailure(
        FailureType.server,
        'استجابة غير صالحة من الخادم.',
      );
    }
    return HealthInfoModel.fromJson(data);
  }
}
