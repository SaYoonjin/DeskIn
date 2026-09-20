package com.deskin.auth;

import com.deskin.auth.controller.CsrfController;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import static org.assertj.core.api.Assertions.assertThat;

class CsrfControllerTest {
    @Test
    void exposesHeaderAndTokenWithoutRequiringAuthentication() {
        var result = new CsrfController().getCsrfToken(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "token"));
        assertThat(result.success()).isTrue();
        assertThat(result.data().csrfToken()).isEqualTo("token");
        assertThat(result.data().headerName()).isEqualTo("X-XSRF-TOKEN");
    }
}
