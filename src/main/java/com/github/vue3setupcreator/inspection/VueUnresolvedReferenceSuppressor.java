package com.github.vue3setupcreator.inspection;

import com.github.vue3setupcreator.util.VueSfcModel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The JavaScript plugin reports unresolved references of a Vue template with its own
 * "Unresolved reference" inspection. Inside a Vue {@code <template>} block that inspection is
 * replaced by {@link VueTemplateUnresolvedReferenceInspection}, which additionally offers the quick
 * fixes to create the missing binding inside {@code <script setup>}.
 *
 * <p>The suppression is only active while {@link VueTemplateUnresolvedReferenceInspection} is
 * enabled, so the original diagnostics are never lost.</p>
 */
public class VueUnresolvedReferenceSuppressor implements InspectionSuppressor {

  private static final Set<String> SUPPRESSED_TOOLS =
    Set.of("JSUnresolvedReference", "JSUnresolvedVariable", "JSUnresolvedFunction");

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
    if (!SUPPRESSED_TOOLS.contains(toolId)) {
      return false;
    }
    if (!VueSfcModel.isInsideTemplate(element)) {
      return false;
    }
    return isVueInspectionEnabled(element);
  }

  @Override
  public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
    return SuppressQuickFix.EMPTY_ARRAY;
  }

  private static boolean isVueInspectionEnabled(@NotNull PsiElement element) {
    Project project = element.getProject();
    if (project == null || project.isDisposed()) {
      return false;
    }
    try {
      InspectionProfile profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
      HighlightDisplayKey key = HighlightDisplayKey.find(VueTemplateUnresolvedReferenceInspection.SHORT_NAME);
      return key != null && profile.isToolEnabled(key, element);
    }
    catch (Throwable e) {
      // Never hide the original diagnostics because of a failure of the lookup.
      return false;
    }
  }
}
