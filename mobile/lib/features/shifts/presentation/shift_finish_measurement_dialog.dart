import 'package:flutter/material.dart';

import '../data/models/shift_model.dart';

/// Collects the physical measurement required to finish HOUR/QUANTITY shifts.
/// The server remains authoritative for validation and final money rounding.
class ShiftFinishMeasurementDialog extends StatefulWidget {
  const ShiftFinishMeasurementDialog({Key? key, required this.shift}) : super(key: key);

  final ShiftModel shift;

  @override
  State<ShiftFinishMeasurementDialog> createState() =>
      _ShiftFinishMeasurementDialogState();
}

class _ShiftFinishMeasurementDialogState
    extends State<ShiftFinishMeasurementDialog> {
  final TextEditingController _controller = TextEditingController();

  bool get _isHourly => widget.shift.compensationBasis == 'HOUR';
  int get _fractionDigits => _isHourly ? 2 : 3;
  String get _unitLabel => _isHourly ? 'ساعة' : widget.shift.compensationUnitCode;

  double? get _measurement {
    final raw = _controller.text.trim().replaceAll(',', '.');
    if (raw.isEmpty) return null;
    return double.tryParse(raw);
  }

  bool get _validMeasurement {
    final value = _measurement;
    if (value == null || value <= 0) return false;
    final raw = _controller.text.trim().replaceAll(',', '.');
    final parts = raw.split('.');
    if (parts.length > 2 || (parts.length == 2 && parts[1].length > _fractionDigits)) {
      return false;
    }
    return parts.first.length <= (_isHourly ? 6 : 9);
  }

  double? get _preview {
    if (!_validMeasurement) return null;
    final rawAmount = widget.shift.compensationRate * _measurement!;
    return (rawAmount * 100).round() / 100;
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
          Text('${_isHourly ? 'سعر الساعة' : 'سعر الوحدة'} المعتمد: '
              '${widget.shift.compensationRate.toStringAsFixed(2)} جنيه'),
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
              errorText: _controller.text.isNotEmpty && !_validMeasurement
                  ? 'أدخل قيمة موجبة وبحد أقصى $_fractionDigits خانات عشرية'
                  : null,
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 12),
          Text(
            preview == null
                ? (_isHourly ? 'أدخل عدد الساعات لعرض إجمالي الأجر.' : 'أدخل الكمية لعرض إجمالي الأجر.')
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
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('رجوع'),
        ),
        ElevatedButton(
          onPressed: _validMeasurement
              ? () => Navigator.pop(context, _measurement)
              : null,
          child: const Text('تأكيد الإنهاء'),
        ),
      ],
    );
  }
}
