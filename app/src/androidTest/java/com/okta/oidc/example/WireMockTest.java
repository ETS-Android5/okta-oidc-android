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

package com.okta.oidc.example;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.okta.oidc.AuthenticationPayload;
import com.okta.oidc.OIDCConfig;
import com.okta.oidc.Okta;
import com.okta.oidc.storage.SharedPreferenceStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.okta.oidc.example.Utils.getAsset;
import static com.okta.oidc.example.Utils.getNow;
import static com.okta.oidc.example.Utils.getTomorrow;
import static com.okta.oidc.example.WireMockStubs.mockConfigurationRequest;
import static com.okta.oidc.example.WireMockStubs.mockIntrospectRequest;
import static com.okta.oidc.example.WireMockStubs.mockProfileRequest;
import static com.okta.oidc.example.WireMockStubs.mockRevokeRequest;
import static com.okta.oidc.example.WireMockStubs.mockTokenRequest;
import static com.okta.oidc.example.WireMockStubs.mockWebAuthorizeRequest;
import static com.okta.oidc.net.ConnectionParameters.USER_AGENT_HEADER;
import static com.okta.oidc.net.ConnectionParameters.X_OKTA_USER_AGENT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static org.hamcrest.core.StringContains.containsString;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4.class)
@LargeTest
public class WireMockTest {
    private static final int HTTPS_PORT = 8443;
    private static final int PORT = 8080;
    private static final String KEYSTORE_PASSWORD = "123456";
    private static final String KEYSTORE_PATH = "/sdcard/Download/mock.keystore.bks";
    //must match issuer in configuration.json
    private static final String ISSUER = "https://127.0.0.1:8443/mocktest";
    //apk package names
    private static final String FIRE_FOX = "org.mozilla.firefox";
    private static final String CHROME_STABLE = "com.android.chrome";
    private static final String SAMPLE_APP = "com.okta.oidc.example";
    //timeout for app transition from browser to app.
    private static final int TRANSITION_TIMEOUT = 2000;
    private static final int NETWORK_TIMEOUT = 5000;

    //web page resource ids
    private static final String ID_USERNAME = "okta-signin-username";
    private static final String ID_PASSWORD = "okta-signin-password";
    private static final String ID_SUBMIT = "okta-signin-submit";
    private static final String ID_NO_THANKS = "com.android.chrome:id/negative_button";
    private static final String ID_ACCEPT = "com.android.chrome:id/terms_accept";
    private static final String ID_CLOSE_BROWSER = "com.android.chrome:id/close_button";
    private static final String ID_ADDRESS_BAR = "com.android.chrome:id/url_bar";

    //app resource ids
    private static final String ID_PROGRESS_BAR = "com.okta.oidc.example:id/progress_horizontal";

    private AuthenticationPayload mMockPayload;

    private Context mMockContext;

    private final String FAKE_CODE = "NPcg5pmx7oZbXSfbnhmE";
    private final String FAKE_STATE = "dNj95w5rW_ZWtAefdIrTug";
    private final String FAKE_NONCE = "UHdBvbK4x6GPRhos8gPXPg";

    private UiDevice mDevice;
    @Rule
    public ActivityTestRule<SampleActivity> activityRule = new ActivityTestRule<>(SampleActivity.class);
    @Rule
    public GrantPermissionRule grant = GrantPermissionRule.grant(READ_EXTERNAL_STORAGE, INTERNET);

    public WireMockServer mWireMockServer;

    @Before
    public void setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mMockContext = InstrumentationRegistry.getInstrumentation().getContext();
        mWireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
                .keystorePassword(KEYSTORE_PASSWORD)
                .keystoreType("BKS")
                .keystorePath(KEYSTORE_PATH)
                .port(PORT)
                .httpsPort(HTTPS_PORT));
        mWireMockServer.start();

        mMockPayload = new AuthenticationPayload.Builder()
                .setState(FAKE_STATE)
                .addParameter("nonce", FAKE_NONCE)
                .build();

        //samples sdk test
        activityRule.getActivity().mOidcConfig = new OIDCConfig.Builder()
                .clientId("0oalpui4tcMY8u7RX0h7")
                .redirectUri("com.oidc.sdk.sample:/callback")
                .endSessionRedirectUri("com.oidc.sdk.sample:/logout")
                .scopes("openid", "profile", "offline_access")
                .discoveryUri("https://127.0.0.1:8443")
                .create();

        activityRule.getActivity().mWebAuth = new Okta.WebAuthBuilder()
                .withConfig(activityRule.getActivity().mOidcConfig)
                .withContext(activityRule.getActivity())
                .withStorage(new SharedPreferenceStorage(activityRule.getActivity()))
                .withOktaHttpClient(new MockOktaHttpClient())
                .setRequireHardwareBackedKeyStore(false)
                .create();

        activityRule.getActivity().mSessionClient =
                activityRule.getActivity().mWebAuth.getSessionClient();

        activityRule.getActivity().setupCallback();
    }

    @After
    public void tearDown() throws Exception {
        mWireMockServer.stop();
    }

    private UiObject getProgressBar() {
        return mDevice.findObject(new UiSelector().resourceId(ID_PROGRESS_BAR));
    }

    private void acceptChromePrivacyOption() throws UiObjectNotFoundException {
        UiSelector selector = new UiSelector();
        UiObject accept = mDevice.findObject(selector.resourceId(ID_ACCEPT));
        accept.waitForExists(TRANSITION_TIMEOUT);
        if (accept.exists()) {
            accept.click();
        }

        UiObject noThanks = mDevice.findObject(selector.resourceId(ID_NO_THANKS));
        noThanks.waitForExists(TRANSITION_TIMEOUT);
        if (noThanks.exists()) {
            noThanks.click();
        }
    }

    @Test
    public void test0_signInNoSessionCancel() throws UiObjectNotFoundException {
        activityRule.getActivity().mPayload = mMockPayload;
        mockConfigurationRequest(aResponse()
                .withStatus(HTTP_OK)
                .withBody(getAsset(mMockContext, "configuration.json")));
        String redirect = String.format("dev-486177.oktapreview.com:/callback?code=%s&state=%s",
                FAKE_CODE, FAKE_STATE);
        mockWebAuthorizeRequest(aResponse().withStatus(HTTP_MOVED_TEMP)
                .withHeader("Location", redirect));

        onView(withId(R.id.switch1)).withFailureHandler((error, viewMatcher) -> {
            onView(withId(R.id.switch1)).check(matches(isDisplayed()));
            onView(withId(R.id.switch1)).perform(click());
        }).check(matches(isChecked()));

        onView(withId(R.id.sign_in_native)).withFailureHandler((error, viewMatcher) -> {
            onView(withId(R.id.clear_data)).check(matches(isDisplayed()));
            onView(withId(R.id.clear_data)).perform(click());
        }).check(matches(isDisplayed()));
        onView(withId(R.id.sign_in)).perform(click());

        mDevice.wait(Until.findObject(By.pkg(CHROME_STABLE)), TRANSITION_TIMEOUT);

        UiSelector selector = new UiSelector();
        UiObject closeBrowser = mDevice.findObject(selector.resourceId(ID_CLOSE_BROWSER));
        closeBrowser.click();

        mDevice.wait(Until.findObject(By.pkg(SAMPLE_APP)), TRANSITION_TIMEOUT);
        onView(withId(R.id.status)).check(matches(withText(containsString("canceled"))));
    }

    @Test
    public void test1_signInNoSession() throws UiObjectNotFoundException, InterruptedException {
        activityRule.getActivity().mPayload = mMockPayload;
        mockConfigurationRequest(aResponse()
                .withStatus(HTTP_OK)
                .withBody(getAsset(mMockContext, "configuration.json")));

        String response = getAsset(mMockContext, "response.html");
        mockWebAuthorizeRequest(aResponse().withStatus(HTTP_OK)
                .withBody(response));

        String tokenResponse = getAsset(mMockContext, "token_response.json");

        String jwt = Utils.getJwt(ISSUER, FAKE_NONCE, getTomorrow(), getNow(),
                activityRule.getActivity().mOidcConfig.getClientId());

        String token = String.format(tokenResponse, jwt);

        mockTokenRequest(aResponse().withStatus(HTTP_OK)
                .withBody(token));

        onView(withId(R.id.switch1)).withFailureHandler((error, viewMatcher) -> {
            onView(withId(R.id.switch1)).check(matches(isDisplayed()));
            onView(withId(R.id.switch1)).perform(click());
        }).check(matches(isChecked()));

        onView(withId(R.id.sign_in_native)).withFailureHandler((error, viewMatcher) -> {
            onView(withId(R.id.clear_data)).check(matches(isDisplayed()));
            onView(withId(R.id.clear_data)).perform(click());
        }).check(matches(isDisplayed()));
        onView(withId(R.id.sign_in)).perform(click());

        mDevice.wait(Until.findObject(By.pkg(CHROME_STABLE)), TRANSITION_TIMEOUT);

        UiSelector selector = new UiSelector();
        UiObject address = mDevice.findObject(selector.resourceId(ID_ADDRESS_BAR));
        if (!address.exists()) {
            acceptChromePrivacyOption();
        }

        mDevice.wait(Until.findObject(By.pkg(SAMPLE_APP)), TRANSITION_TIMEOUT);

        //wait for token exchange
        getProgressBar().waitUntilGone(NETWORK_TIMEOUT);

        //check if get profile is visible
        onView(withId(R.id.get_profile)).check(matches(isDisplayed()));
        onView(withId(R.id.status))
                .check(matches(withText(containsString("authentication authorized"))));

        verify(getRequestedFor(urlMatching("/authorize.*"))
                .withHeader(X_OKTA_USER_AGENT, equalTo(USER_AGENT_HEADER)));
    }

    @Test
    public void test2_getProfile_with_network_throttling_success() throws UiObjectNotFoundException, InterruptedException {
        String profileResponse = getAsset(mMockContext, "profile.json");

        mockProfileRequest(aResponse().withStatus(HTTP_OK)
                .withLogNormalRandomDelay(100, 0.1)
                .withBody(profileResponse));

        onView(withId(R.id.get_profile)).perform(click());

        getProgressBar().waitUntilGone(NETWORK_TIMEOUT);

        onView(withId(R.id.status)).check(matches(isDisplayed()));
        onView(withId(R.id.status)).check(matches(withText(containsString("Developer Experience"))));
    }

    @Test
    public void test3_getProfile_unauthorized_error() throws UiObjectNotFoundException, InterruptedException {

        mockProfileRequest(aResponse()
                .withStatus(HTTP_UNAUTHORIZED)
                .withHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The access token is invalid\"")
        );

        onView(withId(R.id.get_profile)).perform(click());
        getProgressBar().waitUntilGone(NETWORK_TIMEOUT);
        onView(withId(R.id.status)).check(matches(isDisplayed()));
        onView(withId(R.id.status)).check(matches(withText(containsString("Unauthorized"))));
    }

    @Test
    public void test4_getProfile_forbidden_error() throws UiObjectNotFoundException, InterruptedException {

        mockProfileRequest(aResponse()
                .withStatus(HTTP_FORBIDDEN)
                .withHeader("Expires", "0")
                .withHeader("WWW-Authenticate:", "Bearer error=\"insufficient_scope\", " +
                        "error_description=\"The access token must provide access to at least one of" +
                        " these scopes - profile, email, address or phone\"")
        );

        onView(withId(R.id.get_profile)).perform(click());
        getProgressBar().waitUntilGone(NETWORK_TIMEOUT);
        onView(withId(R.id.status)).check(matches(isDisplayed()));
        onView(withId(R.id.status)).check(matches(withText(containsString("Forbidden"))));
    }


    @Test
    public void test5_revokeToken_error() throws UiObjectNotFoundException, InterruptedException {

        mockRevokeRequest(aResponse()
                .withStatus(HTTP_UNAUTHORIZED)
                .withHeader("Content-Type", "application/json;charset=UTF-8")
                .withBody("{\n" +
                        "    \"error\": \"invalid_client\",\n" +
                        "    \"error_description\": \"No client credentials found.\"\n" +
                        "}")
        );

        onView(withId(R.id.revoke_access)).perform(click());
        getProgressBar().waitUntilGone(NETWORK_TIMEOUT);
        onView(withId(R.id.status)).check(matches(isDisplayed()));
        onView(withId(R.id.status)).check(matches(withText(containsString("false"))));
    }

    @Test
    public void test6_introspect_error() throws UiObjectNotFoundException, InterruptedException {

        mockIntrospectRequest(aResponse()
                .withStatus(HTTP_UNAUTHORIZED)
                .withHeader("Content-Type", "application/json;charset=UTF-8")
                .withBody("{\n" +
                        "    \"error\" : \"invalid_client\",\n" +
                        "    \"error_description\" : \"No client credentials found.\"\n" +
                        "}")
        );

        onView(withId(R.id.revoke_access)).perform(click());
        getProgressBar().waitUntilGone(NETWORK_TIMEOUT);
        onView(withId(R.id.status)).check(matches(isDisplayed()));
        onView(withId(R.id.status)).check(matches(withText(containsString("false"))));
    }
}