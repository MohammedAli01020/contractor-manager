import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

class DriverEditDialog extends StatefulWidget {
  const DriverEditDialog({Key? key, this.driver}) : super(key: key);

  final Map<String, dynamic>? driver;

  @override
  State<DriverEditDialog> createState() => _DriverEditDialogState();
}

class _DriverEditDialogState extends State<DriverEditDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _phone;
  late final TextEditingController _nationalId;
  late final TextEditingController _morning;
  late final TextEditingController _evening;
  late final TextEditingController _night;
  late final TextEditingController _notes;
  late final TextEditingController _reason;

  late String _workerType;
  late String _availability;
  late bool _active;
  bool _saving = false;
  String? _error;

  Map<String, dynamic>? get _driver => widget.driver;

  String _valueFor(String key, {String fallback = '', bool empty = false}) {
    final value = _driver?[key];
    if (_driver == null || value == null) return empty ? '' : fallback;
    return value.toString();
  }

  @override
  void initState() {
    super.initState();
    _name = TextEditingController(text: _valueFor('name'));
    _phone = TextEditingController(text: _valueFor('phone', empty: true));
    _nationalId =
        TextEditingController(text: _valueFor('nationalId', empty: true));
    _morning = TextEditingController(
      text: _valueFor('morningRate', fallback: '0'),
    );
    _evening = TextEditingController(
      text: _valueFor('eveningRate', fallback: '0'),
    );
    _night = TextEditingController(
      text: _valueFor('nightRate', fallback: '0'),
    );
    _notes = TextEditingController(text: _valueFor('notes', empty: true));
    _reason = TextEditingController(
      text: _valueFor('unavailableReason', empty: true),
    );
    _workerType = _valueFor('workerType', fallback: 'DRIVER');
    _availability = _valueFor('availability', fallback: 'AVAILABLE');
    _active = _driver == null || _driver!['active'] != false;
  }

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _nationalId.dispose();
    _morning.dispose();
    _evening.dispose();
    _night.dispose();
    _notes.dispose();
    _reason.dispose();
    super.dispose();
  }

  String? _numberValidator(String? value) {
    if (value == null || value.trim().isEmpty) return 'القيمة مطلوبة';
    final number = double.tryParse(value.trim());
    if (number == null || number < 0) {
      return 'اكتب رقمًا صحيحًا غير سالب';
    }
    return null;
  }

  String _message(DioError error) {
    final data = error.response?.data;
    if (data is Map && data['message'] != null) {
      return data['message'].toString();
    }
    if (error.response?.statusCode == 401) {
      return 'انتهت الجلسة؛ سجّل الدخول مرة أخرى.';
    }
    if (error.response?.statusCode == 403) {
      return 'ليس لديك صلاحية لحفظ بيانات السائق.';
    }
    return 'تعذر حفظ البيانات. تأكد من الاتصال بالخادم وحاول مرة أخرى.';
  }

  Future<void> _save() async {
    if (_saving || !_formKey.currentState!.validate()) return;

    setState(() {
      _saving = true;
      _error = null;
    });

    final payload = <String, dynamic>{
      'name': _name.text.trim(),
      'phone': _phone.text.trim().isEmpty ? null : _phone.text.trim(),
      'nationalId':
          _nationalId.text.trim().isEmpty ? null : _nationalId.text.trim(),
      'workerType': _workerType,
      'morningRate': double.parse(_morning.text.trim()),
      'eveningRate': double.parse(_evening.text.trim()),
      'nightRate': double.parse(_night.text.trim()),
      'availability': _availability,
      'unavailableReason':
          _availability == 'UNAVAILABLE' && _reason.text.trim().isNotEmpty
              ? _reason.text.trim()
              : null,
      'notes': _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      'active': _active,
      if (_driver != null) 'version': _driver!['version'],
    };

    try {
      if (_driver == null) {
        await getIt<Dio>().post('/drivers', data: payload);
      } else {
        await getIt<Dio>().put(
          '/drivers/' + _driver!['id'].toString(),
          data: payload,
        );
      }

      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on DioError catch (error) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = _message(error);
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = 'تعذر الحفظ: $error';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(_driver == null ? 'إضافة سائق أو عامل' : 'تعديل البيانات'),
      content: SizedBox(
        width: 440,
        child: SingleChildScrollView(
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                TextFormField(
                  controller: _name,
                  decoration: const InputDecoration(labelText: 'الاسم *'),
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'الاسم مطلوب'
                      : null,
                ),
                TextFormField(
                  controller: _phone,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(labelText: 'الهاتف'),
                ),
                TextFormField(
                  controller: _nationalId,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'الرقم القومي'),
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  value: _workerType,
                  decoration: const InputDecoration(labelText: 'نوع الشخص'),
                  items: const <DropdownMenuItem<String>>[
                    DropdownMenuItem(value: 'DRIVER', child: Text('سائق')),
                    DropdownMenuItem(value: 'WORKER', child: Text('عامل')),
                  ],
                  onChanged: _saving
                      ? null
                      : (value) => setState(
                            () => _workerType = value ?? 'DRIVER',
                          ),
                ),
                TextFormField(
                  controller: _morning,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration:
                      const InputDecoration(labelText: 'أجر الوردية الصباحية *'),
                  validator: _numberValidator,
                ),
                TextFormField(
                  controller: _evening,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration:
                      const InputDecoration(labelText: 'أجر الوردية المسائية *'),
                  validator: _numberValidator,
                ),
                TextFormField(
                  controller: _night,
                  keyboardType:
                      const TextInputType.numberWithOptions(decimal: true),
                  decoration:
                      const InputDecoration(labelText: 'أجر الوردية الليلية *'),
                  validator: _numberValidator,
                ),
                DropdownButtonFormField<String>(
                  value: _availability,
                  decoration: const InputDecoration(labelText: 'التوافر'),
                  items: const <DropdownMenuItem<String>>[
                    DropdownMenuItem(value: 'AVAILABLE', child: Text('متاح')),
                    DropdownMenuItem(
                      value: 'UNAVAILABLE',
                      child: Text('غير متاح'),
                    ),
                  ],
                  onChanged: _saving
                      ? null
                      : (value) => setState(
                            () => _availability = value ?? 'AVAILABLE',
                          ),
                ),
                if (_availability == 'UNAVAILABLE')
                  TextFormField(
                    controller: _reason,
                    decoration:
                        const InputDecoration(labelText: 'سبب عدم التوافر'),
                  ),
                TextFormField(
                  controller: _notes,
                  maxLines: 2,
                  decoration: const InputDecoration(labelText: 'ملاحظات'),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('الحساب نشط'),
                  value: _active,
                  onChanged: _saving
                      ? null
                      : (value) => setState(() => _active = value),
                ),
                if (_error != null) ...<Widget>[
                  const SizedBox(height: 8),
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Text(
                      _error!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(false),
          child: const Text('إلغاء'),
        ),
        ElevatedButton(
          onPressed: _saving ? null : _save,
          child: Text(_saving ? 'جارٍ الحفظ...' : 'حفظ'),
        ),
      ],
    );
  }
}
