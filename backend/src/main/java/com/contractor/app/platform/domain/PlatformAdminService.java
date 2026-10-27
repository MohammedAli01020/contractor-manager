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
import com.contractor.app.platform.api.dto.PlatformTenantSummary;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
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
 public PlatformAdminService(PlatformOwnerRepository owners,TenantRepository tenants,UserRepository users,TenantAdminActivationTokenRepository tokens,PasswordEncoder encoder,JwtService jwt, PlatformEmailService emailService, Environment environment,
 @Value("${app.platform.activation-base-url:http://localhost:8081/activate-admin/index.html}") String baseUrl,
 @Value("${app.platform.activation-hours:24}") long activationHours,
 @Value("${app.platform.activation-link-return-enabled:false}") boolean linkReturnEnabled){this.owners=owners;this.tenants=tenants;this.users=users;this.encoder=encoder;this.jwt=jwt;this.emailService=emailService;this.baseUrl=baseUrl;this.activationHours=activationHours;this.linkReturnEnabled=linkReturnEnabled && environment.acceptsProfiles(Profiles.of("dev","test"));}
 @Transactional(readOnly=true)
 public PlatformLoginResponse login(PlatformLoginRequest req){
  String username=User.normalizeUsername(req.getUsername());
  PlatformOwner owner=owners.findByUsername(username).filter(PlatformOwner::isActive).filter(o->encoder.matches(req.getPassword(),o.getPasswordHash()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"بيانات الدخول غير صحيحة"));
  return new PlatformLoginResponse(jwt.createPlatformOwnerAccessToken(owner.getId()),jwt.getAccessTtlSeconds(),owner.getId(),owner.getUsername());
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
