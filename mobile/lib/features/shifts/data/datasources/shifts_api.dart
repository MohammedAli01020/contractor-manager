import 'package:dio/dio.dart';

import '../../../../core/error/app_failure.dart';
import '../models/shift_model.dart';

class ShiftPage {
  const ShiftPage({
    required this.items,
    required this.page,
    required this.totalPages,
  });

  final List<ShiftModel> items;
  final int page;
  final int totalPages;

  bool get hasMore => page + 1 < totalPages;
}

class ShiftsApi {
  ShiftsApi(this._dio);

  final Dio _dio;

  Future<List<ShiftModel>> fetchRunningShifts() async {
    final response = await _dio.get<List<dynamic>>('/shifts/running');
    final items = response.data;
    if (items == null) {
      throw const AppFailure(
        FailureType.server,
        'استجابة الورديات الجارية غير صالحة.',
      );
    }
    return items.map(_parseShift).toList();
  }

  Future<ShiftPage> fetchShifts({
    String? from,
    String? to,
    int page = 0,
    int size = 50,
    String? status,
  }) async {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null) query['status'] = status;
    if (from != null) query['from'] = from;
    if (to != null) query['to'] = to;

    final response = await _dio.get<Map<String, dynamic>>(
      '/shifts',
      queryParameters: query,
    );
    final data = response.data;
    if (data == null || data['items'] is! List) {
      throw const AppFailure(
        FailureType.server,
        'استجابة قائمة الورديات غير صالحة.',
      );
    }

    final items = data['items'] as List;
    return ShiftPage(
      items: items.map(_parseShift).toList(),
      page: _intValue(data['page'], page),
      totalPages: _intValue(data['totalPages'], 1),
    );
  }

  ShiftModel _parseShift(dynamic item) {
    if (item is! Map<String, dynamic>) {
      throw const FormatException('Invalid shift item');
    }
    return ShiftModel.fromJson(item);
  }

  int _intValue(dynamic value, int fallback) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '') ?? fallback;
  }
}
