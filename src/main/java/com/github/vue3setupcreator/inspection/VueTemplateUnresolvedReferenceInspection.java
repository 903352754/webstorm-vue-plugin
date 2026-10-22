package com.github.vue3setupcreator.inspection;

import com.github.vue3setupcreator.VueSetupCreatorBundle;
import com.github.vue3setupcreator.util.VueSfcModel;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.JSElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports identifiers of a Vue 3 {@code <template>} block that cannot be resolved and offers the
 * quick fixes "创建变量" (create variable) and "创建方法" (create method). Both quick fixes add the
 * new binding to the {@code <script setup>} block of the component.
 *
 * <p>The inspection is registered for the {@code VueExpr} meta language, therefore it is applied to
 * the JavaScript/TypeScript expressions of a Vue template only:</p>
 * <ul>
 *   <li>attribute expressions ({@code v-if="…"}, {@code :title="…"}, {@code @click="…"}) are part
 *   of the Vue file itself,</li>
 *   <li>interpolations ({@code {{ … }}}) live in an injected JavaScript fragment.</li>
 * </ul>
 */
public class VueTemplateUnresolvedReferenceInspection extends LocalInspectionTool {

  public static final String SHORT_NAME = "VueTemplateUnresolvedReference";

  @Override
  public @NotNull String getShortName() {
    return SHORT_NAME;
  }

  @Override
  public @NotNull String getDisplayName() {
    return VueSetupCreatorBundle.message("inspection.unresolved.template.reference.display.name");
  }

  @Override
  public @NotNull String getGroupDisplayName() {
    return "Vue.js";
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    ModelCache cache = new ModelCache();
    return new JSElementVisitor() {
      @Override
      public void visitJSReferenceExpression(@NotNull JSReferenceExpression expression) {
        super.visitJSReferenceExpression(expression);
        checkReference(holder, cache, expression);
      }
    };
  }

  private static void checkReference(@NotNull ProblemsHolder holder,
                                     @NotNull ModelCache cache,
                                     @NotNull JSReferenceExpression expression) {
    // Only the root of a qualified expression (`foo` of `foo.bar`) is interesting, the members of
    // the chain are properties of an existing object.
    if (expression.getQualifier() != null) {
      return;
    }
    String name = expression.getReferenceName();
    if (!isIdentifier(name)) {
      return;
    }
    if (!VueSfcModel.isInsideTemplate(expression)) {
      return;
    }
    PsiReference reference = expression.getReference();
    if (reference == null || reference.isSoft()) {
      return;
    }
    if (reference.resolve() != null) {
      // The binding exists, nothing to report.
      return;
    }
    // Safety net: never report a name that is mentioned inside `<script setup>`.
    VueSfcModel model = cache.get(expression);
    if (model != null && model.isMentionedInScriptSetup(name)) {
      return;
    }
    PsiElement nameElement = expression.getReferenceNameElement();
    if (nameElement == null) {
      return;
    }
    holder.registerProblem(nameElement,
                           VueSetupCreatorBundle.message("inspection.unresolved.template.reference.message", name),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           createFixes(nameElement, name, isCall(expression)));
  }

  private static @NotNull LocalQuickFix[] createFixes(@NotNull PsiElement element,
                                                       @NotNull String name,
                                                       boolean called) {
    CreateScriptSetupDeclarationFix variable = new CreateScriptSetupDeclarationFix(element, name, false);
    CreateScriptSetupDeclarationFix method = new CreateScriptSetupDeclarationFix(element, name, true);
    // The fix that matches the usage is offered first.
    return called ? new LocalQuickFix[]{method, variable} : new LocalQuickFix[]{variable, method};
  }

  private static boolean isCall(@NotNull JSReferenceExpression expression) {
    PsiElement parent = expression.getParent();
    return parent instanceof JSCallExpression call && call.getMethodExpression() == expression;
  }

  private static boolean isIdentifier(@Nullable String name) {
    if (name == null || name.isEmpty()) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    for (int i = 1; i < name.length(); i++) {
      if (!Character.isJavaIdentifierPart(name.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Keeps the {@link VueSfcModel} of the current component, the top level file is the same for all
   * references of one component.
   */
  private static final class ModelCache {
    private @Nullable PsiFile myTopLevelFile;
    private @Nullable VueSfcModel myModel;

    private @Nullable VueSfcModel get(@NotNull PsiElement element) {
      PsiFile topLevel = InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element);
      if (topLevel == null) {
        return null;
      }
      if (topLevel != myTopLevelFile) {
        myTopLevelFile = topLevel;
        myModel = new VueSfcModel(topLevel);
      }
      return myModel;
    }
  }
}
