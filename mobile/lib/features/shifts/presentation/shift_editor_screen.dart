import 'package:flutter/material.dart';

import '../../../core/di/injector.dart';
import '../../../core/error/app_failure.dart';
import '../data/datasources/shifts_api.dart';
import '../data/models/shift_model.dart';

class ShiftEditorScreen extends StatefulWidget {
  const ShiftEditorScreen({Key? key, this.shift}) : super(key: key);

  final ShiftModel? shift;

  @override
  State<ShiftEditorScreen> createState() => _ShiftEditorScreenState();
}

class _ShiftEditorScreenState extends State<ShiftEditorScreen> {
  final _formKey = GlobalKey<FormState>();
  final _price = TextEditingController();
  final _wage = TextEditingController();
  final _notes = TextEditingController();
  final _api = getIt<ShiftsApi>();

  List<Map<String, dynamic>> _drivers = [];
  List<Map<String, dynamic>> _equipment = [];
  List<Map<String, dynamic>> _sites = [];
  int? _driverId;
  int? _equipmentId;
  int? _siteId;
  String _type = 'MORNING';
  String _date = '';
  bool _loading = true;
  bool _saving = false;
  bool _defaultsLoaded = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    final shift = widget.shift;
    _type = shift?.shiftType ?? 'MORNING';
    _date = shift?.shiftDate ?? _today();
    _driverId = shift?.driverId;
    _equipmentId = shift?.equipmentId;
    _siteId = shift?.siteId;
    _price.text = shift?.price.toStringAsFixed(2) ?? '';
    _wage.text = shift?.driverWage.toStringAsFixed(2) ?? '';
    _notes.text = shift?.notes ?? '';
    _loadOptions();
  }

  String _today() {
    final now = DateTime.now();
    return '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
  }

  Future<void> _loadOptions() async {
    try {
      final result = await Future.wait<dynamic>([
        _api.fetchLiveDrivers(),
        _api.fetchLiveEquipment(),
        _api.fetchSites(),
      ]);
      if (!mounted) return;
      setState(() {
        _drivers = result[0] as List<Map<String, dynamic>>;
        _equipment = result[1] as List<Map<String, dynamic>>;
        _sites = result[2] as List<Map<String, dynamic>>;
        _loading = false;
      });
      if (widget.shift == null) _loadDefaults();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = _message(e);
      });
    }
  }

  Future<void> _loadDefaults() async {
    if (_driverId == null || _equipmentId == null) return;
    try {
      final data = await _api.fetchDefaults(
        driverId: _driverId!,
        equipmentId: _equipmentId!,
        shiftType: _type,
      );
      if (!mounted) return;
      setState(() {
        if (!_defaultsLoaded || _price.text.isEmpty) {
          _price.text = _value(data['price']);
        }
        if (!_defaultsLoaded || _wage.text.isEmpty) {
          _wage.text = _value(data['driverWage']);
        }
        _defaultsLoaded = true;
      });
    } catch (_) {
      // يظل بإمكان المستخدم إدخال السعر والأجر يدويًا.
    }
  }

  String _value(dynamic value) {
    if (value == null) return '';
    if (value is num) return value.toStringAsFixed(2);
    return value.toString();
  }

  String _message(Object error) {
    if (error is AppFailure) return error.message;
    return 'تعذر تنفيذ الطلب. تحقق من الاتصال وحاول مرة أخرى.';
  }

  bool _available(Map<String, dynamic> item, String kind) {
    final ownShiftId = item['currentShiftId'];
    if (widget.shift != null && ownShiftId != null &&
        ownShiftId.toString() == widget.shift!.id.toString()) return true;
    final status = (item['liveStatus'] ?? item['status'] ?? '').toString();
    if (kind == 'driver') {
      return status == 'AVAILABLE' &&
          (item['availability'] ?? 'AVAILABLE').toString() == 'AVAILABLE';
    }
    return status == 'AVAILABLE';
  }

  String _unavailableReason(Map<String, dynamic> item, String kind) {
    final status = (item['liveStatus'] ?? item['status'] ?? '').toString();
    if (status == 'WORKING' || item['currentShiftId'] != null) {
      final shift = item['currentShiftId'] == null ? '' : 'وردية #${item['currentShiftId']}';
      final site = (item['siteName'] ?? '').toString();
      return 'غير متاح: يعمل حاليًا ${shift.isEmpty ? '' : 'في $shift'}${site.isEmpty ? '' : ' في موقع $site'}';
    }
    if (kind == 'driver' && (item['availability'] ?? '').toString() == 'UNAVAILABLE') {
      final reason = (item['unavailableReason'] ?? '').toString();
      return reason.isEmpty ? 'غير متاح حسب حالة العامل' : 'غير متاح: $reason';
    }
    if (status == 'MAINTENANCE') return 'غير متاح: المعدة في الصيانة';
    if (status == 'OUT_OF_SERVICE') return 'غير متاح: المعدة خارج الخدمة';
    return 'غير متاح حاليًا';
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    if (_driverId == null || _equipmentId == null || _siteId == null) {
      setState(() => _error = 'اختار الموقع والسائق/العامل والمعدة.');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final payload = <String, dynamic>{
        'shiftDate': _date,
        'shiftType': _type,
        'siteId': _siteId,
        'driverId': _driverId,
        'equipmentId': _equipmentId,
        'price': _price.text.trim().isEmpty ? null : double.parse(_price.text.trim()),
        'driverWage': _wage.text.trim().isEmpty ? null : double.parse(_wage.text.trim()),
        'notes': _notes.text.trim(),
      };
      if (widget.shift != null) payload['version'] = widget.shift!.version;
      await _api.saveShift(payload, id: widget.shift?.id);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = _message(e));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  void dispose() {
    _price.dispose();
    _wage.dispose();
    _notes.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.shift == null ? 'بدء وردية جديدة' : 'تعديل وردية #${widget.shift!.id}')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null && _drivers.isEmpty && _equipment.isEmpty && _sites.isEmpty
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
              : Form(
                  key: _formKey,
                  child: ListView(
                    padding: const EdgeInsets.all(16),
                    children: [
                      if (_error != null) ...[
                        Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                        const SizedBox(height: 8),
                      ],
                      TextFormField(
                        initialValue: _date,
                        decoration: const InputDecoration(labelText: 'تاريخ الوردية', hintText: 'YYYY-MM-DD'),
                        validator: (v) => v == null || !RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(v) ? 'اكتب التاريخ بالشكل YYYY-MM-DD' : null,
                        onChanged: (v) => _date = v,
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<String>(
                        value: _type,
                        decoration: const InputDecoration(labelText: 'نوع الوردية'),
                        items: const [
                          DropdownMenuItem(value: 'MORNING', child: Text('صباحية')),
                          DropdownMenuItem(value: 'EVENING', child: Text('مسائية')),
                          DropdownMenuItem(value: 'NIGHT', child: Text('ليلية')),
                        ],
                        onChanged: (v) {
                          if (v == null) return;
                          setState(() { _type = v; _defaultsLoaded = false; });
                          _loadDefaults();
                        },
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<int>(
                        value: _siteId,
                        decoration: const InputDecoration(labelText: 'الموقع'),
                        items: _sites.map((s) => DropdownMenuItem<int>(
                          value: (s['id'] as num).toInt(),
                          child: Text((s['name'] ?? 'موقع').toString()),
                        )).toList(),
                        validator: (v) => v == null ? 'اختار الموقع' : null,
                        onChanged: (v) => setState(() => _siteId = v),
                      ),
                      const SizedBox(height: 12),
                      _resourceDropdown(
                        label: 'السائق / العامل',
                        items: _drivers,
                        selectedId: _driverId,
                        kind: 'driver',
                        onChanged: (v) {
                          setState(() { _driverId = v; _defaultsLoaded = false; });
                          _loadDefaults();
                        },
                      ),
                      const SizedBox(height: 12),
                      _resourceDropdown(
                        label: 'المعدة',
                        items: _equipment,
                        selectedId: _equipmentId,
                        kind: 'equipment',
                        onChanged: (v) {
                          setState(() { _equipmentId = v; _defaultsLoaded = false; });
                          _loadDefaults();
                        },
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _price,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'قيمة الوردية بالجنيه', helperText: 'تُملأ تلقائيًا من السعر الافتراضي عند اختيار السائق والمعدة'),
                        validator: _moneyValidator,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _wage,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'أجر السائق بالجنيه'),
                        validator: _moneyValidator,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _notes,
                        maxLength: 500,
                        maxLines: 3,
                        decoration: const InputDecoration(labelText: 'ملاحظات'),
                      ),
                      const SizedBox(height: 20),
                      ElevatedButton.icon(
                        onPressed: _saving ? null : _save,
                        icon: _saving ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.save_outlined),
                        label: Text(_saving ? 'جارٍ الحفظ...' : widget.shift == null ? 'بدء الوردية' : 'حفظ التعديلات'),
                      ),
                    ],
                  ),
                ),
    );
  }

  String? _moneyValidator(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final number = double.tryParse(value.trim());
    if (number == null || number < 0) return 'أدخل مبلغًا صحيحًا';
    return null;
  }

  Widget _resourceDropdown({
    required String label,
    required List<Map<String, dynamic>> items,
    required int? selectedId,
    required String kind,
    required ValueChanged<int?> onChanged,
  }) {
    final options = <DropdownMenuItem<int>>[];
    for (final item in items) {
      final id = (item['id'] as num).toInt();
      final available = _available(item, kind);
      final name = kind == 'driver'
          ? (item['name'] ?? 'عامل').toString()
          : '${item['code'] ?? ''} - ${item['name'] ?? 'معدة'}';
      final status = available ? 'متاح' : _unavailableReason(item, kind);
      options.add(DropdownMenuItem<int>(
        value: id,
        enabled: available,
        child: SizedBox(
          width: 280,
          child: Text('$name · $status', maxLines: 2, overflow: TextOverflow.ellipsis),
        ),
      ));
    }
    return DropdownButtonFormField<int>(
      value: items.any((e) => (e['id'] as num).toInt() == selectedId) ? selectedId : null,
      isExpanded: true,
      decoration: InputDecoration(labelText: label),
      items: options,
      validator: (v) => v == null ? 'اختار $label' : null,
      onChanged: onChanged,
    );
  }
}
