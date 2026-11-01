import 'package:flutter_test/flutter_test.dart';
import 'package:contractor_mobile/features/accounting/journal_pagination.dart';

void main() {
  group('JournalPage', () {
    test('parses paged API metadata and items', () {
      final page = JournalPage.fromJson({
        'items': [
          {'id': 10, 'description': 'First'},
          {'id': 11, 'description': 'Second'},
        ],
        'page': 2,
        'size': 20,
        'totalElements': 45,
        'totalPages': 3,
        'hasNext': true,
        'hasPrevious': true,
      });

      expect(page.page, 2);
      expect(page.hasNext, isTrue);
      expect(page.items.map((item) => item['id']), [10, 11]);
    });

    test('rejects a response without an items list', () {
      expect(
        () => JournalPage.fromJson({'page': 0, 'hasNext': false}),
        throwsFormatException,
      );
    });
  });

  group('appendUniqueById', () {
    test('keeps existing order and skips duplicate IDs from the next page', () {
      final firstPage = [
        {'id': 20, 'description': 'Newest'},
        {'id': 19, 'description': 'Older'},
      ];
      final secondPage = [
        {'id': 19, 'description': 'Duplicate from overlap'},
        {'id': 18, 'description': 'Next'},
      ];

      final combined = appendUniqueById<Map<String, dynamic>>(
        firstPage,
        secondPage,
        (item) => item['id'],
      );

      expect(combined.map((item) => item['id']), [20, 19, 18]);
      expect(combined.last['description'], 'Next');
    });

    test('does not change the existing list when the next page is empty', () {
      final existing = [
        {'id': 5},
      ];

      final combined = appendUniqueById<Map<String, dynamic>>(
        existing,
        const [],
        (item) => item['id'],
      );

      expect(combined, existing);
      expect(combined, isNot(same(existing)));
    });
  });
}
