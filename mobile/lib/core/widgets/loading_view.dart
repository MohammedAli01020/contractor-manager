import 'package:flutter/material.dart';

/// Compact loading indicator for screens that do not need list placeholders.
class LoadingView extends StatelessWidget {
  const LoadingView({Key? key, this.label = 'جارٍ تحميل البيانات...'})
      : super(key: key);

  final String label;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Semantics(
        label: label,
        liveRegion: true,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 12),
            Text(label, style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}

/// Skeleton placeholders for list screens while their first request is pending.
/// Static placeholders avoid expensive repeating animations on low-end devices.
class SkeletonLoadingView extends StatelessWidget {
  const SkeletonLoadingView({
    Key? key,
    this.label = 'جارٍ تحميل البيانات...',
  }) : super(key: key);

  final String label;

  @override
  Widget build(BuildContext context) {
    final borderColor = Theme.of(context).dividerColor.withOpacity(0.55);

    return Semantics(
      label: label,
      liveRegion: true,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: const [
              Expanded(child: _SkeletonBlock(height: 20, width: 150)),
              SizedBox(width: 12),
              _SkeletonBlock(height: 36, width: 92, radius: 12),
            ],
          ),
          const SizedBox(height: 20),
          Row(
            children: const [
              Expanded(child: _SkeletonTile()),
              SizedBox(width: 10),
              Expanded(child: _SkeletonTile()),
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
                    children: const [
                      Expanded(child: _SkeletonBlock(height: 16, width: 145)),
                      SizedBox(width: 12),
                      _SkeletonBlock(height: 26, width: 68, radius: 10),
                    ],
                  ),
                  const SizedBox(height: 18),
                  Row(
                    children: const [
                      _SkeletonBlock(height: 16, width: 16, radius: 5),
                      SizedBox(width: 10),
                      Expanded(child: _SkeletonBlock(width: 190)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: const [
                      _SkeletonBlock(height: 16, width: 16, radius: 5),
                      SizedBox(width: 10),
                      Expanded(child: _SkeletonBlock(width: 130)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: const [
                      _SkeletonBlock(height: 16, width: 16, radius: 5),
                      SizedBox(width: 10),
                      Expanded(child: _SkeletonBlock(width: 165)),
                    ],
                  ),
                  const SizedBox(height: 18),
                  const Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: _SkeletonBlock(height: 36, width: 112, radius: 10),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
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
                Text(label, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

class _SkeletonTile extends StatelessWidget {
  const _SkeletonTile();

  @override
  Widget build(BuildContext context) {
    final borderColor = Theme.of(context).dividerColor.withOpacity(0.55);
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).cardColor,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: borderColor),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _SkeletonBlock(height: 22, width: 30, radius: 7),
          SizedBox(height: 12),
          _SkeletonBlock(height: 18, width: 54),
          SizedBox(height: 7),
          _SkeletonBlock(width: 78),
        ],
      ),
    );
  }
}

class _SkeletonBlock extends StatelessWidget {
  const _SkeletonBlock({
    this.height = 12,
    this.width,
    this.radius = 8,
  });

  final double height;
  final double? width;
  final double radius;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      width: width,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.onSurface.withOpacity(0.07),
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}
