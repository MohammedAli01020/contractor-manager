import 'package:dio/dio.dart';
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
  bool _contractualWage = false;
  bool _hourlyCompensation = false;
  bool _quantityCompensation = false;
  String _compensationUnitCode = '';
  int _defaultsRequestId = 0;
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
    _hourlyCompensation = shift?.compensationBasis == 'HOUR';
    _quantityCompensation = shift?.compensationBasis == 'QUANTITY';
    _compensationUnitCode = shift?.compensationUnitCode ?? '';
    _wage.text = shift == null
        ? ''
        : (_hourlyCompensation || _quantityCompensation
            ? shift.compensationRate.toStringAsFixed(2)
            : shift.driverWage.toStringAsFixed(2));
    _contractualWage = shift?.wageSource == 'AGREEMENT';
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
        _api.fetchWorkforceCandidates(shiftDate: _date, siteId: _siteId),
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


  Future<void> _loadWorkforceCandidates() async {
    try {
      final candidates = await _api.fetchWorkforceCandidates(
        shiftDate: _date,
        siteId: _siteId,
      );
      // Editing an existing shift must remain possible even if its historical
      // assignment has since ended or the old record predates Workforce roles.
      if (widget.shift != null && _driverId != null &&
          !candidates.any((item) => (item['id'] as num?)?.toInt() == _driverId)) {
        final legacy = await _api.fetchLiveDrivers();
        final current = legacy.where(
          (item) => (item['id'] as num?)?.toInt() == _driverId,
        );
        candidates.addAll(current);
      }
      if (!mounted) return;
      setState(() {
        _drivers = candidates;
        if (_driverId != null &&
            !candidates.any((item) => (item['id'] as num?)?.toInt() == _driverId)) {
          _driverId = null;
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = _message(e));
    }
  }

  Future<void> _loadDefaults() async {
    final requestId = ++_defaultsRequestId;
    if (_driverId == null && _equipmentId == null) return;
    try {
      final data = await _api.fetchDefaults(
        driverId: _driverId,
        equipmentId: _equipmentId,
        siteId: _siteId,
        shiftDate: _date,
        shiftType: _type,
      );
      if (!mounted || requestId != _defaultsRequestId) return;
      setState(() {
        _contractualWage = data['wageSource'] == 'AGREEMENT';
        _hourlyCompensation = data['compensationBasis'] == 'HOUR';
        _quantityCompensation = data['compensationBasis'] == 'QUANTITY';
        _compensationUnitCode = (data['compensationUnitCode'] ?? '').toString();
        if (_driverId != null) {
          final suggestedWage = (_hourlyCompensation || _quantityCompensation)
              ? data['compensationRate']
              : data['driverWage'];
          if (suggestedWage != null) {
            _wage.text = _value(suggestedWage);
          } else {
            _wage.clear();
          }
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

  bool get _isZeroWage => double.tryParse(_wage.text.trim()) == 0;

  Future<bool> _confirmZeroWage(String action) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('تنبيه: أجر الوردية صفر'),
        content: Text(
          '${_quantityCompensation
              ? 'سعر وحدة الاتفاقية صفر جنيه؛ وبالتالي سيكون إجمالي الأجر صفرًا مهما كانت الكمية. '
              : _hourlyCompensation
                  ? 'سعر الساعة في الاتفاقية صفر جنيه؛ وبالتالي سيكون إجمالي الأجر صفرًا مهما كانت ساعات العمل. '
                  : 'أجر السائق لهذه الوردية 0.00 جنيه. '}'
          '$action بهذه القيمة؟ عند إنهاء الوردية لن يُسجل قيد أجر أو استحقاق، '
          'ولن تُسترد أي سلفة من أجر هذه الوردية.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('مراجعة الأجر'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('تأكيد والمتابعة'),
          ),
        ],
      ),
    );
    return confirmed == true;
  }

  String _message(Object error) {
    if (error is AppFailure) return error.message;
    if (error is DioError) return AppFailure.fromDio(error).message;
    return 'تعذر تنفيذ الطلب. حدث خطأ غير متوقع.';
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
    if (_isZeroWage) {
      final confirmed = await _confirmZeroWage(
        widget.shift == null ? 'هل تريد بدء الوردية' : 'هل تريد حفظ الوردية',
      );
      if (!mounted || !confirmed) return;
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
        if (!_hourlyCompensation && !_quantityCompensation)
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
                        onChanged: (v) {
                          setState(() { _siteId = v; _defaultsLoaded = false; });
                          _loadWorkforceCandidates();
                          _loadDefaults();
                        },
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
                        readOnly: _contractualWage,
                        decoration: InputDecoration(
                          labelText: _hourlyCompensation
                              ? 'سعر الساعة بالجنيه'
                              : _quantityCompensation
                                  ? 'سعر الوحدة بالجنيه'
                                  : 'أجر السائق بالجنيه',
                          helperText: _hourlyCompensation
                              ? 'سعر الساعة من الاتفاقية المعتمدة؛ أدخل الساعات الفعلية عند إنهاء الوردية'
                              : _quantityCompensation
                                  ? 'سعر الوحدة (${_compensationUnitCode.isEmpty ? 'وحدة الاتفاقية' : _compensationUnitCode}) من الاتفاقية؛ أدخل الكمية الفعلية عند إنهاء الوردية'
                                  : _contractualWage
                                      ? 'أجر الاتفاقية المعتمدة؛ لا يمكن تغييره من نموذج الوردية'
                                      : 'يتحدد تلقائيًا عند اختيار الموقع والسائق ونوع الوردية، ويمكن تعديله للعامل بنظام الأجر القديم',
                        ),
                        validator: _moneyValidator,
                        onChanged: (_) => setState(() {}),
                      ),
                      if (_isZeroWage) ...[
                        const SizedBox(height: 8),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: Colors.orange.shade50,
                            border: Border.all(color: Colors.orange.shade300),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(Icons.warning_amber_rounded, color: Colors.orange.shade900),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  'تنبيه: أجر هذه الوردية صفر. تأكد أن ذلك مقصود؛ لن يُسجل أجر أو تُخصم سلفة من هذه الوردية عند إنهائها.',
                                  style: TextStyle(color: Colors.orange.shade900, fontWeight: FontWeight.w600),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
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
      _defaultsLoaded = false;
    });
    _loadWorkforceCandidates();
    _loadDefaults();
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
