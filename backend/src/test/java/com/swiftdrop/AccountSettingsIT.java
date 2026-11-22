package com.swiftdrop;

import com.swiftdrop.controller.identity.RegistrationController.CsrfResponse;
import com.swiftdrop.dto.identity.*;
import com.swiftdrop.dto.identity.AccountSecurityRequests.*;
import com.swiftdrop.exception.*;
import com.swiftdrop.integration.identity.AccountSecurityDelivery;
import com.swiftdrop.repository.identity.AuthSessionRepository;
import com.swiftdrop.security.IdentityContext;
import com.swiftdrop.service.identity.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"swiftdrop.google.enabled=false","swiftdrop.events.enabled=false"})
class AccountSettingsIT {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.11-bookworm");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",DB::getJdbcUrl);p.add("spring.datasource.username",DB::getUsername);p.add("spring.datasource.password",DB::getPassword);
        p.add("spring.flyway.url",DB::getJdbcUrl);p.add("spring.flyway.user",DB::getUsername);p.add("spring.flyway.password",DB::getPassword);p.add("spring.rabbitmq.password",()->"unused-fixture");
    }
    @Autowired AccountSecurityService security;
    @Autowired SavedAddressService addresses;
    @Autowired IdentitySessionService identities;
    @Autowired AccessTokenService access;
    @Autowired AuthSessionRepository sessions;
    @Autowired JdbcTemplate sql;
    @Autowired PasswordEncoder passwords;
    @Autowired TestRestTemplate http;
    @MockitoBean AccountSecurityDelivery mail;
    static final String PASSWORD="Test-only password 12";
    record Fixture(UUID id,UUID sid,String email,IdentityContext identity,String jwt) { }
    Fixture fixture(boolean withPassword){
        var id=UUID.randomUUID();var sid=UUID.randomUUID();String email=id+"@example.test";
        sql.update("insert into swiftdrop.users(id,email,full_name,password_hash,email_verified_at) values(?,?,?,?,current_timestamp)",id,email,"Profile fixture",withPassword?passwords.encode(PASSWORD):null);
        sql.update("insert into swiftdrop.user_roles(user_id,role_id) select ?,id from swiftdrop.roles where name='CUSTOMER'",id);
        sql.update("insert into swiftdrop.auth_sessions(id,user_id,authorization_version,expires_at) values(?,?,0,current_timestamp+interval '1 hour')",sid,id);
        return new Fixture(id,sid,email,new IdentityContext(id,sid,Set.of("CUSTOMER"),0),access.issue(sessions.findById(sid).orElseThrow()).accessToken());
    }
    String requestEmail(Fixture user,String email){
        security.requestEmail(user.identity(),new ChangeEmail(email,PASSWORD,null));
        var message=ArgumentCaptor.forClass(String.class);
        verify(mail,timeout(5000)).send(eq(email),eq("Confirm your SwiftDrop email change"),message.capture());
        return message.getValue().split("#token=",2)[1].substring(0,43);
    }
    SavedAddressRequest address(String label,boolean defaultPickup,long version){return new SavedAddressRequest(label,"Test contact","0771234567","1 Test Road","","Colombo","00600",defaultPickup,version);}
    @BeforeEach void clearMail(){reset(mail);}

    @Test void emailIsUnchangedUntilSingleUseConfirmationThenAllSessionsAndOldResetTokensAreRevoked(){
        var user=fixture(true);String next=UUID.randomUUID()+"@example.test";
        String raw=requestEmail(user,next);
        assertThat(sql.queryForObject("select email from swiftdrop.users where id=?",String.class,user.id())).isEqualTo(user.email());
        assertThat(sql.queryForObject("select token_hash from swiftdrop.account_action_tokens where user_id=?",String.class,user.id())).isEqualTo(TokenSecrets.hash(raw)).isNotEqualTo(raw);
        sql.update("insert into swiftdrop.password_reset_tokens(user_id,token_hash,expires_at) values(?,?,current_timestamp+interval '30 minutes')",user.id(),TokenSecrets.hash(TokenSecrets.generate()));
        security.confirmEmail(raw);
        assertThat(sql.queryForObject("select email from swiftdrop.users where id=?",String.class,user.id())).isEqualTo(next);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.auth_sessions where user_id=? and revoked_at is null",Integer.class,user.id())).isZero();
        assertThat(sql.queryForObject("select count(*) from swiftdrop.password_reset_tokens where user_id=? and revoked_at is null",Integer.class,user.id())).isZero();
        assertThatThrownBy(()->security.confirmEmail(raw)).isInstanceOf(AccountSettingsException.class);
        verify(mail,timeout(5000)).send(eq(user.email()),eq("Your SwiftDrop email was changed"),anyString());
        assertThat(sql.queryForObject("select count(*) from swiftdrop.identity_audit_events where target_user_id=? and action='EMAIL_CHANGED'",Integer.class,user.id())).isEqualTo(1);
    }
    @Test void expiredSupersededAndDuplicateEmailRequestsCannotChangeTheIdentity(){
        var user=fixture(true);var other=fixture(true);
        security.requestEmail(user.identity(),new ChangeEmail(other.email(),PASSWORD,null));
        assertThat(sql.queryForObject("select count(*) from swiftdrop.account_action_tokens where user_id=?",Integer.class,user.id())).isZero();
        String first=requestEmail(user,UUID.randomUUID()+"@example.test");
        String second=requestEmail(user,UUID.randomUUID()+"@example.test");
        assertThatThrownBy(()->security.confirmEmail(first)).isInstanceOf(AccountSettingsException.class);
        sql.update("update swiftdrop.account_action_tokens set created_at=current_timestamp-interval '2 minutes',expires_at=current_timestamp-interval '1 second' where token_hash=?",TokenSecrets.hash(second));
        assertThatThrownBy(()->security.confirmEmail(second)).isInstanceOf(AccountSettingsException.class);
        assertThat(sql.queryForObject("select email from swiftdrop.users where id=?",String.class,user.id())).isEqualTo(user.email());
    }
    @Test void competingEmailClaimsUseDatabaseUniquenessAndRollbackTheLosingChange(){
        var first=fixture(true);var second=fixture(true);String target=UUID.randomUUID()+"@example.test";
        String firstLink=requestEmail(first,target);reset(mail);
        String secondLink=requestEmail(second,target);
        security.confirmEmail(firstLink);
        assertThatThrownBy(()->security.confirmEmail(secondLink)).isInstanceOf(AccountSettingsException.class);
        assertThat(sql.queryForObject("select email from swiftdrop.users where id=?",String.class,second.id())).isEqualTo(second.email());
        assertThat(sql.queryForObject("select count(*) from swiftdrop.auth_sessions where user_id=? and revoked_at is null",Integer.class,second.id())).isEqualTo(1);
    }
    @Test void changingPasswordRequiresCurrentPasswordAndInvalidatesAccessAndPendingEmail(){
        var user=fixture(true);String proof=requestEmail(user,UUID.randomUUID()+"@example.test");
        assertThatThrownBy(()->security.changePassword(user.identity(),new ChangePassword("wrong","New password 12",null))).isInstanceOf(AccountSettingsException.class);
        security.changePassword(user.identity(),new ChangePassword(PASSWORD,"New password 12",null));
        assertThat(passwords.matches("New password 12",sql.queryForObject("select password_hash from swiftdrop.users where id=?",String.class,user.id()))).isTrue();
        assertThatThrownBy(()->identities.currentIdentity(user.id(),user.sid(),0)).isInstanceOf(IdentityAuthenticationException.class);
        assertThatThrownBy(()->security.confirmEmail(proof)).isInstanceOf(AccountSettingsException.class);
    }
    @Test void googleProofRequiresSameLinkedSubjectFreshAuthTimeAndIsBoundToSessionAndSingleUse(){
        var user=fixture(false);var wrong=fixture(false);String subject=UUID.randomUUID().toString();
        sql.update("insert into swiftdrop.oauth_accounts(user_id,provider,provider_subject) values(?,'google',?)",user.id(),subject);
        var intent=security.beginGoogle(user.identity());
        var now=Instant.now();
        assertThatThrownBy(()->security.completeGoogle(intent,google(subject,now.minusSeconds(600)))).isInstanceOf(AccountSettingsException.class);
        assertThatThrownBy(()->security.completeGoogle(intent,google("unlinked-subject",now))).isInstanceOf(AccountSettingsException.class);
        String proof=security.completeGoogle(intent,google(subject,now));
        assertThatThrownBy(()->security.requestEmail(wrong.identity(),new ChangeEmail("next@example.test",null,proof))).isInstanceOf(AccountSettingsException.class);
        security.requestEmail(user.identity(),new ChangeEmail(UUID.randomUUID()+"@example.test",null,proof));
        assertThatThrownBy(()->security.requestEmail(user.identity(),new ChangeEmail("next@example.test",null,proof))).isInstanceOf(AccountSettingsException.class);
        assertThat(sql.queryForObject("select count(*) from swiftdrop.oauth_accounts where user_id=? and provider_subject=?",Integer.class,user.id(),subject)).isEqualTo(1);
    }
    OidcUser google(String subject,Instant authentication){
        var now=Instant.now();
        return new DefaultOidcUser(Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
            new OidcIdToken("test-id-token",now,now.plusSeconds(300),Map.of("sub",subject,"email","provider@example.test","email_verified",true,"auth_time",authentication.getEpochSecond())));
    }
    @Test void addressOwnershipVersionsAndConcurrentDefaultsAreEnforced() throws Exception {
        var user=fixture(true);var other=fixture(true);
        var address=addresses.create(user.identity(),address("Home",true,0));
        assertThatThrownBy(()->addresses.update(other.identity(),address.id(),address("Stolen",false,0))).isInstanceOf(AccountSettingsException.class);
        assertThatThrownBy(()->addresses.delete(other.identity(),address.id(),0)).isInstanceOf(AccountSettingsException.class);
        var updated=addresses.update(user.identity(),address.id(),address("Updated home",true,address.version()));
        assertThatThrownBy(()->addresses.delete(user.identity(),address.id(),address.version())).isInstanceOf(ProfileConflictException.class);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->addresses.create(user.identity(),address("Work",true,0)));
            var b=pool.submit(()->addresses.create(user.identity(),address("Family",true,0)));
            a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertThat(addresses.list(user.identity()).stream().filter(SavedAddressView::defaultPickup)).hasSize(1);
        assertThat(addresses.list(other.identity())).isEmpty();
        assertThat(updated.label()).isEqualTo("Updated home");
    }
    @Test void securityAndAddressHttpMutationsRequireCsrfAndRejectClientOwnerFields(){
        var user=fixture(true);var h=new HttpHeaders();h.setBearerAuth(user.jwt());h.setContentType(MediaType.APPLICATION_JSON);h.setOrigin("http://localhost:5173");
        assertThat(http.exchange("/api/v1/me/security/password",HttpMethod.POST,new HttpEntity<>(Map.of("currentPassword",PASSWORD,"password","New password 12"),h),String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(http.exchange("/api/v1/auth/email-change/confirm",HttpMethod.POST,new HttpEntity<>(Map.of("token",TokenSecrets.generate()),h),String.class).getStatusCode().value()).isEqualTo(403);
        var csrf=http.getForEntity("/api/v1/auth/csrf",CsrfResponse.class);
        h.set(csrf.getBody().headerName(),csrf.getBody().token());h.set(HttpHeaders.COOKIE,csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0]);
        assertThat(http.exchange("/api/v1/me/addresses",HttpMethod.POST,new HttpEntity<>(Map.of("userId",UUID.randomUUID()),h),String.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.exchange("/api/v1/me/security/password",HttpMethod.POST,new HttpEntity<>(Map.of("currentPassword",PASSWORD,"password","short"),h),String.class).getStatusCode().value()).isEqualTo(400);
    }
}
