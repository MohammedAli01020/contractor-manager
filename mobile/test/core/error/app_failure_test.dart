import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../lib/core/error/app_failure.dart';

void main() {
  group('AppFailure.fromDio', () {
    test('preserves the API message for a 409 conflict', () {
      final request = RequestOptions(path: '/shifts/34');
      final error = DioError(
        requestOptions: request,
        response: Response<dynamic>(
          requestOptions: request,
          statusCode: 409,
          data: <String, dynamic>{
            'status': 409,
            'code': 'BUSINESS_CONFLICT',
            'message': 'لا يمكن تعديل بيانات المصدر لوردية مكتملة.',
          },
        ),
        type: DioErrorType.response,
      );

      final failure = AppFailure.fromDio(error);

      expect(failure.type, FailureType.validation);
      expect(failure.statusCode, 409);
      expect(failure.message, 'لا يمكن تعديل بيانات المصدر لوردية مكتملة.');
    });
  });
}
