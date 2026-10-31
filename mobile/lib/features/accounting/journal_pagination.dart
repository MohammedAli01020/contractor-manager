/// Parsed response from GET /api/v1/accounting/journals.
class JournalPage {
  const JournalPage({
    required this.items,
    required this.page,
    required this.hasNext,
  });

  final List<Map<String, dynamic>> items;
  final int page;
  final bool hasNext;

  factory JournalPage.fromJson(Map<String, dynamic> json) {
    final rawItems = json['items'];
    if (rawItems is! List) {
      throw const FormatException('Journal page items must be a list.');
    }

    final rawPage = json['page'];
    final rawHasNext = json['hasNext'];
    return JournalPage(
      items: rawItems
          .whereType<Map>()
          .map((item) => Map<String, dynamic>.from(item))
          .toList(growable: false),
      page: rawPage is num ? rawPage.toInt() : 0,
      hasNext: rawHasNext is bool ? rawHasNext : false,
    );
  }
}

/// Appends new rows without duplicating IDs already present in the list.
List<T> appendUniqueById<T>(
  List<T> existing,
  List<T> incoming,
  Object? Function(T item) idOf,
) {
  final seenIds = existing.map(idOf).toSet();
  final result = List<T>.of(existing);
  for (final item in incoming) {
    if (seenIds.add(idOf(item))) {
      result.add(item);
    }
  }
  return result;
}
