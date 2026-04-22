package com.example.dreamdiary.security;

import java.net.URI;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class OAuthAuthorizationController {

    private final OAuthTokenService oauthTokenService;

    public OAuthAuthorizationController(OAuthTokenService oauthTokenService) {
        this.oauthTokenService = oauthTokenService;
    }

    @GetMapping(path = "/oauth/authorize", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorizationPage(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam("code_challenge_method") String codeChallengeMethod,
            @RequestParam(value = "resource", required = false) String resource) {
        try {
            OAuthTokenService.AuthorizationRequest request = oauthTokenService.validateAuthorizationRequest(
                    responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, resource
            );
            return htmlResponse(renderAuthorizationForm(request, null), HttpStatus.OK);
        } catch (OAuthTokenService.OAuthException exception) {
            return htmlResponse(renderErrorPage(exception.error(), exception.getMessage()), exception.status());
        }
    }

    @PostMapping(path = "/oauth/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam("code_challenge") String codeChallenge,
            @RequestParam("code_challenge_method") String codeChallengeMethod,
            @RequestParam(value = "resource", required = false) String resource,
            @RequestParam("username") String username,
            @RequestParam("password") String password) {
        OAuthTokenService.AuthorizationRequest request;
        try {
            request = oauthTokenService.validateAuthorizationRequest(
                    responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, resource
            );
        } catch (OAuthTokenService.OAuthException exception) {
            return htmlResponse(renderErrorPage(exception.error(), exception.getMessage()), exception.status());
        }

        try {
            String code = oauthTokenService.authorizeAndIssueCode(request, username, password);
            URI redirectUriWithCode = UriComponentsBuilder
                    .fromUriString(request.redirectUri())
                    .queryParam("code", code)
                    .queryParamIfPresent("state", Optional.ofNullable(request.state()))
                    .build()
                    .encode()
                    .toUri();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(redirectUriWithCode)
                    .build();
        } catch (OAuthTokenService.OAuthException exception) {
            if ("access_denied".equals(exception.error())) {
                return htmlResponse(renderAuthorizationForm(request, "Falscher Benutzername oder Passwort."), HttpStatus.OK);
            }
            return htmlResponse(renderErrorPage(exception.error(), exception.getMessage()), exception.status());
        }
    }

    private ResponseEntity<String> htmlResponse(String html, HttpStatus status) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String renderAuthorizationForm(OAuthTokenService.AuthorizationRequest request, String errorMessage) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"UTF-8\"><title>Dream Diary OAuth Login</title>");
        html.append("<style>body{font-family:Arial,sans-serif;max-width:480px;margin:48px auto;padding:0 16px;} ");
        html.append("label{display:block;margin-top:12px;} input{width:100%;padding:8px;box-sizing:border-box;} ");
        html.append("button{margin-top:16px;padding:10px 14px;} .error{background:#fde8e8;color:#8a1f1f;padding:10px;border-radius:4px;margin-top:12px;}</style>");
        html.append("</head><body>");
        html.append("<h1>Dream Diary verbinden</h1>");
        html.append("<p>Bitte mit dem statischen OAuth-Benutzer anmelden.</p>");
        html.append("<p><strong>Client ID:</strong> ").append(escapeHtml(request.clientId())).append("</p>");
        html.append("<p><strong>Redirect URI:</strong> ").append(escapeHtml(request.redirectUri())).append("</p>");
        if (StringUtils.hasText(errorMessage)) {
            html.append("<div class=\"error\">").append(escapeHtml(errorMessage)).append("</div>");
        }
        html.append("<form method=\"post\" action=\"/oauth/authorize\">");
        html.append(hidden("response_type", request.responseType()));
        html.append(hidden("client_id", request.clientId()));
        html.append(hidden("redirect_uri", request.redirectUri()));
        html.append(hidden("scope", request.scope()));
        html.append(hidden("state", request.state()));
        html.append(hidden("code_challenge", request.codeChallenge()));
        html.append(hidden("code_challenge_method", request.codeChallengeMethod()));
        html.append(hidden("resource", request.resource()));
        html.append("<label>Username<input name=\"username\" type=\"text\" required></label>");
        html.append("<label>Password<input name=\"password\" type=\"password\" required></label>");
        html.append("<button type=\"submit\">Autorisieren</button>");
        html.append("</form></body></html>");
        return html.toString();
    }

    private String renderErrorPage(String error, String description) {
        return "<!doctype html><html><head><meta charset=\"UTF-8\"><title>OAuth Error</title></head><body>"
                + "<h1>OAuth Fehler</h1>"
                + "<p><strong>" + escapeHtml(error) + "</strong></p>"
                + "<p>" + escapeHtml(description) + "</p>"
                + "</body></html>";
    }

    private String hidden(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return "<input type=\"hidden\" name=\"" + escapeHtml(name) + "\" value=\"" + escapeHtml(value) + "\">";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
