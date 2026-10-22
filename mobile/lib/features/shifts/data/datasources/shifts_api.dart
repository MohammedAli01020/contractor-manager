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


  Future<List<Map<String, dynamic>>> fetchLiveDrivers() async {
    final response = await _dio.get<List<dynamic>>('/live/drivers');
    return _mapList(response.data, 'قائمة السائقين غير صالحة.');
  }

  Future<List<Map<String, dynamic>>> fetchLiveEquipment() async {
    final response = await _dio.get<List<dynamic>>('/live/equipment');
    return _mapList(response.data, 'قائمة المعدات غير صالحة.');
  }

  Future<List<Map<String, dynamic>>> fetchSites() async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/sites',
      queryParameters: const {'page': 0, 'size': 100},
    );
    final data = response.data;
    if (data == null || data['items'] is! List) {
      throw const AppFailure(FailureType.server, 'قائمة المواقع غير صالحة.');
    }
    return _mapList(data['items'] as List, 'قائمة المواقع غير صالحة.');
  }

  Future<Map<String, dynamic>> fetchDefaults({
    int? driverId,
    int? equipmentId,
    required String shiftType,
  }) async {
    final response = await _dio.get<Map<String, dynamic>>(
      '/shifts/defaults',
      queryParameters: {
        if (driverId != null) 'driverId': driverId,
        if (equipmentId != null) 'equipmentId': equipmentId,
        'shiftType': shiftType,
      },
    );
    return response.data ?? <String, dynamic>{};
  }

  Future<ShiftModel> fetchShift(int shiftId) async {
    final response = await _dio.get<Map<String, dynamic>>('/shifts/$shiftId');
    if (response.data == null) {
      throw const AppFailure(FailureType.server, 'لم تصل بيانات الوردية المطلوبة.');
    }
    return ShiftModel.fromJson(response.data!);
  }

  Future<ShiftModel> saveShift(Map<String, dynamic> payload, {int? id}) async {
    final response = id == null
        ? await _dio.post<Map<String, dynamic>>('/shifts', data: payload)
        : await _dio.put<Map<String, dynamic>>('/shifts/$id', data: payload);
    if (response.data == null) {
      throw const AppFailure(FailureType.server, 'لم تصل بيانات الوردية بعد الحفظ.');
    }
    return ShiftModel.fromJson(response.data!);
  }


  Future<List<Map<String, dynamic>>> fetchShiftHistory(int shiftId) async {
    final response = await _dio.get<List<dynamic>>('/shifts/$shiftId/history');
    return _mapList(response.data, 'سجل التعديلات غير صالح.');
  }

  Future<ShiftModel> finishShift(ShiftModel shift) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/shifts/${shift.id}/finish',
      data: {'version': shift.version},
    );
    if (response.data == null) {
      throw const AppFailure(FailureType.server, 'لم تصل بيانات الوردية بعد الإنهاء.');
    }
    return ShiftModel.fromJson(response.data!);
  }

  Future<ShiftModel> cancelShift(ShiftModel shift, String reason) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/shifts/${shift.id}/cancel',
      data: {'version': shift.version, 'reason': reason},
    );
    if (response.data == null) {
      throw const AppFailure(FailureType.server, 'لم تصل بيانات الوردية بعد الإلغاء.');
    }
    return ShiftModel.fromJson(response.data!);
  }

  Future<ShiftModel> reinstateShift(ShiftModel shift) async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/shifts/${shift.id}/reinstate',
      data: {'version': shift.version},
    );
    if (response.data == null) {
      throw const AppFailure(FailureType.server, 'لم تصل بيانات الوردية بعد إعادة التفعيل.');
    }
    return ShiftModel.fromJson(response.data!);
  }

  List<Map<String, dynamic>> _mapList(List<dynamic>? items, String message) {
    if (items == null) {
      throw AppFailure(FailureType.server, message);
    }
    return items.whereType<Map<String, dynamic>>().toList();
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
