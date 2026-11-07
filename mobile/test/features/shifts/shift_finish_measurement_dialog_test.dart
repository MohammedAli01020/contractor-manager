import 'package:contractor_mobile/features/shifts/data/models/shift_model.dart';
import 'package:contractor_mobile/features/shifts/presentation/shift_finish_measurement_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

ShiftModel quantityShift({double rate = 10.01}) => ShiftModel(
      id: 52,
      shiftDate: '2026-11-05',
      shiftType: 'MORNING',
      status: 'IN_PROGRESS',
      siteName: 'الموقع',
      driverName: 'العامل',
      equipmentCode: 'EQ-1',
      equipmentName: 'لودر',
      price: 1000,
      driverWage: 0,
      compensationBasis: 'QUANTITY',
      compensationRate: rate,
      compensationUnitCode: 'TON',
      priceAfterWage: 0,
      notes: '',
    );

ShiftModel hourlyShift({double rate = 125.50}) => ShiftModel(
      id: 53,
      shiftDate: '2026-11-07',
      shiftType: 'MORNING',
      status: 'IN_PROGRESS',
      siteName: 'الموقع',
      driverName: 'العامل',
      equipmentCode: 'EQ-1',
      equipmentName: 'لودر',
      price: 1000,
      driverWage: 0,
      compensationBasis: 'HOUR',
      compensationRate: rate,
      priceAfterWage: 0,
      notes: '',
    );

Future<void> openDialog(WidgetTester tester, ShiftModel shift) async {
  await tester.pumpWidget(MaterialApp(
    home: Builder(
      builder: (context) => Scaffold(
        body: ElevatedButton(
          onPressed: () => showDialog<double>(
            context: context,
            builder: (_) => ShiftFinishMeasurementDialog(shift: shift),
          ),
          child: const Text('open'),
        ),
      ),
    ),
  ));
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('quantity dialog previews rate times actual quantity',
      (tester) async {
    await openDialog(tester, quantityShift());

    expect(find.text('سعر الوحدة المعتمد: 10.01 جنيه'), findsOneWidget);
    expect(find.text('الكمية الفعلية'), findsOneWidget);
    expect(find.text('TON'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '2.345');
    await tester.pumpAndSettle();

    expect(find.text('إجمالي الأجر: 23.47 جنيه'), findsOneWidget);
    expect(find.textContaining('الإجمالي صفر'), findsNothing);
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNotNull);

    await tester.tap(find.text('تأكيد الإنهاء'));
    await tester.pumpAndSettle();
  });

  testWidgets('zero unit rate warns and still allows measured completion',
      (tester) async {
    await openDialog(tester, quantityShift(rate: 0));

    await tester.enterText(find.byType(TextField), '3.500');
    await tester.pumpAndSettle();

    expect(find.text('إجمالي الأجر: 0.00 جنيه'), findsOneWidget);
    expect(find.textContaining('الإجمالي صفر'), findsOneWidget);
    expect(find.textContaining('لن تُسترد سلفة'), findsOneWidget);
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNotNull);
  });

  testWidgets('rejects zero and quantity precision beyond three decimals',
      (tester) async {
    await openDialog(tester, quantityShift());

    await tester.enterText(find.byType(TextField), '0');
    await tester.pumpAndSettle();
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNull);

    await tester.enterText(find.byType(TextField), '1.2345');
    await tester.pumpAndSettle();
    expect(find.text('أدخل قيمة موجبة وبحد أقصى 3 خانات عشرية'), findsOneWidget);
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNull);
  });
  testWidgets('hourly dialog asks for actual hours and previews the frozen rate',
      (tester) async {
    await openDialog(tester, hourlyShift());

    expect(find.text('سعر الساعة المعتمد: 125.50 جنيه'), findsOneWidget);
    expect(find.text('ساعات العمل الفعلية'), findsOneWidget);
    expect(find.text('ساعة'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '7.50');
    await tester.pumpAndSettle();

    expect(find.text('إجمالي الأجر: 941.25 جنيه'), findsOneWidget);
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNotNull);
  });

  testWidgets('hourly dialog rejects zero and precision beyond two decimals',
      (tester) async {
    await openDialog(tester, hourlyShift());

    await tester.enterText(find.byType(TextField), '0');
    await tester.pumpAndSettle();
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNull);

    await tester.enterText(find.byType(TextField), '7.555');
    await tester.pumpAndSettle();
    expect(find.text('أدخل قيمة موجبة وبحد أقصى 2 خانات عشرية'), findsOneWidget);
    expect(tester.widget<ElevatedButton>(find.widgetWithText(
      ElevatedButton, 'تأكيد الإنهاء')).onPressed, isNull);
  });

}
