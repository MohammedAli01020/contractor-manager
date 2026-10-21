import 'package:dio/dio.dart';

import '../../../../core/error/app_failure.dart';
import '../datasources/shifts_api.dart';
import '../models/shift_model.dart';

class ShiftsRepositoryImpl {
  ShiftsRepositoryImpl(this._api);

  final ShiftsApi _api;

  Future<List<ShiftModel>> fetchShifts() async {
    try {
      return await _api.fetchShifts();
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
