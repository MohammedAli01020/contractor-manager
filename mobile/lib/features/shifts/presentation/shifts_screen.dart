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
              onPressed: () => context
                  .read<ShiftsBloc>()
                  .add(const ShiftsRequested()),
            );
          }
          if (state is ShiftsLoaded) {
            if (state.items.isEmpty) {
              return RefreshIndicator(
                onRefresh: () async => context
                    .read<ShiftsBloc>()
                    .add(const ShiftsRequested()),
                child: ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    SizedBox(height: 150),
                    _MessageView(
                      icon: Icons.event_note_outlined,
                      message: 'لا توجد ورديات لعرضها حاليًا.',
                    ),
                  ],
                ),
              );
            }
            return RefreshIndicator(
              onRefresh: () async => context
                  .read<ShiftsBloc>()
                  .add(const ShiftsRequested()),
              child: ListView.separated(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(12),
                itemCount: state.items.length,
                separatorBuilder: (_, __) => const SizedBox(height: 8),
                itemBuilder: (context, index) =>
                    _ShiftCard(shift: state.items[index]),
              ),
            );
          }
          return const SizedBox.shrink();
        },
      ),
    );
  }
}

class _ShiftCard extends StatelessWidget {
  const _ShiftCard({required this.shift});

  final ShiftModel shift;

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
            const SizedBox(height: 8),
            _InfoLine(icon: Icons.calendar_today_outlined, text: shift.shiftDate),
            _InfoLine(icon: Icons.location_on_outlined, text: shift.siteName),
            _InfoLine(icon: Icons.person_outline, text: shift.driverName),
            _InfoLine(icon: Icons.construction_outlined, text: equipment),
            const Divider(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('قيمة الوردية'),
                Text(_money(shift.price),
                    style: theme.textTheme.bodyMedium),
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
