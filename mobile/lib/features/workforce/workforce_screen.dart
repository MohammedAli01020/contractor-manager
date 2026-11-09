import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';
import 'workforce_member_form_dialog.dart';
import 'workforce_ledger_screen.dart';

/// Mobile administration for canonical workforce identities and their pay agreements.
class WorkforceScreen extends StatefulWidget {
  const WorkforceScreen({Key? key, this.canManage = false}) : super(key: key);

  final bool canManage;

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

  Future<void> _createMember() async {
    final created = await showDialog<Map<String, dynamic>>(
      context: context,
      barrierDismissible: false,
      builder: (_) => const WorkforceMemberFormDialog(),
    );
    if (created == null || !mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('تمت إضافة الشخص إلى القوى العاملة.')),
    );
    await _load();
  }

  Future<void> _editMember(Map<String, dynamic> member) async {
    final rawId = member['workforceMemberId'];
    final memberId = rawId is num ? rawId.toInt() : int.tryParse('$rawId');
    if (memberId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('لا يوجد معرّف Workforce صالح لهذا الشخص.')),
      );
      return;
    }
    try {
      // Fetch the canonical detail first: the directory intentionally omits
      // sensitive national ID and the optimistic-lock version.
      final response = await getIt<Dio>().get<Map<String, dynamic>>(
        '/workforce/members/$memberId',
      );
      final detail = response.data;
      if (detail == null || detail['version'] == null) {
        throw Exception('بيانات الشخص غير مكتملة؛ لم يتم فتح التعديل.');
      }
      if (!mounted) return;
      final updated = await showDialog<Map<String, dynamic>>(
        context: context,
        barrierDismissible: false,
        builder: (_) => WorkforceMemberFormDialog(initialMember: detail),
      );
      if (updated == null || !mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم حفظ بيانات الفرد بنجاح.')),
      );
      await _load();
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_message(error))),
      );
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
        builder: (_) => WorkforceAssignmentsScreen(
          memberId: memberId,
          memberName: _value(member['fullName']),
          canManage: widget.canManage,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      floatingActionButton: widget.canManage
          ? FloatingActionButton.extended(
              onPressed: _loading ? null : _createMember,
              icon: const Icon(Icons.person_add_alt_1),
              label: const Text('إضافة فرد'),
            )
          : null,
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
                        ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(widget.canManage ? 'لا توجد أفراد بعد. ابدأ بإضافة أول فرد إلى القوى العاملة.' : 'لا توجد سجلات مطابقة.')))
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(bottom: 24),
                              itemCount: _members.length,
                              itemBuilder: (context, index) {
                                final member = _members[index];
                                final active = member['active'] == true;
                                final linked = member['operationalProfileLinked'] == true;
                                final roleSummaries = member['roles'] is List
                                    ? (member['roles'] as List).whereType<Map<String, dynamic>>().toList()
                                    : <Map<String, dynamic>>[];
                                final roleNames = roleSummaries
                                    .map((role) => role['roleName']?.toString())
                                    .where((name) => name != null && name.trim().isNotEmpty)
                                    .cast<String>()
                                    .toList();
                                final roleCodes = roleSummaries
                                    .map((role) => role['roleCode']?.toString())
                                    .toSet();
                                final isDriver = roleCodes.contains('DRIVER') ||
                                    roleCodes.contains('EQUIPMENT_OPERATOR');
                                final categoryLabel = roleNames.isEmpty
                                    ? 'يحتاج تحديد دور وظيفي'
                                    : roleNames.join(' • ');
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                                  child: ListTile(
                                    leading: CircleAvatar(
                                      child: Icon(isDriver ? Icons.person_outline : Icons.engineering_outlined),
                                    ),
                                    title: Text(_value(member['fullName']),
                                        style: const TextStyle(fontWeight: FontWeight.bold)),
                                    subtitle: Text(
                                      '$categoryLabel • هاتف: ${_value(member['phone'])}\n'
                                      '${active ? 'نشط' : 'معطّل'} • '
                                      '${roleSummaries.length} تكليف وظيفي نشط',
                                    ),
                                    isThreeLine: true,
                                    trailing: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: <Widget>[
                                        if (widget.canManage)
                                          IconButton(
                                            tooltip: 'تعديل بيانات الفرد',
                                            icon: const Icon(Icons.edit_outlined),
                                            onPressed: () => _editMember(member),
                                          ),
                                        const Icon(Icons.chevron_left),
                                      ],
                                    ),
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


class WorkforceAssignmentsScreen extends StatefulWidget {
  const WorkforceAssignmentsScreen({
    Key? key,
    required this.memberId,
    required this.memberName,
    required this.canManage,
  }) : super(key: key);

  final int memberId;
  final String memberName;
  final bool canManage;

  @override
  State<WorkforceAssignmentsScreen> createState() => _WorkforceAssignmentsScreenState();
}

class _WorkforceAssignmentsScreenState extends State<WorkforceAssignmentsScreen> {
  List<Map<String, dynamic>> _roles = <Map<String, dynamic>>[];
  List<Map<String, dynamic>> _assignments = <Map<String, dynamic>>[];
  bool _loading = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  String get _endpoint => '/workforce/members/${widget.memberId}/assignments';

  String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

  String _value(dynamic value, [String fallback = '—']) =>
      value == null || value.toString().trim().isEmpty ? fallback : value.toString();

  String _message(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 403) return 'إدارة التكليفات متاحة للمسؤول فقط.';
      if (error.response?.statusCode == 409) return 'يوجد تعارض في التكليف أو تم تحديثه من مكان آخر.';
    }
    return 'تعذر تحميل أو حفظ التكليفات. تحقق من الاتصال وحاول مرة أخرى.';
  }

  String _roleName(dynamic roleId) {
    for (final role in _roles) {
      if ('${role['id']}' == '$roleId') {
        return _value(role['name'], _value(role['code'], 'دور وظيفي'));
      }
    }
    return 'دور وظيفي غير متاح';
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() { _loading = true; _error = null; });
    try {
      final responses = await Future.wait<dynamic>(<Future<dynamic>>[
        getIt<Dio>().get<List<dynamic>>('/workforce/roles?includeInactive=true'),
        getIt<Dio>().get<List<dynamic>>(_endpoint),
      ]);
      final rolesData = responses[0].data as List<dynamic>? ?? <dynamic>[];
      final assignmentsData = responses[1].data as List<dynamic>? ?? <dynamic>[];
      if (!mounted) return;
      setState(() {
        _roles = rolesData.whereType<Map<String, dynamic>>().toList();
        _assignments = assignmentsData.whereType<Map<String, dynamic>>().toList();
      });
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _addAssignment() async {
    final activeRoles = _roles.where((role) => role['active'] == true).toList();
    if (activeRoles.isEmpty) {
      setState(() => _error = 'لا توجد أدوار نشطة متاحة لإضافتها.');
      return;
    }
    final payload = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (_) => _NewWorkforceAssignmentDialog(roles: activeRoles),
    );
    if (payload == null || !mounted) return;
    setState(() { _saving = true; _error = null; });
    try {
      await getIt<Dio>().post<Map<String, dynamic>>(_endpoint, data: payload);
      await _load();
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _endAssignment(Map<String, dynamic> assignment) async {
    final from = DateTime.tryParse('${assignment['effectiveFrom']}') ?? DateTime(2020);
    final today = DateTime.now();
    final firstAllowed = DateTime(from.year, from.month, from.day);
    final todayDate = DateTime(today.year, today.month, today.day);
    final initial = todayDate.isBefore(firstAllowed) ? firstAllowed : todayDate;
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: firstAllowed,
      lastDate: DateTime(today.year + 10),
      helpText: 'تاريخ انتهاء التكليف',
    );
    if (picked == null || !mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('إنهاء التكليف'),
        content: Text('سيتم إنهاء دور ${_roleName(assignment['roleId'])} بتاريخ ${_date(picked)} مع الاحتفاظ بسجل التكليف. هل تريد المتابعة؟'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('رجوع')),
          ElevatedButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('إنهاء التكليف')),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() { _saving = true; _error = null; });
    try {
      await getIt<Dio>().put<Map<String, dynamic>>(
        '$_endpoint/${assignment['id']}/end',
        data: <String, dynamic>{
          'effectiveTo': _date(picked),
          'version': assignment['version'],
        },
      );
      await _load();
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _openLedger() async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => WorkforceLedgerScreen(
          memberId: widget.memberId,
          memberName: widget.memberName,
        ),
      ),
    );
  }

  Future<void> _openAgreements() async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => WorkforceAgreementsScreen(
          memberId: widget.memberId,
          memberName: widget.memberName,
          canManage: widget.canManage,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.memberName),
        actions: <Widget>[
          IconButton(
            tooltip: 'كشف الحساب',
            onPressed: _openLedger,
            icon: const Icon(Icons.receipt_long_outlined),
          ),
          IconButton(
            tooltip: 'اتفاقيات الأجر',
            onPressed: _openAgreements,
            icon: const Icon(Icons.payments_outlined),
          ),
          IconButton(tooltip: 'تحديث', onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      floatingActionButton: widget.canManage
          ? FloatingActionButton.extended(
              onPressed: _loading || _saving ? null : _addAssignment,
              icon: const Icon(Icons.add),
              label: const Text('إضافة دور'),
            )
          : null,
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: <Widget>[
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ),
                Expanded(
                  child: _assignments.isEmpty
                      ? const Center(child: Text('لا توجد تكليفات وظيفية مسجلة لهذا الشخص.'))
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: ListView.builder(
                            padding: const EdgeInsets.only(bottom: 88),
                            itemCount: _assignments.length,
                            itemBuilder: (context, index) {
                              final assignment = _assignments[index];
                              final active = assignment['status'] == 'ACTIVE';
                              return Card(
                                margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                                child: ListTile(
                                  leading: CircleAvatar(
                                    child: Icon(active ? Icons.work_outline : Icons.history),
                                  ),
                                  title: Text(_roleName(assignment['roleId']),
                                      style: const TextStyle(fontWeight: FontWeight.bold)),
                                  subtitle: Text(
                                    'من ${_value(assignment['effectiveFrom'])}'
                                    '${assignment['effectiveTo'] == null ? '' : ' إلى ${assignment['effectiveTo']}'}'
                                    ' • ${active ? 'تكليف نشط' : 'منتهي'}'
                                    '${assignment['siteId'] == null ? '' : ' • موقع #${assignment['siteId']}'}',
                                  ),
                                  trailing: active && widget.canManage
                                      ? IconButton(
                                          tooltip: 'إنهاء التكليف',
                                          onPressed: _saving ? null : () => _endAssignment(assignment),
                                          icon: const Icon(Icons.event_busy_outlined),
                                        )
                                      : null,
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

class _NewWorkforceAssignmentDialog extends StatefulWidget {
  const _NewWorkforceAssignmentDialog({Key? key, required this.roles}) : super(key: key);

  final List<Map<String, dynamic>> roles;

  @override
  State<_NewWorkforceAssignmentDialog> createState() => _NewWorkforceAssignmentDialogState();
}

class _NewWorkforceAssignmentDialogState extends State<_NewWorkforceAssignmentDialog> {
  int? _roleId;
  DateTime _effectiveFrom = DateTime.now();

  @override
  void initState() {
    super.initState();
    if (widget.roles.isNotEmpty && widget.roles.first['id'] is num) {
      _roleId = (widget.roles.first['id'] as num).toInt();
    }
  }

  String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

  Future<void> _pickDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _effectiveFrom,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year + 10),
      helpText: 'بداية التكليف',
    );
    if (picked != null && mounted) setState(() => _effectiveFrom = picked);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('إضافة دور وظيفي'),
      content: SizedBox(
        width: 360,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            DropdownButtonFormField<int>(
              value: _roleId,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'الدور الوظيفي'),
              items: widget.roles.map((role) => DropdownMenuItem<int>(
                value: (role['id'] as num).toInt(),
                child: Text(role['name']?.toString() ?? role['code']?.toString() ?? 'دور'),
              )).toList(),
              onChanged: (value) => setState(() => _roleId = value),
            ),
            const SizedBox(height: 12),
            InkWell(
              onTap: _pickDate,
              child: InputDecorator(
                decoration: const InputDecoration(
                  labelText: 'تاريخ بداية التكليف',
                  prefixIcon: Icon(Icons.calendar_month_outlined),
                  border: OutlineInputBorder(),
                ),
                child: Text(_date(_effectiveFrom)),
              ),
            ),
          ],
        ),
      ),
      actions: <Widget>[
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('إلغاء')),
        ElevatedButton(
          onPressed: _roleId == null ? null : () => Navigator.pop(context, <String, dynamic>{
            'roleId': _roleId,
            'effectiveFrom': _date(_effectiveFrom),
          }),
          child: const Text('حفظ التكليف'),
        ),
      ],
    );
  }
}

class WorkforceAgreementsScreen extends StatefulWidget {
  const WorkforceAgreementsScreen({
    Key? key,
    required this.memberId,
    required this.memberName,
    required this.canManage,
  }) : super(key: key);

  final int memberId;
  final String memberName;
  final bool canManage;

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
      builder: (_) => _CompensationAgreementDialog(
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
      final message = _message(error);
      // The draft may already have been created. Reload so it remains visible
      // and can be reviewed instead of silently retrying and duplicating it.
      await _load();
      if (mounted) setState(() => _error = message);
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

  String _componentUnit(Map raw) {
    final basis = _value(raw['basis'], '');
    switch (basis) {
      case 'SHIFT':
        final type = _value(raw['shiftType'], '');
        return type == 'MORNING' ? 'وردية صباحية' : type == 'EVENING' ? 'وردية مسائية' : 'وردية ليلية';
      case 'HOUR': return 'ساعة';
      case 'DAY': return 'يوم';
      case 'FIXED': return _value(raw['unitCode'], 'فترة');
      case 'QUANTITY': return _value(raw['unitCode'], 'وحدة');
      default: return _value(raw['unitCode'], 'وحدة');
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
      floatingActionButton: widget.canManage
          ? FloatingActionButton.extended(
              onPressed: _saving ? null : _createAgreement,
              icon: const Icon(Icons.add),
              label: const Text('اتفاقية أجر جديدة'),
            )
          : null,
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
                            child: Text('لا توجد اتفاقيات أجر لهذا الشخص. أنشئ اتفاقية لتحديد طريقة احتساب مستحقات هذا الشخص.'),
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
                                              '${_value(raw['displayName'])}: ${_value(raw['rate'])} ${_value(agreement['currencyCode'], 'EGP')} / ${_componentUnit(raw)}',
                                            ),
                                          ),
                                      if (agreement['changeReason'] != null)
                                        Text('سبب التغيير: ${agreement['changeReason']}'),
                                      if (status == 'DRAFT' && widget.canManage)
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

class _CompensationAgreementDialog extends StatefulWidget {
  const _CompensationAgreementDialog({Key? key, this.currentAgreement}) : super(key: key);

  final Map<String, dynamic>? currentAgreement;

  @override
  State<_CompensationAgreementDialog> createState() => _CompensationAgreementDialogState();
}

class _CompensationAgreementDialogState extends State<_CompensationAgreementDialog> {
  final _formKey = GlobalKey<FormState>();
  final _hourRate = TextEditingController();
  final _morningRate = TextEditingController();
  final _eveningRate = TextEditingController();
  final _nightRate = TextEditingController();
  final _quantityRate = TextEditingController();
  final _unitCode = TextEditingController();
  final _reference = TextEditingController();
  final _reason = TextEditingController();
  DateTime _effectiveFrom = DateTime.now();
  String _basis = 'SHIFT';

  @override
  void initState() {
    super.initState();
    final rawComponents = widget.currentAgreement?['components'];
    if (rawComponents is! List) return;
    for (final raw in rawComponents) {
      if (raw is! Map) continue;
      final basis = raw['basis']?.toString();
      if (basis == 'SHIFT' || basis == 'HOUR' || basis == 'QUANTITY') {
        _basis = basis!;
      }
      final rate = raw['rate']?.toString() ?? '';
      switch (basis) {
        case 'SHIFT':
          switch (raw['shiftType']?.toString()) {
            case 'MORNING': _morningRate.text = rate; break;
            case 'EVENING': _eveningRate.text = rate; break;
            case 'NIGHT': _nightRate.text = rate; break;
          }
          break;
        case 'HOUR':
          _hourRate.text = rate;
          break;
        case 'QUANTITY':
          _quantityRate.text = rate;
          _unitCode.text = raw['unitCode']?.toString() ?? '';
          break;
      }
    }
  }

  @override
  void dispose() {
    _hourRate.dispose();
    _morningRate.dispose();
    _eveningRate.dispose();
    _nightRate.dispose();
    _quantityRate.dispose();
    _unitCode.dispose();
    _reference.dispose();
    _reason.dispose();
    super.dispose();
  }

  String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

  String _number(TextEditingController controller) =>
      controller.text.trim().replaceAll(',', '.');

  String? _validateRate(String? value) {
    final normalized = (value ?? '').trim().replaceAll(',', '.');
    final parsed = double.tryParse(normalized);
    if (parsed == null || parsed < 0) return 'أدخل مبلغًا صحيحًا غير سالب';
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(normalized)) {
      return 'استخدم منزلتين عشريتين كحد أقصى';
    }
    return null;
  }

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

  String? _validateUnit(String? value) {
    final unit = (value ?? '').trim();
    if (unit.isEmpty) return 'اكتب وحدة القياس مثل طن أو م³';
    if (unit.length > 30) return 'الوحدة طويلة جدًا';
    return null;
  }

  List<Map<String, dynamic>> _components() {
    Map<String, dynamic> component({
      required String code,
      required String name,
      required String basis,
      required TextEditingController rate,
      String? unit,
      String? shiftType,
      required int order,
    }) => <String, dynamic>{
      'componentCode': code,
      'displayName': name,
      'basis': basis,
      'rate': double.parse(_number(rate)),
      if (unit != null) 'unitCode': unit,
      if (shiftType != null) 'shiftType': shiftType,
      'sortOrder': order,
    };

    switch (_basis) {
      case 'HOUR':
        return <Map<String, dynamic>>[
          component(code: 'BASE_HOURLY', name: 'أجر الساعة', basis: 'HOUR',
              rate: _hourRate, unit: 'HOUR', order: 0),
        ];
      case 'QUANTITY':
        return <Map<String, dynamic>>[
          component(code: 'BASE_QUANTITY', name: 'أجر الوحدة', basis: 'QUANTITY',
              rate: _quantityRate, unit: _unitCode.text.trim().toUpperCase(), order: 0),
        ];
      default:
        return <Map<String, dynamic>>[
          component(code: 'BASE_SHIFT_MORNING', name: 'أجر الوردية الصباحية',
              basis: 'SHIFT', rate: _morningRate, shiftType: 'MORNING', order: 0),
          component(code: 'BASE_SHIFT_EVENING', name: 'أجر الوردية المسائية',
              basis: 'SHIFT', rate: _eveningRate, shiftType: 'EVENING', order: 1),
          component(code: 'BASE_SHIFT_NIGHT', name: 'أجر الوردية الليلية',
              basis: 'SHIFT', rate: _nightRate, shiftType: 'NIGHT', order: 2),
        ];
    }
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
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
        if (old['workforceAssignmentId'] != null)
          'workforceAssignmentId': old['workforceAssignmentId'],
        'supersedesAgreementId': old['id'],
        'changeReason': _reason.text.trim(),
      },
      'components': _components(),
    };
    Navigator.of(context).pop(payload);
  }

  Widget _rateField(String label, TextEditingController controller) {
    return Padding(
      padding: const EdgeInsets.only(top: 10),
      child: TextFormField(
        controller: controller,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: const Icon(Icons.payments_outlined),
        ),
        validator: _validateRate,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final replacing = widget.currentAgreement != null;
    return AlertDialog(
      title: Text(replacing ? 'تغيير اتفاقية الأجر' : 'اتفاقية أجر جديدة'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                DropdownButtonFormField<String>(
                  value: _basis,
                  decoration: const InputDecoration(
                    labelText: 'طريقة احتساب الأجر',
                    prefixIcon: Icon(Icons.calculate_outlined),
                  ),
                  items: const <DropdownMenuItem<String>>[
                    DropdownMenuItem(value: 'SHIFT', child: Text('بالوردية')),
                    DropdownMenuItem(value: 'HOUR', child: Text('بالساعة')),
                    DropdownMenuItem(value: 'QUANTITY', child: Text('بالكمية / الوحدة')),
                  ],
                  onChanged: (value) {
                    if (value != null) setState(() => _basis = value);
                  },
                ),
                if (_basis == 'SHIFT') ...<Widget>[
                  _rateField('أجر الوردية الصباحية بالجنيه *', _morningRate),
                  _rateField('أجر الوردية المسائية بالجنيه *', _eveningRate),
                  _rateField('أجر الوردية الليلية بالجنيه *', _nightRate),
                ] else if (_basis == 'HOUR')
                  _rateField('سعر الساعة بالجنيه *', _hourRate)
                else ...<Widget>[
                  _rateField('سعر الوحدة بالجنيه *', _quantityRate),
                  const SizedBox(height: 10),
                  TextFormField(
                    controller: _unitCode,
                    maxLength: 30,
                    decoration: const InputDecoration(
                      labelText: 'وحدة القياس (مثل طن أو م³) *',
                      prefixIcon: Icon(Icons.straighten),
                    ),
                    validator: _validateUnit,
                  ),
                ],
                const SizedBox(height: 8),
                const Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: Text(
                    'الأجر اليومي والراتب الثابت سيظهران بعد استكمال دورة احتسابهما واعتمادهما؛ لن نفعّلهما قبل منع التكرار وربطهما بفترة الاستحقاق.',
                    style: TextStyle(fontSize: 12),
                  ),
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
