import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/config/app_config.dart';
import '../../../core/di/injector.dart';
import '../../../core/widgets/error_view.dart';
import '../../../core/widgets/loading_view.dart';
import 'bloc/health_bloc.dart';

/// شاشة مؤقتة للتحقق من اتصال التطبيق بالخادم. تُستبدل بشاشة الدخول في المرحلة 1.
class HealthScreen extends StatelessWidget {
  const HealthScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return BlocProvider<HealthBloc>(
      create: (_) => getIt<HealthBloc>()..add(const HealthCheckRequested()),
      child: Scaffold(
        appBar: AppBar(title: const Text('حالة الاتصال بالخادم')),
        body: BlocBuilder<HealthBloc, HealthState>(
          builder: (context, state) {
            if (state is HealthLoaded) {
              return _StatusCard(state: state);
            }
            if (state is HealthFailed) {
              return ErrorView(
                message: state.message,
                onRetry: () => context
                    .read<HealthBloc>()
                    .add(const HealthCheckRequested()),
              );
            }
            return const LoadingView();
          },
        ),
      ),
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({Key? key, required this.state}) : super(key: key);

  final HealthLoaded state;

  @override
  Widget build(BuildContext context) {
    final info = state.info;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          child: Column(
            children: [
              ListTile(
                leading: Icon(
                  info.status == 'UP' ? Icons.check_circle : Icons.warning,
                  color: info.status == 'UP' ? Colors.green : Colors.orange,
                ),
                title: const Text('الخادم'),
                subtitle: Text(info.status == 'UP' ? 'يعمل' : 'يعمل جزئيًا'),
              ),
              ListTile(
                leading: Icon(
                  info.database == 'UP' ? Icons.check_circle : Icons.error,
                  color: info.database == 'UP' ? Colors.green : Colors.red,
                ),
                title: const Text('قاعدة البيانات'),
                subtitle: Text(info.database == 'UP' ? 'متصلة' : 'غير متصلة'),
              ),
              const ListTile(
                leading: Icon(Icons.link),
                title: Text('عنوان الخادم'),
                subtitle: Text(
                  AppConfig.apiBaseUrl,
                  textDirection: TextDirection.ltr,
                  textAlign: TextAlign.start,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        ElevatedButton.icon(
          onPressed: () =>
              context.read<HealthBloc>().add(const HealthCheckRequested()),
          icon: const Icon(Icons.refresh),
          label: const Text('إعادة الفحص'),
        ),
      ],
    );
  }
}
