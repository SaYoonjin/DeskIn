package com.deskin.auth;

import com.deskin.auth.security.RefreshCookieWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class RefreshCookieWriterTest extends AuthServiceTestSupport {
    @Test
    void setsSecureHttpOnlyCookieWithRemainingLifetime() {
        var response = new MockHttpServletResponse();
        new RefreshCookieWriter(properties, clock).writeCookie(response, "opaque", clock.instant().plusSeconds(60));
        assertThat(response.getHeader("Set-Cookie")).contains("refreshToken=opaque", "Path=/auth", "HttpOnly",
                "Secure", "SameSite=Lax", "Max-Age=60").doesNotContain("Domain=");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void clearsCookieUsingSameScope() {
        var response = new MockHttpServletResponse();
        new RefreshCookieWriter(properties, clock).clearCookie(response);
        assertThat(response.getHeader("Set-Cookie")).contains("refreshToken=", "Path=/auth", "Max-Age=0", "HttpOnly", "Secure");
    }
}
