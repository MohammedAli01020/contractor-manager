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
  final _equipmentCost = TextEditingController();
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
    _equipmentCost.text = shift?.equipmentOperatingCost.toStringAsFixed(2) ?? '';
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
    if (_driverId == null && _equipmentId == null) return;
    try {
      final data = await _api.fetchDefaults(
        driverId: _driverId,
        equipmentId: _equipmentId,
        shiftType: _type,
      );
      if (!mounted) return;
      setState(() {
        if (_driverId != null && data['driverWage'] != null) {
          _wage.text = _value(data['driverWage']);
        }
        if (_equipmentId != null) {
          if (data['price'] != null) _price.text = _value(data['price']);
          if (data['equipmentOperatingCost'] != null) {
            _equipmentCost.text = _value(data['equipmentOperatingCost']);
          }
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
    _equipmentCost.dispose();
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
                      InkWell(
                        borderRadius: BorderRadius.circular(12),
                        onTap: _saving ? null : _pickDate,
                        child: InputDecorator(
                          decoration: const InputDecoration(
                            labelText: 'تاريخ الوردية',
                            prefixIcon: Icon(Icons.calendar_month_outlined),
                            border: OutlineInputBorder(),
                          ),
                          child: Row(
                            children: [
                              Expanded(child: Text(_date, style: Theme.of(context).textTheme.titleMedium)),
                              const Icon(Icons.arrow_drop_down),
                            ],
                          ),
                        ),
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
                      _resourcePicker(
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
                      _resourcePicker(
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
                        decoration: const InputDecoration(
                          labelText: 'أجر السائق بالجنيه',
                          helperText: 'يتحدد تلقائيًا عند اختيار السائق ونوع الوردية',
                        ),
                        validator: _moneyValidator,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _equipmentCost,
                        readOnly: true,
                        decoration: const InputDecoration(
                          labelText: 'تكلفة تشغيل المعدة بالجنيه',
                          helperText: 'تُحسب تلقائيًا حسب المعدة المختارة',
                          prefixIcon: Icon(Icons.construction_outlined),
                        ),
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

  Future<void> _pickDate() async {
    final parsed = DateTime.tryParse(_date) ?? DateTime.now();
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: parsed.isAfter(now) ? now : parsed,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year, now.month, now.day),
      helpText: 'اختار تاريخ الوردية',
      cancelText: 'إلغاء',
      confirmText: 'اختيار',
    );
    if (picked == null || !mounted) return;
    setState(() {
      _date = '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';
    });
  }

  Widget _resourcePicker({
    required String label,
    required List<Map<String, dynamic>> items,
    required int? selectedId,
    required String kind,
    required ValueChanged<int?> onChanged,
  }) {
    final selected = items.where((e) => (e['id'] as num).toInt() == selectedId).toList();
    final selectedItem = selected.isEmpty ? null : selected.first;
    final selectedName = selectedItem == null
        ? 'اضغط للاختيار'
        : kind == 'driver'
            ? (selectedItem['name'] ?? 'عامل').toString()
            : '${selectedItem['code'] ?? ''} - ${selectedItem['name'] ?? 'معدة'}';
    final selectedAvailable = selectedItem != null && _available(selectedItem, kind);
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: _saving ? null : () => _chooseResource(
        label: label, items: items, selectedId: selectedId, kind: kind, onChanged: onChanged,
      ),
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: label,
          prefixIcon: Icon(kind == 'driver' ? Icons.person_outline : Icons.construction_outlined),
          border: const OutlineInputBorder(),
          suffixIcon: const Icon(Icons.search),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(selectedName, maxLines: 1, overflow: TextOverflow.ellipsis),
                  if (selectedItem != null) ...[
                    const SizedBox(height: 3),
                    Text(
                      selectedAvailable ? 'متاح للاختيار' : _unavailableReason(selectedItem, kind),
                      style: TextStyle(
                        fontSize: 12,
                        color: selectedAvailable ? Colors.green.shade700 : Theme.of(context).colorScheme.error,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ],
              ),
            ),
            const Icon(Icons.keyboard_arrow_down),
          ],
        ),
      ),
    );
  }

  Future<void> _chooseResource({
    required String label,
    required List<Map<String, dynamic>> items,
    required int? selectedId,
    required String kind,
    required ValueChanged<int?> onChanged,
  }) async {
    final available = items.where((item) => _available(item, kind)).toList();
    final unavailable = items.where((item) => !_available(item, kind)).toList();
    available.sort((a, b) => _resourceName(a, kind).toLowerCase().compareTo(_resourceName(b, kind).toLowerCase()));
    unavailable.sort((a, b) => _resourceName(a, kind).toLowerCase().compareTo(_resourceName(b, kind).toLowerCase()));
    final chosen = await showDialog<int>(
      context: context,
      builder: (dialogContext) => Dialog(
        insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        child: SizedBox(
          width: 520,
          height: MediaQuery.of(dialogContext).size.height * 0.72,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(18, 18, 18, 10),
                child: Row(
                  children: [
                    Icon(kind == 'driver' ? Icons.person_search_outlined : Icons.precision_manufacturing_outlined),
                    const SizedBox(width: 10),
                    Expanded(child: Text('اختيار $label', style: Theme.of(dialogContext).textTheme.titleLarge)),
                    IconButton(onPressed: () => Navigator.pop(dialogContext), icon: const Icon(Icons.close)),
                  ],
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.all(12),
                  children: [
                    _resourceSectionTitle('المتاحون', available.length, Colors.green.shade700),
                    if (available.isEmpty)
                      const Padding(
                        padding: EdgeInsets.all(14),
                        child: Text('لا توجد اختيارات متاحة حاليًا'),
                      ),
                    ...available.map((item) {
                      final id = (item['id'] as num).toInt();
                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: Colors.green.withOpacity(0.12),
                            child: Icon(kind == 'driver' ? Icons.person_outline : Icons.construction_outlined, color: Colors.green.shade700),
                          ),
                          title: Text(_resourceName(item, kind), maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: Text(_resourceDetail(item, kind, true)),
                          trailing: selectedId == id ? const Icon(Icons.check_circle, color: Colors.green) : const Icon(Icons.chevron_left),
                          onTap: () => Navigator.pop(dialogContext, id),
                        ),
                      );
                    }),
                    const SizedBox(height: 8),
                    _resourceSectionTitle('غير متاحين — لا يمكن اختيارهم', unavailable.length, Colors.grey.shade600),
                    ...unavailable.map((item) => Card(
                      color: Theme.of(dialogContext).disabledColor.withOpacity(0.05),
                      margin: const EdgeInsets.only(bottom: 8),
                      child: ListTile(
                        enabled: false,
                        leading: CircleAvatar(
                          backgroundColor: Colors.grey.withOpacity(0.12),
                          child: Icon(kind == 'driver' ? Icons.person_off_outlined : Icons.construction_outlined, color: Colors.grey),
                        ),
                        title: Text(_resourceName(item, kind), maxLines: 1, overflow: TextOverflow.ellipsis),
                        subtitle: Text(_resourceDetail(item, kind, false)),
                      ),
                    )),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
    if (chosen != null) onChanged(chosen);
  }

  Widget _resourceSectionTitle(String title, int count, Color color) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 12, 4, 10),
      child: Row(
        children: [
          Expanded(child: Text(title, style: TextStyle(fontWeight: FontWeight.bold, color: color))),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
            decoration: BoxDecoration(color: color.withOpacity(0.12), borderRadius: BorderRadius.circular(20)),
            child: Text('$count', style: TextStyle(color: color, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  String _resourceName(Map<String, dynamic> item, String kind) {
    if (kind == 'driver') return (item['name'] ?? 'عامل').toString();
    final code = (item['code'] ?? '').toString();
    final name = (item['name'] ?? 'معدة').toString();
    return code.isEmpty ? name : '$code - $name';
  }

  String _resourceDetail(Map<String, dynamic> item, String kind, bool isAvailable) {
    if (isAvailable) {
      final type = kind == 'driver' ? (item['workerType'] ?? '').toString() : (item['equipmentTypeName'] ?? '').toString();
      return type.isEmpty ? 'متاح الآن' : 'متاح الآن · $type';
    }
    return _unavailableReason(item, kind);
  }
}
