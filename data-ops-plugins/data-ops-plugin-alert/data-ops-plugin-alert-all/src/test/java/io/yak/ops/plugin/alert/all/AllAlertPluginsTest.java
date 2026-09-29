package io.yak.ops.plugin.alert.all;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.plugin.alert.api.AlertPlugin;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies that the all-plugins artifact exposes every built-in alert provider. */
class AllAlertPluginsTest {

  @Test
  void shouldDiscoverAllBuiltInAlertPlugins() {
    Set<String> discovered = new HashSet<>();
    ServiceLoader.load(AlertPlugin.class)
        .forEach(plugin -> discovered.add(plugin.type()));

    assertThat(discovered).containsExactlyInAnyOrder("DINGTALK");
  }
}
