import 'dart:math';

import 'package:flutter/material.dart';

import '../../../core/error/app_failure.dart';
import '../data/datasources/shifts_api.dart';
import '../data/models/shift_model.dart';

class ShiftCorrectionScreen extends StatefulWidget {
  const ShiftCorrectionScreen({Key? key, required this.shift, required this.api})
      : super(key: key);

  final ShiftModel shift;
  final ShiftsApi api;

  @override
  State<ShiftCorrectionScreen> createState() => _ShiftCorrectionScreenState();
}

class _ShiftCorrectionScreenState extends State<ShiftCorrectionScreen> {
  final _formKey = GlobalKey<FormState>();
  final _reason = TextEditingController();
  final _wage = TextEditingController();
  final _price = TextEditingController();
  final _operatingCost = TextEditingController();
  final _notes = TextEditingController();

  List<Map<String, dynamic>> _sites = [];
  List<Map<String, dynamic>> _equipment = [];
  int? _siteId;
  int? _equipmentId;
  String _shiftDate = '';
  String _shiftType = 'MORNING';
  bool _loading = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final shift = widget.shift;
    _siteId = shift.siteId;
    _equipmentId = shift.equipmentId;
    _shiftDate = shift.shiftDate;
    _shiftType = shift.shiftType;
    _wage.text = shift.driverWage.toStringAsFixed(2);
    _price.text = shift.price.toStringAsFixed(2);
    _operatingCost.text = shift.equipmentOperatingCost.toStringAsFixed(2);
    _notes.text = shift.notes;
    _loadOptions();
  }

  Future<void> _loadOptions() async {
    try {
      final result = await Future.wait<dynamic>([
        widget.api.fetchSites(),
        widget.api.fetchLiveEquipment(),
      ]);
      if (!mounted) return;
      setState(() {
        _sites = result[0] as List<Map<String, dynamic>>;
        _equipment = result[1] as List<Map<String, dynamic>>;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = _message(error);
      });
    }
  }

  String _message(Object error) {
    if (error is AppFailure) return error.message;
    return 'تعذر تحميل بيانات التصحيح. تحقق من الاتصال وحاول مرة أخرى.';
  }

  List<Map<String, dynamic>> get _siteOptions {
    final items = List<Map<String, dynamic>>.from(_sites);
    if (_siteId != null && !items.any((item) => _id(item['id']) == _siteId)) {
      items.add({'id': _siteId, 'name': widget.shift.siteName, 'active': false});
    }
    return items;
  }

  List<Map<String, dynamic>> get _equipmentOptions {
    final items = List<Map<String, dynamic>>.from(_equipment);
    if (_equipmentId != null &&
        !items.any((item) => _id(item['id']) == _equipmentId)) {
      items.add({
        'id': _equipmentId,
        'name': widget.shift.equipmentName,
        'code': widget.shift.equipmentCode,
        'status': 'CURRENT',
      });
    }
    return items;
  }

  int? _id(dynamic value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse(value?.toString() ?? '');
  }

  String? _moneyValidator(String? value, {required bool positive}) {
    final parsed = double.tryParse((value ?? '').trim());
    if (parsed == null || parsed.isNaN || parsed.isInfinite ||
        parsed < 0 || (positive && parsed <= 0)) {
      return positive
          ? 'أجر السائق يجب أن يكون أكبر من صفر'
          : 'أدخل مبلغًا صحيحًا يساوي صفرًا أو أكثر';
    }
    if ((parsed * 100 - (parsed * 100).round()).abs() > 0.000001) {
      return 'المبلغ يقبل خانتين عشريتين فقط';
    }
    return null;
  }

  String _newIdempotencyKey() {
    final random = Random.secure();
    final suffix = List<int>.generate(24, (_) => random.nextInt(16))
        .map((value) => value.toRadixString(16))
        .join();
    return 'mobile-shift-correction-$suffix';
  }

  Future<void> _pickDate() async {
    final parsed = DateTime.tryParse(_shiftDate) ?? DateTime.now();
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: parsed.isAfter(now) ? now : parsed,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year, now.month, now.day),
      helpText: 'تاريخ الوردية المصحح',
      cancelText: 'إلغاء',
      confirmText: 'اختيار',
    );
    if (picked == null || !mounted) return;
    setState(() {
      _shiftDate =
          '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    });
  }

  Future<void> _submit() async {
    if (_saving || !_formKey.currentState!.validate()) return;
    if (_siteId == null || _equipmentId == null) {
      setState(() => _error = 'اختار الموقع والمعدة.');
      return;
    }
    final version = widget.shift.version;
    if (version == null) {
      setState(() => _error =
          'لا يمكن التصحيح لأن رقم إصدار الوردية غير متاح. حدّث البيانات وحاول مرة أخرى.');
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('تأكيد تصحيح الوردية'),
        content: const Text(
          'سيتم عكس القيد المحاسبي الحالي وإنشاء قيد جديد وتحديث استحقاق السائق. لا يمكن التراجع عن ذلك بتعديل عادي.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('مراجعة'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('تأكيد التصحيح'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final corrected = await widget.api.correctShift(widget.shift.id, {
        'idempotencyKey': _newIdempotencyKey(),
        'reason': _reason.text.trim(),
        'expectedVersion': version,
        'shiftDate': _shiftDate,
        'shiftType': _shiftType,
        'siteId': _siteId,
        'equipmentId': _equipmentId,
        'driverWage': double.parse(_wage.text.trim()),
        'price': double.parse(_price.text.trim()),
        'equipmentOperatingCost': double.parse(_operatingCost.text.trim()),
        'notes': _notes.text.trim(),
      });
      if (!mounted) return;
      Navigator.of(context).pop(corrected);
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  void dispose() {
    _reason.dispose();
    _wage.dispose();
    _price.dispose();
    _operatingCost.dispose();
    _notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('تصحيح الوردية #${widget.shift.id}')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Form(
              key: _formKey,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.error.withOpacity(0.07),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: Theme.of(context).colorScheme.error.withOpacity(0.3),
                      ),
                    ),
                    child: const Text(
                      'التصحيح مخصص للمدير ADMIN. سيُحفظ سجل تدقيق، وتظل القيود القديمة محفوظة مع إنشاء قيود عكسية.',
                    ),
                  ),
                  if (_error != null) ...[
                    const SizedBox(height: 12),
                    Text(_error!,
                        style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ],
                  const SizedBox(height: 16),
                  Text('البيانات الحالية',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 8),
                  _currentLine('السائق', widget.shift.driverName),
                  _currentLine('الموقع', widget.shift.siteName),
                  _currentLine('المعدة',
                      '${widget.shift.equipmentCode} ${widget.shift.equipmentName}'),
                  _currentLine('أجر السائق',
                      '${widget.shift.driverWage.toStringAsFixed(2)} جنيه'),
                  const Divider(height: 28),
                  Text('البيانات بعد التصحيح',
                      style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 12),
                  InkWell(
                    onTap: _saving ? null : _pickDate,
                    child: InputDecorator(
                      decoration: const InputDecoration(
                        labelText: 'تاريخ الوردية',
                        border: OutlineInputBorder(),
                        prefixIcon: Icon(Icons.calendar_month_outlined),
                      ),
                      child: Text(_shiftDate),
                    ),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: _shiftType,
                    decoration: const InputDecoration(
                      labelText: 'نوع الوردية',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: 'MORNING', child: Text('صباحية')),
                      DropdownMenuItem(value: 'EVENING', child: Text('مسائية')),
                      DropdownMenuItem(value: 'NIGHT', child: Text('ليلية')),
                    ],
                    onChanged: _saving
                        ? null
                        : (value) {
                            if (value != null) setState(() => _shiftType = value);
                          },
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    value: _siteId,
                    decoration: const InputDecoration(
                      labelText: 'الموقع',
                      border: OutlineInputBorder(),
                    ),
                    items: _siteOptions
                        .map((item) => DropdownMenuItem<int>(
                              value: _id(item['id']),
                              child: Text((item['name'] ?? 'موقع').toString()),
                            ))
                        .where((item) => item.value != null)
                        .toList(),
                    validator: (value) => value == null ? 'اختار الموقع' : null,
                    onChanged: _saving
                        ? null
                        : (value) => setState(() => _siteId = value),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    value: _equipmentId,
                    decoration: const InputDecoration(
                      labelText: 'المعدة',
                      border: OutlineInputBorder(),
                    ),
                    items: _equipmentOptions
                        .map((item) {
                          final id = _id(item['id']);
                          final code = (item['code'] ?? '').toString();
                          final name = (item['name'] ?? 'معدة').toString();
                          return DropdownMenuItem<int>(
                            value: id,
                            child: Text(code.isEmpty ? name : '$code - $name'),
                          );
                        })
                        .where((item) => item.value != null)
                        .toList(),
                    validator: (value) => value == null ? 'اختار المعدة' : null,
                    onChanged: _saving
                        ? null
                        : (value) => setState(() => _equipmentId = value),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _wage,
                    enabled: !_saving,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(
                      labelText: 'أجر السائق (جنيه)',
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) => _moneyValidator(value, positive: true),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _price,
                    enabled: !_saving,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(
                      labelText: 'قيمة الوردية (جنيه)',
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) => _moneyValidator(value, positive: false),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _operatingCost,
                    enabled: !_saving,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(
                      labelText: 'تكلفة تشغيل المعدة (جنيه)',
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) => _moneyValidator(value, positive: false),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _notes,
                    enabled: !_saving,
                    maxLength: 500,
                    maxLines: 2,
                    decoration: const InputDecoration(
                      labelText: 'ملاحظات',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _reason,
                    enabled: !_saving,
                    minLines: 2,
                    maxLines: 4,
                    maxLength: 500,
                    decoration: const InputDecoration(
                      labelText: 'سبب التصحيح (إلزامي)',
                      hintText: 'اكتب سبب تعديل بيانات الوردية',
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) {
                      final text = (value ?? '').trim();
                      if (text.length < 5) return 'سبب التصحيح يجب ألا يقل عن 5 أحرف';
                      if (text.length > 500) return 'الحد الأقصى 500 حرف';
                      return null;
                    },
                  ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _saving ? null : _submit,
                      icon: _saving
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.verified_outlined),
                      label: Text(_saving ? 'جارٍ تنفيذ التصحيح...' : 'مراجعة وتأكيد التصحيح'),
                    ),
                  ),
                ],
              ),
            ),
    );
  }

  Widget _currentLine(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(width: 105, child: Text(label)),
          Expanded(
            child: Text(value,
                style: const TextStyle(fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}
