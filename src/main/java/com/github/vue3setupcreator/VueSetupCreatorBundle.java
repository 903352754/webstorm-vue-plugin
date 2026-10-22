package com.github.vue3setupcreator;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * Message bundle of the plugin.
 *
 * <p>The default bundle ({@code messages/VueSetupCreatorBundle.properties}) contains the Chinese
 * texts, {@code messages/VueSetupCreatorBundle_en.properties} contains the English ones.</p>
 */
public final class VueSetupCreatorBundle extends DynamicBundle {

  private static final String BUNDLE = "messages.VueSetupCreatorBundle";
  private static final VueSetupCreatorBundle INSTANCE = new VueSetupCreatorBundle();

  private VueSetupCreatorBundle() {
    super(BUNDLE);
  }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                             Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> lazy(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                   Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
