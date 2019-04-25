package com.okta.oidc.clients.sessions;

import android.net.Uri;

import com.google.gson.JsonObject;
import com.okta.oidc.OIDCConfig;
import com.okta.oidc.OktaState;
import com.okta.oidc.Tokens;
import com.okta.oidc.net.HttpConnection;
import com.okta.oidc.net.HttpConnectionFactory;
import com.okta.oidc.net.request.AuthorizedRequest;
import com.okta.oidc.net.request.HttpRequest;
import com.okta.oidc.net.request.HttpRequestBuilder;
import com.okta.oidc.net.request.IntrospectRequest;
import com.okta.oidc.net.request.RefreshTokenRequest;
import com.okta.oidc.net.request.RevokeTokenRequest;
import com.okta.oidc.net.response.IntrospectInfo;
import com.okta.oidc.net.response.TokenResponse;
import com.okta.oidc.net.response.UserInfo;
import com.okta.oidc.util.AuthorizationException;

import org.json.JSONObject;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static com.okta.oidc.State.IDLE;

public class SyncSessionClient implements SyncSession {
    private OIDCConfig mOIDCConfig;
    private OktaState mOktaState;
    private HttpConnectionFactory mConnectionFactory;

    public SyncSessionClient(OIDCConfig oidcConfig, OktaState oktaState, HttpConnectionFactory connectionFactory) {
        this.mOIDCConfig = oidcConfig;
        this.mOktaState = oktaState;
        this.mConnectionFactory = connectionFactory;
    }

    public AuthorizedRequest authorizedRequest(@NonNull Uri uri, @Nullable Map<String, String> properties, @Nullable Map<String, String> postParameters,
                                               @NonNull HttpConnection.RequestMethod method) {
        return (AuthorizedRequest) HttpRequestBuilder.newRequest()
                .request(HttpRequest.Type.AUTHORIZED)
                .connectionFactory(mConnectionFactory)
                .account(mOIDCConfig)
                .httpRequestMethod(method)
                .providerConfiguration(mOktaState.getProviderConfiguration())
                .tokenResponse(mOktaState.getTokenResponse())
                .uri(uri)
                .properties(properties)
                .postParameters(postParameters)
                .createRequest();
    }


    protected AuthorizedRequest userProfileRequest() {
        return (AuthorizedRequest) HttpRequestBuilder.newRequest()
                .request(HttpRequest.Type.PROFILE)
                .connectionFactory(mConnectionFactory)
                .tokenResponse(mOktaState.getTokenResponse())
                .providerConfiguration(mOktaState.getProviderConfiguration())
                .account(mOIDCConfig).createRequest();
    }

    @Override
    public UserInfo getUserProfile() throws AuthorizationException {
        JSONObject userInfo = userProfileRequest().executeRequest();
        return new UserInfo(userInfo);
    }

    protected IntrospectRequest introspectTokenRequest(String token, String tokenType) {
        return (IntrospectRequest) HttpRequestBuilder.newRequest()
                .request(HttpRequest.Type.INTROSPECT)
                .connectionFactory(mConnectionFactory)
                .introspect(token, tokenType)
                .providerConfiguration(mOktaState.getProviderConfiguration())
                .account(mOIDCConfig).createRequest();
    }

    @Override
    public IntrospectInfo introspectToken(String token, String tokenType) throws AuthorizationException {
        return introspectTokenRequest(token, tokenType).executeRequest();
    }

    public RevokeTokenRequest revokeTokenRequest(String token) {
        return (RevokeTokenRequest) HttpRequestBuilder.newRequest()
                .request(HttpRequest.Type.REVOKE_TOKEN)
                .connectionFactory(mConnectionFactory)
                .tokenToRevoke(token)
                .providerConfiguration(mOktaState.getProviderConfiguration())
                .account(mOIDCConfig).createRequest();
    }

    @Override
    public Boolean revokeToken(String token) throws AuthorizationException {
        return revokeTokenRequest(token).executeRequest();
    }

    public RefreshTokenRequest refreshTokenRequest() {
        return (RefreshTokenRequest) HttpRequestBuilder.newRequest()
                .request(HttpRequest.Type.REFRESH_TOKEN)
                .connectionFactory(mConnectionFactory)
                .tokenResponse(mOktaState.getTokenResponse())
                .providerConfiguration(mOktaState.getProviderConfiguration())
                .account(mOIDCConfig).createRequest();
    }

    @Override
    public Tokens refreshToken() throws AuthorizationException {
        //Wrap the callback from the app because we want to be consistent in
        //returning a Tokens object instead of a TokenResponse.
        TokenResponse tokenResponse = refreshTokenRequest().executeRequest();
        mOktaState.save(tokenResponse);
        return new Tokens(tokenResponse);
    }

    @Override
    public Tokens getTokens() {
        TokenResponse response = mOktaState.getTokenResponse();
        if (response == null) return null;
        return new Tokens(response);
    }

    @Override
    public boolean isLoggedIn() {
        TokenResponse tokenResponse = mOktaState.getTokenResponse();
        return tokenResponse != null &&
                (tokenResponse.getAccessToken() != null || tokenResponse.getIdToken() != null);
    }

    @Override
    public void clear() {
        mOktaState.delete(mOktaState.getProviderConfiguration());
        mOktaState.delete(mOktaState.getTokenResponse());
        mOktaState.delete(mOktaState.getAuthorizeRequest());
        mOktaState.setCurrentState(IDLE);
    }

    @VisibleForTesting
    OktaState getOktaState() {
        return mOktaState;
    }
}
