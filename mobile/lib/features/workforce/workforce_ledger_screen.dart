import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

/// Read-only statement backed by the canonical Workforce ledger endpoints.
class WorkforceLedgerScreen extends StatefulWidget {
  const WorkforceLedgerScreen({
    Key? key,
    required this.memberId,
    required this.memberName,
  }) : super(key: key);

  final int memberId;
  final String memberName;

  @override
  State<WorkforceLedgerScreen> createState() => _WorkforceLedgerScreenState();
}

class _WorkforceLedgerScreenState extends State<WorkforceLedgerScreen> {
  List<Map<String, dynamic>> _entries = <Map<String, dynamic>>[];
  Map<String, dynamic> _balance = <String, dynamic>{};
  bool _loading = true;
  String? _error;

  String _money(dynamic value) {
    final amount = value is num ? value.toDouble() : double.tryParse('$value') ?? 0;
    return amount.toStringAsFixed(2);
  }

  String _date(dynamic value) {
    if (value == null) return '—';
    final parsed = DateTime.tryParse(value.toString());
    if (parsed == null) return value.toString();
    return '${parsed.year.toString().padLeft(4, '0')}-'
        '${parsed.month.toString().padLeft(2, '0')}-'
        '${parsed.day.toString().padLeft(2, '0')}';
  }

  String _typeLabel(String type) {
    switch (type) {
      case 'EARNING':
      case 'ADJUSTMENT':
        return 'استحقاق';
      case 'PAYMENT':
        return 'صرف مستحقات';
      case 'ADVANCE':
        return 'سلفة من المستحقات';
      case 'RECOVERABLE_ADVANCE':
        return 'سلفة مستردة';
      case 'ADVANCE_RECOVERY':
        return 'استرداد سلفة';
      default:
        return type;
    }
  }

  String _message(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 401) return 'انتهت الجلسة؛ سجّل الدخول مرة أخرى.';
      if (error.response?.statusCode == 404) return 'بيانات العامل غير موجودة في هذه الشركة.';
    }
    return 'تعذر تحميل كشف الحساب. تحقق من الاتصال وحاول مرة أخرى.';
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() { _loading = true; _error = null; });
    try {
      final base = '/workforce/members/${widget.memberId}/ledger';
      final responses = await Future.wait<dynamic>(<Future<dynamic>>[
        getIt<Dio>().get<List<dynamic>>(base),
        getIt<Dio>().get<Map<String, dynamic>>('$base/balance'),
      ]);
      final rows = responses[0].data as List<dynamic>? ?? <dynamic>[];
      final summary = responses[1].data as Map<String, dynamic>? ?? <String, dynamic>{};
      if (!mounted) return;
      setState(() {
        _entries = rows.whereType<Map<String, dynamic>>().toList();
        _balance = summary;
      });
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final payable = _money(_balance['payableBalance']);
    final recoverable = _money(_balance['recoverableAdvanceBalance']);
    return Scaffold(
      appBar: AppBar(
        title: Text('كشف حساب ${widget.memberName}',
            maxLines: 1, overflow: TextOverflow.ellipsis),
        actions: <Widget>[
          IconButton(tooltip: 'تحديث', onPressed: _loading ? null : _load,
              icon: const Icon(Icons.refresh)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: <Widget>[
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 12),
                        ElevatedButton(onPressed: _load, child: const Text('إعادة المحاولة')),
                      ],
                    ),
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView(
                    padding: const EdgeInsets.all(12),
                    children: <Widget>[
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              const Text('المستحقات المتاحة',
                                  style: TextStyle(fontWeight: FontWeight.bold)),
                              const SizedBox(height: 4),
                              Text('$payable جنيه',
                                  style: Theme.of(context).textTheme.headlineSmall),
                              const Divider(),
                              const Text('السلف المستردة القائمة'),
                              Text('$recoverable جنيه',
                                  style: Theme.of(context).textTheme.titleLarge),
                              const SizedBox(height: 4),
                              const Text(
                                'السلف المستردة منفصلة عن المستحقات ولا تخصم منها إلا عند تسجيل الاسترداد.',
                                style: TextStyle(fontSize: 12),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const Padding(
                        padding: EdgeInsets.fromLTRB(4, 16, 4, 8),
                        child: Text('الحركات المالية',
                            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                      ),
                      if (_entries.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(24),
                          child: Text('لا توجد حركات مالية مسجلة لهذا الفرد.',
                              textAlign: TextAlign.center),
                        )
                      else
                        for (final entry in _entries)
                          Card(
                            child: ListTile(
                              leading: CircleAvatar(
                                child: Icon((entry['balanceEffect'] is num &&
                                        (entry['balanceEffect'] as num) < 0)
                                    ? Icons.south_west
                                    : Icons.north_east),
                              ),
                              title: Text(_typeLabel(entry['transactionType']?.toString() ?? '')),
                              subtitle: Text(
                                '${_date(entry['transactionDate'])} • '
                                '${entry['description'] ?? ''}\n'
                                'الرصيد بعد الحركة: ${_money(entry['balanceAfter'])} جنيه',
                              ),
                              isThreeLine: true,
                              trailing: Text(
                                '${_money(entry['amount'])} ج',
                                style: const TextStyle(fontWeight: FontWeight.bold),
                              ),
                            ),
                          ),
                    ],
                  ),
                ),
    );
  }
}
