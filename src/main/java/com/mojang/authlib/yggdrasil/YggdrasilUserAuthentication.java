package com.mojang.authlib.yggdrasil;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.*;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.yggdrasil.request.AuthenticationRequest;
import com.mojang.authlib.yggdrasil.request.RefreshRequest;
import com.mojang.authlib.yggdrasil.request.ValidateRequest;
import com.mojang.authlib.yggdrasil.response.AuthenticationResponse;
import com.mojang.authlib.yggdrasil.response.RefreshResponse;
import com.mojang.authlib.yggdrasil.response.Response;
import com.mojang.authlib.yggdrasil.response.User;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;

public class YggdrasilUserAuthentication
        extends HttpUserAuthentication {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String BASE_URL = "http://localhost/minecraft/auth/";
    private static final URL ROUTE_AUTHENTICATE = HttpAuthenticationService.constantURL("http://localhost/minecraft/auth/authenticate");
    private static final URL ROUTE_REFRESH = HttpAuthenticationService.constantURL("http://localhost/minecraft/auth/refresh");
    private static final URL ROUTE_VALIDATE = HttpAuthenticationService.constantURL("http://localhost/minecraft/auth/validate");
    private static final URL ROUTE_INVALIDATE = HttpAuthenticationService.constantURL("http://localhost/minecraft/auth/invalidate");
    private static final URL ROUTE_SIGNOUT = HttpAuthenticationService.constantURL("http://localhost/minecraft/auth/signout");
    private static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
    private final Agent agent;
    private GameProfile[] profiles;
    private String accessToken;
    private boolean isOnline;

    public YggdrasilUserAuthentication(YggdrasilAuthenticationService authenticationService, Agent agent) {
        super(authenticationService);
        this.agent = agent;
    }

    public boolean canLogIn() {
        return (!canPlayOnline()) && (StringUtils.isNotBlank(getUsername())) && ((StringUtils.isNotBlank(getPassword())) || (StringUtils.isNotBlank(getAuthenticatedToken())));
    }

    public void logIn()
            throws AuthenticationException {
        if (StringUtils.isBlank(getUsername())) {
            throw new InvalidCredentialsException("Invalid username");
        }
        if (StringUtils.isNotBlank(getAuthenticatedToken())) {
            logInWithToken();
        } else if (StringUtils.isNotBlank(getPassword())) {
            logInWithPassword();
        } else {
            throw new InvalidCredentialsException("Invalid password");
        }
    }

    protected void logInWithPassword()
            throws AuthenticationException {
        if (StringUtils.isBlank(getUsername())) {
            throw new InvalidCredentialsException("Invalid username");
        }
        if (StringUtils.isBlank(getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }
        LOGGER.info("Logging in with username & password");

        AuthenticationRequest request = new AuthenticationRequest(this, getUsername(), getPassword());
        LOGGER.info(getUsername());
        LOGGER.info(getPassword());
        GsonBuilder builder = new GsonBuilder();
        LOGGER.info(builder.create().toJson(request));

        AuthenticationResponse response = (AuthenticationResponse) getAuthenticationService().makeRequest(ROUTE_AUTHENTICATE, request, AuthenticationResponse.class);
//        TODO uncomment me and fix it
//        if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {
//            throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
//        }
        if (response.getSelectedProfile() != null) {
            setUserType(response.getSelectedProfile().isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        } else if (ArrayUtils.isNotEmpty(response.getAvailableProfiles())) {
            setUserType(response.getAvailableProfiles()[0].isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        }
        User user = response.getUser();
        if ((user != null) && (user.getId() != null)) {
            setUserid(user.getId());
        } else {
            setUserid(getUsername());
        }
        this.isOnline = true;
        this.accessToken = response.getAccessToken();
        this.profiles = response.getAvailableProfiles();
        setSelectedProfile(response.getSelectedProfile());
        getModifiableUserProperties().clear();

        updateUserProperties(user);
    }

    protected void updateUserProperties(User user) {
        if (user == null) {
            return;
        }
        if (user.getProperties() != null) {
            getModifiableUserProperties().putAll(user.getProperties());
        }
    }

    protected void logInWithToken()
            throws AuthenticationException {
        if (StringUtils.isBlank(getUserID())) {
            if (StringUtils.isBlank(getUsername())) {
                setUserid(getUsername());
            } else {
                throw new InvalidCredentialsException("Invalid uuid & username");
            }
        }
        if (StringUtils.isBlank(getAuthenticatedToken())) {
            throw new InvalidCredentialsException("Invalid access token");
        }
        LOGGER.info("Logging in with access token");
        if (checkTokenValidity()) {
            LOGGER.debug("Skipping refresh call as we're safely logged in.");
            this.isOnline = true;
            return;
        }
        RefreshRequest request = new RefreshRequest(this);
        RefreshResponse response = (RefreshResponse) getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);
        if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {
            throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
        }
        if (response.getSelectedProfile() != null) {
            setUserType(response.getSelectedProfile().isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        } else if (ArrayUtils.isNotEmpty(response.getAvailableProfiles())) {
            setUserType(response.getAvailableProfiles()[0].isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        }
        if ((response.getUser() != null) && (response.getUser().getId() != null)) {
            setUserid(response.getUser().getId());
        } else {
            setUserid(getUsername());
        }
        this.isOnline = true;
        this.accessToken = response.getAccessToken();
        this.profiles = response.getAvailableProfiles();
        setSelectedProfile(response.getSelectedProfile());
        getModifiableUserProperties().clear();

        updateUserProperties(response.getUser());
    }

    protected boolean checkTokenValidity()
            throws AuthenticationException {
        ValidateRequest request = new ValidateRequest(this);
        try {
            getAuthenticationService().makeRequest(ROUTE_VALIDATE, request, Response.class);
            return true;
        } catch (AuthenticationException ex) {
        }
        return false;
    }

    public void logOut() {
        super.logOut();

        this.accessToken = null;
        this.profiles = null;
        this.isOnline = false;
    }

    public GameProfile[] getAvailableProfiles() {
        return this.profiles;
    }

    public boolean isLoggedIn() {
        return StringUtils.isNotBlank(this.accessToken);
    }

    public boolean canPlayOnline() {
        return (isLoggedIn()) && (getSelectedProfile() != null) && (this.isOnline);
    }

    public void selectGameProfile(GameProfile profile)
            throws AuthenticationException {
        if (!isLoggedIn()) {
            throw new AuthenticationException("Cannot change game profile whilst not logged in");
        }
        if (getSelectedProfile() != null) {
            throw new AuthenticationException("Cannot change game profile. You must log out and back in.");
        }
        if ((profile == null) || (!ArrayUtils.contains(this.profiles, profile))) {
            throw new IllegalArgumentException("Invalid profile '" + profile + "'");
        }
        RefreshRequest request = new RefreshRequest(this, profile);
        RefreshResponse response = (RefreshResponse) getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);
        if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {
            throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
        }
        this.isOnline = true;
        this.accessToken = response.getAccessToken();
        setSelectedProfile(response.getSelectedProfile());
    }

    public void loadFromStorage(Map<String, Object> credentials) {
        super.loadFromStorage(credentials);

        this.accessToken = String.valueOf(credentials.get("accessToken"));
    }

    public Map<String, Object> saveForStorage() {
        Map<String, Object> result = super.saveForStorage();
        if (StringUtils.isNotBlank(getAuthenticatedToken())) {
            result.put("accessToken", getAuthenticatedToken());
        }
        return result;
    }

    @Deprecated
    public String getSessionToken() {
        if ((isLoggedIn()) && (getSelectedProfile() != null) && (canPlayOnline())) {
            return String.format("token:%s:%s", new Object[]{getAuthenticatedToken(), getSelectedProfile().getId()});
        }
        return null;
    }

    public String getAuthenticatedToken() {
        return this.accessToken;
    }

    public Agent getAgent() {
        return this.agent;
    }

    public String toString() {
        return "YggdrasilAuthenticationService{agent=" + this.agent + ", profiles=" + Arrays.toString(this.profiles) + ", selectedProfile=" + getSelectedProfile() + ", username='" + getUsername() + '\'' + ", isLoggedIn=" + isLoggedIn() + ", userType=" + getUserType() + ", canPlayOnline=" + canPlayOnline() + ", accessToken='" + this.accessToken + '\'' + ", clientToken='" + getAuthenticationService().getClientToken() + '\'' + '}';
    }

    public YggdrasilAuthenticationService getAuthenticationService() {
        return (YggdrasilAuthenticationService) super.getAuthenticationService();
    }
}


/*
package com.mojang.authlib.yggdrasil;

import com.google.common.collect.Multimap;
import com.mojang.authlib.Agent;
import com.mojang.authlib.AuthenticationService;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.HttpUserAuthentication;
import com.mojang.authlib.UserType;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.request.AuthenticationRequest;
import com.mojang.authlib.yggdrasil.request.RefreshRequest;
import com.mojang.authlib.yggdrasil.request.ValidateRequest;
import com.mojang.authlib.yggdrasil.response.AuthenticationResponse;
import com.mojang.authlib.yggdrasil.response.RefreshResponse;
import com.mojang.authlib.yggdrasil.response.Response;
import com.mojang.authlib.yggdrasil.response.User;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class YggdrasilUserAuthentication
extends HttpUserAuthentication {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String BASE_URL = "http://localhost/minecraft/auth/";
    private static final URL ROUTE_AUTHENTICATE = HttpAuthenticationService.constantURL("http://localhost//minecraft/auth/authenticate");
    private static final URL ROUTE_REFRESH = HttpAuthenticationService.constantURL("http://localhost//minecraft/auth/refresh");
    private static final URL ROUTE_VALIDATE = HttpAuthenticationService.constantURL("http://localhost//minecraft/auth/validate");
    private static final URL ROUTE_INVALIDATE = HttpAuthenticationService.constantURL("http://localhost//minecraft/auth/invalidate");
    private static final URL ROUTE_SIGNOUT = HttpAuthenticationService.constantURL("http://localhost//minecraft/auth/signout");
    private static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
    private final Agent agent;
    private GameProfile[] profiles;
    private String accessToken;
    private boolean isOnline;

    public YggdrasilUserAuthentication(YggdrasilAuthenticationService authenticationService, Agent agent) {
        super(authenticationService);
        this.agent = agent;
    }

    @Override
    public boolean canLogIn() {
        return !this.canPlayOnline() && StringUtils.isNotBlank((CharSequence)this.getUsername()) && (StringUtils.isNotBlank((CharSequence)this.getPassword()) || StringUtils.isNotBlank((CharSequence)this.getAuthenticatedToken()));
    }

    @Override
    public void logIn() throws AuthenticationException {
        if (StringUtils.isBlank((CharSequence)this.getUsername())) {
            throw new InvalidCredentialsException("Invalid username");
        }
        if (StringUtils.isNotBlank((CharSequence)this.getAuthenticatedToken())) {
            this.logInWithToken();
        } else if (StringUtils.isNotBlank((CharSequence)this.getPassword())) {
            this.logInWithPassword();
        } else {
            throw new InvalidCredentialsException("Invalid password");
        }
    }

    protected void logInWithPassword() throws AuthenticationException {
        if (StringUtils.isBlank((CharSequence)this.getUsername())) {
            throw new InvalidCredentialsException("Invalid username");
        }
        if (StringUtils.isBlank((CharSequence)this.getPassword())) {
            throw new InvalidCredentialsException("Invalid password");
        }
        LOGGER.info("Logging in with username & password");
        AuthenticationRequest request = new AuthenticationRequest(this, this.getUsername(), this.getPassword());
        AuthenticationResponse response = (AuthenticationResponse)this.getAuthenticationService().makeRequest(ROUTE_AUTHENTICATE, request, AuthenticationResponse.class);
        if (!response.getClientToken().equals(this.getAuthenticationService().getClientToken())) {
            throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
        }
        if (response.getSelectedProfile() != null) {
            this.setUserType(response.getSelectedProfile().isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        } else if (ArrayUtils.isNotEmpty((Object[])response.getAvailableProfiles())) {
            this.setUserType(response.getAvailableProfiles()[0].isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        }
        User user = response.getUser();
        if (user != null && user.getId() != null) {
            this.setUserid(user.getId());
        } else {
            this.setUserid(this.getUsername());
        }
        this.isOnline = true;
        this.accessToken = response.getAccessToken();
        this.profiles = response.getAvailableProfiles();
        this.setSelectedProfile(response.getSelectedProfile());
        this.getModifiableUserProperties().clear();
        this.updateUserProperties(user);
    }

    protected void updateUserProperties(User user) {
        if (user == null) {
            return;
        }
        if (user.getProperties() != null) {
            this.getModifiableUserProperties().putAll((Multimap)user.getProperties());
        }
    }

    protected void logInWithToken() throws AuthenticationException {
        if (StringUtils.isBlank((CharSequence)this.getUserID())) {
            if (StringUtils.isBlank((CharSequence)this.getUsername())) {
                this.setUserid(this.getUsername());
            } else {
                throw new InvalidCredentialsException("Invalid uuid & username");
            }
        }
        if (StringUtils.isBlank((CharSequence)this.getAuthenticatedToken())) {
            throw new InvalidCredentialsException("Invalid access token");
        }
        LOGGER.info("Logging in with access token");
        if (this.checkTokenValidity()) {
            LOGGER.debug("Skipping refresh call as we're safely logged in.");
            this.isOnline = true;
            return;
        }
        RefreshRequest request = new RefreshRequest(this);
        RefreshResponse response = (RefreshResponse)this.getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);
        if (!response.getClientToken().equals(this.getAuthenticationService().getClientToken())) {
            throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
        }
        if (response.getSelectedProfile() != null) {
            this.setUserType(response.getSelectedProfile().isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        } else if (ArrayUtils.isNotEmpty((Object[])response.getAvailableProfiles())) {
            this.setUserType(response.getAvailableProfiles()[0].isLegacy() ? UserType.LEGACY : UserType.MOJANG);
        }
        if (response.getUser() != null && response.getUser().getId() != null) {
            this.setUserid(response.getUser().getId());
        } else {
            this.setUserid(this.getUsername());
        }
        this.isOnline = true;
        this.accessToken = response.getAccessToken();
        this.profiles = response.getAvailableProfiles();
        this.setSelectedProfile(response.getSelectedProfile());
        this.getModifiableUserProperties().clear();
        this.updateUserProperties(response.getUser());
    }

    protected boolean checkTokenValidity() throws AuthenticationException {
        ValidateRequest request = new ValidateRequest(this);
        try {
            this.getAuthenticationService().makeRequest(ROUTE_VALIDATE, request, Response.class);
            return true;
        }
        catch (AuthenticationException ex) {
            return false;
        }
    }

    @Override
    public void logOut() {
        super.logOut();
        this.accessToken = null;
        this.profiles = null;
        this.isOnline = false;
    }

    @Override
    public GameProfile[] getAvailableProfiles() {
        return this.profiles;
    }

    @Override
    public boolean isLoggedIn() {
        return StringUtils.isNotBlank((CharSequence)this.accessToken);
    }

    @Override
    public boolean canPlayOnline() {
        return this.isLoggedIn() && this.getSelectedProfile() != null && this.isOnline;
    }

    @Override
    public void selectGameProfile(GameProfile profile) throws AuthenticationException {
        if (!this.isLoggedIn()) {
            throw new AuthenticationException("Cannot change game profile whilst not logged in");
        }
        if (this.getSelectedProfile() != null) {
            throw new AuthenticationException("Cannot change game profile. You must log out and back in.");
        }
        if (!(profile != null && ArrayUtils.contains((Object[])this.profiles, (Object)profile))) {
            throw new IllegalArgumentException("Invalid profile '" + profile + "'");
        }
        RefreshRequest request = new RefreshRequest(this, profile);
        RefreshResponse response = (RefreshResponse)this.getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);
        if (!response.getClientToken().equals(this.getAuthenticationService().getClientToken())) {
            throw new AuthenticationException("Server requested we change our client token. Don't know how to handle this!");
        }
        this.isOnline = true;
        this.accessToken = response.getAccessToken();
        this.setSelectedProfile(response.getSelectedProfile());
    }

    @Override
    public void loadFromStorage(Map<String, Object> credentials) {
        super.loadFromStorage(credentials);
        this.accessToken = String.valueOf(credentials.get("accessToken"));
    }

    @Override
    public Map<String, Object> saveForStorage() {
        Map<String, Object> result = super.saveForStorage();
        if (StringUtils.isNotBlank((CharSequence)this.getAuthenticatedToken())) {
            result.put("accessToken", this.getAuthenticatedToken());
        }
        return result;
    }

    @Deprecated
    public String getSessionToken() {
        if (this.isLoggedIn() && this.getSelectedProfile() != null && this.canPlayOnline()) {
            return String.format("token:%s:%s", this.getAuthenticatedToken(), this.getSelectedProfile().getId());
        }
        return null;
    }

    @Override
    public String getAuthenticatedToken() {
        return this.accessToken;
    }

    public Agent getAgent() {
        return this.agent;
    }

    @Override
    public String toString() {
        return "YggdrasilAuthenticationService{agent=" + this.agent + ", profiles=" + Arrays.toString(this.profiles) + ", selectedProfile=" + this.getSelectedProfile() + ", username='" + this.getUsername() + '\'' + ", isLoggedIn=" + this.isLoggedIn() + ", userType=" + (Object)this.getUserType() + ", canPlayOnline=" + this.canPlayOnline() + ", accessToken='" + this.accessToken + '\'' + ", clientToken='" + this.getAuthenticationService().getClientToken() + '\'' + '}';
    }

    @Override
    public YggdrasilAuthenticationService getAuthenticationService() {
        return (YggdrasilAuthenticationService)super.getAuthenticationService();
    }
}

*/
