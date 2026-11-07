import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

/// Canonical identity form. New people are onboarded with one or more role
/// assignments in the same backend transaction; edits remain identity-only.
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
  final List<Map<String, dynamic>> _roles = <Map<String, dynamic>>[];
  final Set<int> _selectedRoleIds = <int>{};
  bool _saving = false;
  bool _loadingRoles = false;
  String? _error;
  DateTime _effectiveFrom = DateTime.now();

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
    } else {
      _loadRoles();
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

  String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

  Future<void> _loadRoles() async {
    setState(() { _loadingRoles = true; _error = null; });
    try {
      final response = await getIt<Dio>().get<List<dynamic>>('/workforce/roles');
      final items = response.data ?? <dynamic>[];
      final roles = items.whereType<Map<String, dynamic>>()
          .where((role) => role['active'] != false && role['id'] is num)
          .toList();
      if (!mounted) return;
      setState(() {
        _roles
          ..clear()
          ..addAll(roles);
        if (_roles.isEmpty) _error = 'لا توجد أدوار وظيفية نشطة. راجع إعدادات القوى العاملة قبل إضافة أفراد.';
      });
    } catch (error) {
      if (mounted) setState(() => _error = _errorMessage(error));
    } finally {
      if (mounted) setState(() => _loadingRoles = false);
    }
  }

  String _errorMessage(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 403) return 'عرض الأدوار الوظيفية غير متاح لهذا الحساب.';
    }
    return 'تعذر تحميل الأدوار الوظيفية. تحقق من الاتصال ثم أعد المحاولة.';
  }

  Future<void> _pickEffectiveDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _effectiveFrom,
      firstDate: DateTime(2020),
      lastDate: DateTime(now.year + 10),
      helpText: 'بداية التكليف الوظيفي',
    );
    if (picked != null && mounted) setState(() => _effectiveFrom = picked);
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    if (!_editing && _selectedRoleIds.isEmpty) {
      setState(() => _error = 'اختر دورًا وظيفيًا واحدًا على الأقل قبل الحفظ.');
      return;
    }
    if (!_editing && _roles.isEmpty) {
      setState(() => _error = 'لا يمكن إضافة الشخص دون دور وظيفي نشط.');
      return;
    }

    setState(() { _saving = true; _error = null; });
    try {
      final member = widget.initialMember;
      final identity = <String, dynamic>{
        'fullName': _name.text.trim(),
        'phone': _phone.text.trim().isEmpty ? null : _phone.text.trim(),
        'nationalId': _nationalId.text.trim().isEmpty ? null : _nationalId.text.trim(),
        'notes': _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      };
      final response = _editing
          ? await getIt<Dio>().put<Map<String, dynamic>>(
              '/workforce/members/${member!['id']}',
              data: <String, dynamic>{...identity, 'version': member['version']},
            )
          : await getIt<Dio>().post<Map<String, dynamic>>(
              '/workforce/members/onboard',
              data: <String, dynamic>{
                'member': identity,
                'initialAssignments': _selectedRoleIds.map((roleId) => <String, dynamic>{
                  'roleId': roleId,
                  'effectiveFrom': _date(_effectiveFrom),
                }).toList(),
              },
            );
      if (!mounted) return;
      Navigator.of(context).pop(response.data);
    } catch (error) {
      var message = _editing
          ? 'تعذر حفظ تعديلات الشخص. راجع البيانات والاتصال وحاول مرة أخرى.'
          : 'تعذر إضافة الشخص وتكليفاته. لم يتم تأكيد الحفظ؛ راجع البيانات وحاول مرة أخرى.';
      if (error is DioError) {
        final data = error.response?.data;
        if (data is Map && data['message'] != null) {
          message = data['message'].toString();
        } else if (error.response?.statusCode == 403) {
          message = 'إدارة بيانات أفراد القوى العاملة متاحة للمسؤول فقط.';
        } else if (error.response?.statusCode == 409) {
          message = 'يوجد تعارض في التكليفات. حدّث البيانات وحاول مرة أخرى.';
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
                if (!_editing) ...<Widget>[
                  const SizedBox(height: 12),
                  const Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Text('الأدوار الوظيفية *', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  const Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Text('يمكن اختيار أكثر من دور للشخص نفسه.'),
                  ),
                  if (_loadingRoles)
                    const Padding(
                      padding: EdgeInsets.all(12),
                      child: CircularProgressIndicator(),
                    )
                  else
                    ..._roles.map((role) {
                      final rawId = role['id'] as num;
                      final id = rawId.toInt();
                      return CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        value: _selectedRoleIds.contains(id),
                        title: Text(role['name']?.toString() ?? role['code']?.toString() ?? 'دور وظيفي'),
                        subtitle: role['code'] == null ? null : Text(role['code'].toString()),
                        onChanged: _saving ? null : (selected) {
                          setState(() {
                            if (selected == true) {
                              _selectedRoleIds.add(id);
                            } else {
                              _selectedRoleIds.remove(id);
                            }
                          });
                        },
                      );
                    }),
                  const SizedBox(height: 8),
                  InkWell(
                    onTap: _saving ? null : _pickEffectiveDate,
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
          onPressed: _saving || _loadingRoles ? null : _save,
          child: _saving
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : Text(_editing ? 'حفظ التعديلات' : 'حفظ الفرد والأدوار'),
        ),
      ],
    );
  }
}
