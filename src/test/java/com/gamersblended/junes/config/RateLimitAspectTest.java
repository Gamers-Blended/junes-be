package com.gamersblended.junes.config;

import com.gamersblended.junes.annotation.RateLimit;
import com.gamersblended.junes.dto.request.CreateUserRequest;
import com.gamersblended.junes.service.RateLimiterService;
import com.gamersblended.junes.util.JwtUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

    private static final SecretKey SIGNING_KEY =
            Keys.hmacShaKeyFor("a".repeat(32).getBytes(StandardCharsets.UTF_8));

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private JwtUtils jwtUtils;

    private RateLimitAspect aspect;

    // ---- fixtures whose annotated methods drive the aspect's reflection-based logic ----

    static class Fixture {
        @RateLimit
        public void defaultMethod() {
            // Never invoked: aspect is exercised via mockJoinPoint(), which only reads this
            // method's reflected signature and annotations, so body has nothing to do
        }

        @RateLimit(key = "custom-key")
        public void customKeyMethod() {
            // Never invoked: see defaultMethod()
        }

        @RateLimit(keyFromRequestBody = "email")
        public void emailFromBodyMethod(Object request) {
            // Never invoked: see defaultMethod()
        }

        @RateLimit(keyFromRequestParam = "identifier")
        public void paramByValueMethod(@RequestParam("identifier") String identifier) {
            // Never invoked: see defaultMethod()
        }

        @RateLimit(keyFromRequestParam = "identifier")
        public void paramByNameAttributeMethod(@RequestParam(name = "identifier") String param) {
            // Never invoked: see defaultMethod()
        }

        @RateLimit(keyFromRequestParam = "identifier")
        public void paramByParameterNameMethod(@RequestParam String identifier) {
            // Never invoked: see defaultMethod()
        }

        @RateLimit(perUser = true)
        public void perUserMethod() {
            // Never invoked: see defaultMethod()
        }

        public void notAnnotatedMethod() {
            // Never invoked: deliberately carries no @RateLimit, to prove the aspect proceeds
            // untouched when annotation lookup finds nothing
        }
    }

    @RateLimit(key = "class-level-key")
    static class ClassAnnotatedFixture {
        public void inheritedMethod() {
            // Never invoked: relies solely on the class-level @RateLimit above, since this method
            // itself is deliberately left unannotated
        }

        @RateLimit(key = "method-level-key")
        public void overriddenMethod() {
            // Never invoked: see defaultMethod()
        }
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void buildAspect() {
        aspect = new RateLimitAspect(rateLimiterService, jwtUtils);
        ReflectionTestUtils.setField(aspect, "accessSecretKey", "unused-since-jwtUtils-is-mocked");
    }

    private void setCurrentRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ProceedingJoinPoint mockJoinPoint(Method method, Object... args) throws Throwable {
        MethodSignature signature = mock(MethodSignature.class);
        lenient().when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(joinPoint.getArgs()).thenReturn(args);
        lenient().when(joinPoint.proceed()).thenReturn("proceeded");
        return joinPoint;
    }

    // ---- annotation resolution ----

    @Test
    void rateLimit_proceedsWithoutTouchingRequest_whenMethodNotAnnotated() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("notAnnotatedMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("proceeded");
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void rateLimit_usesClassLevelAnnotation_whenNoMethodLevelAnnotationPresent() throws Throwable {
        buildAspect();
        Method method = ClassAnnotatedFixture.class.getMethod("inheritedMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("1.1.1.1"));
        when(rateLimiterService.isAllowed(eq("class-level-key:1.1.1.1"), any())).thenReturn(true);

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("proceeded");
        verify(rateLimiterService).isAllowed(eq("class-level-key:1.1.1.1"), any());
    }

    @Test
    void rateLimit_methodLevelAnnotation_takesPrecedenceOverClassLevel() throws Throwable {
        buildAspect();
        Method method = ClassAnnotatedFixture.class.getMethod("overriddenMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("1.1.1.1"));
        when(rateLimiterService.isAllowed(eq("method-level-key:1.1.1.1"), any())).thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("method-level-key:1.1.1.1"), any());
    }

    // ---- allow / deny outcomes ----

    @Test
    void rateLimit_proceeds_whenUnderLimit_usingDefaultClassAndMethodBasedKey() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("defaultMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("10.0.0.5"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:defaultMethod:10.0.0.5"), any())).thenReturn(true);

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isEqualTo("proceeded");
    }

    @Test
    void rateLimit_returnsTooManyRequests_withRetryHeadersAndBody_whenOverLimit() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("defaultMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("10.0.0.5"));
        when(rateLimiterService.isAllowed(anyString(), any())).thenReturn(false);
        when(rateLimiterService.getRemainingTimeInSeconds(anyString(), any())).thenReturn(42L);

        Object result = aspect.rateLimit(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        // Wildcard cast (unlike a cast to ResponseEntity<Map<String, Object>>) is fully checked by compiler
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeaders().getFirst("X-RateLimit-Reset")).isNotNull();

        assertThat(response.getBody())
                .asInstanceOf(MAP)
                .containsEntry("error", "Rate limit exceeded")
                .containsEntry("message", "Too many requests. Limit: 5 requests per 1 minutes")
                .containsEntry("retryAfterSeconds", 42L)
                .containsKey("timestamp");

        verify(joinPoint, never()).proceed();
    }

    // ---- key construction: custom key ----

    @Test
    void rateLimit_appendsIpToCustomKey_whenKeySpecified() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("customKeyMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("2.2.2.2"));
        when(rateLimiterService.isAllowed(eq("custom-key:2.2.2.2"), any())).thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("custom-key:2.2.2.2"), any());
    }

    // ---- key construction: request body field ----

    @Test
    void rateLimit_usesEmailFromRequestBody_whenArgumentIsCreateUserRequest() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("emailFromBodyMethod", Object.class);
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("user@example.com");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, request);
        setCurrentRequest(requestWithIp("3.3.3.3"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:emailFromBodyMethod:user@example.com"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:emailFromBodyMethod:user@example.com"), any());
    }

    @Test
    void rateLimit_fallsBackToIp_whenRequestBodyArgumentIsNotRecognisedType() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("emailFromBodyMethod", Object.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, "not-a-create-user-request");
        setCurrentRequest(requestWithIp("4.4.4.4"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:emailFromBodyMethod:4.4.4.4"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("rate_limit:Fixture:emailFromBodyMethod:4.4.4.4"), any());
    }

    // ---- key construction: request param field ----

    @Test
    void rateLimit_usesRequestParamValueAttribute_toLocateArgument() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("paramByValueMethod", String.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, "abc-123");
        setCurrentRequest(requestWithIp("5.5.5.5"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:paramByValueMethod:abc-123"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("rate_limit:Fixture:paramByValueMethod:abc-123"), any());
    }

    @Test
    void rateLimit_usesRequestParamNameAttribute_whenValueAttributeEmpty() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("paramByNameAttributeMethod", String.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, "xyz-789");
        setCurrentRequest(requestWithIp("6.6.6.6"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:paramByNameAttributeMethod:xyz-789"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:paramByNameAttributeMethod:xyz-789"), any());
    }

    @Test
    void rateLimit_fallsBackToParameterName_whenRequestParamHasNoValueOrName() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("paramByParameterNameMethod", String.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint(method, "param-name-value");
        setCurrentRequest(requestWithIp("7.7.7.7"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:paramByParameterNameMethod:param-name-value"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:paramByParameterNameMethod:param-name-value"), any());
    }

    // ---- key construction: per-user ----

    @Test
    void rateLimit_perUser_usesJwtSubject_whenValidBearerTokenPresent() throws Throwable {
        buildAspect();
        lenient().when(jwtUtils.getSigningKey(anyString())).thenReturn(SIGNING_KEY);
        Method method = Fixture.class.getMethod("perUserMethod");
        String token = Jwts.builder().subject("user@example.com").signWith(SIGNING_KEY).compact();
        MockHttpServletRequest request = requestWithIp("8.8.8.8");
        request.addHeader("Authorization", "Bearer " + token);
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(request);
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:perUserMethod:user:user@example.com"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:perUserMethod:user:user@example.com"), any());
    }

    @Test
    void rateLimit_perUser_fallsBackToSessionId_whenNoAuthorizationHeader() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("perUserMethod");
        MockHttpServletRequest request = requestWithIp("9.9.9.9");
        request.addHeader("X-Session-Id", "session-abc");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(request);
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:perUserMethod:session:session-abc"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:perUserMethod:session:session-abc"), any());
    }

    @Test
    void rateLimit_perUser_fallsBackToIp_whenNoUserOrSessionIdentifierAvailable() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("perUserMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("11.11.11.11"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:perUserMethod:11.11.11.11"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:perUserMethod:11.11.11.11"), any());
    }

    @Test
    void rateLimit_perUser_ignoresInvalidToken_andFallsBackToIp() throws Throwable {
        buildAspect();
        lenient().when(jwtUtils.getSigningKey(anyString())).thenReturn(SIGNING_KEY);
        Method method = Fixture.class.getMethod("perUserMethod");
        MockHttpServletRequest request = requestWithIp("12.12.12.12");
        request.addHeader("Authorization", "Bearer not-a-real-jwt");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(request);
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:perUserMethod:12.12.12.12"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(
                eq("rate_limit:Fixture:perUserMethod:12.12.12.12"), any());
    }

    // ---- IP address resolution ----

    @Test
    void rateLimit_prefersXForwardedFor_overOtherIpSources() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("defaultMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 70.41.3.18");
        request.addHeader("X-Real-IP", "198.51.100.7");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(request);
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:defaultMethod:203.0.113.5"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("rate_limit:Fixture:defaultMethod:203.0.113.5"), any());
    }

    @Test
    void rateLimit_fallsBackToXRealIp_whenNoForwardedForHeader() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("defaultMethod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(request);
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:defaultMethod:198.51.100.7"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("rate_limit:Fixture:defaultMethod:198.51.100.7"), any());
    }

    @Test
    void rateLimit_fallsBackToRemoteAddr_whenNoForwardingHeadersPresent() throws Throwable {
        buildAspect();
        Method method = Fixture.class.getMethod("defaultMethod");
        ProceedingJoinPoint joinPoint = mockJoinPoint(method);
        setCurrentRequest(requestWithIp("192.168.0.1"));
        when(rateLimiterService.isAllowed(eq("rate_limit:Fixture:defaultMethod:192.168.0.1"), any()))
                .thenReturn(true);

        aspect.rateLimit(joinPoint);

        verify(rateLimiterService).isAllowed(eq("rate_limit:Fixture:defaultMethod:192.168.0.1"), any());
    }

    private MockHttpServletRequest requestWithIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }
}
