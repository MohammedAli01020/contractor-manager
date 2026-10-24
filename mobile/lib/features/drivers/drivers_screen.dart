import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

class DriversScreen extends StatefulWidget {
  const DriversScreen({Key? key}) : super(key: key);

  @override
  State<DriversScreen> createState() => _DriversScreenState();
}

class _DriversScreenState extends State<DriversScreen> {
  final _search = TextEditingController();
  List<Map<String, dynamic>> _items = <Map<String, dynamic>>[];
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

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final response = await getIt<Dio>().get<Map<String, dynamic>>(
        '/drivers',
        queryParameters: <String, dynamic>{
          'page': 0,
          'size': 100,
          'q': _search.text.trim().isEmpty ? null : _search.text.trim(),
          'includeInactive': _includeInactive,
        },
      );
      final data = response.data;
      if (data == null || data['items'] is! List) {
        throw Exception('استجابة قائمة السائقين غير صالحة.');
      }
      final list = (data['items'] as List)
          .whereType<Map<String, dynamic>>()
          .toList();
      if (mounted) setState(() => _items = list);
    } on DioError catch (e) {
      if (mounted) setState(() => _error = _message(e));
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _message(DioError e) {
    final data = e.response == null ? null : e.response!.data;
    if (data is Map && data['message'] != null) {
      return data['message'].toString();
    }
    if (e.response?.statusCode == 401) return 'انتهت الجلسة؛ سجّل الدخول مرة أخرى.';
    if (e.response?.statusCode == 403) return 'ليس لديك صلاحية لعرض السائقين.';
    return 'تعذر تحميل السائقين. تأكد من اتصال الخادم وحاول مرة أخرى.';
  }

  String _value(dynamic value) => value == null ? '—' : value.toString();

  Future<void> _edit([Map<String, dynamic>? driver]) async {
    final formKey = GlobalKey<FormState>();
    final name = TextEditingController(text: _valueFor(driver, 'name'));
    final phone = TextEditingController(text: _valueFor(driver, 'phone', empty: true));
    final nationalId = TextEditingController(text: _valueFor(driver, 'nationalId', empty: true));
    final morning = TextEditingController(text: _valueFor(driver, 'morningRate', fallback: '0'));
    final evening = TextEditingController(text: _valueFor(driver, 'eveningRate', fallback: '0'));
    final night = TextEditingController(text: _valueFor(driver, 'nightRate', fallback: '0'));
    final notes = TextEditingController(text: _valueFor(driver, 'notes', empty: true));
    String workerType = _valueFor(driver, 'workerType', fallback: 'DRIVER');
    String availability = _valueFor(driver, 'availability', fallback: 'AVAILABLE');
    bool active = driver == null ? true : driver['active'] != false;
    String? unavailableReason = _valueFor(driver, 'unavailableReason', empty: true);
    final reason = TextEditingController(text: unavailableReason ?? '');
    bool saving = false;

    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(driver == null ? 'إضافة سائق أو عامل' : 'تعديل البيانات'),
          content: SizedBox(
            width: 440,
            child: SingleChildScrollView(
              child: Form(
                key: formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    TextFormField(
                      controller: name,
                      decoration: const InputDecoration(labelText: 'الاسم *'),
                      validator: (v) => v == null || v.trim().isEmpty ? 'الاسم مطلوب' : null,
                    ),
                    TextFormField(controller: phone, keyboardType: TextInputType.phone, decoration: const InputDecoration(labelText: 'الهاتف')),
                    TextFormField(controller: nationalId, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'الرقم القومي')),
                    const SizedBox(height: 8),
                    DropdownButtonFormField<String>(
                      value: workerType,
                      decoration: const InputDecoration(labelText: 'نوع الشخص'),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem(value: 'DRIVER', child: Text('سائق')),
                        DropdownMenuItem(value: 'WORKER', child: Text('عامل')),
                      ],
                      onChanged: (v) => setDialogState(() => workerType = v ?? 'DRIVER'),
                    ),
                    TextFormField(controller: morning, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'أجر الوردية الصباحية *'), validator: _numberValidator),
                    TextFormField(controller: evening, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'أجر الوردية المسائية *'), validator: _numberValidator),
                    TextFormField(controller: night, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'أجر الوردية الليلية *'), validator: _numberValidator),
                    DropdownButtonFormField<String>(
                      value: availability,
                      decoration: const InputDecoration(labelText: 'التوافر'),
                      items: const <DropdownMenuItem<String>>[
                        DropdownMenuItem(value: 'AVAILABLE', child: Text('متاح')),
                        DropdownMenuItem(value: 'UNAVAILABLE', child: Text('غير متاح')),
                      ],
                      onChanged: (v) => setDialogState(() => availability = v ?? 'AVAILABLE'),
                    ),
                    if (availability == 'UNAVAILABLE')
                      TextFormField(controller: reason, decoration: const InputDecoration(labelText: 'سبب عدم التوافر')),
                    TextFormField(controller: notes, maxLines: 2, decoration: const InputDecoration(labelText: 'ملاحظات')),
                    SwitchListTile(
                      contentPadding: EdgeInsets.zero,
                      title: const Text('الحساب نشط'),
                      value: active,
                      onChanged: (v) => setDialogState(() => active = v),
                    ),
                  ],
                ),
              ),
            ),
          ),
          actions: <Widget>[
            TextButton(onPressed: saving ? null : () => Navigator.of(dialogContext).pop(), child: const Text('إلغاء')),
            ElevatedButton(
              onPressed: saving ? null : () async {
                if (!formKey.currentState!.validate()) return;
                setDialogState(() => saving = true);
                final payload = <String, dynamic>{
                  'name': name.text.trim(),
                  'phone': phone.text.trim().isEmpty ? null : phone.text.trim(),
                  'nationalId': nationalId.text.trim().isEmpty ? null : nationalId.text.trim(),
                  'workerType': workerType,
                  'morningRate': double.parse(morning.text.trim()),
                  'eveningRate': double.parse(evening.text.trim()),
                  'nightRate': double.parse(night.text.trim()),
                  'availability': availability,
                  'unavailableReason': availability == 'UNAVAILABLE' && reason.text.trim().isNotEmpty ? reason.text.trim() : null,
                  'notes': notes.text.trim().isEmpty ? null : notes.text.trim(),
                  'active': active,
                  if (driver != null) 'version': driver['version'],
                };
                try {
                  if (driver == null) {
                    await getIt<Dio>().post('/drivers', data: payload);
                  } else {
                    await getIt<Dio>().put('/drivers/' + driver['id'].toString(), data: payload);
                  }
                  if (mounted) Navigator.of(dialogContext).pop();
                  await _load();
                  if (mounted) ScaffoldMessenger.of(this.context).showSnackBar(
                    SnackBar(content: Text(driver == null ? 'تمت إضافة البيانات' : 'تم حفظ التعديلات')),
                  );
                } on DioError catch (e) {
                  setDialogState(() => saving = false);
                  final msg = _message(e);
                  if (mounted) ScaffoldMessenger.of(this.context).showSnackBar(SnackBar(content: Text(msg)));
                } catch (e) {
                  setDialogState(() => saving = false);
                  if (mounted) ScaffoldMessenger.of(this.context).showSnackBar(SnackBar(content: Text('تعذر الحفظ: ' + e.toString())));
                }
              },
              child: Text(saving ? 'جارٍ الحفظ...' : 'حفظ'),
            ),
          ],
        ),
      ),
    );
    name.dispose();
    phone.dispose();
    nationalId.dispose();
    morning.dispose();
    evening.dispose();
    night.dispose();
    notes.dispose();
    reason.dispose();
  }

  String _valueFor(Map<String, dynamic>? item, String key, {String? fallback, bool empty = false}) {
    if (item == null || item[key] == null) return empty ? '' : (fallback ?? '');
    return item[key].toString();
  }

  String? _numberValidator(String? value) {
    if (value == null || value.trim().isEmpty) return 'القيمة مطلوبة';
    final number = double.tryParse(value.trim());
    if (number == null || number < 0) return 'اكتب رقمًا صحيحًا غير سالب';
    return null;
  }

  Future<void> _deactivate(Map<String, dynamic> driver) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('تعطيل الحساب'),
        content: Text('هل تريد تعطيل ' + _value(driver['name']) + '؟ لن يتم حذف السجل نهائيًا.'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('رجوع')),
          ElevatedButton(onPressed: () => Navigator.pop(context, true), child: const Text('تعطيل')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await getIt<Dio>().delete('/drivers/' + driver['id'].toString());
      await _load();
    } on DioError catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(_message(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('السائقون والعمال'),
        actions: <Widget>[
          IconButton(tooltip: 'تحديث', icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => _edit(),
        child: const Icon(Icons.person_add_alt_1),
        tooltip: 'إضافة سائق أو عامل',
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
                suffixIcon: IconButton(icon: const Icon(Icons.search), onPressed: _load),
                border: const OutlineInputBorder(),
              ),
            ),
          ),
          SwitchListTile(
            title: const Text('عرض الحسابات المعطلة أيضًا'),
            value: _includeInactive,
            onChanged: (v) { setState(() => _includeInactive = v); _load(); },
          ),
          if (_loading) const LinearProgressIndicator(),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: <Widget>[
                  Text(_error!, textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ElevatedButton(onPressed: _load, child: const Text('إعادة المحاولة')),
                ],
              ),
            ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? const SizedBox.shrink()
                    : _items.isEmpty
                        ? const Center(child: Text('لا توجد بيانات مطابقة للبحث.'))
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(bottom: 88),
                              itemCount: _items.length,
                              itemBuilder: (context, index) {
                                final item = _items[index];
                                final isDriver = item['workerType'] == 'DRIVER';
                                final available = item['availability'] == 'AVAILABLE';
                                final active = item['active'] != false;
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                                  child: ListTile(
                                    leading: CircleAvatar(child: Icon(isDriver ? Icons.drive_eta : Icons.engineering)),
                                    title: Text(_value(item['name']), style: const TextStyle(fontWeight: FontWeight.bold)),
                                    subtitle: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: <Widget>[
                                        Text((isDriver ? 'سائق' : 'عامل') + ' • هاتف: ' + _value(item['phone'])),
                                        Text('صباحي: ' + _value(item['morningRate']) + ' | مسائي: ' + _value(item['eveningRate']) + ' | ليلي: ' + _value(item['nightRate'])),
                                        Text(!active ? 'الحساب معطل' : (available ? 'متاح' : 'غير متاح: ' + _value(item['unavailableReason']))),
                                      ],
                                    ),
                                    isThreeLine: true,
                                    trailing: PopupMenuButton<String>(
                                      onSelected: (action) {
                                        if (action == 'edit') _edit(item);
                                        if (action == 'deactivate') _deactivate(item);
                                      },
                                      itemBuilder: (context) => <PopupMenuEntry<String>>[
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
