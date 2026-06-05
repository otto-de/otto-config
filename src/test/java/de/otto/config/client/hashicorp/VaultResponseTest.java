package de.otto.config.client.hashicorp;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class VaultResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserialize() throws Exception {
        // given
        String json = """
            {
              "data": {
                "data": {"foo": "bar"},
                "metadata": {"meta": 42},
                "versions": {
                  "v1": {
                    "created_time": "2024-01-01T00:00:00Z",
                    "deletion_time": "",
                    "destroyed": false
                  },
                  "v2": {
                    "created_time": "2024-01-10T00:00:00Z",
                    "deletion_time": "",
                    "destroyed": false
                  }
                }
              },
              "auth": {
                "client_token": "token123",
                "lease_duration": 3600
              }
            }
            """;

        // when
        VaultResponse response = objectMapper.readValue(json, VaultResponse.class);

        // then
        assertThat(response.data().data(), is(Map.of("foo", "bar")));
        assertThat(response.data().metadata().get("meta"), is(42));
        assertThat(response.data().versions().get("v1").created_time(), is("2024-01-01T00:00:00Z"));
        assertThat(response.data().versions().get("v2").created_time(), is("2024-01-10T00:00:00Z"));
        assertThat(response.auth().client_token(), is("token123"));
        assertThat(response.auth().lease_duration(), is(3600L));
    }

    @Test
    void shouldDeserializeData() throws Exception {
        // given
        String json = """
            {
              "data": {
                "data": {"foo": "bar"}
              }
            }
            """;

        // when
        VaultResponse response = objectMapper.readValue(json, VaultResponse.class);

        // then
        assertThat(response.data().data(), is(Map.of("foo", "bar")));
    }

    @Test
    void shouldDeserializeVersion() throws Exception {
        // given
        String json = """
            {
              "data": {
                "versions": {
                  "v1": {
                    "created_time": "2024-01-01T00:00:00Z",
                    "deletion_time": "2024-01-02T00:00:00Z",
                    "destroyed": true
                  }
                }
              }
            }
            """;

        // when
        VaultResponse response = objectMapper.readValue(json, VaultResponse.class);

        // then
        VaultResponse.Version version = response.data().versions().get("v1");
        assertThat(version.created_time(), is("2024-01-01T00:00:00Z"));
        assertThat(version.deletion_time(), is("2024-01-02T00:00:00Z"));
        assertThat(version.destroyed(), is(true));
    }

    @Test
    void shouldDeserializeAuth() throws Exception {
        // given
        String json = """
            {
              "auth": {
                "client_token": "client-token",
                "lease_duration": 1234
              }
            }
            """;

        // when
        VaultResponse response = objectMapper.readValue(json, VaultResponse.class);

        // then
        assertThat(response.auth().client_token(), is("client-token"));
        assertThat(response.auth().lease_duration(), is(1234L));
    }
}
