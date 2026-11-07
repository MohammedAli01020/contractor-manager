import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

/// Canonical person identity form. Job capabilities and assignments are managed
/// separately so creating a person never creates a duplicate operational identity.
class WorkforceMemberFormDialog extends StatefulWidget {
  const WorkforceMemberFormDialog({Key? key, this.initialMember}) : super(key: key);

  final Map<String, dynamic>? initialMember;

  @override
  State<WorkforceMemberFormDialog> createState() => _WorkforceMemberFormDialogState();
}

class _WorkforceMemberFormDialogState extends State<WorkforceMemberFormDialog> {
  final _formKey = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _phone = TextEditingController();
  final _nationalId = TextEditingController();
  final _notes = TextEditingController();
  bool _saving = false;
  String? _error;

  bool get _editing => widget.initialMember != null;

  @override
  void initState() {
    super.initState();
    final member = widget.initialMember;
    if (member != null) {
      _name.text = member['fullName']?.toString() ?? '';
      _phone.text = member['phone']?.toString() ?? '';
      _nationalId.text = member['nationalId']?.toString() ?? '';
      _notes.text = member['notes']?.toString() ?? '';
    }
  }

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    _nationalId.dispose();
    _notes.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() { _saving = true; _error = null; });
    try {
      final member = widget.initialMember;
      final payload = <String, dynamic>{
          'fullName': _name.text.trim(),
          'phone': _phone.text.trim().isEmpty ? null : _phone.text.trim(),
          'nationalId': _nationalId.text.trim().isEmpty ? null : _nationalId.text.trim(),
          'notes': _notes.text.trim().isEmpty ? null : _notes.text.trim(),
          if (_editing) 'version': member!['version'],
        };
      final response = _editing
          ? await getIt<Dio>().put<Map<String, dynamic>>(
              '/workforce/members/${member!['id']}', data: payload)
          : await getIt<Dio>().post<Map<String, dynamic>>(
              '/workforce/members', data: payload);
      if (!mounted) return;
      Navigator.of(context).pop(response.data);
    } catch (error) {
      var message = _editing
          ? 'تعذر حفظ تعديلات الشخص. راجع البيانات والاتصال وحاول مرة أخرى.'
          : 'تعذر إضافة الشخص. راجع البيانات والاتصال وحاول مرة أخرى.';
      if (error is DioError) {
        final data = error.response?.data;
        if (data is Map && data['message'] != null) {
          message = data['message'].toString();
        } else if (error.response?.statusCode == 403) {
          message = 'إدارة بيانات أفراد القوى العاملة متاحة للمسؤول فقط.';
        } else if (error.response?.statusCode == 409) {
          message = 'تم تعديل بيانات الشخص من مكان آخر. أغلق النافذة وأعد فتحها لتحميل أحدث نسخة.';
        }
      }
      if (mounted) setState(() => _error = message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(_editing ? 'تعديل بيانات الفرد' : 'إضافة فرد للقوى العاملة'),
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
                  autofocus: !_editing,
                  maxLength: 150,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'الاسم بالكامل *',
                    prefixIcon: Icon(Icons.person_outline),
                  ),
                  validator: (value) => value == null || value.trim().isEmpty
                      ? 'اسم الشخص مطلوب'
                      : null,
                ),
                TextFormField(
                  controller: _phone,
                  maxLength: 30,
                  keyboardType: TextInputType.phone,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'رقم الهاتف',
                    prefixIcon: Icon(Icons.phone_outlined),
                  ),
                ),
                TextFormField(
                  controller: _nationalId,
                  maxLength: 30,
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.next,
                  decoration: const InputDecoration(
                    labelText: 'الرقم القومي (اختياري)',
                    prefixIcon: Icon(Icons.badge_outlined),
                  ),
                ),
                TextFormField(
                  controller: _notes,
                  maxLength: 500,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'ملاحظات',
                    prefixIcon: Icon(Icons.notes_outlined),
                  ),
                ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      _error!,
                      style: TextStyle(color: Theme.of(context).colorScheme.error),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: _saving ? null : () => Navigator.of(context).pop(),
          child: const Text('إلغاء'),
        ),
        ElevatedButton(
          onPressed: _saving ? null : _save,
          child: _saving
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(_editing ? 'حفظ التعديلات' : 'حفظ'),
        ),
      ],
    );
  }
}
