import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/di/injector.dart';
import '../../../core/error/app_failure.dart';
import '../../../core/widgets/loading_view.dart';
import '../data/datasources/shifts_api.dart';
import 'shift_editor_screen.dart';
import 'bulk_finish_shifts_screen.dart';
import '../data/models/shift_model.dart';
import 'bloc/shifts_bloc.dart';

class ShiftsScreen extends StatelessWidget {
  const ShiftsScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return BlocProvider<ShiftsBloc>(
      create: (_) => getIt<ShiftsBloc>()..add(const ShiftsRequested()),
      child: const _ShiftsView(),
    );
  }
}

class _ShiftsView extends StatelessWidget {
  const _ShiftsView();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('الورديات')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final saved = await Navigator.of(context).push<bool>(
            MaterialPageRoute(builder: (_) => const ShiftEditorScreen()),
          );
          if (saved == true) {
            context.read<ShiftsBloc>().add(const ShiftsRequested());
          }
        },
        icon: const Icon(Icons.add),
        label: const Text('بدء وردية'),
      ),
      body: BlocBuilder<ShiftsBloc, ShiftsState>(
        builder: (context, state) {
          if (state is ShiftsInitial || state is ShiftsLoading) {
            return const Center(child: LoadingView());
          }
          if (state is ShiftsFailed) {
            return _MessageView(
              icon: Icons.cloud_off_outlined,
              message: state.message,
              buttonText: 'إعادة المحاولة',
              onPressed: () =>
                  context.read<ShiftsBloc>().add(const ShiftsRequested()),
            );
          }
          if (state is! ShiftsLoaded) return const SizedBox.shrink();

          final bloc = context.read<ShiftsBloc>();
          return Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  children: [
                    _TabChip(
                      label: 'الجارية الآن (${state.runningItems.length})',
                      icon: Icons.play_circle_outline,
                      selected: state.selectedTab == ShiftListTab.running,
                      onTap: () => bloc.add(
                        const ShiftTabChanged(ShiftListTab.running),
                      ),
                    ),
                    _TabChip(
                      label: 'ورديات اليوم (${state.todayTotalCount})',
                      icon: Icons.today_outlined,
                      selected: state.selectedTab == ShiftListTab.today,
                      onTap: () => bloc.add(
                        const ShiftTabChanged(ShiftListTab.today),
                      ),
                    ),
                    _TabChip(
                      label: 'كل الورديات (${state.allTotalCount})',
                      icon: Icons.history,
                      selected: state.selectedTab == ShiftListTab.all,
                      onTap: () =>
                          bloc.add(const ShiftTabChanged(ShiftListTab.all)),
                    ),
                  ],
                ),
              ),
              if (state.selectedTab == ShiftListTab.running)
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 4, 12, 4),
                  child: _RunningSummary(items: state.runningItems),
                ),
              if (state.selectedTab == ShiftListTab.running)
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
                  child: SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: () async {
                        await Navigator.of(context).push<void>(
                          MaterialPageRoute(
                            builder: (_) => const BulkFinishShiftsScreen(),
                          ),
                        );
                        bloc.add(const ShiftsRequested());
                      },
                      icon: const Icon(Icons.playlist_add_check),
                      label: const Text('مراجعة وإنهاء عدة ورديات'),
                    ),
                  ),
                ),
              if (state.selectedTab == ShiftListTab.all)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  child: DropdownButtonFormField<String>(
                    value: state.allStatus ?? '',
                    decoration: const InputDecoration(
                      labelText: 'تصفية حسب الحالة',
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                    items: const [
                      DropdownMenuItem(value: '', child: Text('كل الحالات')),
                      DropdownMenuItem(
                        value: 'IN_PROGRESS',
                        child: Text('جارية'),
                      ),
                      DropdownMenuItem(
                        value: 'COMPLETED',
                        child: Text('منتهية'),
                      ),
                      DropdownMenuItem(
                        value: 'CANCELLED',
                        child: Text('ملغاة'),
                      ),
                    ],
                    onChanged: (value) => bloc.add(
                      AllShiftsStatusFilterChanged(
                        value == null || value.isEmpty ? null : value,
                      ),
                    ),
                  ),
                ),
              Expanded(
                child: RefreshIndicator(
                  onRefresh: () async => bloc.add(const ShiftsRequested()),
                  child: state.visibleItems.isEmpty
                      ? ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: [
                            const SizedBox(height: 100),
                            _MessageView(
                              icon: Icons.event_note_outlined,
                              message: _emptyMessage(state.selectedTab),
                            ),
                          ],
                        )
                      : ListView.separated(
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(12),
                          itemCount: state.visibleItems.length +
                              (state.selectedTab == ShiftListTab.all &&
                                      (state.hasMore || state.loadingMore)
                                  ? 1
                                  : 0),
                          separatorBuilder: (_, __) =>
                              const SizedBox(height: 8),
                          itemBuilder: (context, index) {
                            if (index == state.visibleItems.length) {
                              return Padding(
                                padding: const EdgeInsets.symmetric(vertical: 8),
                                child: Center(
                                  child: state.loadingMore
                                      ? const CircularProgressIndicator()
                                      : OutlinedButton.icon(
                                          onPressed: () => bloc.add(
                                            const AllShiftsLoadMoreRequested(),
                                          ),
                                          icon: const Icon(Icons.expand_more),
                                          label: const Text('تحميل المزيد'),
                                        ),
                                ),
                              );
                            }
                            final shift = state.visibleItems[index];
                            return _ShiftCard(
                              shift: shift,
                              onManage: () => _manageShift(context, shift, bloc),
                            );
                          },
                        ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }


  Future<void> _manageShift(
    BuildContext context,
    ShiftModel shift,
    ShiftsBloc bloc,
  ) async {
    final actions = <String, String>{};
    if (shift.status == 'IN_PROGRESS') {
      actions['edit'] = 'تعديل الوردية';
      actions['finish'] = 'إنهاء الوردية';
      actions['cancel'] = 'إلغاء الوردية';
    } else if (shift.status == 'CANCELLED') {
      actions['reinstate'] = 'إعادة تفعيل الوردية';
      actions['history'] = 'عرض سجل التعديلات';
    } else {
      actions['edit'] = 'عرض / تعديل البيانات';
      actions['history'] = 'عرض سجل التعديلات';
    }
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('إجراءات الوردية #${shift.id}',
                  style: Theme.of(sheetContext).textTheme.titleMedium),
            ),
            ...actions.entries.map((entry) => ListTile(
                  leading: Icon(_actionIcon(entry.key)),
                  title: Text(entry.value),
                  onTap: () => Navigator.of(sheetContext).pop(entry.key),
                )),
          ],
        ),
      ),
    );
    if (choice == null) return;
    if (choice == 'edit') {
      final saved = await Navigator.of(context).push<bool>(
        MaterialPageRoute(builder: (_) => ShiftEditorScreen(shift: shift)),
      );
      if (saved == true) {
        bloc.add(const ShiftsRequested());
      }
      return;
    }
    if (choice == 'cancel') {
      final reason = await showDialog<String>(
        context: context,
        builder: (_) => _CancelShiftDialog(shiftId: shift.id),
      );
      if (reason == null || reason.trim().isEmpty) return;
      await _runShiftAction(
        context,
        bloc,
        () => getIt<ShiftsApi>().cancelShift(shift, reason),
        successMessage: 'تم إلغاء الوردية بنجاح.',
      );
      return;
    }
    if (choice == 'finish') {
      final confirm = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text('إنهاء الوردية #${shift.id}؟'),
          content: const Text('سيتم تغيير حالة الوردية إلى منتهية.'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('رجوع')),
            ElevatedButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('إنهاء')),
          ],
        ),
      );
      if (confirm == true) {
        await _runShiftAction(
          context,
          bloc,
          () => getIt<ShiftsApi>().finishShift(shift),
          successMessage: 'تم إنهاء الوردية بنجاح.',
        );
      }
      return;
    }
    if (choice == 'reinstate') {
      final confirm = await showDialog<bool>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          title: Text('إعادة تفعيل الوردية #${shift.id}؟'),
          content: const Text(
            'سيتم استرجاع الوردية. إذا كان السائق أو المعدة مشغولين في وردية أخرى، سنوضح لك السبب.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('رجوع')),
            ElevatedButton(onPressed: () => Navigator.pop(dialogContext, true), child: const Text('إعادة التفعيل')),
          ],
        ),
      );
      if (confirm == true) {
        await _runShiftAction(
          context,
          bloc,
          () => getIt<ShiftsApi>().reinstateShift(shift),
          conflictTitle: 'تعذر إعادة تفعيل الوردية',
          successMessage: 'تمت إعادة تفعيل الوردية بنجاح.',
        );
      }
      return;
    }
    if (choice == 'history') {
      await _showHistory(context, shift.id);
    }
  }

  Future<void> _showHistory(BuildContext context, int shiftId) async {
    try {
      final items = await getIt<ShiftsApi>().fetchShiftHistory(shiftId);
      await showDialog<void>(
        context: context,
        builder: (dialogContext) => AlertDialog(
          titlePadding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
          contentPadding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('سجل الوردية #$shiftId'),
              const SizedBox(height: 4),
              Text(
                items.isEmpty
                    ? 'لا توجد تغييرات مسجلة'
                    : '${items.length} سجل تغيير',
                style: Theme.of(dialogContext).textTheme.bodySmall,
              ),
            ],
          ),
          content: SizedBox(
            width: double.maxFinite,
            height: MediaQuery.of(dialogContext).size.height * 0.62,
            child: items.isEmpty
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.history, size: 42),
                        SizedBox(height: 12),
                        Text('لا توجد تغييرات مسجلة على الوردية حتى الآن.'),
                      ],
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    itemCount: items.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 10),
                    itemBuilder: (_, index) {
                      final item = items[index];
                      final field = (item['fieldName'] ?? item['action'] ?? 'تغيير').toString();
                      final oldValue = (item['oldValue'] ?? '—').toString();
                      final newValue = (item['newValue'] ?? '—').toString();
                      final user = (item['userName'] ?? 'مستخدم').toString();
                      final rawTime = (item['createdAt'] ?? '').toString();
                      final time = rawTime.isEmpty
                          ? 'الوقت غير متاح'
                          : rawTime.replaceFirst('T', ' ');
                      return Card(
                        margin: EdgeInsets.zero,
                        child: Padding(
                          padding: const EdgeInsets.all(12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Icon(Icons.edit_note, size: 22),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      field,
                                      style: Theme.of(dialogContext)
                                          .textTheme.titleSmall
                                          ?.copyWith(fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 12),
                              Container(
                                width: double.infinity,
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: Theme.of(dialogContext)
                                      .colorScheme.surfaceVariant
                                      .withOpacity(0.45),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text('القيمة السابقة'),
                                    const SizedBox(height: 4),
                                    SelectableText(oldValue),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 8),
                              Container(
                                width: double.infinity,
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(
                                  color: Theme.of(dialogContext)
                                      .colorScheme.primary
                                      .withOpacity(0.08),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    const Text('القيمة الجديدة'),
                                    const SizedBox(height: 4),
                                    SelectableText(
                                      newValue,
                                      style: const TextStyle(fontWeight: FontWeight.w600),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: 10),
                              const Divider(height: 1),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 12,
                                runSpacing: 6,
                                children: [
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      const Icon(Icons.person_outline, size: 16),
                                      const SizedBox(width: 4),
                                      Text(user),
                                    ],
                                  ),
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      const Icon(Icons.access_time, size: 16),
                                      const SizedBox(width: 4),
                                      Text(time),
                                    ],
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('إغلاق'),
            ),
          ],
        ),
      );
    } catch (error) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('تعذر تحميل سجل الوردية: $error')),
      );
    }

  }

  IconData _actionIcon(String action) {
    switch (action) {
      case 'edit': return Icons.edit_outlined;
      case 'finish': return Icons.check_circle_outline;
      case 'cancel': return Icons.cancel_outlined;
      case 'reinstate': return Icons.restore;
      default: return Icons.history;
    }
  }

  Future<void> _runShiftAction(
    BuildContext context,
    ShiftsBloc bloc,
    Future<ShiftModel> Function() action, {
    String conflictTitle = 'تعذر تنفيذ الإجراء',
    String successMessage = 'تم حفظ التغييرات بنجاح.',
  }) async {
    try {
      await action();
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(successMessage)),
      );
      bloc.add(const ShiftsRequested());
    } on DioError catch (error) {
      final failure = AppFailure.fromDio(error);
      if (error.response?.statusCode == 409) {
        final responseData = error.response?.data;
        final errorDetails = responseData is Map ? responseData['details'] : null;
        final structuredShiftId = errorDetails is Map
            ? int.tryParse('${errorDetails['conflictingShiftId'] ?? ''}')
            : null;
        final match = RegExp(r'الوردية رقم\s+(\d+)').firstMatch(failure.message);
        final conflictingShiftId = structuredShiftId ??
            (match == null ? null : int.tryParse(match.group(1)!));
        final openConflictingShift = await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: Text(conflictTitle),
            content: Text(failure.message),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('إغلاق'),
              ),
              if (conflictingShiftId != null)
                ElevatedButton.icon(
                  onPressed: () => Navigator.of(dialogContext).pop(true),
                  icon: const Icon(Icons.open_in_new),
                  label: const Text('فتح الوردية'),
                ),
            ],
          ),
        );
        if (openConflictingShift == true && conflictingShiftId != null) {
          try {
            final conflictingShift =
                await getIt<ShiftsApi>().fetchShift(conflictingShiftId);
            await Navigator.of(context).push<void>(
              MaterialPageRoute(
                builder: (detailsContext) => _ShiftDetailsScreen(
                  shift: conflictingShift,
                  conflictMessage: failure.message,
                  onManage: (currentShift) =>
                      _manageShift(detailsContext, currentShift, bloc),
                ),
              ),
            );
          } catch (openError) {
            final message = openError is AppFailure
                ? openError.message
                : 'تعذر فتح الوردية الأخرى. حدّث القائمة وحاول مرة أخرى.';
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text(message)),
            );
          }
        }
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(failure.message)),
        );
      }
    } catch (error) {
      final message = error is AppFailure
          ? error.message
          : 'حدث خطأ غير متوقع أثناء تنفيذ الإجراء.';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(message)),
      );
    }
  }

  String _emptyMessage(ShiftListTab tab) {
    switch (tab) {
      case ShiftListTab.running:
        return 'لا توجد ورديات جارية حاليًا.';
      case ShiftListTab.today:
        return 'لا توجد ورديات مسجلة بتاريخ اليوم.';
      case ShiftListTab.all:
        return 'لا توجد ورديات مطابقة للتصفية.';
    }
  }

}

class _CancelShiftDialog extends StatefulWidget {
  const _CancelShiftDialog({required this.shiftId});

  final int shiftId;

  @override
  State<_CancelShiftDialog> createState() => _CancelShiftDialogState();
}

class _CancelShiftDialogState extends State<_CancelShiftDialog> {
  final TextEditingController _reasonController = TextEditingController();

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('إلغاء الوردية #${widget.shiftId}'),
      content: TextField(
        controller: _reasonController,
        autofocus: true,
        maxLength: 300,
        maxLines: 3,
        decoration: const InputDecoration(
          labelText: 'سبب الإلغاء (مطلوب)',
          border: OutlineInputBorder(),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('رجوع'),
        ),
        ElevatedButton(
          onPressed: () {
            final value = _reasonController.text.trim();
            if (value.isNotEmpty) Navigator.pop(context, value);
          },
          child: const Text('تأكيد الإلغاء'),
        ),
      ],
    );
  }
}

class _TabChip extends StatelessWidget {
  const _TabChip({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ChoiceChip(
      avatar: Icon(icon, size: 18),
      label: Text(label),
      selected: selected,
      onSelected: (_) => onTap(),
    );
  }
}

class _RunningSummary extends StatefulWidget {
  const _RunningSummary({required this.items});
  final List<ShiftModel> items;
  @override
  State<_RunningSummary> createState() => _RunningSummaryState();
}

class _RunningSummaryState extends State<_RunningSummary> {
  Timer? _timer;
  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(minutes: 1), (_) {
      if (mounted) setState(() {});
    });
  }
  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) {
    final items = widget.items;
    final oldCount = items.where((shift) {
      final startedAt = shift.createdAt;
      return shift.status == 'IN_PROGRESS' && startedAt != null &&
          DateTime.now().difference(startedAt) >= const Duration(hours: 24);
    }).length;
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        child: Row(
          children: [
            Expanded(child: _SummaryNumber(label: 'ورديات جارية', value: items.length.toString(), icon: Icons.play_circle_outline)),
            const SizedBox(width: 8),
            Expanded(child: _SummaryNumber(label: 'سائقون مشغولون', value: items.map((s) => s.driverId).whereType<int>().toSet().length.toString(), icon: Icons.person_outline)),
            const SizedBox(width: 8),
            Expanded(child: _SummaryNumber(label: 'معدات مشغولة', value: items.map((s) => s.equipmentId).whereType<int>().toSet().length.toString(), icon: Icons.construction_outlined)),
            if (oldCount > 0) ...[
              const SizedBox(width: 8),
              Tooltip(
                message: 'هناك $oldCount وردية مستمرة منذ 24 ساعة أو أكثر',
                child: Icon(Icons.warning_amber_rounded, color: Colors.orange.shade800),
              ),
              Text(oldCount.toString(), style: TextStyle(color: Colors.orange.shade900, fontWeight: FontWeight.bold)),
            ],
          ],
        ),
      ),
    );
  }
}

class _SummaryNumber extends StatelessWidget {
  const _SummaryNumber({required this.label, required this.value, required this.icon});

  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
        const SizedBox(height: 3),
        Text(value, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
        Text(label, textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}

class _ShiftCard extends StatefulWidget {
  const _ShiftCard({required this.shift, required this.onManage});
  final ShiftModel shift;
  final VoidCallback onManage;
  @override
  State<_ShiftCard> createState() => _ShiftCardState();
}

class _ShiftCardState extends State<_ShiftCard> {
  Timer? _timer;
  @override
  void initState() {
    super.initState();
    _startTimerIfRunning();
  }
  @override
  void didUpdateWidget(covariant _ShiftCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.shift.status != widget.shift.status ||
        oldWidget.shift.createdAt != widget.shift.createdAt) {
      _timer?.cancel();
      _startTimerIfRunning();
    }
  }
  void _startTimerIfRunning() {
    if (widget.shift.status == 'IN_PROGRESS' && widget.shift.createdAt != null) {
      _timer = Timer.periodic(const Duration(minutes: 1), (_) {
        if (mounted) setState(() {});
      });
    }
  }
  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) {
    final shift = widget.shift;
    final theme = Theme.of(context);
    final equipment = [shift.equipmentCode, shift.equipmentName]
        .where((value) => value.isNotEmpty).join(' - ');
    final startedAt = shift.createdAt;
    final age = startedAt == null ? Duration.zero : DateTime.now().difference(startedAt);
    final isOverdue = shift.status == 'IN_PROGRESS' && !age.isNegative &&
        age >= const Duration(hours: 24);
    final isSeverelyOverdue = shift.status == 'IN_PROGRESS' && !age.isNegative &&
        age >= const Duration(hours: 72);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(
                  'وردية #${shift.id} · ${shift.shiftTypeLabel}',
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                )),
                Chip(label: Text(shift.statusLabel)),
              ],
            ),
            if (shift.status == 'IN_PROGRESS') ...[
              const SizedBox(height: 6),
              Row(children: [
                Icon(Icons.timer_outlined, size: 18,
                  color: isSeverelyOverdue ? Colors.red.shade700 :
                      isOverdue ? Colors.orange.shade800 : theme.colorScheme.primary),
                const SizedBox(width: 6),
                Expanded(child: Text(
                  startedAt == null ? 'المدة: وقت البداية غير متاح' : 'المدة: ${_elapsed(startedAt)}',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: isSeverelyOverdue ? Colors.red.shade700 :
                        isOverdue ? Colors.orange.shade900 : null,
                  ),
                )),
              ]),
            ],
            if (isOverdue) ...[
              const SizedBox(height: 6),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(
                  color: isSeverelyOverdue ? Colors.red.shade100 : Colors.orange.shade100,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: isSeverelyOverdue ? Colors.red.shade300 : Colors.orange.shade300),
                ),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Icon(Icons.warning_amber_rounded,
                    color: isSeverelyOverdue ? Colors.red.shade900 : Colors.orange.shade900),
                  const SizedBox(width: 8),
                  Expanded(child: Text(
                    isSeverelyOverdue
                      ? 'تنبيه شديد: الوردية مستمرة منذ 3 أيام أو أكثر. راجع حالتها فورًا.'
                      : 'تنبيه: الوردية مستمرة منذ 24 ساعة أو أكثر. تأكد من حالتها.',
                    style: TextStyle(
                      color: isSeverelyOverdue ? Colors.red.shade900 : Colors.orange.shade900,
                      fontWeight: FontWeight.w600,
                    ),
                  )),
                ]),
              ),
            ],
            const SizedBox(height: 8),
            _InfoLine(icon: Icons.calendar_today_outlined, text: shift.shiftDate),
            _InfoLine(icon: Icons.location_on_outlined, text: shift.siteName),
            _InfoLine(icon: Icons.person_outline, text: shift.driverName),
            _InfoLine(icon: Icons.construction_outlined, text: equipment),
            const Divider(height: 24),
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              const Text('قيمة الوردية'),
              Text(_money(shift.price), style: theme.textTheme.bodyMedium),
            ]),
            const SizedBox(height: 4),
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              const Text('بعد أجر السائق'),
              Text(_money(shift.priceAfterWage),
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            ]),
            if (shift.notes.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('ملاحظات: ${shift.notes}'),
            ],
            const SizedBox(height: 12),
            Align(
              alignment: AlignmentDirectional.centerEnd,
              child: OutlinedButton.icon(
                onPressed: widget.onManage,
                icon: const Icon(Icons.more_horiz),
                label: const Text('إجراءات الوردية'),
              ),
            ),
          ],
        ),
      ),
    );
  }
  String _elapsed(DateTime startedAt) {
    final duration = DateTime.now().difference(startedAt);
    if (duration.isNegative) return 'وقت غير متاح';
    final days = duration.inDays;
    final hours = duration.inHours % 24;
    final minutes = duration.inMinutes % 60;
    if (days > 0) return hours == 0 ? '${days} يوم' : '${days} يوم و${hours} ساعة';
    if (duration.inHours > 0) return '${duration.inHours} ساعة و${minutes} دقيقة';
    return '${duration.inMinutes} دقيقة';
  }
  String _money(double value) => value.toStringAsFixed(2) + ' جنيه';
}

class _InfoLine extends StatelessWidget {
  const _InfoLine({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(child: Text(text.isEmpty ? 'غير محدد' : text)),
        ],
      ),
    );
  }
}

class _MessageView extends StatelessWidget {
  const _MessageView({
    required this.icon,
    required this.message,
    this.buttonText,
    this.onPressed,
  });

  final IconData icon;
  final String message;
  final String? buttonText;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            if (buttonText != null && onPressed != null) ...[
              const SizedBox(height: 16),
              ElevatedButton(onPressed: onPressed, child: Text(buttonText!)),
            ],
          ],
        ),
      ),
    );
  }
}


class _ShiftDetailsScreen extends StatefulWidget {
  const _ShiftDetailsScreen({
    required this.shift,
    required this.onManage,
    this.conflictMessage,
  });

  final ShiftModel shift;
  final Future<void> Function(ShiftModel shift) onManage;
  final String? conflictMessage;

  @override
  State<_ShiftDetailsScreen> createState() => _ShiftDetailsScreenState();
}

class _ShiftDetailsScreenState extends State<_ShiftDetailsScreen> {
  late ShiftModel _shift;
  bool _refreshing = false;

  @override
  void initState() {
    super.initState();
    _shift = widget.shift;
  }

  Future<void> _manageAndRefresh() async {
    await widget.onManage(_shift);
    if (!mounted) return;
    setState(() => _refreshing = true);
    try {
      final latest = await getIt<ShiftsApi>().fetchShift(_shift.id);
      if (mounted) setState(() => _shift = latest);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('تم تنفيذ الإجراء، لكن تعذر تحديث تفاصيل الوردية.'),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final shift = _shift; 
    final equipment = [
      shift.equipmentCode,
      shift.equipmentName,
    ].where((value) => value.isNotEmpty).join(' - ');
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text('تفاصيل الوردية #${shift.id}'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (widget.conflictMessage != null && widget.conflictMessage!.trim().isNotEmpty) ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: theme.colorScheme.error.withOpacity(0.08),
                border: Border.all(color: theme.colorScheme.error.withOpacity(0.35)),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.warning_amber_rounded, color: theme.colorScheme.error),
                  const SizedBox(width: 8),
                  Expanded(child: Text(widget.conflictMessage!)),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          'وردية #${shift.id}',
                          style: theme.textTheme.titleLarge,
                        ),
                      ),
                      Chip(label: Text(shift.statusLabel)),
                    ],
                  ),
                  const Divider(height: 24),
                  _InfoLine(icon: Icons.calendar_today_outlined, text: shift.shiftDate),
                  _InfoLine(icon: Icons.wb_twilight_outlined, text: shift.shiftTypeLabel),
                  _InfoLine(icon: Icons.location_on_outlined, text: shift.siteName),
                  _InfoLine(icon: Icons.person_outline, text: shift.driverName),
                  _InfoLine(icon: Icons.construction_outlined, text: equipment),
                  const Divider(height: 24),
                  _DetailMoneyLine(label: 'قيمة الوردية', value: shift.price),
                  _DetailMoneyLine(label: 'أجر السائق', value: shift.driverWage),
                  const Divider(),
                  _DetailMoneyLine(
                    label: 'الصافي بعد أجر السائق',
                    value: shift.priceAfterWage,
                    emphasize: true,
                  ),
                  if (shift.cancelReason.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text('سبب الإلغاء', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 4),
                    Text(shift.cancelReason),
                  ],
                  if (shift.notes.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Text('ملاحظات', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 4),
                    Text(shift.notes),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _refreshing ? null : _manageAndRefresh,
              icon: const Icon(Icons.tune),
              label: Text(_refreshing ? 'جارٍ تحديث البيانات...' : 'إجراءات الوردية'),
            ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () => Navigator.of(context).pop(),
              icon: const Icon(Icons.arrow_back),
              label: const Text('العودة إلى الوردية الأصلية'),
            ),
          ),
        ],
      ),
    );
  }
}

class _DetailMoneyLine extends StatelessWidget {
  const _DetailMoneyLine({
    required this.label,
    required this.value,
    this.emphasize = false,
  });

  final String label;
  final double value;
  final bool emphasize;

  @override
  Widget build(BuildContext context) {
    final style = emphasize
        ? Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.bold,
            )
        : Theme.of(context).textTheme.bodyMedium;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          Text('${value.toStringAsFixed(2)} جنيه', style: style),
        ],
      ),
    );
  }
}
