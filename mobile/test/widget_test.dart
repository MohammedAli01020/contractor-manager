import 'package:contractor_mobile/core/widgets/error_view.dart';
import 'package:contractor_mobile/core/widgets/loading_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('ErrorView shows message and calls onRetry', (tester) async {
    var taps = 0;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ErrorView(
            message: 'حدث خطأ',
            onRetry: () => taps++,
          ),
        ),
      ),
    );

    expect(find.text('حدث خطأ'), findsOneWidget);

    await tester.tap(find.text('إعادة المحاولة'));
    expect(taps, 1);
  });

  testWidgets(
    'SkeletonLoadingView exposes a labelled loading state',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SkeletonLoadingView(
              label: 'جارٍ تحميل الورديات...',
            ),
          ),
        ),
      );

      expect(
        find.bySemanticsLabel('جارٍ تحميل الورديات...'),
        findsOneWidget,
      );

      expect(find.byType(SkeletonLoadingView), findsOneWidget);
    },
  );
}
