/*
 * Copyright (c) 2019, Okta, Inc. and/or its affiliates. All rights reserved.
 * The Okta software accompanied by this notice is provided pursuant to the Apache License,
 * Version 2.0 (the "License.")
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and limitations under the
 * License.
 */
package com.okta.oidc.net.request;

import com.google.gson.Gson;
import com.okta.oidc.util.TestValues;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static com.okta.oidc.util.TestValues.AUTHORIZATION_ENDPOINT;
import static com.okta.oidc.util.TestValues.CUSTOM_OAUTH2_URL;
import static com.okta.oidc.util.TestValues.CUSTOM_URL;
import static com.okta.oidc.util.TestValues.END_SESSION_ENDPOINT;
import static com.okta.oidc.util.TestValues.INTROSPECT_ENDPOINT;
import static com.okta.oidc.util.TestValues.JWKS_ENDPOINT;
import static com.okta.oidc.util.TestValues.REGISTRATION_ENDPOINT;
import static com.okta.oidc.util.TestValues.REVOCATION_ENDPOINT;
import static com.okta.oidc.util.TestValues.TOKEN_ENDPOINT;
import static com.okta.oidc.util.TestValues.USERINFO_ENDPOINT;
import static com.okta.oidc.util.TestValues.getCustomConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27)
public class ProviderConfigurationTest {
    private ProviderConfiguration mValidConfiguration;
    private ProviderConfiguration mValidOAuth2Configuration;

    private ProviderConfiguration mInvalidConfiguration;

    @Rule
    public ExpectedException mExpectedEx = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        mValidConfiguration = TestValues.getProviderConfiguration(CUSTOM_URL);
        mInvalidConfiguration = TestValues.getProviderConfiguration(null);
        mValidOAuth2Configuration = TestValues.getOAuth2ProviderConfiguration(CUSTOM_OAUTH2_URL);
    }

    @Test
    public void validate() throws IllegalArgumentException {
        mValidConfiguration.validate(false);
        mValidOAuth2Configuration.validate(true);
    }

    @Test
    public void validateFail() throws IllegalArgumentException {
        mExpectedEx.expect(IllegalArgumentException.class);
        mInvalidConfiguration.validate(false);
    }

    @Test
    public void validateMissingUserInfo() throws IllegalArgumentException {
        mExpectedEx.expect(IllegalArgumentException.class);
        mValidOAuth2Configuration.validate(false);
    }

    @Test
    public void getKey() {
        assertEquals(mValidConfiguration.getKey(), ProviderConfiguration.RESTORE.getKey());
    }

    @Test
    public void persist() {
        String json = mValidConfiguration.persist();
        ProviderConfiguration other = new Gson().fromJson(json, ProviderConfiguration.class);
        other.validate(false);
        assertNotNull(other);
        assertEquals(other.persist(), json);
    }

    @Test
    public void useCustomConfiguration() {
        ProviderConfiguration config =
                new ProviderConfiguration(getCustomConfiguration(CUSTOM_URL));
        assertEquals(CUSTOM_URL + END_SESSION_ENDPOINT, config.end_session_endpoint);
        assertEquals(CUSTOM_URL + AUTHORIZATION_ENDPOINT, config.authorization_endpoint);
        assertEquals(CUSTOM_URL + REVOCATION_ENDPOINT, config.revocation_endpoint);
        assertEquals(CUSTOM_URL + JWKS_ENDPOINT, config.jwks_uri);
        assertEquals(CUSTOM_URL + INTROSPECT_ENDPOINT, config.introspection_endpoint);
        assertEquals(CUSTOM_URL + TOKEN_ENDPOINT, config.token_endpoint);
        assertEquals(CUSTOM_URL + USERINFO_ENDPOINT, config.userinfo_endpoint);
        assertEquals(CUSTOM_URL + REGISTRATION_ENDPOINT, config.registration_endpoint);
    }
}
