package com.swiftdrop.integration.identity;
import com.swiftdrop.controller.identity.GoogleReauthController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public final class GoogleSecurityRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final DefaultOAuth2AuthorizationRequestResolver normal;
    private final DefaultOAuth2AuthorizationRequestResolver fresh;
    public GoogleSecurityRequestResolver(ClientRegistrationRepository clients){
        normal=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        fresh=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        fresh.setAuthorizationRequestCustomizer(builder->builder.additionalParameters(parameters->{
            parameters.put("prompt","select_account");parameters.put("max_age","0");
            parameters.put("claims","{\"id_token\":{\"auth_time\":{\"essential\":true}}}");
        }));
    }
    private OAuth2AuthorizationRequestResolver resolver(HttpServletRequest request){
        var session=request.getSession(false);
        return session!=null&&session.getAttribute(GoogleReauthController.INTENT)!=null?fresh:normal;
    }
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request){return resolver(request).resolve(request);}
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request,String id){return resolver(request).resolve(request,id);}
}
