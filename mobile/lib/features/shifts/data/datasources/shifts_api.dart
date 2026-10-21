import 'package:dio/dio.dart';

import '../../../../core/error/app_failure.dart';
import '../models/shift_model.dart';

class ShiftsApi {
  ShiftsApi(this._dio);

  final Dio _dio;

  Future<List<ShiftModel>> fetchShifts() async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/shifts',
      queryParameters: const {'page': 0, 'size': 50},
    );
    final data = response.data;
    final items = data == null ? null : data['items'];
    if (items is! List) {
      throw const AppFailure(
        FailureType.server,
        'استجابة قائمة الورديات غير صالحة.',
      );
    }
    return items.map((item) {
      if (item is! Map<String, dynamic>) {
        throw const FormatException('Invalid shift item');
      }
      return ShiftModel.fromJson(item);
    }).toList();
  }
}
