import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

/// Read-only statement backed by the canonical Workforce ledger endpoints.
class WorkforceLedgerScreen extends StatefulWidget {
  const WorkforceLedgerScreen({
    Key? key,
    required this.memberId,
    required this.memberName,
    required this.canManage,
  }) : super(key: key);

  final int memberId;
  final String memberName;
  final bool canManage;

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

  Future<void> _openCommand({required bool advance}) async {
    try {
      final response = await getIt<Dio>().get<List<dynamic>>('/accounting/accounts');
      final accounts = (response.data ?? <dynamic>[])
          .whereType<Map<String, dynamic>>()
          .where((account) => account['active'] == true &&
              account['cashBankAccount'] == true)
          .toList();
      if (!mounted) return;
      if (accounts.isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('لا يوجد حساب خزينة أو بنك نشط للصرف منه.')),
        );
        return;
      }
      final posted = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (_) => _WorkforceLedgerCommandDialog(
          memberId: widget.memberId,
          advance: advance,
          accounts: accounts,
        ),
      );
      if (posted == true && mounted) {
        await _load();
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(advance ? 'تم تسجيل السلفة.' : 'تم صرف المستحقات.')),
        );
      }
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_message(error))),
      );
    }
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
          if (widget.canManage)
            PopupMenuButton<String>(
              tooltip: 'عملية مالية',
              icon: const Icon(Icons.add_card),
              onSelected: (value) => _openCommand(advance: value == 'advance'),
              itemBuilder: (_) => const <PopupMenuEntry<String>>[
                PopupMenuItem<String>(
                  value: 'payment',
                  child: Text('صرف مستحقات'),
                ),
                PopupMenuItem<String>(
                  value: 'advance',
                  child: Text('تسجيل سلفة مستردة'),
                ),
              ],
            ),
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


class _WorkforceLedgerCommandDialog extends StatefulWidget {
  const _WorkforceLedgerCommandDialog({
    Key? key,
    required this.memberId,
    required this.advance,
    required this.accounts,
  }) : super(key: key);

  final int memberId;
  final bool advance;
  final List<Map<String, dynamic>> accounts;

  @override
  State<_WorkforceLedgerCommandDialog> createState() =>
      _WorkforceLedgerCommandDialogState();
}

class _WorkforceLedgerCommandDialogState
    extends State<_WorkforceLedgerCommandDialog> {
  final _formKey = GlobalKey<FormState>();
  final _amount = TextEditingController();
  final _description = TextEditingController();
  late final String _requestKey;
  late int? _accountId;
  DateTime _date = DateTime.now();
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _requestKey = 'wf-${widget.memberId}-${widget.advance ? 'adv' : 'pay'}-${DateTime.now().microsecondsSinceEpoch}';
    _accountId = (widget.accounts.first['id'] as num).toInt();
  }

  @override
  void dispose() {
    _amount.dispose();
    _description.dispose();
    super.dispose();
  }

  String _dateText(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';

  String? _validateAmount(String? value) {
    final normalized = (value ?? '').trim().replaceAll(',', '.');
    final parsed = double.tryParse(normalized);
    if (parsed == null || parsed <= 0 ||
        !RegExp(r'^\\d+(\\.\\d{1,2})?$').hasMatch(normalized)) {
      return 'أدخل مبلغًا موجبًا وبحد أقصى منزلتين عشريتين.';
    }
    return null;
  }

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year + 10),
      helpText: widget.advance ? 'تاريخ صرف السلفة' : 'تاريخ صرف المستحقات',
    );
    if (picked != null && mounted) setState(() => _date = picked);
  }

  String _errorMessage(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 409) {
        return 'مفتاح العملية مستخدم لحركة مختلفة. لا تعاود المحاولة بمفتاح جديد قبل مراجعة الكشف.';
      }
      if (error.response?.statusCode == 400) return 'تعذر تنفيذ العملية؛ راجع الرصيد والمبلغ والحساب.';
    }
    return 'تعذر تسجيل العملية. يمكنك إعادة المحاولة بنفس مفتاح العملية.';
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_accountId == null) {
      setState(() => _error = 'اختر حساب الخزينة أو البنك.');
      return;
    }
    setState(() { _saving = true; _error = null; });
    final dateKey = widget.advance ? 'advanceDate' : 'paymentDate';
    final payload = <String, dynamic>{
      'clientRequestKey': _requestKey,
      dateKey: _dateText(_date),
      'amount': double.parse(_amount.text.trim().replaceAll(',', '.')),
      'paymentAccountId': _accountId,
      'description': _description.text.trim(),
      if (widget.advance) 'advanceType': 'RECOVERABLE',
    };
    try {
      final base = '/workforce/members/${widget.memberId}/ledger';
      await getIt<Dio>().post(
        '$base/${widget.advance ? 'advances' : 'payments'}',
        data: payload,
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) setState(() => _error = _errorMessage(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.advance ? 'تسجيل سلفة مستردة' : 'صرف مستحقات'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                TextFormField(
                  controller: _amount,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: 'المبلغ بالجنيه *',
                    prefixIcon: Icon(Icons.payments_outlined),
                  ),
                  validator: _validateAmount,
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  value: _accountId,
                  decoration: const InputDecoration(
                    labelText: 'حساب الخزينة أو البنك *',
                    prefixIcon: Icon(Icons.account_balance_outlined),
                  ),
                  items: widget.accounts.map((account) {
                    final id = (account['id'] as num).toInt();
                    final code = account['accountCode']?.toString() ?? '';
                    final name = account['name']?.toString() ?? '';
                    return DropdownMenuItem<int>(
                      value: id,
                      child: Text('$code - $name', overflow: TextOverflow.ellipsis),
                    );
                  }).toList(),
                  onChanged: _saving ? null : (value) => setState(() => _accountId = value),
                  validator: (value) => value == null ? 'اختر الحساب.' : null,
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _description,
                  maxLength: 300,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'البيان *',
                    prefixIcon: Icon(Icons.notes),
                  ),
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'البيان مطلوب.'
                      : null,
                ),
                const SizedBox(height: 8),
                InkWell(
                  onTap: _saving ? null : _pickDate,
                  child: InputDecorator(
                    decoration: const InputDecoration(
                      labelText: 'التاريخ',
                      prefixIcon: Icon(Icons.calendar_month_outlined),
                      border: OutlineInputBorder(),
                    ),
                    child: Text(_dateText(_date)),
                  ),
                ),
                if (widget.advance)
                  const Padding(
                    padding: EdgeInsets.only(top: 10),
                    child: Text(
                      'هذه سلفة مستردة تُسجل كرصيد مستقل ولا تخصم من المستحقات حتى تسجيل الاسترداد.',
                      style: TextStyle(fontSize: 12),
                    ),
                  ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ),
              ],
            ),
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(false),
          child: const Text('إلغاء'),
        ),
        ElevatedButton(
          onPressed: _saving ? null : _submit,
          child: Text(_saving ? 'جارٍ الحفظ...' : 'تأكيد'),
        ),
      ],
    );
  }
}
