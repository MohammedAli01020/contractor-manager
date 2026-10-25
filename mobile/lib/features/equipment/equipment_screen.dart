import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

class EquipmentScreen extends StatefulWidget {
  const EquipmentScreen({Key? key}) : super(key: key);

  @override
  State<EquipmentScreen> createState() => _EquipmentScreenState();
}

class _EquipmentScreenState extends State<EquipmentScreen> {
  final _search = TextEditingController();
  List<Map<String, dynamic>> _items = <Map<String, dynamic>>[];
  final Set<int> _runningEquipmentIds = <int>{};
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

  int? _intValue(dynamic value) => value is int ? value : int.tryParse('$value');

  Future<void> _load() async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final responses = await Future.wait([
        getIt<Dio>().get<Map<String, dynamic>>(
          '/equipment',
          queryParameters: <String, dynamic>{
            'page': 0,
            'size': 100,
            'q': _search.text.trim().isEmpty ? null : _search.text.trim(),
            'includeInactive': _includeInactive,
          },
        ),
        getIt<Dio>().get<List<dynamic>>('/shifts/running'),
      ]);
      final data = responses[0].data as Map<String, dynamic>?;
      if (data == null || data['items'] is! List) {
        throw Exception('استجابة قائمة المعدات غير صالحة.');
      }
      final items = (data['items'] as List)
          .whereType<Map<String, dynamic>>()
          .toList();
      final running = responses[1].data as List<dynamic>? ?? <dynamic>[];
      final runningIds = running
          .whereType<Map<String, dynamic>>()
          .map((shift) => _intValue(shift['equipmentId']))
          .whereType<int>()
          .toSet();
      if (mounted) {
        setState(() {
          _items = items;
          _runningEquipmentIds
            ..clear()
            ..addAll(runningIds);
        });
      }
    } on DioError catch (e) {
      if (mounted) setState(() => _error = _message(e));
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _message(DioError e) {
    final data = e.response?.data;
    if (data is Map && data['message'] != null) return data['message'].toString();
    if (e.response?.statusCode == 401) return 'انتهت الجلسة؛ سجّل الدخول مرة أخرى.';
    if (e.response?.statusCode == 403) return 'ليس لديك صلاحية لعرض المعدات.';
    return 'تعذر تحميل المعدات. تأكد من اتصال الخادم وحاول مرة أخرى.';
  }

  String _value(dynamic value) => value == null || value.toString().isEmpty ? '—' : value.toString();

  Future<void> _edit([Map<String, dynamic>? equipment]) async {
    final saved = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _EquipmentEditDialog(equipment: equipment),
    );
    if (saved != true || !mounted) return;
    await _load();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(equipment == null ? 'تمت إضافة المعدة' : 'تم حفظ التعديلات')),
    );
  }

  Future<void> _deactivate(Map<String, dynamic> equipment) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('تعطيل المعدة'),
        content: Text('هل تريد تعطيل ' + _value(equipment['code']) + '؟ سيظل سجلها محفوظًا.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('رجوع')),
          ElevatedButton(onPressed: () => Navigator.pop(context, true), child: const Text('تعطيل')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await getIt<Dio>().delete('/equipment/${equipment['id']}');
      await _load();
    } on DioError catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(_message(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('المعدات'),
        actions: [
          IconButton(tooltip: 'تحديث', onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'إضافة معدة',
        onPressed: () => _edit(),
        child: const Icon(Icons.add),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              controller: _search,
              textInputAction: TextInputAction.search,
              onSubmitted: (_) => _load(),
              decoration: InputDecoration(
                labelText: 'بحث بكود المعدة أو اسمها',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: IconButton(onPressed: _load, icon: const Icon(Icons.search)),
                border: const OutlineInputBorder(),
              ),
            ),
          ),
          SwitchListTile(
            title: const Text('عرض المعدات المعطّلة أيضًا'),
            value: _includeInactive,
            onChanged: (value) {
              setState(() => _includeInactive = value);
              _load();
            },
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(children: [
                Text(_error!, textAlign: TextAlign.center, style: TextStyle(color: theme.colorScheme.error)),
                ElevatedButton(onPressed: _load, child: const Text('إعادة المحاولة')),
              ]),
            ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? const SizedBox.shrink()
                    : _items.isEmpty
                        ? const Center(child: Text('لا توجد معدات مطابقة للبحث.'))
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(bottom: 88),
                              itemCount: _items.length,
                              itemBuilder: (context, index) {
                                final item = _items[index];
                                final id = _intValue(item['id']);
                                final running = id != null && _runningEquipmentIds.contains(id);
                                final active = item['active'] != false;
                                final status = item['status']?.toString() ?? '';
                                final Color stateColor;
                                final String stateLabel;
                                if (!active) {
                                  stateColor = Colors.grey;
                                  stateLabel = 'معطّلة';
                                } else if (running) {
                                  stateColor = Colors.green.shade700;
                                  stateLabel = 'شغالة الآن';
                                } else if (status == 'MAINTENANCE' || status == 'OUT_OF_SERVICE') {
                                  stateColor = Colors.orange.shade800;
                                  stateLabel = status == 'MAINTENANCE' ? 'صيانة' : 'خارج الخدمة';
                                } else {
                                  stateColor = Colors.blue.shade700;
                                  stateLabel = 'متاحة';
                                }
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                                  child: ListTile(
                                    leading: CircleAvatar(
                                      backgroundColor: stateColor.withOpacity(0.12),
                                      child: Icon(Icons.construction_outlined, color: stateColor),
                                    ),
                                    title: Text(_value(item['name']) == '—'
                                        ? _value(item['code'])
                                        : '${item['name']} · ${item['code']}',
                                      style: const TextStyle(fontWeight: FontWeight.bold)),
                                    subtitle: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(_value(item['equipmentTypeName'])),
                                        Text(stateLabel, style: TextStyle(color: stateColor, fontWeight: FontWeight.w700)),
                                        Text('قيمة الوردية: ${_value(item['defaultShiftPrice'])} جنيه'),
                                        Text('تكلفة التشغيل/وردية: ${_value(item['operatingCostPerShift'])} جنيه'),
                                      ],
                                    ),
                                    isThreeLine: true,
                                    trailing: PopupMenuButton<String>(
                                      onSelected: (action) {
                                        if (action == 'edit') _edit(item);
                                        if (action == 'deactivate') _deactivate(item);
                                      },
                                      itemBuilder: (_) => [
                                        const PopupMenuItem(value: 'edit', child: Text('تعديل')),
                                        if (active) const PopupMenuItem(value: 'deactivate', child: Text('تعطيل')),
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

class _EquipmentEditDialog extends StatefulWidget {
  const _EquipmentEditDialog({Key? key, this.equipment}) : super(key: key);
  final Map<String, dynamic>? equipment;

  @override
  State<_EquipmentEditDialog> createState() => _EquipmentEditDialogState();
}

class _EquipmentEditDialogState extends State<_EquipmentEditDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _code;
  late final TextEditingController _name;
  late final TextEditingController _price;
  late final TextEditingController _cost;
  late final TextEditingController _notes;
  List<Map<String, dynamic>> _types = <Map<String, dynamic>>[];
  int? _typeId;
  String _status = 'AVAILABLE';
  bool _active = true;
  bool _loadingTypes = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final e = widget.equipment;
    _code = TextEditingController(text: e?['code']?.toString() ?? '');
    _name = TextEditingController(text: e?['name']?.toString() ?? '');
    _price = TextEditingController(text: e?['defaultShiftPrice']?.toString() ?? '0');
    _cost = TextEditingController(text: e?['operatingCostPerShift']?.toString() ?? '0');
    _notes = TextEditingController(text: e?['notes']?.toString() ?? '');
    _typeId = e?['equipmentTypeId'] is int ? e!['equipmentTypeId'] as int : int.tryParse('${e?['equipmentTypeId']}');
    _status = e?['status']?.toString() ?? 'AVAILABLE';
    _active = e == null || e['active'] != false;
    _loadTypes();
  }

  @override
  void dispose() {
    _code.dispose();
    _name.dispose();
    _price.dispose();
    _cost.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _loadTypes() async {
    try {
      final response = await getIt<Dio>().get<List<dynamic>>('/equipment-types');
      if (!mounted) return;
      setState(() {
        _types = (response.data ?? <dynamic>[])
            .whereType<Map<String, dynamic>>()
            .where((type) => type['active'] != false)
            .toList();
        _typeId ??= _types.isEmpty ? null : (_types.first['id'] as num).toInt();
        _loadingTypes = false;
      });
    } on DioError catch (e) {
      if (mounted) setState(() {
        _loadingTypes = false;
        _error = e.response?.data is Map && e.response?.data['message'] != null
            ? e.response!.data['message'].toString()
            : 'تعذر تحميل أنواع المعدات.';
      });
    }
  }

  String? _requiredNumber(String? value) {
    if (value == null || value.trim().isEmpty) return 'القيمة مطلوبة';
    final n = double.tryParse(value.trim());
    if (n == null || n < 0) return 'اكتب رقمًا صحيحًا غير سالب';
    if (value.trim().contains('.') && value.trim().split('.').last.length > 2) return 'مسموح بخانتين عشريتين فقط';
    return null;
  }

  Future<void> _save() async {
    if (_saving || !_formKey.currentState!.validate()) return;
    if (_typeId == null) {
      setState(() => _error = 'أضف نوع معدة أولًا من النظام.');
      return;
    }
    setState(() { _saving = true; _error = null; });
    final payload = <String, dynamic>{
      'code': _code.text.trim(),
      'name': _name.text.trim().isEmpty ? null : _name.text.trim(),
      'equipmentTypeId': _typeId,
      'operatingCostPerShift': double.parse(_cost.text.trim()),
      'defaultShiftPrice': double.parse(_price.text.trim()),
      'status': _status,
      'notes': _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      'active': _active,
      if (widget.equipment != null) 'version': widget.equipment!['version'],
    };
    try {
      if (widget.equipment == null) {
        await getIt<Dio>().post('/equipment', data: payload);
      } else {
        await getIt<Dio>().put('/equipment/${widget.equipment!['id']}', data: payload);
      }
      if (mounted) Navigator.of(context).pop(true);
    } on DioError catch (e) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        final data = e.response?.data;
        _error = data is Map && data['message'] != null
            ? data['message'].toString()
            : 'تعذر حفظ المعدة. راجع البيانات والاتصال بالخادم.';
      });
    } catch (e) {
      if (mounted) setState(() { _saving = false; _error = 'تعذر الحفظ: $e'; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.equipment == null ? 'إضافة معدة' : 'تعديل المعدة'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(controller: _code, decoration: const InputDecoration(labelText: 'كود المعدة *'),
                  validator: (v) => v == null || v.trim().isEmpty ? 'كود المعدة مطلوب' : null),
                TextFormField(controller: _name, decoration: const InputDecoration(labelText: 'اسم المعدة')),
                const SizedBox(height: 8),
                if (_loadingTypes)
                  const LinearProgressIndicator()
                else
                  DropdownButtonFormField<int>(
                    value: _types.any((t) => t['id'] == _typeId) ? _typeId : null,
                    decoration: const InputDecoration(labelText: 'نوع المعدة *'),
                    items: _types.map((type) => DropdownMenuItem<int>(
                      value: (type['id'] as num).toInt(), child: Text(type['name'].toString()),
                    )).toList(),
                    onChanged: _saving ? null : (value) => setState(() => _typeId = value),
                  ),
                TextFormField(controller: _price, keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'قيمة الوردية الافتراضية (جنيه)'),
                  validator: _requiredNumber),
                TextFormField(controller: _cost, keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'تكلفة التشغيل لكل وردية (جنيه) *'),
                  validator: _requiredNumber),
                DropdownButtonFormField<String>(
                  value: _status,
                  decoration: const InputDecoration(labelText: 'الحالة التشغيلية'),
                  items: const [
                    DropdownMenuItem(value: 'AVAILABLE', child: Text('متاحة')),
                    DropdownMenuItem(value: 'MAINTENANCE', child: Text('صيانة')),
                    DropdownMenuItem(value: 'OUT_OF_SERVICE', child: Text('خارج الخدمة')),
                  ],
                  onChanged: _saving ? null : (v) => setState(() => _status = v ?? 'AVAILABLE'),
                ),
                TextFormField(controller: _notes, maxLines: 2, decoration: const InputDecoration(labelText: 'ملاحظات')),
                SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('المعدة نشطة'),
                  value: _active, onChanged: _saving ? null : (v) => setState(() => _active = v)),
                if (_error != null)
                  Align(alignment: AlignmentDirectional.centerStart,
                    child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(onPressed: _saving ? null : () => Navigator.of(context).pop(false), child: const Text('إلغاء')),
        ElevatedButton(onPressed: _saving || _loadingTypes ? null : _save,
          child: Text(_saving ? 'جارٍ الحفظ...' : 'حفظ')),
      ],
    );
  }
}
