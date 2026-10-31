import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

class JournalEntryFormScreen extends StatefulWidget {
  const JournalEntryFormScreen({Key? key}) : super(key: key);

  @override
  State<JournalEntryFormScreen> createState() => _JournalEntryFormScreenState();
}

class _JournalEntryFormScreenState extends State<JournalEntryFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final _description = TextEditingController();
  final _postingKey = 'manual-${DateTime.now().microsecondsSinceEpoch}';
  final List<_JournalLineInput> _lines = [
    _JournalLineInput(),
    _JournalLineInput(),
  ];

  DateTime _date = DateTime.now();
  List<_AccountOption> _accounts = [];
  bool _loadingAccounts = true;
  bool _saving = false;
  String? _loadError;
  String? _formError;

  @override
  void initState() {
    super.initState();
    _loadAccounts();
  }

  @override
  void dispose() {
    _description.dispose();
    for (final line in _lines) {
      line.dispose();
    }
    super.dispose();
  }

  Future<void> _loadAccounts() async {
    setState(() {
      _loadingAccounts = true;
      _loadError = null;
    });
    try {
      final response = await getIt<Dio>().get<List<dynamic>>('/accounting/accounts');
      final data = response.data;
      if (data == null) throw const FormatException();
      final accounts = data
          .whereType<Map<String, dynamic>>()
          .map(_AccountOption.fromJson)
          .where((account) => account.active)
          .toList();
      if (!mounted) return;
      setState(() {
        _accounts = accounts;
        _loadingAccounts = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadError = 'تعذر تحميل الحسابات. تحقق من الاتصال ثم أعد المحاولة.';
        _loadingAccounts = false;
      });
    }
  }

  Future<void> _pickDate() async {
    final selected = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2000),
      lastDate: DateTime(2100),
      locale: const Locale('ar'),
    );
    if (selected != null && mounted) setState(() => _date = selected);
  }

  void _addLine() => setState(() => _lines.add(_JournalLineInput()));

  void _removeLine(int index) {
    if (_lines.length <= 2) return;
    final removed = _lines.removeAt(index);
    removed.dispose();
    setState(() {});
  }

  int? _parseCents(String value) {
    final normalized = value.trim();
    if (normalized.isEmpty) return 0;
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(normalized)) return null;
    final parts = normalized.split('.');
    final whole = int.tryParse(parts[0]);
    if (whole == null) return null;
    final fraction = parts.length == 1 ? 0 : int.parse(parts[1].padRight(2, '0'));
    return whole * 100 + fraction;
  }

  String _money(int cents) =>
      '${cents ~/ 100}.${(cents % 100).toString().padLeft(2, '0')}';

  String _dateValue(DateTime date) =>
      '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';

  String _apiError(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map) {
        final message = data['message'] ?? data['detail'];
        if (message is String && message.trim().isNotEmpty) return message;
      }
      final status = error.response?.statusCode;
      if (status == 400) return 'راجع بيانات القيد والمبالغ المدخلة.';
      if (status == 403) return 'إنشاء القيود متاح لمدير الشركة فقط.';
      if (status == 409) return 'مفتاح القيد مستخدم لحركة أخرى. أعد فتح النموذج.';
      if (status == 401) return 'انتهت جلسة الدخول. سجّل الدخول مرة أخرى.';
      if (status != null && status >= 500) return 'حدث خطأ بالخادم. حاول لاحقًا.';
    }
    return 'تعذر حفظ القيد. تحقق من الاتصال وحاول مرة أخرى.';
  }

  Future<void> _submit() async {
    if (_saving || _loadingAccounts || _loadError != null) return;
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;

    var debitTotal = 0;
    var creditTotal = 0;
    final requestLines = <Map<String, dynamic>>[];

    for (final line in _lines) {
      final debit = _parseCents(line.debit.text);
      final credit = _parseCents(line.credit.text);
      if (line.accountId == null || debit == null || credit == null ||
          ((debit > 0) == (credit > 0))) {
        setState(() => _formError =
            'كل سطر يحتاج حسابًا ومبلغًا في المدين أو الدائن فقط.');
        return;
      }
      debitTotal += debit;
      creditTotal += credit;
      requestLines.add({
        'accountId': line.accountId,
        'description': line.description.text.trim().isEmpty
            ? null
            : line.description.text.trim(),
        'debit': _money(debit),
        'credit': _money(credit),
        'driverId': null,
        'equipmentId': null,
      });
    }

    if (debitTotal <= 0 || debitTotal != creditTotal) {
      setState(() => _formError = debitTotal <= 0
          ? 'يجب إدخال مبلغ أكبر من صفر.'
          : 'القيد غير متوازن. إجمالي المدين يجب أن يساوي إجمالي الدائن.');
      return;
    }

    setState(() {
      _saving = true;
      _formError = null;
    });
    try {
      await getIt<Dio>().post('/accounting/journals', data: {
        'postingKey': _postingKey,
        'sourceType': 'MANUAL',
        'sourceId': null,
        'accountingDate': _dateValue(_date),
        'description': _description.text.trim(),
        'reversalOfEntryId': null,
        'lines': requestLines,
      });
      if (mounted) Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) setState(() => _formError = _apiError(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String? _validateAmount(String? value) =>
      _parseCents(value ?? '') == null ? 'مبلغ غير صالح' : null;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('قيد محاسبي جديد')),
      body: _loadingAccounts
          ? const Center(child: CircularProgressIndicator())
          : _loadError != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(_loadError!, textAlign: TextAlign.center),
                        const SizedBox(height: 12),
                        ElevatedButton.icon(
                          onPressed: _loadAccounts,
                          icon: const Icon(Icons.refresh),
                          label: const Text('إعادة المحاولة'),
                        ),
                      ],
                    ),
                  ),
                )
              : _accounts.isEmpty
                  ? const Center(
                      child: Padding(
                        padding: EdgeInsets.all(24),
                        child: Text(
                          'لا توجد حسابات نشطة. جهّز دليل الحسابات أولًا.',
                          textAlign: TextAlign.center,
                        ),
                      ),
                    )
                  : Form(
                      key: _formKey,
                      child: ListView(
                        padding: const EdgeInsets.all(16),
                        children: [
                          TextFormField(
                            controller: _description,
                            enabled: !_saving,
                            maxLength: 300,
                            decoration: const InputDecoration(
                              labelText: 'وصف القيد',
                              hintText: 'مثال: إثبات مصروف صيانة',
                              prefixIcon: Icon(Icons.notes_outlined),
                            ),
                            validator: (value) =>
                                value == null || value.trim().isEmpty
                                    ? 'وصف القيد مطلوب'
                                    : null,
                          ),
                          const SizedBox(height: 12),
                          OutlinedButton.icon(
                            onPressed: _saving ? null : _pickDate,
                            icon: const Icon(Icons.calendar_today_outlined),
                            label: Text('تاريخ القيد: ${_dateValue(_date)}'),
                          ),
                          const SizedBox(height: 20),
                          Row(
                            children: [
                              Expanded(
                                child: Text('بنود القيد',
                                    style: Theme.of(context).textTheme.titleMedium),
                              ),
                              TextButton.icon(
                                onPressed: _saving ? null : _addLine,
                                icon: const Icon(Icons.add),
                                label: const Text('إضافة سطر'),
                              ),
                            ],
                          ),
                          for (var index = 0; index < _lines.length; index++)
                            _buildLineCard(index),
                          if (_formError != null) ...[
                            const SizedBox(height: 12),
                            Text(
                              _formError!,
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.error,
                                fontWeight: FontWeight.w600,
                              ),
                              textAlign: TextAlign.center,
                            ),
                          ],
                          const SizedBox(height: 20),
                          SizedBox(
                            height: 50,
                            child: ElevatedButton.icon(
                              onPressed: _saving ? null : _submit,
                              icon: _saving
                                  ? const SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(strokeWidth: 2),
                                    )
                                  : const Icon(Icons.save_outlined),
                              label: Text(_saving ? 'جارٍ ترحيل القيد...' : 'حفظ وترحيل القيد'),
                            ),
                          ),
                          const SizedBox(height: 12),
                          const Text(
                            'سيتم التحقق من توازن القيد وصلاحية الحسابات على الخادم قبل الترحيل.',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 12),
                          ),
                        ],
                      ),
                    ),
    );
  }

  Widget _buildLineCard(int index) {
    final line = _lines[index];
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('السطر ${index + 1}',
                      style: const TextStyle(fontWeight: FontWeight.w700)),
                ),
                if (_lines.length > 2)
                  IconButton(
                    tooltip: 'حذف السطر',
                    onPressed: _saving ? null : () => _removeLine(index),
                    icon: const Icon(Icons.delete_outline),
                  ),
              ],
            ),
            DropdownButtonFormField<int>(
              value: line.accountId,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'الحساب المحاسبي',
                prefixIcon: Icon(Icons.account_balance_wallet_outlined),
              ),
              items: _accounts
                  .map((account) => DropdownMenuItem<int>(
                        value: account.id,
                        child: Text('${account.code} - ${account.name}'),
                      ))
                  .toList(),
              onChanged: _saving
                  ? null
                  : (value) => setState(() => line.accountId = value),
              validator: (value) => value == null ? 'اختر الحساب' : null,
            ),
            const SizedBox(height: 10),
            TextFormField(
              controller: line.description,
              enabled: !_saving,
              maxLength: 300,
              decoration: const InputDecoration(
                labelText: 'بيان السطر (اختياري)',
                counterText: '',
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: TextFormField(
                    controller: line.debit,
                    enabled: !_saving,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    textDirection: TextDirection.ltr,
                    decoration: const InputDecoration(labelText: 'مدين', suffixText: 'جنيه'),
                    validator: _validateAmount,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: TextFormField(
                    controller: line.credit,
                    enabled: !_saving,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    textDirection: TextDirection.ltr,
                    decoration: const InputDecoration(labelText: 'دائن', suffixText: 'جنيه'),
                    validator: _validateAmount,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _JournalLineInput {
  int? accountId;
  final description = TextEditingController();
  final debit = TextEditingController(text: '0.00');
  final credit = TextEditingController(text: '0.00');

  void dispose() {
    description.dispose();
    debit.dispose();
    credit.dispose();
  }
}

class _AccountOption {
  const _AccountOption({
    required this.id,
    required this.code,
    required this.name,
    required this.active,
  });

  final int id;
  final String code;
  final String name;
  final bool active;

  factory _AccountOption.fromJson(Map<String, dynamic> json) => _AccountOption(
        id: (json['id'] as num).toInt(),
        code: json['accountCode'] as String? ?? '',
        name: json['name'] as String? ?? '',
        active: json['active'] as bool? ?? false,
      );
}
