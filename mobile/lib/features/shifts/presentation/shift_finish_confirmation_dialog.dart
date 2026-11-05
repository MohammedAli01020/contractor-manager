import 'package:flutter/material.dart';

import '../data/models/shift_model.dart';

/// Collects the physical measurement required to finish HOUR/QUANTITY shifts.
/// The backend remains authoritative for validation and financial calculation.
class ShiftFinishConfirmationDialog extends StatefulWidget {
  const ShiftFinishConfirmationDialog({Key? key, required this.shift}) : super(key: key);

  final ShiftModel shift;

  @override
  State<ShiftFinishConfirmationDialog> createState() =>
      _ShiftFinishConfirmationDialogState();
}

class _ShiftFinishConfirmationDialogState
    extends State<ShiftFinishConfirmationDialog> {
  final TextEditingController _controller = TextEditingController();

  bool get _isHourly => widget.shift.compensationBasis == 'HOUR';
  bool get _isQuantity => widget.shift.compensationBasis == 'QUANTITY';
  bool get _isMeasured => _isHourly || _isQuantity;

  String get _unitLabel {
    if (_isHourly) return 'ساعة';
    final unit = widget.shift.compensationUnitCode.trim();
    return unit.isEmpty ? 'وحدة' : unit;
  }

  String get _normalizedInput => _controller.text.trim().replaceAll(',', '.');

  double? get _measurement => double.tryParse(_normalizedInput);

  bool get _validMeasurement {
    if (!_isMeasured || _measurement == null || _measurement! <= 0) return false;
    final maxScale = _isHourly ? 2 : 3;
    final parts = _normalizedInput.split('.');
    return parts.length == 1 || parts[1].length <= maxScale;
  }

  double? get _preview {
    if (!_validMeasurement) return null;
    // Rates are stored at two decimal places and QUANTITY measurements at
    // three. Integer arithmetic avoids binary floating-point half-cent drift.
    final rateCents = BigInt.from((widget.shift.compensationRate * 100).round());
    final quantityUnits = _scaledInteger(_normalizedInput, _isHourly ? 2 : 3);
    final denominator = BigInt.from(_isHourly ? 100 : 1000);
    final cents = (rateCents * quantityUnits * BigInt.from(2) + denominator) ~/
        (denominator * BigInt.from(2));
    return cents.toDouble() / 100;
  }

  BigInt _scaledInteger(String value, int scale) {
    final parts = value.split('.');
    final whole = BigInt.parse(parts.first);
    final fraction = parts.length == 1 ? '' : parts[1];
    final padded = fraction.padRight(scale, '0');
    return whole * BigInt.from(10).pow(scale) +
        BigInt.parse(padded.isEmpty ? '0' : padded);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final preview = _preview;
    return AlertDialog(
      title: Text('إنهاء الوردية #${widget.shift.id}؟'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (_isMeasured) ...[
            Text('${_isHourly ? 'سعر الساعة' : 'سعر الوحدة'} المعتمد: ${widget.shift.compensationRate.toStringAsFixed(2)} جنيه'),
            const SizedBox(height: 12),
            TextField(
              controller: _controller,
              autofocus: true,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: InputDecoration(
                labelText: _isHourly ? 'ساعات العمل الفعلية' : 'الكمية الفعلية',
                hintText: _isHourly ? 'مثال: 7.5 أو 8.25' : 'أدخل الكمية المنفذة',
                suffixText: _unitLabel,
                border: const OutlineInputBorder(),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: 12),
            Text(
              preview == null
                  ? (_isHourly
                      ? 'أدخل عدد الساعات لعرض إجمالي الأجر.'
                      : 'أدخل الكمية لعرض إجمالي الأجر.')
                  : 'إجمالي الأجر: ${preview.toStringAsFixed(2)} جنيه',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            if (preview == 0) ...[
              const SizedBox(height: 8),
              const Text(
                'تنبيه: الإجمالي صفر؛ لن يُنشأ قيد أجر أو استحقاق، ولن تُسترد سلفة من هذه الوردية.',
              ),
            ],
            const SizedBox(height: 8),
            Text(_isHourly
                ? 'لن يتم حساب الساعات من وقت بداية الوردية ونهايتها.'
                : 'سيتم حساب الأجر من الكمية المدخلة وسعر الوحدة المحفوظ في الاتفاقية.'),
          ] else
            Text(
              widget.shift.driverWage == 0
                  ? 'أجر السائق لهذه الوردية صفر جنيه. سيتم إنهاؤها دون قيد أجر أو استحقاق، ولن تُسترد أي سلفة من أجر هذه الوردية. هل تؤكد؟'
                  : 'سيتم تغيير حالة الوردية إلى منتهية وترحيل أجر السائق المستحق.',
            ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('رجوع'),
        ),
        ElevatedButton(
          onPressed: _isMeasured && !_validMeasurement
              ? null
              : () => Navigator.pop(
                    context,
                    _isMeasured ? _measurement : 0.0,
                  ),
          child: const Text('تأكيد الإنهاء'),
        ),
      ],
    );
  }
}
