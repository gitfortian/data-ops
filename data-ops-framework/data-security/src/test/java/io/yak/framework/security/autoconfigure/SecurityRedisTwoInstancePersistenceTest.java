package io.yak.framework.security.autoconfigure;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import io.yak.framework.security.config.YakSecurityProperties;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a real Redis server. Two independently constructed Starter DAOs
 * stand in for application instances with the same Redis connection settings.
 * This checks shared login ticket/session persistence and peer logout visibility;
 * HTTP and distributed-process authorization remain separate acceptance gates.
 */
class SecurityRedisTwoInstancePersistenceTest {

  @Test
  void peerInstanceSeesLoginSessionAndLogoutImmediately() {
    String host = System.getenv("ARCHITECTURE_REDIS_HOST");
    Assumptions.assumeTrue(host != null && !host.isBlank(),
        "Real Redis service not configured");
    int port = Integer.parseInt(System.getenv().getOrDefault(
        "ARCHITECTURE_REDIS_PORT", "6379"));

    YakSecurityProperties properties = new YakSecurityProperties();
    properties.getAuthentication().getRedis().setHost(host);
    properties.getAuthentication().getRedis().setPort(port);

    YakSecurityAuthenticationConfiguration first =
        new YakSecurityAuthenticationConfiguration();
    YakSecurityAuthenticationConfiguration second =
        new YakSecurityAuthenticationConfiguration();

    SaTokenDao instanceA = first.saTokenRedisDao(properties);
    SaTokenDao instanceB = second.saTokenRedisDao(properties);
    assertNotSame(instanceA, instanceB);

    String id = UUID.randomUUID().toString();
    String ticketKey = "a82:login-ticket:" + id;
    String sessionKey = "a82:login-session:" + id;
    try {
      instanceA.set(ticketKey, "user:42", 90L);
      assertEquals("user:42", instanceB.get(ticketKey));
      assertTrue(instanceB.getTimeout(ticketKey) > 0);

      SaSession session = new SaSession(id);
      session.set("yak-security:username", "redis-user");
      instanceA.setObject(sessionKey, session, 90L);
      SaSession shared = instanceB.getObject(sessionKey, SaSession.class);
      assertNotNull(shared);
      assertEquals(id, shared.getId());
      assertEquals("redis-user", shared.get("yak-security:username"));
      assertTrue(instanceB.getObjectTimeout(sessionKey) > 0);

      // A user logging out through either node must invalidate the other node's view.
      instanceB.delete(ticketKey);
      instanceB.deleteObject(sessionKey);
      assertNull(instanceA.get(ticketKey));
      assertNull(instanceA.getObject(sessionKey));
    } finally {
      instanceA.delete(ticketKey);
      instanceA.deleteObject(sessionKey);
    }
  }
}
