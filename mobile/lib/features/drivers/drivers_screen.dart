import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';
import 'driver_edit_dialog.dart';

class DriversScreen extends StatefulWidget {
  const DriversScreen({Key? key}) : super(key: key);

  @override
  State<DriversScreen> createState() => _DriversScreenState();
}

class _DriversScreenState extends State<DriversScreen> {
  final _search = TextEditingController();
  List<Map<String, dynamic>> _items = <Map<String, dynamic>>[];
  final Set<int> _runningDriverIds = <int>{};
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
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final responses = await Future.wait([
        getIt<Dio>().get<Map<String, dynamic>>(
          '/drivers',
          queryParameters: <String, dynamic>{
            'page': 0,
            'size': 100,
            'q': _search.text.trim().isEmpty ? null : _search.text.trim(),
            'includeInactive': _includeInactive,
          },
        ),
        getIt<Dio>().get<List<dynamic>>('/shifts/running'),
      ]);
      final response = responses[0];
      final running = responses[1].data as List<dynamic>? ?? <dynamic>[];
      final runningIds = running
          .whereType<Map<String, dynamic>>()
          .map((shift) => shift['driverId'] is int
              ? shift['driverId'] as int
              : int.tryParse('${shift['driverId']}'))
          .whereType<int>()
          .toSet();
      final data = response.data as Map<String, dynamic>?;
      if (data == null || data['items'] is! List) {
        throw Exception('استجابة قائمة السائقين غير صالحة.');
      }
      final list = (data['items'] as List)
          .whereType<Map<String, dynamic>>()
          .toList();
      if (mounted) setState(() {
        _items = list;
        _runningDriverIds..clear()..addAll(runningIds);
      });
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
    final saved = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => DriverEditDialog(driver: driver),
    );
    if (saved != true || !mounted) return;

    await _load();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(driver == null ? 'تمت إضافة البيانات' : 'تم حفظ التعديلات'),
      ),
    );
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
                                final rawId = item['id'];
                                final id = rawId is int ? rawId : int.tryParse('$rawId');
                                final running = id != null && _runningDriverIds.contains(id);
                                final stateColor = !active
                                    ? Colors.grey
                                    : running
                                        ? Colors.green.shade700
                                        : available
                                            ? Colors.blue.shade700
                                            : Colors.orange.shade800;
                                final stateLabel = !active
                                    ? 'الحساب معطّل'
                                    : running
                                        ? 'شغال الآن'
                                        : available
                                            ? 'متاح'
                                            : 'غير متاح: ' + _value(item['unavailableReason']);
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                                  child: ListTile(
                                    leading: CircleAvatar(
                                      backgroundColor: stateColor.withOpacity(0.12),
                                      child: Icon(
                                        isDriver ? Icons.person_outline : Icons.engineering_outlined,
                                        color: stateColor,
                                      ),
                                    ),
                                    title: Text(_value(item['name']), style: const TextStyle(fontWeight: FontWeight.bold)),
                                    subtitle: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: <Widget>[
                                        Text((isDriver ? 'سائق' : 'عامل') + ' • هاتف: ' + _value(item['phone'])),
                                        Text('صباحي: ' + _value(item['morningRate']) + ' | مسائي: ' + _value(item['eveningRate']) + ' | ليلي: ' + _value(item['nightRate'])),
                                        Text(
                                          stateLabel,
                                          style: TextStyle(
                                            color: stateColor,
                                            fontWeight: FontWeight.w700,
                                          ),
                                        ),
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
