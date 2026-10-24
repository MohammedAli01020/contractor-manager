import 'package:flutter/material.dart';

/// Lightweight placeholder UI for initial data loading.
///
/// Uses static placeholders instead of a repeating animation so the loading
/// state stays inexpensive on low-end devices and weak connections.
class LoadingView extends StatelessWidget {
  const LoadingView({Key? key, this.label = 'جارٍ تحميل البيانات...'})
      : super(key: key);

  final String label;

  @override
  Widget build(BuildContext context) {
    final placeholderColor = Theme.of(context).colorScheme.onSurface
        .withOpacity(0.07);
    final borderColor = Theme.of(context).dividerColor.withOpacity(0.55);

    Widget placeholder({
      double height = 12,
      double? width,
      double radius = 8,
    }) {
      return Container(
        height: height,
        width: width,
        decoration: BoxDecoration(
          color: placeholderColor,
          borderRadius: BorderRadius.circular(radius),
        ),
      );
    }

    return Semantics(
      label: label,
      liveRegion: true,
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              Expanded(child: placeholder(height: 20, width: 150)),
              const SizedBox(width: 12),
              placeholder(height: 36, width: 92, radius: 12),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              Expanded(
                child: _SkeletonTile(
                  color: placeholderColor,
                  borderColor: borderColor,
                  placeholder: placeholder,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _SkeletonTile(
                  color: placeholderColor,
                  borderColor: borderColor,
                  placeholder: placeholder,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          for (var index = 0; index < 3; index++) ...[
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Theme.of(context).cardColor,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: borderColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(child: placeholder(height: 16, width: 145)),
                      const SizedBox(width: 12),
                      placeholder(height: 26, width: 68, radius: 10),
                    ],
                  ),
                  const SizedBox(height: 18),
                  Row(
                    children: [
                      placeholder(height: 16, width: 16, radius: 5),
                      const SizedBox(width: 10),
                      Expanded(child: placeholder(width: 190)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      placeholder(height: 16, width: 16, radius: 5),
                      const SizedBox(width: 10),
                      Expanded(child: placeholder(width: 130)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      placeholder(height: 16, width: 16, radius: 5),
                      const SizedBox(width: 10),
                      Expanded(child: placeholder(width: 165)),
                    ],
                  ),
                  const SizedBox(height: 18),
                  Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: placeholder(height: 36, width: 112, radius: 10),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
          const SizedBox(height: 4),
          Center(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: Theme.of(context).colorScheme.secondary,
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  label,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SkeletonTile extends StatelessWidget {
  const _SkeletonTile({
    required this.color,
    required this.borderColor,
    required this.placeholder,
  });

  final Color color;
  final Color borderColor;
  final Widget Function({
    double height,
    double? width,
    double radius,
  }) placeholder;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          placeholder(height: 22, width: 30, radius: 7),
          const SizedBox(height: 12),
          placeholder(height: 18, width: 54),
          const SizedBox(height: 7),
          placeholder(width: 78),
        ],
      ),
    );
  }
}
