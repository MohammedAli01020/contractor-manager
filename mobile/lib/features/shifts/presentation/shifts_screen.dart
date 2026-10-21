import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/di/injector.dart';
import '../../../core/widgets/loading_view.dart';
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
                      label: 'الجارية الآن',
                      icon: Icons.play_circle_outline,
                      selected: state.selectedTab == ShiftListTab.running,
                      onTap: () => bloc.add(
                        const ShiftTabChanged(ShiftListTab.running),
                      ),
                    ),
                    _TabChip(
                      label: 'ورديات اليوم',
                      icon: Icons.today_outlined,
                      selected: state.selectedTab == ShiftListTab.today,
                      onTap: () => bloc.add(
                        const ShiftTabChanged(ShiftListTab.today),
                      ),
                    ),
                    _TabChip(
                      label: 'كل الورديات',
                      icon: Icons.history,
                      selected: state.selectedTab == ShiftListTab.all,
                      onTap: () =>
                          bloc.add(const ShiftTabChanged(ShiftListTab.all)),
                    ),
                  ],
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
                            final isPreviousDayRunning =
                                state.selectedTab == ShiftListTab.running &&
                                shift.status == 'IN_PROGRESS' &&
                                _isBeforeToday(shift.shiftDate);
                            return _ShiftCard(
                              shift: shift,
                              previousDayRunning: isPreviousDayRunning,
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

  bool _isBeforeToday(String value) {
    if (value.length < 10) return false;
    final date = DateTime.tryParse(value.substring(0, 10));
    if (date == null) return false;
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    return date.isBefore(today);
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

class _ShiftCard extends StatelessWidget {
  const _ShiftCard({
    required this.shift,
    this.previousDayRunning = false,
  });

  final ShiftModel shift;
  final bool previousDayRunning;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final equipment = [
      shift.equipmentCode,
      shift.equipmentName,
    ].where((value) => value.isNotEmpty).join(' - ');
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    'وردية #${shift.id} · ${shift.shiftTypeLabel}',
                    style: theme.textTheme.titleMedium
                        ?.copyWith(fontWeight: FontWeight.bold),
                  ),
                ),
                Chip(label: Text(shift.statusLabel)),
              ],
            ),
            if (previousDayRunning) ...[
              const SizedBox(height: 4),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: Colors.orange.shade100,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.warning_amber_rounded,
                        size: 18, color: Colors.orange.shade900),
                    const SizedBox(width: 6),
                    Text(
                      'مستمرة من يوم سابق',
                      style: TextStyle(color: Colors.orange.shade900),
                    ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 8),
            _InfoLine(
              icon: Icons.calendar_today_outlined,
              text: shift.shiftDate,
            ),
            _InfoLine(icon: Icons.location_on_outlined, text: shift.siteName),
            _InfoLine(icon: Icons.person_outline, text: shift.driverName),
            _InfoLine(icon: Icons.construction_outlined, text: equipment),
            const Divider(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('قيمة الوردية'),
                Text(_money(shift.price), style: theme.textTheme.bodyMedium),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('بعد أجر السائق'),
                Text(
                  _money(shift.priceAfterWage),
                  style: theme.textTheme.titleMedium
                      ?.copyWith(fontWeight: FontWeight.bold),
                ),
              ],
            ),
            if (shift.notes.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text('ملاحظات: ${shift.notes}'),
            ],
          ],
        ),
      ),
    );
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
