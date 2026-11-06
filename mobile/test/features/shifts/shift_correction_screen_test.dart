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
    await tester.scrollUntilVisible(
      find.text('أجر السائق (جنيه)'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('أجر السائق (جنيه)'), findsOneWidget);
  });

  testWidgets('HOUR correction submits a new measurement without a client wage',
      (tester) async {
    final hourly = ShiftModel(
      id: 42,
      shiftDate: '2026-10-31',
      shiftType: 'MORNING',
      status: 'COMPLETED',
      siteName: 'موقع الاختبار',
      driverName: 'سائق الاختبار',
      equipmentCode: 'EQ-09',
      equipmentName: 'حفار',
      price: 1500,
      driverWage: 40,
      compensationBasis: 'HOUR',
      compensationRate: 10,
      compensationCalculatedAmount: 40,
      actualHours: 4,
      priceAfterWage: 1460,
      notes: '',
      siteId: 4,
      driverId: 7,
      equipmentId: 9,
      version: 4,
      equipmentOperatingCost: 100,
    );
    when(() => api.correctShift(42, any())).thenAnswer((_) async => hourly);
    await tester.pumpWidget(MaterialApp(
      home: ShiftCorrectionScreen(shift: hourly, api: api),
    ));
    await tester.pumpAndSettle();
    expect(find.text('أجر السائق (جنيه)'), findsNothing);

    final measurement = find.ancestor(
      of: find.text('الساعات الفعلية الجديدة'),
      matching: find.byType(TextFormField),
    );
    await tester.scrollUntilVisible(
      measurement,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('سعر الساعة'), findsOneWidget);
    await tester.enterText(measurement, '5.5');

    final reason = find.ancestor(
      of: find.text('سبب التصحيح (إلزامي)'),
      matching: find.byType(TextFormField),
    );
    await tester.scrollUntilVisible(
      reason,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.enterText(reason, 'تصحيح ساعات التشغيل من سجل المعدة');
    tester.testTextInput.hide();
    await tester.pumpAndSettle();

    final submit = find.text('مراجعة وتأكيد التصحيح');
    await tester.scrollUntilVisible(
      submit,
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    await tester.tap(submit);
    await tester.pumpAndSettle();
    expect(find.text('تأكيد تصحيح الوردية'), findsOneWidget);
    expect(find.text('55.00 جنيه'), findsOneWidget);
    await tester.tap(find.text('تأكيد التصحيح'));
    await tester.pumpAndSettle();

    verify(() => api.correctShift(42, any(that: predicate<Map<String, dynamic>>(
      (payload) =>
          payload['actualHours'] == 5.5 &&
          payload['price'] == 1500.0 &&
          payload['equipmentOperatingCost'] == 100.0 &&
          !payload.containsKey('driverWage') &&
          !payload.containsKey('actualQuantity'),
    )))).called(1);
  });

  testWidgets('QUANTITY correction submits a measured quantity without a client wage',
      (tester) async {
    final quantity = ShiftModel(
      id: 43,
      shiftDate: '2026-10-31',
      shiftType: 'MORNING',
      status: 'COMPLETED',
      siteName: 'موقع الاختبار',
      driverName: 'سائق الاختبار',
      equipmentCode: 'EQ-09',
      equipmentName: 'حفار',
      price: 1500,
      driverWage: 40.63,
      compensationBasis: 'QUANTITY',
      compensationRate: 3.25,
      compensationCalculatedAmount: 40.63,
      compensationUnitCode: 'TON',
      actualQuantity: 12.5,
      priceAfterWage: 1459.37,
      notes: '',
      siteId: 4,
      driverId: 7,
      equipmentId: 9,
      version: 5,
      equipmentOperatingCost: 100,
    );
    when(() => api.correctShift(43, any())).thenAnswer((_) async => quantity);
    await tester.pumpWidget(MaterialApp(
      home: ShiftCorrectionScreen(shift: quantity, api: api),
    ));
    await tester.pumpAndSettle();
    expect(find.text('أجر السائق (جنيه)'), findsNothing);

    final measurement = find.ancestor(
      of: find.text('الكمية الفعلية الجديدة'),
      matching: find.byType(TextFormField),
    );
    await tester.scrollUntilVisible(
      measurement,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('سعر الوحدة'), findsOneWidget);
    expect(find.text('3.25 جنيه / TON'), findsOneWidget);
    await tester.enterText(measurement, '8.250');

    final reason = find.ancestor(
      of: find.text('سبب التصحيح (إلزامي)'),
      matching: find.byType(TextFormField),
    );
    await tester.scrollUntilVisible(
      reason,
      300,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.enterText(reason, 'تصحيح كمية الإنتاج من كشف الموقع');
    tester.testTextInput.hide();
    await tester.pumpAndSettle();

    final submit = find.text('مراجعة وتأكيد التصحيح');
    await tester.scrollUntilVisible(
      submit,
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.pumpAndSettle();
    await tester.tap(submit);
    await tester.pumpAndSettle();
    expect(find.text('تأكيد تصحيح الوردية'), findsOneWidget);
    expect(find.text('26.81 جنيه'), findsOneWidget);
    await tester.tap(find.text('تأكيد التصحيح'));
    await tester.pumpAndSettle();

    verify(() => api.correctShift(43, any(that: predicate<Map<String, dynamic>>(
      (payload) =>
          payload['actualQuantity'] == 8.25 &&
          payload['price'] == 1500.0 &&
          payload['equipmentOperatingCost'] == 100.0 &&
          !payload.containsKey('driverWage') &&
          !payload.containsKey('actualHours'),
    )))).called(1);
  });

  testWidgets('requires a reason before a correction can be submitted',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: ShiftCorrectionScreen(shift: shift, api: api),
    ));
    await tester.pumpAndSettle();

    final submitButton = find.text('مراجعة وتأكيد التصحيح');
    await tester.scrollUntilVisible(
      submitButton,
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(submitButton);
    await tester.pumpAndSettle();

    expect(find.text('سبب التصحيح يجب ألا يقل عن 5 أحرف'), findsOneWidget);
    verifyNever(() => api.correctShift(any(), any()));
  });

  testWidgets('allows a zero driver wage and submits the correction',
      (tester) async {
    when(() => api.correctShift(41, any())).thenAnswer((_) async => shift);
    await tester.pumpWidget(MaterialApp(
      home: ShiftCorrectionScreen(shift: shift, api: api),
    ));
    await tester.pumpAndSettle();

    await tester.scrollUntilVisible(
      find.text('أجر السائق (جنيه)'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    final wageField = find.ancestor(
      of: find.text('أجر السائق (جنيه)'),
      matching: find.byType(TextFormField),
    );
    expect(wageField, findsOneWidget);
    await tester.enterText(wageField, '0');

    await tester.scrollUntilVisible(
      find.text('سبب التصحيح (إلزامي)'),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    final reasonField = find.ancestor(
      of: find.text('سبب التصحيح (إلزامي)'),
      matching: find.byType(TextFormField),
    );
    await tester.enterText(reasonField, 'تصحيح الأجر بعد مراجعة المستندات');

    final submitButton = find.text('مراجعة وتأكيد التصحيح');
    await tester.scrollUntilVisible(
      submitButton,
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(submitButton);
    await tester.pumpAndSettle();

    expect(find.text('تأكيد تصحيح الوردية'), findsOneWidget);
    expect(find.textContaining('سيتم عكس قيد الأجر واستحقاق العامل الحالي.'), findsOneWidget);
    expect(find.textContaining('لن يتم إنشاء قيد أجر أو حركة استحقاق جديدة بقيمة صفر.'), findsOneWidget);
    await tester.tap(find.text('تأكيد التصحيح'));
    await tester.pumpAndSettle();

    verify(() => api.correctShift(41, any(that: predicate<Map<String, dynamic>>(
      (payload) => payload['driverWage'] == 0.0,
    )))).called(1);
    expect(find.text('أجر السائق يجب أن يكون أكبر من صفر'), findsNothing);
  });
}
