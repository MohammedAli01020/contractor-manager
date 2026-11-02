import 'package:contractor_mobile/features/shifts/data/datasources/shifts_api.dart';
import 'package:contractor_mobile/features/shifts/data/models/shift_model.dart';
import 'package:contractor_mobile/features/shifts/presentation/shift_correction_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

class MockShiftsApi extends Mock implements ShiftsApi {}

void main() {
  late MockShiftsApi api;
  late ShiftModel shift;

  setUp(() {
    api = MockShiftsApi();
    when(() => api.fetchSites()).thenAnswer((_) async => [
          {'id': 4, 'name': 'موقع الاختبار', 'active': true},
        ]);
    when(() => api.fetchLiveEquipment()).thenAnswer((_) async => [
          {'id': 9, 'code': 'EQ-09', 'name': 'حفار', 'status': 'AVAILABLE'},
        ]);
    shift = const ShiftModel(
      id: 41,
      shiftDate: '2026-10-31',
      shiftType: 'MORNING',
      status: 'COMPLETED',
      siteName: 'موقع الاختبار',
      driverName: 'سائق الاختبار',
      equipmentCode: 'EQ-09',
      equipmentName: 'حفار',
      price: 1500,
      driverWage: 250,
      priceAfterWage: 1250,
      notes: '',
      siteId: 4,
      driverId: 7,
      equipmentId: 9,
      version: 3,
      equipmentOperatingCost: 100,
    );
  });

  testWidgets('shows the completed shift snapshot and current financial values',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: ShiftCorrectionScreen(shift: shift, api: api),
    ));
    await tester.pumpAndSettle();

    expect(find.text('تصحيح الوردية #41'), findsOneWidget);
    expect(find.text('سائق الاختبار'), findsOneWidget);
    expect(find.text('موقع الاختبار'), findsWidgets);
    expect(find.text('250.00 جنيه'), findsOneWidget);
    expect(find.text('أجر السائق (جنيه)'), findsOneWidget);
    expect(find.text('سبب التصحيح (إلزامي)'), findsOneWidget);
  });

  testWidgets('requires a reason before a correction can be submitted',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: ShiftCorrectionScreen(shift: shift, api: api),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('مراجعة وتأكيد التصحيح'));
    await tester.pump();

    expect(find.text('سبب التصحيح يجب ألا يقل عن 5 أحرف'), findsOneWidget);
    verifyNever(() => api.correctShift(any(), any()));
  });
}
