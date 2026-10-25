package com.contractor.app.platform.domain;

import com.contractor.app.common.security.JwtService;
import com.contractor.app.common.security.TokenHasher;
import com.contractor.app.platform.api.dto.ActivateAdminRequest;
import com.contractor.app.platform.api.dto.CreateTenantRequest;
import com.contractor.app.platform.api.dto.CreateTenantResponse;
import com.contractor.app.platform.api.dto.PlatformLoginRequest;
import com.contractor.app.platform.api.dto.PlatformLoginResponse;
import com.contractor.app.platform.infra.PlatformOwnerRepository;
import com.contractor.app.platform.infra.TenantAdminActivationTokenRepository;
import com.contractor.app.tenant.domain.Tenant;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.user.domain.Role;
import com.contractor.app.user.domain.User;
import com.contractor.app.user.infra.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformAdminService {
 private final PlatformOwnerRepository owners; private final TenantRepository tenants; private final UserRepository users;
 private final TenantAdminActivationTokenRepository tokens; private final PasswordEncoder encoder; private final JwtService jwt;
 private final String baseUrl; private final long activationHours;
 public PlatformAdminService(PlatformOwnerRepository owners,TenantRepository tenants,UserRepository users,TenantAdminActivationTokenRepository tokens,PasswordEncoder encoder,JwtService jwt,
 @Value("${app.platform.activation-base-url:http://localhost:8081/activate-admin}") String baseUrl,
 @Value("${app.platform.activation-hours:24}") long activationHours){this.owners=owners;this.tenants=tenants;this.users=users;this.tokens=tokens;this.encoder=encoder;this.jwt=jwt;this.baseUrl=baseUrl;this.activationHours=activationHours;}
 @Transactional(readOnly=true)
 public PlatformLoginResponse login(PlatformLoginRequest req){
  String username=User.normalizeUsername(req.getUsername());
  PlatformOwner owner=owners.findByUsername(username).filter(PlatformOwner::isActive).filter(o->encoder.matches(req.getPassword(),o.getPasswordHash()))
   .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"بيانات الدخول غير صحيحة"));
  return new PlatformLoginResponse(jwt.createPlatformOwnerAccessToken(owner.getId()),jwt.getAccessTtlSeconds(),owner.getId(),owner.getUsername());
 }
 @Transactional
 public CreateTenantResponse createTenant(CreateTenantRequest req){
  String username=User.normalizeUsername(req.getAdminUsername());
  if(users.existsByUsername(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"اسم المستخدم مستخدم بالفعل");
  Tenant tenant=tenants.saveAndFlush(new Tenant(req.getTenantName().trim()));
  String raw=TokenHasher.newOpaqueToken();
  User admin=new User(tenant.getId(),username,req.getAdminFullName().trim(),req.getAdminPhone(),encoder.encode(TokenHasher.newOpaqueToken()),Role.ADMIN);
  admin.setPasswordActivated(false); admin=users.saveAndFlush(admin);
  LocalDateTime expiry=LocalDateTime.now(ZoneOffset.UTC).plusHours(activationHours);
  tokens.save(new TenantAdminActivationToken(tenant.getId(),admin.getId(),TokenHasher.sha256Hex(raw),expiry));
  String sep=baseUrl.contains("?")?"&":"?";
  return new CreateTenantResponse(tenant.getId(),tenant.getName(),admin.getId(),admin.getUsername(),baseUrl+sep+"token="+raw,expiry.toString());
 }
 @Transactional
 public void activateAdmin(ActivateAdminRequest req){
  TenantAdminActivationToken token=tokens.findByTokenHashForUpdate(TokenHasher.sha256Hex(req.getToken()))
   .orElseThrow(()->invalidToken());
  LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
  if(token.isRevoked()||token.getConsumedAt()!=null||!token.getExpiresAt().isAfter(now)) throw invalidToken();
  User admin=users.findByIdAndTenantId(token.getUserId(),token.getTenantId()).orElseThrow(()->invalidToken());
  if(admin.isPasswordActivated()) throw invalidToken();
  admin.setPasswordHash(encoder.encode(req.getPassword())); admin.setPasswordActivated(true); token.consume();
 }
 private ResponseStatusException invalidToken(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"رابط التفعيل غير صالح أو منتهي");}
}
