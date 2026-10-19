import 'package:flutter/widgets.dart';

import 'app.dart';
import 'core/di/injector.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  configureDependencies();
  runApp(const ContractorApp());
}
