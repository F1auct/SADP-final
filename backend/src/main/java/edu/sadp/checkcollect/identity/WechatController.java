package edu.sadp.checkcollect.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class WechatController {
    private static final String FLOW = "wechatFlow";
    private final String appId;
    private final String secret;
    private final String redirectUri;
    private final String frontendBaseUrl;
    private final IdentityService identity;
    private final SessionLogin sessionLogin;
    private final RestClient client = RestClient.create();
    private final SecureRandom random = new SecureRandom();

    private record Flow(String state, String purpose, UUID userId, Instant expiresAt) {}

    public WechatController(@Value("${app.wechat.app-id:}") String appId,
                            @Value("${app.wechat.app-secret:}") String secret,
                            @Value("${app.wechat.redirect-uri:}") String redirectUri,
                            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
                            IdentityService identity, SessionLogin sessionLogin) {
        this.appId = appId;
        this.secret = secret;
        this.redirectUri = redirectUri;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/$", "");
        this.identity = identity;
        this.sessionLogin = sessionLogin;
    }

    @GetMapping("/auth/wechat/start")
    public ResponseEntity<Void> startLogin(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", start(request, "LOGIN", null)).build();
    }

    @GetMapping("/auth/wechat/status")
    public Map<String, Boolean> status() {
        return Map.of("configured", !appId.isBlank() && !secret.isBlank() && !redirectUri.isBlank());
    }

    @PostMapping("/me/wechat/bind/start")
    public Map<String, String> startBinding(HttpServletRequest request) {
        UserAccount account = identity.currentUser();
        if (account.wechatBound()) throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已绑定微信");
        return Map.of("url", start(request, "BIND", account.id()));
    }

    private String start(HttpServletRequest request, String purpose, UUID userId) {
        if (appId.isBlank() || secret.isBlank() || redirectUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录尚未配置");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.getSession(true).setAttribute(FLOW, new Flow(state, purpose, userId, Instant.now().plusSeconds(600)));
        return "https://open.weixin.qq.com/connect/qrconnect?appid=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code&scope=snsapi_login&state=" + encode(state) + "#wechat_redirect";
    }

    @GetMapping("/auth/wechat/callback")
    public void callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
                         HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        HttpSession session = request.getSession(false);
        Flow flow = session == null ? null : (Flow) session.getAttribute(FLOW);
        if (session != null) session.removeAttribute(FLOW);
        if (flow == null || state == null || !MessageDigest.isEqual(flow.state().getBytes(StandardCharsets.UTF_8), state.getBytes(StandardCharsets.UTF_8))
                || flow.expiresAt().isBefore(Instant.now()) || code == null || code.isBlank()) {
            response.sendRedirect(frontendBaseUrl + "/login?error=wechat-state");
            return;
        }
        try {
            String openId = exchangeCode(code);
            if (flow.purpose().equals("BIND")) {
                if (!identity.currentUser().id().equals(flow.userId())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                identity.bindWechat(flow.userId(), appId, openId);
                response.sendRedirect(frontendBaseUrl + "/account?notice=wechat-bound");
            } else {
                UserAccount account = identity.userForWechat(appId, openId);
                sessionLogin.establish(account.id(), request, response);
                response.sendRedirect(frontendBaseUrl + (account.mustChangePassword() ? "/password" : "/"));
            }
        } catch (Exception error) {
            response.sendRedirect(frontendBaseUrl + (flow.purpose().equals("BIND") ? "/account?error=wechat-bind" : "/login?error=wechat-login"));
        }
    }

    private String exchangeCode(String code) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("https://api.weixin.qq.com/sns/oauth2/access_token?appid={appid}&secret={secret}&code={code}&grant_type=authorization_code",
                appId, secret, code).retrieve().body(Map.class);
        if (body == null || !(body.get("openid") instanceof String openId) || openId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信授权失败");
        }
        return openId;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
