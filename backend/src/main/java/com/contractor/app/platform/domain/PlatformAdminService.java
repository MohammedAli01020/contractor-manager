package com.contractor.app.platform.domain;

import com.contractor.app.common.security.JwtService;
import com.contractor.app.common.email.PlatformEmailService;
import com.contractor.app.common.security.TokenHasher;
import com.contractor.app.platform.api.dto.ActivateAdminRequest;
import com.contractor.app.platform.api.dto.CreateTenantRequest;
import com.contractor.app.platform.api.dto.CreateTenantResponse;
import com.contractor.app.platform.api.dto.PlatformAdminSummary;
import com.contractor.app.platform.api.dto.PlatformLoginRequest;
import com.contractor.app.platform.api.dto.PlatformLoginResponse;
import com.contractor.app.platform.api.dto.PlatformMfaChallengeRequest;
import com.contractor.app.platform.api.dto.PlatformMfaCodeRequest;
import com.contractor.app.platform.api.dto.PlatformMfaSetupResponse;
import com.contractor.app.platform.security.PlatformTotpService;
import com.contractor.app.platform.security.PlatformTotpSecretCipher;
import java.time.Instant;
import com.contractor.app.platform.api.dto.PlatformTenantSummary;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
import com.contractor.app.platform.infra.PlatformOwnerRecoveryCodeRepository;
import com.contractor.app.platform.domain.PlatformOwnerRecoveryCode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Locale;
import com.contractor.app.platform.infra.TenantAdminActivationTokenRepository;
import com.contractor.app.tenant.domain.Tenant;
import com.contractor.app.tenant.domain.TenantStatus;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.user.domain.Role;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformAdminService {
 private final PlatformOwnerRepository owners; private final TenantRepository tenants; private final UserRepository users;
 private final TenantAdminActivationTokenRepository tokens; private final PasswordEncoder encoder; private final JwtService jwt;
 private final String baseUrl; private final long activationHours; private final boolean linkReturnEnabled; private final PlatformEmailService emailService;
 private final PlatformTotpService totp; private final PlatformTotpSecretCipher totpCipher;
 private final PlatformOwnerRecoveryCodeRepository recoveryCodes;
 private static final SecureRandom RECOVERY_RANDOM = new SecureRandom();
 // A fixed BCrypt hash keeps the password-check cost similar when the account is absent or inactive.
 private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
 private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
 public PlatformAdminService(PlatformOwnerRepository owners,TenantRepository tenants,UserRepository users,TenantAdminActivationTokenRepository tokens,PasswordEncoder encoder,JwtService jwt, PlatformEmailService emailService, Environment environment,
 @Value("${app.platform.activation-base-url:http://localhost:8081/activate-admin/index.html}") String baseUrl,
 @Value("${app.platform.activation-hours:24}") long activationHours,
 @Value("${app.platform.activation-link-return-enabled:false}") boolean linkReturnEnabled, PlatformTotpService totp, PlatformTotpSecretCipher totpCipher, PlatformOwnerRecoveryCodeRepository recoveryCodes){this.owners=owners;this.tenants=tenants;this.users=users;this.tokens=tokens;this.encoder=encoder;this.jwt=jwt;this.emailService=emailService;this.baseUrl=baseUrl;this.activationHours=activationHours;this.linkReturnEnabled=linkReturnEnabled && environment.acceptsProfiles(Profiles.of("dev","test"));this.totp=totp;this.totpCipher=totpCipher;this.recoveryCodes=recoveryCodes;}
 @Transactional
 public PlatformLoginResponse login(PlatformLoginRequest req){
  String username=User.normalizeUsername(req.getUsername());
  PlatformOwner found=owners.findByUsername(username).orElse(null);
  boolean active = found != null && found.isActive();
  String passwordHash = active ? found.getPasswordHash() : DUMMY_PASSWORD_HASH;
  boolean passwordMatches = encoder.matches(req.getPassword(), passwordHash);
  if (!active || !passwordMatches) {
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"بيانات الدخول غير صحيحة");
  }
  PlatformOwner owner = found;
  String purpose = owner.isTotpEnabled() ? "verify" : "setup";
  return new PlatformLoginResponse(owner.isTotpEnabled() ? "MFA_REQUIRED" : "MFA_SETUP_REQUIRED",
    jwt.createPlatformMfaChallenge(owner.getId(), purpose), owner.getId(), owner.getUsername());
 }

 @Transactional
 public PlatformMfaSetupResponse beginTotpSetup(PlatformMfaChallengeRequest req) {
  JwtService.PlatformMfaChallenge challenge = requireChallenge(req.getChallengeToken(), "setup");
  PlatformOwner owner = owners.findByIdForUpdate(challenge.getOwnerId())
    .filter(PlatformOwner::isActive)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "انتهت صلاحية جلسة الإعداد."));
  if (owner.isTotpEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "التحقق بخطوتين مفعّل بالفعل.");
  LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
  String secret;
  if (owner.getTotpSecret() != null && owner.getTotpSetupExpiresAt() != null
      && owner.getTotpSetupExpiresAt().isAfter(now)) {
   secret = totpCipher.decrypt(owner.getTotpSecret());
  } else {
   secret = totp.generateSecret();
   owner.setTotpSecret(totpCipher.encrypt(secret));
   owner.setTotpSetupExpiresAt(now.plusMinutes(10));
  }
  return new PlatformMfaSetupResponse(secret, totp.provisioningUri("Mimaar Platform", owner.getUsername(), secret));
 }

 @Transactional(noRollbackFor = ResponseStatusException.class)
 public PlatformLoginResponse enableTotp(PlatformMfaCodeRequest req) {
  JwtService.PlatformMfaChallenge challenge = requireChallenge(req.getChallengeToken(), "setup");
  PlatformOwner owner = owners.findByIdForUpdate(challenge.getOwnerId()).filter(PlatformOwner::isActive)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "انتهت صلاحية جلسة الإعداد."));
  LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
  if (owner.isTotpEnabled() || owner.getTotpSecret() == null || owner.getTotpSetupExpiresAt() == null
      || !owner.getTotpSetupExpiresAt().isAfter(now)) {
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "انتهت صلاحية إعداد التحقق بخطوتين. سجّل الدخول من جديد.");
  }
  if (owner.getTotpLockedUntil() != null && owner.getTotpLockedUntil().isAfter(now))
   throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "تم إيقاف المحاولات مؤقتًا. حاول لاحقًا.");
  String secret = totpCipher.decrypt(owner.getTotpSecret());
  Long step = totp.matchingStep(secret, req.getCode(), Instant.now().getEpochSecond());
  if (step == null) {
   int failures = owner.getTotpFailedAttempts() + 1;
   owner.setTotpFailedAttempts(failures);
   if (failures >= 5) {
    owner.setTotpFailedAttempts(0);
    owner.setTotpLockedUntil(now.plusMinutes(15));
   }
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "رمز التحقق غير صحيح.");
  }
  owner.setTotpEnabled(true);
  owner.setTotpEnabledAt(now);
  owner.setTotpSetupExpiresAt(null);
  owner.setTotpLastUsedStep(step);
  owner.setTotpFailedAttempts(0);
  owner.setTotpLockedUntil(null);
  recoveryCodes.deleteAllForOwner(owner.getId());
  List<String> rawRecoveryCodes = new ArrayList<>();
  for (int i = 0; i < 10; i++) {
   String rawCode = newRecoveryCode();
   rawRecoveryCodes.add(rawCode);
   recoveryCodes.save(new PlatformOwnerRecoveryCode(owner.getId(), TokenHasher.sha256Hex(normalizeRecoveryCode(rawCode))));
  }
  return new PlatformLoginResponse(jwt.createPlatformOwnerAccessToken(owner.getId()), jwt.getAccessTtlSeconds(),
    owner.getId(), owner.getUsername(), rawRecoveryCodes);
 }

 @Transactional(noRollbackFor = ResponseStatusException.class)
 public PlatformLoginResponse verifyTotp(PlatformMfaCodeRequest req) {
  JwtService.PlatformMfaChallenge challenge = requireChallenge(req.getChallengeToken(), "verify");
  PlatformOwner owner = owners.findByIdForUpdate(challenge.getOwnerId()).filter(PlatformOwner::isActive)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "انتهت صلاحية جلسة الدخول."));
  LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
  if (!owner.isTotpEnabled() || owner.getTotpSecret() == null)
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "التحقق بخطوتين غير مهيأ لهذا الحساب.");
  if (owner.getTotpLockedUntil() != null && owner.getTotpLockedUntil().isAfter(now))
   throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "تم إيقاف المحاولات مؤقتًا. حاول لاحقًا.");
  boolean recoveryUsed = req.getRecoveryCode() != null && !req.getRecoveryCode().trim().isEmpty();
  Long step = null;
  boolean valid;
  if (recoveryUsed) {
   String normalized = normalizeRecoveryCode(req.getRecoveryCode());
   valid = normalized.matches("[A-Z2-9]{12}")
     && recoveryCodes.consumeIfUnused(owner.getId(), TokenHasher.sha256Hex(normalized)) == 1;
  } else {
   step = totp.matchingStep(totpCipher.decrypt(owner.getTotpSecret()), req.getCode(), Instant.now().getEpochSecond());
   valid = step != null && (owner.getTotpLastUsedStep() == null || step > owner.getTotpLastUsedStep());
  }
  if (!valid) {
   int failures = owner.getTotpFailedAttempts() + 1;
   owner.setTotpFailedAttempts(failures);
   if (failures >= 5) {
    owner.setTotpFailedAttempts(0);
    owner.setTotpLockedUntil(now.plusMinutes(15));
   }
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "رمز التحقق غير صحيح أو سبق استخدامه.");
  }
  if (step != null) owner.setTotpLastUsedStep(step);
  owner.setTotpFailedAttempts(0);
  owner.setTotpLockedUntil(null);
  return new PlatformLoginResponse(jwt.createPlatformOwnerAccessToken(owner.getId()), jwt.getAccessTtlSeconds(),
    owner.getId(), owner.getUsername());
 }

 private String newRecoveryCode() {
  StringBuilder code = new StringBuilder(12);
  for (int i = 0; i < 12; i++) code.append(RECOVERY_ALPHABET[RECOVERY_RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
  return code.toString();
 }

 private String normalizeRecoveryCode(String value) {
  return value == null ? "" : value.replace("-", "").replaceAll("\\s", "").toUpperCase(Locale.ROOT);
 }

 private JwtService.PlatformMfaChallenge requireChallenge(String token, String purpose) {
  JwtService.PlatformMfaChallenge challenge = jwt.parsePlatformMfaChallenge(token)
    .filter(value -> purpose.equals(value.getPurpose()))
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "رمز جلسة التحقق غير صالح أو منتهي."));
  return challenge;
 }
 @Transactional(readOnly=true)
 public List<PlatformTenantSummary> listTenants(){
  return tenants.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
   .map(tenant -> {
    PlatformAdminSummary admin = users.findByTenantIdOrderByFullNameAsc(tenant.getId()).stream()
     .filter(user -> user.getRole() == Role.ADMIN)
     .findFirst()
     .map(user -> new PlatformAdminSummary(user.getId(), user.getFullName(), user.getUsername(),
       user.isActive(), user.isPasswordActivated()))
     .orElse(null);
    return new PlatformTenantSummary(tenant.getId(), tenant.getName(), tenant.getStatus().name(),
      tenant.getCreatedAt(), admin);
   })
   .collect(Collectors.toList());
 }
 @Transactional
 public void updateTenantStatus(Long tenantId, com.contractor.app.platform.api.dto.UpdateTenantStatusRequest.TenantStatus requestedStatus) {
  Tenant tenant = tenants.findById(tenantId)
   .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الشركة غير موجودة"));
  tenant.setStatus(requestedStatus == com.contractor.app.platform.api.dto.UpdateTenantStatusRequest.TenantStatus.ACTIVE
   ? TenantStatus.ACTIVE : TenantStatus.SUSPENDED);
  tenants.save(tenant);
 }
 @Transactional
 public CreateTenantResponse createTenant(CreateTenantRequest req){
  String username=User.normalizeUsername(req.getAdminUsername());
  if(users.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"اسم المستخدم مستخدم بالفعل");
  Tenant tenant=tenants.saveAndFlush(new Tenant(req.getTenantName().trim()));
  String raw=TokenHasher.newOpaqueToken();
  User admin=new User(tenant.getId(),username,req.getAdminFullName().trim(),req.getAdminPhone(),encoder.encode(TokenHasher.newOpaqueToken()),Role.ADMIN);
  admin.setEmail(req.getAdminEmail()); admin.setPasswordActivated(false); admin=users.saveAndFlush(admin);
  LocalDateTime expiry=LocalDateTime.now(ZoneOffset.UTC).plusHours(activationHours);
  tokens.save(new TenantAdminActivationToken(tenant.getId(),admin.getId(),TokenHasher.sha256Hex(raw),expiry));
  String sep=baseUrl.contains("?")?"&":"?";
  String activationUrl=baseUrl+sep+"token="+java.net.URLEncoder.encode(raw,java.nio.charset.StandardCharsets.UTF_8);
  boolean emailSent=false;
  if(emailService.isEnabled()){emailService.sendActivation(admin.getEmail(),admin.getFullName(),activationUrl,expiry);emailSent=true;}
  else if(!linkReturnEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"خدمة البريد الإلكتروني غير مهيأة. لم يتم إنشاء الشركة.");
  return new CreateTenantResponse(tenant.getId(),tenant.getName(),admin.getId(),admin.getUsername(),emailSent?null:activationUrl,expiry.toString(),emailSent?"EMAIL_SENT":"DEV_LINK");
 }
 @Transactional
 public CreateTenantResponse resendActivation(Long tenantId) {
  User admin=users.findByTenantIdOrderByFullNameAsc(tenantId).stream()
   .filter(user -> user.getRole()==Role.ADMIN && !user.isPasswordActivated()).findFirst()
   .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"حساب مدير غير مفعّل غير موجود"));
  if(admin.getEmail()==null || admin.getEmail().trim().isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"لا يوجد بريد إلكتروني محفوظ لهذا الحساب.");
  if(!emailService.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"خدمة البريد الإلكتروني غير مهيأة.");
  tokens.revokeOutstandingForUser(admin.getId(),tenantId);
  String raw=TokenHasher.newOpaqueToken();
  LocalDateTime expiry=LocalDateTime.now(ZoneOffset.UTC).plusHours(activationHours);
  tokens.save(new TenantAdminActivationToken(tenantId,admin.getId(),TokenHasher.sha256Hex(raw),expiry));
  String sep=baseUrl.contains("?")?"&":"?";
  String activationUrl=baseUrl+sep+"token="+java.net.URLEncoder.encode(raw,java.nio.charset.StandardCharsets.UTF_8);
  emailService.sendActivation(admin.getEmail(),admin.getFullName(),activationUrl,expiry);
  return new CreateTenantResponse(tenantId,"",admin.getId(),admin.getUsername(),null,expiry.toString(),"EMAIL_SENT");
 }

 @Transactional
 public void activateAdmin(ActivateAdminRequest req){
  TenantAdminActivationToken token=tokens.findByTokenHashForUpdate(TokenHasher.sha256Hex(req.getToken()))
   .orElseThrow(()->invalidToken());
  LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
  if(token.isRevoked()||token.getConsumedAt()!=null||!token.getExpiresAt().isAfter(now)) throw invalidToken();
  User admin=users.findByIdAndTenantId(token.getUserId(),token.getTenantId()).orElseThrow(()->invalidToken());
  if(admin.isPasswordActivated()) throw invalidToken();
  admin.setPasswordHash(encoder.encode(req.getPassword())); admin.setPasswordActivated(true);
  if(admin.getEmail()!=null) admin.setEmailVerified(true);
  token.consume();
 }
 private ResponseStatusException invalidToken(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"رابط التفعيل غير صالح أو منتهي");}
}
