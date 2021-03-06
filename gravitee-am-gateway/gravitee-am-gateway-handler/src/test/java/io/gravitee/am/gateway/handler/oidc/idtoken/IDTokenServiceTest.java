/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.oidc.idtoken;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.jwt.JwtService;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.idtoken.impl.IDTokenServiceImpl;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IDTokenServiceTest {

    @InjectMocks
    private IDTokenService idTokenService = new IDTokenServiceImpl();

    @Mock
    private CertificateManager certificateManager;

    @Mock
    private CertificateProvider certificateProvider;

    @Mock
    private CertificateProvider defaultCertificateProvider;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private JwtService jwtService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void shouldCreateIDToken_clientOnly_clientCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("client-certificate");

        String idTokenPayload = "payload";

        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    @Test
    public void shouldCreateIDToken_clientOnly_defaultCertificate() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("certificate-client");

        String idTokenPayload = "payload";

        when(certificateManager.get(anyString())).thenReturn(Maybe.empty());
        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just(idTokenPayload));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, null).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    @Test
    @Ignore
    // ignore due to map order and current timestamp (local test)
    public void shouldCreateIDToken_withUser_claimsRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        oAuth2Request.setSubject("subject");
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
        requestParameters.put("claims", Collections.singletonList("{\"id_token\":{\"name\":{\"essential\":true}}}"));
        oAuth2Request.setRequestParameters(requestParameters);

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.setExp(expectedJwt.getIat() + 14400);

        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));
        ((IDTokenServiceImpl) idTokenService).setObjectMapper(objectMapper);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    @Test
    @Ignore
    // ignore due to map order and current timestamp (local test)
    public void shouldCreateIDToken_withUser_scopesRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "profile")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.put(StandardClaims.WEBSITE, user.getAdditionalInformation().get(StandardClaims.WEBSITE));
        expectedJwt.put(StandardClaims.ZONEINFO, user.getAdditionalInformation().get(StandardClaims.ZONEINFO));
        expectedJwt.put(StandardClaims.BIRTHDATE, user.getAdditionalInformation().get(StandardClaims.BIRTHDATE));
        expectedJwt.put(StandardClaims.GENDER, user.getAdditionalInformation().get(StandardClaims.GENDER));
        expectedJwt.put(StandardClaims.PROFILE, user.getAdditionalInformation().get(StandardClaims.PROFILE));
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.PREFERRED_USERNAME, user.getAdditionalInformation().get(StandardClaims.PREFERRED_USERNAME));
        expectedJwt.put(StandardClaims.GIVEN_NAME, user.getAdditionalInformation().get(StandardClaims.GIVEN_NAME));
        expectedJwt.put(StandardClaims.MIDDLE_NAME, user.getAdditionalInformation().get(StandardClaims.MIDDLE_NAME));
        expectedJwt.put(StandardClaims.LOCALE, user.getAdditionalInformation().get(StandardClaims.LOCALE));
        expectedJwt.put(StandardClaims.PICTURE, user.getAdditionalInformation().get(StandardClaims.PICTURE));
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.UPDATED_AT, user.getAdditionalInformation().get(StandardClaims.UPDATED_AT));
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.put(StandardClaims.NICKNAME, user.getAdditionalInformation().get(StandardClaims.NICKNAME));
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.FAMILY_NAME, user.getAdditionalInformation().get(StandardClaims.FAMILY_NAME));

        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    @Test
    @Ignore
    // ignore due to map order and current timestamp (local test)
    public void shouldCreateIDToken_withUser_scopesRequest_email() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "email")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.setIss(null);
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));

        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    @Test
    @Ignore
    // ignore due to map order and current timestamp (local test)
    public void shouldCreateIDToken_withUser_scopesRequest_email_address() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "email", "address")));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.ADDRESS, user.getAdditionalInformation().get(StandardClaims.ADDRESS));
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.setIss(null);
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));

        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    @Test
    @Ignore
    // ignore due to map order and current timestamp (local test)
    public void shouldCreateIDToken_withUser_scopesRequest_and_claimsRequest() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(new HashSet<>(Arrays.asList("openid", "email", "address")));
        oAuth2Request.setSubject("subject");
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>();
        requestParameters.put("claims", Collections.singletonList("{\"id_token\":{\"name\":{\"essential\":true}}}"));
        oAuth2Request.setRequestParameters(requestParameters);

        Client client = new Client();

        User user = createUser();

        JWT expectedJwt = new JWT();
        expectedJwt.setSub(user.getId());
        expectedJwt.setAud("client-id");
        expectedJwt.put(StandardClaims.ADDRESS, user.getAdditionalInformation().get(StandardClaims.ADDRESS));
        expectedJwt.put(StandardClaims.EMAIL_VERIFIED, user.getAdditionalInformation().get(StandardClaims.EMAIL_VERIFIED));
        expectedJwt.setIss(null);
        expectedJwt.put(StandardClaims.NAME, user.getAdditionalInformation().get(StandardClaims.NAME));
        expectedJwt.setExp((System.currentTimeMillis() / 1000l) + 14400);
        expectedJwt.setIat(System.currentTimeMillis() / 1000l);
        expectedJwt.put(StandardClaims.EMAIL, user.getAdditionalInformation().get(StandardClaims.EMAIL));

        when(certificateManager.defaultCertificateProvider()).thenReturn(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(defaultCertificateProvider));
        when(certificateManager.get(anyString())).thenReturn(Maybe.just(new io.gravitee.am.gateway.handler.certificate.CertificateProvider(certificateProvider)));
        when(jwtService.encode(any(), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class))).thenReturn(Single.just("test"));
        ((IDTokenServiceImpl) idTokenService).setObjectMapper(objectMapper);

        TestObserver<String> testObserver = idTokenService.create(oAuth2Request, client, user).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(certificateManager, times(1)).get(anyString());
        verify(jwtService, times(1)).encode(eq(expectedJwt), any(io.gravitee.am.gateway.handler.certificate.CertificateProvider.class));
    }

    private User createUser() {
        User user = new User();
        user.setId("technical-id");
        Map<String, Object> additionalInformation  = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, "user");
        additionalInformation.put(StandardClaims.NAME, "gravitee user");
        additionalInformation.put(StandardClaims.FAMILY_NAME, "gravitee");
        additionalInformation.put(StandardClaims.GIVEN_NAME, "gravitee");
        additionalInformation.put(StandardClaims.MIDDLE_NAME, "gravitee");
        additionalInformation.put(StandardClaims.NICKNAME, "gravitee");
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, "gravitee");
        additionalInformation.put(StandardClaims.PROFILE, "gravitee");
        additionalInformation.put(StandardClaims.PICTURE, "gravitee");
        additionalInformation.put(StandardClaims.WEBSITE, "gravitee");
        additionalInformation.put(StandardClaims.GENDER, "gravitee");
        additionalInformation.put(StandardClaims.BIRTHDATE, "gravitee");
        additionalInformation.put(StandardClaims.ZONEINFO, "gravitee");
        additionalInformation.put(StandardClaims.LOCALE, "gravitee");
        additionalInformation.put(StandardClaims.UPDATED_AT, "gravitee");
        additionalInformation.put(StandardClaims.EMAIL, "gravitee");
        additionalInformation.put(StandardClaims.EMAIL_VERIFIED, "gravitee");
        additionalInformation.put(StandardClaims.ADDRESS, "gravitee");
        additionalInformation.put(StandardClaims.PHONE_NUMBER, "gravitee");
        additionalInformation.put(StandardClaims.PHONE_NUMBER_VERIFIED, "gravitee");
        user.setAdditionalInformation(additionalInformation);

        return user;
    }
}
