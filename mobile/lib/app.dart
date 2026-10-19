import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/theme/app_theme.dart';
import 'features/health/presentation/health_screen.dart';

class ContractorApp extends StatelessWidget {
  const ContractorApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'إدارة المقاول',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      // اللغة العربية فقط، وهذا يفعّل الاتجاه من اليمين إلى اليسار تلقائيًا.
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: const HealthScreen(),
    );
  }
}
