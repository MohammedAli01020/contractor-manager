import 'package:flutter_test/flutter_test.dart';
import 'package:contractor_mobile/features/shifts/data/models/shift_model.dart';

void main() {
  test('parses canonical workforce member identity from shift response', () {
    final shift = ShiftModel.fromJson({
      'id': 12,
      'shiftDate': '2026-11-09',
      'shiftType': 'MORNING',
      'status': 'IN_PROGRESS',
      'driverId': 7,
      'workforceMemberId': 42,
    });

    expect(shift.driverId, 7);
    expect(shift.workforceMemberId, 42);
  });

  test('keeps legacy shift response compatible without workforce identity', () {
    final shift = ShiftModel.fromJson({
      'id': 13,
      'shiftDate': '2026-11-08',
      'shiftType': 'EVENING',
      'status': 'COMPLETED',
      'driverId': 8,
    });

    expect(shift.driverId, 8);
    expect(shift.workforceMemberId, isNull);
  });
}
