package com.github.vue3setupcreator.inspection;

import com.github.vue3setupcreator.VueSetupCreatorBundle;
import com.github.vue3setupcreator.util.VueSfcModel;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Adds a new binding to the {@code <script setup>} block of a Vue component.
 *
 * <p>The fix is used in two flavours: one creates a variable ({@code const foo = undefined;}) and
 * the other one creates a function ({@code const foo = () => {};}).</p>
 */
public class CreateScriptSetupDeclarationFix extends LocalQuickFixOnPsiElement {

  private final @NotNull String myName;
  private final boolean myMethod;

  public CreateScriptSetupDeclarationFix(@NotNull PsiElement element, @NotNull String name, boolean method) {
    super(element);
    myName = name;
    myMethod = method;
  }

  @Override
  public @NotNull String getText() {
    return myMethod
           ? VueSetupCreatorBundle.message("fix.create.method", myName)
           : VueSetupCreatorBundle.message("fix.create.variable", myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return "Create Vue script setup declaration";
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiFile topLevelFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(startElement);
    if (topLevelFile == null) {
      return;
    }
    new VueSfcModel(topLevelFile).createDeclaration(myName, myMethod);
  }
}
