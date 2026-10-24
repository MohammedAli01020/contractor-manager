import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/di/injector.dart';
import '../../core/widgets/loading_view.dart';
import '../shifts/presentation/shifts_screen.dart';
import '../drivers/drivers_screen.dart';
import 'auth_bloc.dart';

class AuthGate extends StatelessWidget {
  const AuthGate({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return BlocProvider<AuthBloc>(
      create: (_) => getIt<AuthBloc>()..add(const AuthStarted()),
      child: BlocBuilder<AuthBloc, AuthState>(
        builder: (context, state) {
          if (state is AuthChecking || state is AuthLoggingOut) {
            return const Scaffold(body: SafeArea(child: LoadingView()));
          }
          if (state is AuthAuthenticated) return HomeScreen(user: state.user);
          return LoginScreen(
            loading: state is AuthLoginLoading,
            error: state is AuthError ? state.message : null,
          );
        },
      ),
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({Key? key, required this.loading, this.error})
      : super(key: key);
  final bool loading;
  final String? error;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _username = TextEditingController();
  final _password = TextEditingController();
  bool _hidePassword = true;

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    super.dispose();
  }

  void _submit() {
    if (widget.loading || !_formKey.currentState!.validate()) return;
    context.read<AuthBloc>().add(
          AuthLoginSubmitted(_username.text.trim(), _password.text),
        );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Icon(Icons.construction_rounded,
                        size: 64, color: theme.colorScheme.primary),
                    const SizedBox(height: 16),
                    Text('إدارة المقاول',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        )),
                    const SizedBox(height: 8),
                    const Text(
                      'سجّل الدخول لمتابعة الورديات والمعدات والسائقين',
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 28),
                    if (widget.error != null) ...[
                      Text(widget.error!,
                          style: TextStyle(color: theme.colorScheme.error),
                          textAlign: TextAlign.center),
                      const SizedBox(height: 16),
                    ],
                    TextFormField(
                      controller: _username,
                      enabled: !widget.loading,
                      textDirection: TextDirection.ltr,
                      textAlign: TextAlign.left,
                      autocorrect: false,
                      enableSuggestions: false,
                      textInputAction: TextInputAction.next,
                      decoration: const InputDecoration(
                        labelText: 'اسم المستخدم',
                        prefixIcon: Icon(Icons.person_outline),
                      ),
                      validator: (value) =>
                          value == null || value.trim().isEmpty
                              ? 'اكتب اسم المستخدم'
                              : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _password,
                      enabled: !widget.loading,
                      textDirection: TextDirection.ltr,
                      textAlign: TextAlign.left,
                      obscureText: _hidePassword,
                      textInputAction: TextInputAction.done,
                      onFieldSubmitted: (_) => _submit(),
                      decoration: InputDecoration(
                        labelText: 'كلمة المرور',
                        prefixIcon: const Icon(Icons.lock_outline),
                        suffixIcon: IconButton(
                          onPressed: widget.loading
                              ? null
                              : () => setState(
                                    () => _hidePassword = !_hidePassword,
                                  ),
                          icon: Icon(_hidePassword
                              ? Icons.visibility_outlined
                              : Icons.visibility_off_outlined),
                        ),
                      ),
                      validator: (value) =>
                          value == null || value.isEmpty
                              ? 'اكتب كلمة المرور'
                              : null,
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      onPressed: widget.loading ? null : _submit,
                      child: widget.loading
                          ? const SizedBox(
                              width: 22,
                              height: 22,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Text('تسجيل الدخول'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({Key? key, required this.user}) : super(key: key);
  final AuthUser user;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('إدارة المقاول'),
        actions: [
          IconButton(
            tooltip: 'تسجيل الخروج',
            onPressed: () =>
                context.read<AuthBloc>().add(const AuthLogoutRequested()),
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.account_circle_outlined, size: 48),
                  const SizedBox(height: 12),
                  Text('أهلًا، ' + user.fullName,
                      style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 8),
                  Text('اسم المستخدم: ' + user.username),
                  const SizedBox(height: 4),
                  Text('الصلاحية: ' + user.role),
                  const SizedBox(height: 4),
                  Text('رقم حساب الشركة: ' + user.tenantId.toString()),
                ],
              ),
            ),
          ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.people_alt_outlined),
              title: const Text('السائقون والعمال'),
              subtitle: const Text('إضافة وتعديل الأفراد والأجور والتوافر'),
              trailing: const Icon(Icons.chevron_left),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const DriversScreen(),
                ),
              ),
            ),
          ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.view_list_outlined),
              title: const Text('قائمة الورديات'),
              subtitle: const Text('عرض الورديات المسجلة وحالتها وقيمتها'),
              trailing: const Icon(Icons.chevron_left),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => const ShiftsScreen(),
                ),
              ),
            ),
          ),
          const Card(
            child: ListTile(
              leading: Icon(Icons.check_circle_outline),
              title: Text('تم تسجيل الدخول بنجاح'),
              subtitle: Text(
                'تم تجهيز أساس المصادقة. الخطوة التالية ربط شاشات الورديات والسائقين والمعدات.',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
