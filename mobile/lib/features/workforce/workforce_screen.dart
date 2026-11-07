import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

/// Mobile administration for canonical workforce identities and their pay agreements.
class WorkforceScreen extends StatefulWidget {
  const WorkforceScreen({Key? key}) : super(key: key);

  @override
  State<WorkforceScreen> createState() => _WorkforceScreenState();
}

class _WorkforceScreenState extends State<WorkforceScreen> {
  final _search = TextEditingController();
  List<Map<String, dynamic>> _members = <Map<String, dynamic>>[];
  bool _loading = true;
  bool _includeInactive = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  String _value(dynamic value, [String fallback = '—']) =>
      value == null || value.toString().trim().isEmpty ? fallback : value.toString();

  String _message(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 401) return 'انتهت الجلسة؛ سجّل الدخول مرة أخرى.';
      if (error.response?.statusCode == 403) return 'هذه العملية تحتاج صلاحية المسؤول.';
    }
    return 'تعذر تحميل دليل القوى العاملة. تحقق من الاتصال وحاول مرة أخرى.';
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() { _loading = true; _error = null; });
    try {
      final response = await getIt<Dio>().get<Map<String, dynamic>>(
        '/workforce/members',
        queryParameters: <String, dynamic>{
          'page': 0,
          'size': 100,
          'includeInactive': _includeInactive,
          if (_search.text.trim().isNotEmpty) 'search': _search.text.trim(),
        },
      );
      final data = response.data;
      if (data == null || data['content'] is! List) {
        throw Exception('استجابة دليل القوى العاملة غير صالحة.');
      }
      final items = (data['content'] as List)
          .whereType<Map<String, dynamic>>()
          .toList();
      if (mounted) setState(() => _members = items);
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _openMember(Map<String, dynamic> member) async {
    final rawId = member['workforceMemberId'];
    final memberId = rawId is num ? rawId.toInt() : int.tryParse('$rawId');
    if (memberId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('لا يوجد معرّف Workforce صالح لهذا الشخص.')),
      );
      return;
    }
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => WorkforceAgreementsScreen(
          memberId: memberId,
          memberName: _value(member['fullName']),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('القوى العاملة'),
        actions: <Widget>[
          IconButton(tooltip: 'تحديث', onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: Column(
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              controller: _search,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _load(),
              decoration: InputDecoration(
                labelText: 'بحث بالاسم أو الهاتف',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: IconButton(onPressed: _load, icon: const Icon(Icons.search)),
                border: const OutlineInputBorder(),
              ),
            ),
          ),
          SwitchListTile(
            title: const Text('عرض الحسابات المعطلة أيضًا'),
            value: _includeInactive,
            onChanged: (value) { setState(() => _includeInactive = value); _load(); },
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: <Widget>[
                  Text(_error!, textAlign: TextAlign.center,
                      style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ElevatedButton(onPressed: _load, child: const Text('إعادة المحاولة')),
                ],
              ),
            ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? const SizedBox.shrink()
                    : _members.isEmpty
                        ? const Center(child: Text('لا توجد سجلات مطابقة. أضف الشخص أولًا من شاشة السائقين والعمال.'))
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(bottom: 24),
                              itemCount: _members.length,
                              itemBuilder: (context, index) {
                                final member = _members[index];
                                final active = member['active'] == true;
                                final linked = member['operationalProfileLinked'] == true;
                                final isDriver = member['workerType'] == 'DRIVER';
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                                  child: ListTile(
                                    leading: CircleAvatar(
                                      child: Icon(isDriver ? Icons.person_outline : Icons.engineering_outlined),
                                    ),
                                    title: Text(_value(member['fullName']),
                                        style: const TextStyle(fontWeight: FontWeight.bold)),
                                    subtitle: Text(
                                      '${isDriver ? 'سائق' : 'عامل'} • هاتف: ${_value(member['phone'])}\n'
                                      '${active ? 'نشط' : 'معطّل'} • '
                                      '${linked ? 'مرتبط بملف التشغيل' : 'لا يوجد ملف تشغيل مرتبط'}',
                                    ),
                                    isThreeLine: true,
                                    trailing: const Icon(Icons.chevron_left),
                                    onTap: () => _openMember(member),
                                  ),
                                );
                              },
                            ),
                          ),
          ),
        ],
      ),
    );
  }
}

class WorkforceAgreementsScreen extends StatefulWidget {
  const WorkforceAgreementsScreen({
    Key? key,
    required this.memberId,
    required this.memberName,
  }) : super(key: key);

  final int memberId;
  final String memberName;

  @override
  State<WorkforceAgreementsScreen> createState() => _WorkforceAgreementsScreenState();
}

class _WorkforceAgreementsScreenState extends State<WorkforceAgreementsScreen> {
  List<Map<String, dynamic>> _agreements = <Map<String, dynamic>>[];
  bool _loading = true;
  bool _saving = false;
  String? _error;

  String get _endpoint => '/workforce/members/${widget.memberId}/compensation-agreements';

  @override
  void initState() {
    super.initState();
    _load();
  }

  String _value(dynamic value, [String fallback = '—']) =>
      value == null || value.toString().trim().isEmpty ? fallback : value.toString();

  String _message(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 403) return 'اعتماد الاتفاقيات وإنشاؤها متاحان للمسؤول فقط.';
      if (error.response?.statusCode == 409) return data is Map && data['message'] != null
          ? data['message'].toString()
          : 'توجد اتفاقية متعارضة أو أن نطاق التواريخ غير صالح.';
    }
    return 'تعذر تنفيذ العملية. راجع الاتصال والبيانات وحاول مرة أخرى.';
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() { _loading = true; _error = null; });
    try {
      final response = await getIt<Dio>().get<List<dynamic>>(_endpoint);
      final data = response.data ?? <dynamic>[];
      if (mounted) {
        setState(() => _agreements = data.whereType<Map<String, dynamic>>().toList());
      }
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Map<String, dynamic>? get _approved {
    for (final item in _agreements) {
      if (item['status'] == 'APPROVED') return item;
    }
    return null;
  }

  Future<void> _createAgreement() async {
    final approved = _approved;
    final payload = await showDialog<Map<String, dynamic>>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _HourlyAgreementDialog(
        currentAgreement: approved,
      ),
    );
    if (payload == null || !mounted) return;
    setState(() { _saving = true; _error = null; });
    try {
      final created = await getIt<Dio>().post<Map<String, dynamic>>(
        _endpoint,
        data: payload,
      );
      final draft = created.data;
      if (draft == null || draft['id'] == null) {
        throw Exception('لم يرجع الخادم الاتفاقية المنشأة.');
      }
      final id = draft['id'];
      final isReplacement = payload['supersedesAgreementId'] != null;
      await getIt<Dio>().post<void>(
        '$_endpoint/$id/${isReplacement ? 'approve-replacement' : 'approve'}',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم إنشاء واعتماد اتفاقية الأجر بنجاح.')),
      );
      await _load();
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
      // The draft may already have been created. Reload so it remains visible
      // and can be reviewed instead of silently retrying and duplicating it.
      await _load();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _approveDraft(Map<String, dynamic> agreement) async {
    final id = agreement['id'];
    final replacement = agreement['supersedesAgreementId'] != null;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('اعتماد الاتفاقية'),
        content: Text(replacement
            ? 'سيتم اعتماد الاتفاقية الجديدة وإنهاء القديمة في معاملة واحدة. هل تريد المتابعة؟'
            : 'هل راجعت تفاصيل الاتفاقية وتريد اعتمادها؟'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('رجوع')),
          ElevatedButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('اعتماد')),
        ],
      ),
    );
    if (confirm != true) return;
    setState(() { _saving = true; _error = null; });
    try {
      await getIt<Dio>().post<void>(
        '$_endpoint/$id/${replacement ? 'approve-replacement' : 'approve'}',
      );
      await _load();
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _cancelDraft(Map<String, dynamic> agreement) async {
    final id = agreement['id'];
    final confirm = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('إلغاء المسودة'),
        content: const Text('سيتم إلغاء مسودة الاتفاقية فقط، ولن تتأثر أي اتفاقية معتمدة.'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('رجوع')),
          ElevatedButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('إلغاء المسودة')),
        ],
      ),
    );
    if (confirm != true) return;
    setState(() { _saving = true; _error = null; });
    try {
      await getIt<Dio>().post<void>('$_endpoint/$id/cancel');
      await _load();
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String _statusLabel(String status) {
    switch (status) {
      case 'APPROVED': return 'معتمدة';
      case 'DRAFT': return 'مسودة';
      case 'SUPERSEDED': return 'مستبدلة';
      case 'EXPIRED': return 'منتهية';
      case 'CANCELLED': return 'ملغاة';
      default: return status;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.memberName, maxLines: 1, overflow: TextOverflow.ellipsis)),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _saving ? null : _createAgreement,
        icon: const Icon(Icons.add),
        label: const Text('اتفاقية أجر ساعي'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: <Widget>[
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ),
                if (_saving) const LinearProgressIndicator(),
                Expanded(
                  child: _agreements.isEmpty
                      ? const Center(
                          child: Padding(
                            padding: EdgeInsets.all(24),
                            child: Text('لا توجد اتفاقيات أجر لهذا الشخص. أنشئ اتفاقية لتفعيل نظام الأجر بالساعة.'),
                          ),
                        )
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: ListView.builder(
                            padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
                            itemCount: _agreements.length,
                            itemBuilder: (context, index) {
                              final agreement = _agreements[index];
                              final status = _value(agreement['status'], '');
                              final components = agreement['components'] is List
                                  ? agreement['components'] as List
                                  : <dynamic>[];
                              return Card(
                                child: Padding(
                                  padding: const EdgeInsets.all(12),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: <Widget>[
                                      Row(
                                        children: <Widget>[
                                          Expanded(
                                            child: Text(
                                              'اتفاقية #${_value(agreement['id'])}',
                                              style: const TextStyle(fontWeight: FontWeight.bold),
                                            ),
                                          ),
                                          Chip(label: Text(_statusLabel(status))),
                                        ],
                                      ),
                                      Text('سارية من ${_value(agreement['effectiveFrom'])}'
                                          '${agreement['effectiveTo'] == null ? '' : ' إلى ${agreement['effectiveTo']}'}'),
                                      for (final raw in components)
                                        if (raw is Map)
                                          Padding(
                                            padding: const EdgeInsets.only(top: 6),
                                            child: Text(
                                              '${_value(raw['displayName'])}: ${_value(raw['rate'])} ${_value(agreement['currencyCode'], 'EGP')} / ${_value(raw['basis']) == 'HOUR' ? 'ساعة' : _value(raw['unitCode'], 'وحدة')}',
                                            ),
                                          ),
                                      if (agreement['changeReason'] != null)
                                        Text('سبب التغيير: ${agreement['changeReason']}'),
                                      if (status == 'DRAFT')
                                        Wrap(
                                          spacing: 8,
                                          children: <Widget>[
                                            TextButton(
                                              onPressed: _saving ? null : () => _approveDraft(agreement),
                                              child: const Text('اعتماد'),
                                            ),
                                            TextButton(
                                              onPressed: _saving ? null : () => _cancelDraft(agreement),
                                              child: const Text('إلغاء المسودة'),
                                            ),
                                          ],
                                        ),
                                    ],
                                  ),
                                ),
                              );
                            },
                          ),
                        ),
                ),
              ],
            ),
    );
  }
}

class _HourlyAgreementDialog extends StatefulWidget {
  const _HourlyAgreementDialog({Key? key, this.currentAgreement}) : super(key: key);

  final Map<String, dynamic>? currentAgreement;

  @override
  State<_HourlyAgreementDialog> createState() => _HourlyAgreementDialogState();
}

class _HourlyAgreementDialogState extends State<_HourlyAgreementDialog> {
  final _formKey = GlobalKey<FormState>();
  final _rate = TextEditingController();
  final _reference = TextEditingController();
  final _reason = TextEditingController();
  DateTime _effectiveFrom = DateTime.now();
  bool _submitting = false;

  @override
  void dispose() {
    _rate.dispose();
    _reference.dispose();
    _reason.dispose();
    super.dispose();
  }

  String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _effectiveFrom.isBefore(now) ? now : _effectiveFrom,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year + 10),
      helpText: 'بداية سريان الاتفاقية',
    );
    if (picked != null && mounted) setState(() => _effectiveFrom = picked);
  }

  String? _validateRate(String? value) {
    final parsed = double.tryParse((value ?? '').trim().replaceAll(',', '.'));
    if (parsed == null || parsed < 0) return 'أدخل سعر ساعة صحيحًا غير سالب';
    return null;
  }

  void _submit() {
    if (_submitting || !_formKey.currentState!.validate()) return;
    final old = widget.currentAgreement;
    if (old != null) {
      final oldStart = DateTime.tryParse('${old['effectiveFrom']}');
      if (oldStart != null && !_effectiveFrom.isAfter(oldStart)) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('تاريخ الاتفاقية الجديدة يجب أن يكون بعد بداية الاتفاقية الحالية.')),
        );
        return;
      }
      if (_reason.text.trim().isEmpty) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('سبب تغيير الاتفاقية مطلوب.')),
        );
        return;
      }
    }
    final payload = <String, dynamic>{
      'effectiveFrom': _date(_effectiveFrom),
      'currencyCode': 'EGP',
      'source': 'MANUAL',
      'agreementReference': _reference.text.trim().isEmpty ? null : _reference.text.trim(),
      if (old != null) ...<String, dynamic>{
        'supersedesAgreementId': old['id'],
        'changeReason': _reason.text.trim(),
      },
      'components': <Map<String, dynamic>>[
        <String, dynamic>{
          'componentCode': 'BASE_HOURLY',
          'displayName': 'أجر الساعة',
          'basis': 'HOUR',
          'rate': double.parse(_rate.text.trim().replaceAll(',', '.')),
          'unitCode': 'HOUR',
          'sortOrder': 0,
        },
      ],
    };
    Navigator.of(context).pop(payload);
  }

  @override
  Widget build(BuildContext context) {
    final replacing = widget.currentAgreement != null;
    return AlertDialog(
      title: Text(replacing ? 'استبدال اتفاقية الأجر الساعي' : 'اتفاقية أجر ساعي جديدة'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                TextFormField(
                  controller: _rate,
                  autofocus: true,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: 'سعر الساعة بالجنيه *',
                    prefixIcon: Icon(Icons.payments_outlined),
                  ),
                  validator: _validateRate,
                ),
                const SizedBox(height: 12),
                InkWell(
                  onTap: _pickDate,
                  child: InputDecorator(
                    decoration: const InputDecoration(
                      labelText: 'تاريخ بداية السريان',
                      prefixIcon: Icon(Icons.calendar_month_outlined),
                      border: OutlineInputBorder(),
                    ),
                    child: Text(_date(_effectiveFrom)),
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _reference,
                  maxLength: 80,
                  decoration: const InputDecoration(labelText: 'مرجع الاتفاقية (اختياري)'),
                ),
                if (replacing) ...<Widget>[
                  TextFormField(
                    controller: _reason,
                    maxLength: 500,
                    maxLines: 2,
                    decoration: const InputDecoration(labelText: 'سبب تغيير الأجر *'),
                    validator: (value) => value == null || value.trim().isEmpty
                        ? 'سبب التغيير مطلوب'
                        : null,
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'سيتم إنهاء الاتفاقية الحالية في اليوم السابق لبداية الجديدة، واعتماد الجديدة معها في معاملة واحدة.',
                    style: TextStyle(fontSize: 12),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('إلغاء')),
        ElevatedButton(onPressed: _submit, child: const Text('حفظ ومراجعة الاعتماد')),
      ],
    );
  }
}
