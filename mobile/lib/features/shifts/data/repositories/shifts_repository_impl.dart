import 'package:dio/dio.dart';

import '../../../../core/error/app_failure.dart';
import '../datasources/shifts_api.dart';
import '../models/shift_model.dart';

class ShiftsRepositoryImpl {
  ShiftsRepositoryImpl(this._api);

  final ShiftsApi _api;

  Future<List<ShiftModel>> fetchRunningShifts() async {
    try {
      return await _api.fetchRunningShifts();
    } on DioError catch (error) {
      throw AppFailure.fromDio(error);
    } on FormatException {
      throw const AppFailure(
        FailureType.server,
        'استجابة الورديات الجارية غير صالحة.',
      );
    } on TypeError {
      throw const AppFailure(
        FailureType.server,
        'استجابة الورديات الجارية غير صالحة.',
      );
    }
  }

  Future<ShiftPage> fetchShifts({
    String? from,
    String? to,
    int page = 0,
    int size = 50,
    String? status,
  }) async {
    try {
      return await _api.fetchShifts(
        from: from,
        to: to,
        page: page,
        size: size,
        status: status,
      );
    } on DioError catch (error) {
      throw AppFailure.fromDio(error);
    } on FormatException {
      throw const AppFailure(
        FailureType.server,
        'استجابة قائمة الورديات غير صالحة.',
      );
    } on TypeError {
      throw const AppFailure(
        FailureType.server,
        'استجابة قائمة الورديات غير صالحة.',
      );
    }
  }
}
