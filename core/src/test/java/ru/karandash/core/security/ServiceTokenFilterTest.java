package ru.karandash.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenFilterTest {

    @Test
    void passesRequestWithValidToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = filter("core-token", "Bearer core-token", chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        assertThat(filter("core-token", null, new MockFilterChain()).getStatus()).isEqualTo(401);
        assertThat(filter("core-token", "Bearer other", new MockFilterChain()).getStatus()).isEqualTo(401);
        assertThat(filter("core-token", "core-token", new MockFilterChain()).getStatus()).isEqualTo(401);
        assertThat(filter("core-token", "Basic Y29yZS10b2tlbg==", new MockFilterChain()).getStatus()).isEqualTo(401);
    }

    @Test
    void blankConfiguredTokenClosesInternalApi() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        assertThat(filter("", "Bearer ", chain).getStatus()).isEqualTo(401);
        assertThat(filter(null, "Bearer anything", new MockFilterChain()).getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    private static MockHttpServletResponse filter(String configured, String header, MockFilterChain chain)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/telegram/messages");
        if (header != null) {
            request.addHeader("Authorization", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ServiceTokenFilter(configured).doFilter(request, response, chain);
        return response;
    }
}
