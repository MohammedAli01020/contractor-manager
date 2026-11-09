import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../core/di/injector.dart';

/// Tenant-admin controls for defining which Workforce roles may receive shifts.
class WorkforceRoleManagementScreen extends StatefulWidget {
  const WorkforceRoleManagementScreen({Key? key}) : super(key: key);

  @override
  State<WorkforceRoleManagementScreen> createState() =>
      _WorkforceRoleManagementScreenState();
}

class _WorkforceRoleManagementScreenState
    extends State<WorkforceRoleManagementScreen> {
  List<Map<String, dynamic>> _roles = <Map<String, dynamic>>[];
  bool _loading = true;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  String _message(Object error) {
    if (error is DioError) {
      final data = error.response?.data;
      if (data is Map && data['message'] != null) return data['message'].toString();
      if (error.response?.statusCode == 409) {
        return 'تم تعديل الدور من مستخدم آخر. حدّث القائمة ثم أعد المحاولة.';
      }
      if (error.response?.statusCode == 403) return 'إدارة الأدوار متاحة للمسؤول فقط.';
    }
    return 'تعذر تنفيذ العملية. تحقق من الاتصال وحاول مرة أخرى.';
  }

  Future<void> _load() async {
    if (!mounted) return;
    setState(() { _loading = true; _error = null; });
    try {
      final response = await getIt<Dio>().get<List<dynamic>>(
        '/workforce/roles?includeInactive=true',
      );
      final rows = response.data ?? <dynamic>[];
      if (mounted) {
        setState(() => _roles = rows.whereType<Map<String, dynamic>>().toList());
      }
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _setEligibility(Map<String, dynamic> role, bool enabled) async {
    if (_saving) return;
    final id = role['id'];
    final version = role['version'];
    if (id is! num || version is! num) {
      setState(() => _error = 'بيانات الدور غير مكتملة؛ حدّث القائمة.');
      return;
    }
    setState(() { _saving = true; _error = null; });
    try {
      final response = await getIt<Dio>().put<Map<String, dynamic>>(
        '/workforce/roles/${id.toInt()}/shift-eligibility',
        data: <String, dynamic>{
          'shiftEligible': enabled,
          'version': version.toInt(),
        },
      );
      final updated = response.data;
      if (updated != null && mounted) {
        setState(() {
          final index = _roles.indexWhere((item) => item['id'] == id);
          if (index >= 0) _roles[index] = updated;
        });
      } else {
        await _load();
      }
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
      await _load();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _createRole() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (_) => const _CreateWorkforceRoleDialog(),
    );
    if (result == null || !mounted) return;
    setState(() { _saving = true; _error = null; });
    try {
      await getIt<Dio>().post<Map<String, dynamic>>('/workforce/roles', data: result);
      await _load();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('تم إنشاء الدور الوظيفي.')),
      );
    } catch (error) {
      if (mounted) setState(() => _error = _message(error));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final activeRoles = _roles.where((role) => role['active'] == true).toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('إعداد أدوار الورديات'),
        actions: <Widget>[
          IconButton(
            tooltip: 'تحديث',
            onPressed: _loading || _saving ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _loading || _saving ? null : _createRole,
        icon: const Icon(Icons.add),
        label: const Text('إضافة دور'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: <Widget>[
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Row(
                        children: <Widget>[
                          const Icon(Icons.info_outline),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              'فعّل الخيار للدور الذي يمكن لصاحبه استلام ورديات. '
                              'هذا الإعداد مستقل عن المسمى الوظيفي وطريقة حساب الأجر.',
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(_error!,
                        style: TextStyle(color: Theme.of(context).colorScheme.error)),
                  ),
                if (_saving) const LinearProgressIndicator(),
                Expanded(
                  child: activeRoles.isEmpty
                      ? const Center(child: Text('لا توجد أدوار وظيفية نشطة.'))
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: ListView.separated(
                            padding: const EdgeInsets.only(bottom: 88),
                            itemCount: activeRoles.length,
                            separatorBuilder: (_, __) => const Divider(height: 1),
                            itemBuilder: (context, index) {
                              final role = activeRoles[index];
                              final eligible = role['shiftEligible'] == true;
                              final code = role['code']?.toString() ?? '';
                              return SwitchListTile(
                                value: eligible,
                                onChanged: _saving
                                    ? null
                                    : (value) => _setEligibility(role, value),
                                title: Text(
                                  role['name']?.toString() ?? code,
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                                subtitle: Text(
                                  '$code • ${role['systemDefined'] == true ? 'دور أساسي' : 'دور مخصص'}'
                                  '
${eligible ? 'يمكنه استلام ورديات' : 'لا يستلم ورديات'}',
                                ),
                                isThreeLine: true,
                                secondary: const Icon(Icons.badge_outlined),
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

class _CreateWorkforceRoleDialog extends StatefulWidget {
  const _CreateWorkforceRoleDialog({Key? key}) : super(key: key);

  @override
  State<_CreateWorkforceRoleDialog> createState() =>
      _CreateWorkforceRoleDialogState();
}

class _CreateWorkforceRoleDialogState extends State<_CreateWorkforceRoleDialog> {
  final _formKey = GlobalKey<FormState>();
  final _code = TextEditingController();
  final _name = TextEditingController();
  bool _shiftEligible = false;

  @override
  void dispose() {
    _code.dispose();
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('إضافة دور وظيفي'),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              TextFormField(
                controller: _name,
                decoration: const InputDecoration(labelText: 'اسم الدور *'),
                validator: (value) => value == null || value.trim().isEmpty
                    ? 'اسم الدور مطلوب.'
                    : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _code,
                textCapitalization: TextCapitalization.characters,
                decoration: const InputDecoration(
                  labelText: 'كود داخلي بالإنجليزية *',
                  helperText: 'حروف وأرقام وشرطة سفلية فقط.',
                ),
                validator: (value) {
                  final code = (value ?? '').trim();
                  return RegExp(r'^[A-Za-z][A-Za-z0-9_]{1,49}$').hasMatch(code)
                      ? null
                      : 'الكود من حرفين إلى 50 حرفًا/رقمًا.';
                },
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                value: _shiftEligible,
                onChanged: (value) => setState(() => _shiftEligible = value),
                title: const Text('يمكنه استلام ورديات'),
              ),
            ],
          ),
        ),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('إلغاء'),
        ),
        ElevatedButton(
          onPressed: () {
            if (!_formKey.currentState!.validate()) return;
            Navigator.of(context).pop(<String, dynamic>{
              'code': _code.text.trim().toUpperCase(),
              'name': _name.text.trim(),
              'shiftEligible': _shiftEligible,
            });
          },
          child: const Text('إنشاء الدور'),
        ),
      ],
    );
  }
}
