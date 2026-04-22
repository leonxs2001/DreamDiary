package com.example.dreamdiary.security;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuthMetadataController {

    private final OAuthTokenService oauthTokenService;

    public OAuthMetadataController(OAuthTokenService oauthTokenService) {
        this.oauthTokenService = oauthTokenService;
    }

    @GetMapping(path = "/.well-known/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> protectedResourceMetadata() {
        return jsonNoStore(oauthTokenService.protectedResourceMetadata());
    }

    @GetMapping(path = "/.well-known/oauth-protected-resource/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> protectedResourceMetadataForMcp() {
        return jsonNoStore(oauthTokenService.protectedResourceMetadata());
    }

    @GetMapping(path = "/.well-known/oauth-authorization-server", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> authorizationServerMetadata() {
        return jsonNoStore(oauthTokenService.authorizationServerMetadata());
    }

    @GetMapping(path = "/.well-known/oauth-authorization-server/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> authorizationServerMetadataForMcp() {
        return jsonNoStore(oauthTokenService.authorizationServerMetadata());
    }

    private ResponseEntity<Map<String, Object>> jsonNoStore(Map<String, Object> body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
