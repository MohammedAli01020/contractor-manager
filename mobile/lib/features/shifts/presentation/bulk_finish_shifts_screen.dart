import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../../core/di/injector.dart';
import '../../../core/error/app_failure.dart';
import '../data/datasources/shifts_api.dart';
import '../data/models/shift_model.dart';

class BulkFinishShiftsScreen extends StatefulWidget {
  const BulkFinishShiftsScreen({Key? key}) : super(key: key);

  @override
  State<BulkFinishShiftsScreen> createState() => _BulkFinishShiftsScreenState();
}

class _BulkFinishShiftsScreenState extends State<BulkFinishShiftsScreen> {
  List<ShiftModel> _shifts = <ShiftModel>[];
  final Set<int> _selectedIds = <int>{};
  bool _loading = true;
  bool _working = false;
  String? _error;
  final List<String> _results = <String>[];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _selectedIds.clear();
    });
    try {
      final items = await getIt<ShiftsApi>().fetchRunningShifts();
      if (!mounted) return;
      setState(() {
        _shifts = items.where((item) => item.status == 'IN_PROGRESS').toList();
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error is AppFailure
            ? error.message
            : 'تعذر تحميل الورديات النشطة. تحقق من الاتصال ثم أعد المحاولة.';
        _loading = false;
      });
    }
  }

  Future<void> _finishSelected() async {
    final selected = _shifts
        .where((shift) => _selectedIds.contains(shift.id))
        .toList();
    if (selected.isEmpty || _working) return;

    setState(() {
      _working = true;
      _results.clear();
    });

    var completed = 0;
    var skipped = 0;
    var failed = 0;

    for (final shift in selected) {
      if (!mounted) return;
      final confirm = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (dialogContext) => AlertDialog(
          title: Text('تأكيد إنهاء الوردية #${shift.id}'),
          content: Text(
            'السائق: ${shift.driverName}\n'
            'المعدة: ${_equipmentLabel(shift)}\n'
            'الموقع: ${shift.siteName}\n'
            'التاريخ: ${shift.shiftDate}\n\n'
            'هل تؤكد إنهاء هذه الوردية تحديدًا؟ لن يتم إنهاء أي وردية دون تأكيدك.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('تخطي الوردية'),
            ),
            ElevatedButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('تأكيد الإنهاء'),
            ),
          ],
        ),
      );

      if (confirm != true) {
        skipped++;
        _results.add('تم تخطي الوردية #${shift.id}.');
        if (mounted) setState(() {});
        continue;
      }

      if (shift.version == null) {
        failed++;
        _results.add('الوردية #${shift.id}: لا يوجد إصدار version صالح؛ لم يتم إنهاؤها.');
        if (mounted) setState(() {});
        continue;
      }

      try {
        await getIt<ShiftsApi>().finishShift(shift);
        completed++;
        _results.add('تم إنهاء الوردية #${shift.id} بنجاح.');
      } on DioError catch (error) {
        failed++;
        if (error.response?.statusCode == 409) {
          _results.add('الوردية #${shift.id}: تغيرت بياناتها أو حالتها؛ حدّث القائمة وراجعها قبل المحاولة.');
        } else if (error.response?.statusCode == 403) {
          _results.add('الوردية #${shift.id}: ليس لديك صلاحية إنهائها.');
        } else {
          final failure = AppFailure.fromDio(error);
          _results.add('الوردية #${shift.id}: ${failure.message}');
        }
      } catch (error) {
        failed++;
        _results.add('الوردية #${shift.id}: تعذر الإنهاء؛ ${error is AppFailure ? error.message : 'خطأ غير متوقع'}.');
      }
      if (mounted) setState(() {});
    }

    if (!mounted) return;
    setState(() => _working = false);
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('ملخص العملية'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('تم إنهاؤها: $completed'),
              Text('تم تخطيها: $skipped'),
              Text('تعذر إنهاؤها: $failed'),
              const Divider(),
              ..._results.map((item) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Text(item),
                  )),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('إغلاق'),
          ),
        ],
      ),
    );
    if (!mounted) return;
    await _load();
  }

  String _equipmentLabel(ShiftModel shift) {
    final parts = <String>[shift.equipmentCode, shift.equipmentName]
        .where((value) => value.trim().isNotEmpty)
        .toList();
    return parts.isEmpty ? 'غير محددة' : parts.join(' - ');
  }

  @override
  Widget build(BuildContext context) {
    final selectedCount = _selectedIds.length;
    return Scaffold(
      appBar: AppBar(
        title: const Text('مراجعة إنهاء الورديات'),
        actions: [
          IconButton(
            tooltip: 'تحديث القائمة',
            onPressed: _loading || _working ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.cloud_off_outlined, size: 44),
                        const SizedBox(height: 12),
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 12),
                        ElevatedButton(
                          onPressed: _load,
                          child: const Text('إعادة المحاولة'),
                        ),
                      ],
                    ),
                  ),
                )
              : _shifts.isEmpty
                  ? const Center(child: Text('لا توجد ورديات نشطة حاليًا.'))
                  : Column(
                      children: [
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(12),
                          color: Theme.of(context).colorScheme.surfaceVariant,
                          child: Text(
                            'تم تحميل ${_shifts.length} وردية نشطة. '
                            'لا يتم إنهاء أي وردية تلقائيًا؛ اختر الورديات ثم أكّد كل واحدة على حدة.',
                          ),
                        ),
                        Expanded(
                          child: ListView.separated(
                            padding: const EdgeInsets.all(12),
                            itemCount: _shifts.length,
                            separatorBuilder: (_, __) => const SizedBox(height: 8),
                            itemBuilder: (context, index) {
                              final shift = _shifts[index];
                              final selected = _selectedIds.contains(shift.id);
                              return Card(
                                child: CheckboxListTile(
                                  value: selected,
                                  onChanged: _working
                                      ? null
                                      : (checked) {
                                          setState(() {
                                            if (checked == true) {
                                              _selectedIds.add(shift.id);
                                            } else {
                                              _selectedIds.remove(shift.id);
                                            }
                                          });
                                        },
                                  controlAffinity: ListTileControlAffinity.leading,
                                  title: Text(
                                    'وردية #${shift.id} · ${shift.shiftTypeLabel}',
                                    style: const TextStyle(fontWeight: FontWeight.bold),
                                  ),
                                  subtitle: Padding(
                                    padding: const EdgeInsets.only(top: 6),
                                    child: Text(
                                      'السائق: ${shift.driverName}\n'
                                      'المعدة: ${_equipmentLabel(shift)}\n'
                                      'الموقع: ${shift.siteName}\n'
                                      'التاريخ: ${shift.shiftDate}\n'
                                      'الإصدار: ${shift.version ?? 'غير متاح'}',
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
                        ),
                        SafeArea(
                          top: false,
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: SizedBox(
                              width: double.infinity,
                              child: ElevatedButton.icon(
                                onPressed: selectedCount == 0 || _working
                                    ? null
                                    : _finishSelected,
                                icon: _working
                                    ? const SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(strokeWidth: 2),
                                      )
                                    : const Icon(Icons.check_circle_outline),
                                label: Text(_working
                                    ? 'جارٍ تنفيذ التأكيدات...'
                                    : 'مراجعة وإنهاء المحدد ($selectedCount)'),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
    );
  }
}
