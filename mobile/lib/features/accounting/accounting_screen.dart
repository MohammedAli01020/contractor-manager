import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';
import 'journal_entry_form.dart';

class AccountingScreen extends StatefulWidget {
  const AccountingScreen({Key? key, required this.canPostJournals}) : super(key: key);

  final bool canPostJournals;

  @override
  State<AccountingScreen> createState() => _AccountingScreenState();
}

class _AccountingScreenState extends State<AccountingScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  bool _loadingAccounts = true;
  bool _loadingJournals = true;
  bool _loadingMoreJournals = false;
  bool _journalHasMore = false;
  int _journalPage = 0;
  String? _loadMoreJournalsError;
  bool _initializingAccounts = false;
  String? _accountsError;
  String? _journalsError;
  List<_Account> _accounts = [];
  List<_Journal> _journals = [];

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    _tabs.addListener(_handleTabChange);
    _loadAccounts();
    _loadJournals();
  }

  void _handleTabChange() {
    if (mounted) setState(() {});
  }

  Future<void> _openJournalForm() async {
    final posted = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(builder: (_) => const JournalEntryFormScreen()),
    );
    if (posted == true && mounted) {
      await _loadJournals();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم ترحيل القيد بنجاح')),
      );
    }
  }

  @override
  void dispose() {
    _tabs.removeListener(_handleTabChange);
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _loadAccounts() async {
    setState(() {
      _loadingAccounts = true;
      _accountsError = null;
    });
    try {
      final response = await getIt<Dio>().get<List<dynamic>>('/accounting/accounts');
      final data = response.data;
      if (data == null) throw const FormatException();
      final accounts = data
          .whereType<Map<String, dynamic>>()
          .map(_Account.fromJson)
          .toList();
      if (!mounted) return;
      setState(() {
        _accounts = accounts;
        _loadingAccounts = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _accountsError = _errorMessage(error);
        _loadingAccounts = false;
      });
    }
  }

  Future<void> _initializeDefaultAccounts() async {
    setState(() {
      _initializingAccounts = true;
      _accountsError = null;
    });
    try {
      await getIt<Dio>().post('/accounting/accounts/initialize-defaults');
      await _loadAccounts();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم تجهيز دليل الحسابات بنجاح')),
      );
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _accountsError = _errorMessage(error);
      });
    } finally {
      if (mounted) {
        setState(() {
          _initializingAccounts = false;
        });
      }
    }
  }

  Future<void> _loadJournals({bool loadMore = false}) async {
    if (loadMore && (!_journalHasMore || _loadingMoreJournals || _loadingJournals)) {
      return;
    }
    final requestedPage = loadMore ? _journalPage + 1 : 0;
    setState(() {
      if (loadMore) {
        _loadingMoreJournals = true;
        _loadMoreJournalsError = null;
      } else {
        _loadingJournals = true;
        _journalsError = null;
        _loadMoreJournalsError = null;
      }
    });
    try {
      final response = await getIt<Dio>().get<Map<String, dynamic>>(
        '/accounting/journals',
        queryParameters: {'page': requestedPage, 'size': 20},
      );
      final data = response.data;
      if (data == null || data['items'] is! List) throw const FormatException();
      final journals = (data['items'] as List<dynamic>)
          .whereType<Map<String, dynamic>>()
          .map(_Journal.fromJson)
          .toList();
      if (!mounted) return;
      setState(() {
        _journals = loadMore ? [..._journals, ...journals] : journals;
        _journalPage = (data['page'] as num?)?.toInt() ?? requestedPage;
        _journalHasMore = data['hasNext'] as bool? ?? false;
        _loadingJournals = false;
        _loadingMoreJournals = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        if (loadMore) {
          _loadMoreJournalsError = _errorMessage(error);
          _loadingMoreJournals = false;
        } else {
          _journalsError = _errorMessage(error);
          _loadingJournals = false;
        }
      });
    }
  }

  String _errorMessage(Object error) {
    if (error is DioError) {
      final status = error.response?.statusCode;
      if (status == 401) return 'انتهت جلسة الدخول. سجّل الدخول مرة أخرى.';
      if (status == 403) return 'ليس لديك صلاحية لعرض هذه البيانات.';
      if (status != null && status >= 500) return 'حدث خطأ بالخادم. حاول لاحقًا.';
      if (error.type == DioErrorType.connectTimeout ||
          error.type == DioErrorType.receiveTimeout ||
          error.type == DioErrorType.other) {
        return 'تعذر الاتصال بالخادم. تأكد من تشغيله واتصال الشبكة.';
      }
    }
    return 'تعذر تحميل البيانات. حاول مرة أخرى.';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('المحاسبة'),
        bottom: TabBar(
          controller: _tabs,
          indicatorColor: const Color(0xFF0D9488),
          indicatorWeight: 3,
          indicatorSize: TabBarIndicatorSize.tab,
          labelColor: const Color(0xFF17324D),
          unselectedLabelColor: const Color(0xFF526273),
          labelStyle: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w700,
          ),
          unselectedLabelStyle: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w600,
          ),
          tabs: const [
            Tab(text: 'دليل الحسابات', icon: Icon(Icons.account_tree_outlined)),
            Tab(text: 'القيود اليومية', icon: Icon(Icons.receipt_long_outlined)),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _AccountsTab(
            accounts: _accounts,
            loading: _loadingAccounts,
            error: _accountsError,
            onRefresh: _loadAccounts,
            onInitializeDefaults: _initializeDefaultAccounts,
            initializingDefaults: _initializingAccounts,
          ),
          _JournalsTab(
            journals: _journals,
            loading: _loadingJournals,
            error: _journalsError,
            onRefresh: _loadJournals,
            onLoadMore: () => _loadJournals(loadMore: true),
            hasMore: _journalHasMore,
            loadingMore: _loadingMoreJournals,
            loadMoreError: _loadMoreJournalsError,
          ),
        ],
      ),
      floatingActionButton: widget.canPostJournals && _tabs.index == 1
          ? FloatingActionButton.extended(
              onPressed: _openJournalForm,
              icon: const Icon(Icons.add),
              label: const Text('قيد جديد'),
            )
          : null,
    );
  }
}

class _AccountsTab extends StatelessWidget {
  const _AccountsTab({
    required this.accounts,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onInitializeDefaults,
    required this.initializingDefaults,
  });

  final List<_Account> accounts;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onInitializeDefaults;
  final bool initializingDefaults;

  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (error != null) {
      return _RetryView(message: error!, onRetry: onRefresh);
    }
    if (accounts.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          children: [
            const SizedBox(height: 100),
            const Icon(Icons.account_tree_outlined, size: 48),
            const SizedBox(height: 12),
            const Center(child: Text('لا توجد حسابات لعرضها')),
            const SizedBox(height: 8),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 24),
              child: Text(
                'يمكن لمدير الشركة تجهيز دليل الحسابات الافتراضي للبدء.',
                textAlign: TextAlign.center,
              ),
            ),
            const SizedBox(height: 16),
            Center(
              child: ElevatedButton.icon(
                onPressed: initializingDefaults ? null : onInitializeDefaults,
                icon: initializingDefaults
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.account_tree_outlined),
                label: Text(initializingDefaults ? 'جارٍ التجهيز...' : 'تجهيز دليل الحسابات'),
              ),
            ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.builder(
        padding: const EdgeInsets.all(12),
        itemCount: accounts.length,
        itemBuilder: (context, index) {
          final account = accounts[index];
          return Card(
            child: ListTile(
              leading: CircleAvatar(
                child: Text(account.code.length > 3
                    ? account.code.substring(account.code.length - 3)
                    : account.code),
              ),
              title: Text(account.name),
              subtitle: Text('${account.code} · ${_accountTypeLabel(account.type)}'),
              trailing: Icon(
                account.active ? Icons.check_circle_outline : Icons.block_outlined,
                color: account.active ? Colors.green : Colors.grey,
              ),
            ),
          );
        },
      ),
    );
  }
}

class _JournalsTab extends StatelessWidget {
  const _JournalsTab({
    required this.journals,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onLoadMore,
    required this.hasMore,
    required this.loadingMore,
    required this.loadMoreError,
  });

  final List<_Journal> journals;
  final bool loading;
  final String? error;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onLoadMore;
  final bool hasMore;
  final bool loadingMore;
  final String? loadMoreError;

  @override
  Widget build(BuildContext context) {
    if (loading) return const Center(child: CircularProgressIndicator());
    if (error != null) {
      return _RetryView(message: error!, onRetry: onRefresh);
    }
    if (journals.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          children: const [
            SizedBox(height: 120),
            Icon(Icons.receipt_long_outlined, size: 48),
            SizedBox(height: 12),
            Center(child: Text('لا توجد قيود محاسبية حتى الآن')),
          ],
        ),
      );
    }
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.pixels >=
            notification.metrics.maxScrollExtent - 200) {
          onLoadMore();
        }
        return false;
      },
      child: RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView.builder(
          padding: const EdgeInsets.all(12),
          itemCount: journals.length + (hasMore ? 1 : 0),
          itemBuilder: (context, index) {
            if (index >= journals.length) {
              if (loadMoreError != null) {
                return TextButton(
                  onPressed: onLoadMore,
                  child: Text('تعذر تحميل المزيد. اضغط لإعادة المحاولة: $loadMoreError'),
                );
              }
              return Padding(
                padding: const EdgeInsets.all(16),
                child: Center(
                  child: loadingMore
                      ? const CircularProgressIndicator()
                      : const Text('اسحب لأسفل لعرض المزيد'),
                ),
              );
            }
            final journal = journals[index];
            return Card(
              child: ListTile(
                leading: const CircleAvatar(child: Icon(Icons.receipt_long_outlined)),
                title: Text(journal.description.isEmpty
                    ? 'قيد رقم ${journal.id}'
                    : journal.description),
                subtitle: Text(
                  '${journal.date} · ${journal.status} · ${journal.sourceType}',
                ),
                trailing: const Icon(Icons.chevron_left),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => _JournalDetailsScreen(journalId: journal.id),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class _JournalDetailsScreen extends StatefulWidget {
  const _JournalDetailsScreen({Key? key, required this.journalId}) : super(key: key);
  final int journalId;

  @override
  State<_JournalDetailsScreen> createState() => _JournalDetailsScreenState();
}

class _JournalDetailsScreenState extends State<_JournalDetailsScreen> {
  bool _loading = true;
  String? _error;
  Map<String, dynamic>? _journal;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final response = await getIt<Dio>()
          .get<Map<String, dynamic>>('/accounting/journals/${widget.journalId}');
      if (response.data == null) throw const FormatException();
      if (!mounted) return;
      setState(() {
        _journal = response.data;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error is DioError && error.response?.statusCode == 404
            ? 'القيد غير موجود أو لا يتبع شركتك.'
            : 'تعذر تحميل تفاصيل القيد.';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final journal = _journal;
    return Scaffold(
      appBar: AppBar(title: Text('تفاصيل القيد #${widget.journalId}')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? _RetryView(message: _error!, onRetry: _load)
              : _details(journal!),
    );
  }

  Widget _details(Map<String, dynamic> journal) {
    final lines = (journal['lines'] as List<dynamic>? ?? const [])
        .whereType<Map<String, dynamic>>()
        .toList();
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text((journal['description'] as String?)?.isNotEmpty == true
                    ? journal['description'] as String
                    : 'قيد رقم ${journal['id']}',
                    style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                Text('التاريخ: ${journal['accountingDate'] ?? '-'}'),
                Text('الحالة: ${journal['status'] ?? '-'}'),
                Text('المصدر: ${journal['sourceType'] ?? '-'}'),
                const Divider(height: 24),
                _MoneyLine(label: 'إجمالي المدين', value: journal['totalDebit']),
                _MoneyLine(label: 'إجمالي الدائن', value: journal['totalCredit']),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text('سطور القيد', style: Theme.of(context).textTheme.titleMedium),
        for (final line in lines)
          Card(
            child: ListTile(
              title: Text((line['description'] as String?)?.isNotEmpty == true
                  ? line['description'] as String
                  : 'سطر رقم ${line['lineNumber']}'),
              subtitle: Text('الحساب رقم ${line['accountId'] ?? '-'}'),
              trailing: SizedBox(
                width: 118,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text('مدين: ${_money(line['debit'])}'),
                    Text('دائن: ${_money(line['credit'])}'),
                  ],
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _RetryView extends StatelessWidget {
  const _RetryView({required this.message, required this.onRetry});

  final String message;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 44),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            ElevatedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: const Text('إعادة المحاولة'),
            ),
          ],
        ),
      ),
    );
  }
}

class _MoneyLine extends StatelessWidget {
  const _MoneyLine({required this.label, required this.value});
  final String label;
  final dynamic value;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Expanded(child: Text(label)),
            Text('${_money(value)} جنيه'),
          ],
        ),
      );
}

class _Account {
  const _Account({
    required this.id,
    required this.code,
    required this.name,
    required this.type,
    required this.active,
  });

  final int id;
  final String code;
  final String name;
  final String type;
  final bool active;

  factory _Account.fromJson(Map<String, dynamic> json) => _Account(
        id: (json['id'] as num).toInt(),
        code: json['accountCode'] as String? ?? '',
        name: json['name'] as String? ?? '',
        type: json['accountType'] as String? ?? '',
        active: json['active'] as bool? ?? false,
      );
}

class _Journal {
  const _Journal({
    required this.id,
    required this.date,
    required this.description,
    required this.status,
    required this.sourceType,
  });

  final int id;
  final String date;
  final String description;
  final String status;
  final String sourceType;

  factory _Journal.fromJson(Map<String, dynamic> json) => _Journal(
        id: (json['id'] as num).toInt(),
        date: json['accountingDate'] as String? ?? '-',
        description: json['description'] as String? ?? '',
        status: json['status'] as String? ?? '-',
        sourceType: json['sourceType'] as String? ?? '-',
      );
}

String _accountTypeLabel(String type) {
  switch (type) {
    case 'ASSET':
      return 'أصول';
    case 'LIABILITY':
      return 'التزامات';
    case 'EQUITY':
      return 'حقوق ملكية';
    case 'REVENUE':
      return 'إيرادات';
    case 'EXPENSE':
      return 'مصروفات';
    default:
      return type.isEmpty ? 'غير محدد' : type;
  }
}

String _money(dynamic value) {
  if (value is num) return value.toStringAsFixed(2);
  return double.tryParse(value?.toString() ?? '')?.toStringAsFixed(2) ?? '0.00';
}
