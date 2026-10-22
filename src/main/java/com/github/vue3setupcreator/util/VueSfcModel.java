package com.github.vue3setupcreator.util;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small helper around the structure of a Vue single file component ({@code .vue}).
 *
 * <p>It knows how to locate the {@code <template>} and the {@code <script setup>} blocks of a
 * component and how to write a new declaration into the {@code <script setup>} block.</p>
 */
public final class VueSfcModel {

  public static final String TEMPLATE_TAG = "template";
  public static final String SCRIPT_TAG = "script";
  public static final String STYLE_TAG = "style";

  /**
   * The language ID of a Vue single file component.
   */
  private static final String VUE_LANGUAGE_ID = "Vue";

  private static final String SETUP_ATTRIBUTE = "setup";

  /**
   * Single line imports, e.g. {@code import Foo from './Foo.vue';}.
   */
  private static final Pattern SINGLE_LINE_IMPORT = Pattern.compile("(?m)^[ \\t]*import\\b[^;\\n]*;");

  /**
   * Imports with a {@code from} clause, possibly spanning several lines.
   */
  private static final Pattern IMPORT_WITH_FROM =
    Pattern.compile("(?m)^import\\b[\\s\\S]*?\\bfrom\\s+['\"][^'\"]*['\"]\\s*(?:;|$)");

  /**
   * Declarations created by this plugin, they are used to keep the created declarations together.
   */
  private static final Pattern CREATED_DECLARATION =
    Pattern.compile("(?:const\\s+\\w+\\s*=\\s*undefined;|const\\s+\\w+\\s*=\\s*\\(\\)\\s*=>\\s*\\{\\})");

  private final @NotNull PsiFile myFile;

  public VueSfcModel(@NotNull PsiFile file) {
    myFile = file;
  }

  // -------------------------------------------------------------------------------------------
  // Locating the blocks
  // -------------------------------------------------------------------------------------------

  /**
   * @return the top level {@code <template>} tag of the component or {@code null}.
   */
  public @Nullable XmlTag findTemplateTag() {
    return findTopLevelTag(TEMPLATE_TAG, null);
  }

  /**
   * @return the top level {@code <script setup>} tag of the component or {@code null}.
   */
  public @Nullable XmlTag findScriptSetupTag() {
    return findTopLevelTag(SCRIPT_TAG, SETUP_ATTRIBUTE);
  }

  private @Nullable XmlTag findTopLevelTag(@NotNull String tagName, @Nullable String requiredAttribute) {
    for (XmlTag tag : getTopLevelTags()) {
      if (!tagName.equals(tag.getName()) && !tagName.equals(tag.getLocalName())) {
        continue;
      }
      if (requiredAttribute != null) {
        XmlAttribute attribute = tag.getAttribute(requiredAttribute);
        if (attribute == null) {
          continue;
        }
        String value = attribute.getValue();
        if (value != null && "false".equalsIgnoreCase(value.trim())) {
          continue;
        }
      }
      return tag;
    }
    return null;
  }

  /**
   * The direct {@code <template>}/{@code <script>}/{@code <style>} children of the component.
   */
  public @NotNull Collection<XmlTag> getTopLevelTags() {
    List<XmlTag> result = new ArrayList<>();
    PsiElement document = PsiTreeUtil.getChildOfType(myFile, XmlDocument.class);
    PsiElement container = document != null ? document : myFile;
    for (PsiElement child : container.getChildren()) {
      if (child instanceof XmlTag tag) {
        result.add(tag);
      }
    }
    if (result.isEmpty()) {
      // Fall back to the shallowest tags of the file.
      int minDepth = Integer.MAX_VALUE;
      Collection<XmlTag> all = PsiTreeUtil.findChildrenOfType(myFile, XmlTag.class);
      for (XmlTag tag : all) {
        minDepth = Math.min(minDepth, depthOf(tag));
      }
      for (XmlTag tag : all) {
        if (depthOf(tag) == minDepth) {
          result.add(tag);
        }
      }
    }
    return result;
  }

  private static int depthOf(@NotNull PsiElement element) {
    int depth = 0;
    for (PsiElement e = element.getParent(); e != null && !(e instanceof PsiFile); e = e.getParent()) {
      depth++;
    }
    return depth;
  }

  // -------------------------------------------------------------------------------------------
  // Template detection
  // -------------------------------------------------------------------------------------------

  /**
   * Checks whether the given element belongs to the {@code <template>} part of a Vue component.
   *
   * <p>Template expressions live in injected JavaScript/TypeScript fragments, therefore the check
   * is performed on the injection host when the element is part of an injected fragment.</p>
   */
  public static boolean isInsideTemplate(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return false;
    }
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
    PsiElement start = element;
    if (manager.isInjectedFragment(file)) {
      PsiLanguageInjectionHost host = manager.getInjectionHost(file);
      if (host == null) {
        return false;
      }
      start = host;
    }
    else if (!VUE_LANGUAGE_ID.equals(file.getLanguage().getID())) {
      // Not a Vue file and not an injected fragment, so it cannot contain a template.
      return false;
    }
    for (PsiElement e = start; e != null && !(e instanceof PsiFile); e = e.getParent()) {
      if (e instanceof XmlTag tag) {
        String name = tag.getName();
        if (TEMPLATE_TAG.equals(name) || TEMPLATE_TAG.equals(tag.getLocalName())) {
          return true;
        }
        if (SCRIPT_TAG.equals(name) || STYLE_TAG.equals(name)) {
          return false;
        }
      }
    }
    return false;
  }

  // -------------------------------------------------------------------------------------------
  // Writing declarations
  // -------------------------------------------------------------------------------------------

  /**
   * Creates a variable or a function inside the {@code <script setup>} block of the component.
   * The block is created when it does not exist yet.
   */
  public void createDeclaration(@NotNull String name, boolean method) {
    Project project = myFile.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(myFile);
    if (document == null) {
      return;
    }
    WriteCommandAction.runWriteCommandAction(project, () -> {
      // Make sure the PSI is in sync with the document, the ranges of the `<script setup>` block
      // are used to calculate the insertion position.
      documentManager.commitDocument(document);
      XmlTag scriptSetup = findScriptSetupTag();
      if (scriptSetup != null) {
        insertIntoScriptSetup(document, scriptSetup, name, method);
      }
      else {
        createScriptSetupBlock(document, name, method);
      }
      documentManager.commitDocument(document);
    });
  }

  /**
   * @return {@code true} when the given identifier is mentioned somewhere inside the
   * {@code <script setup>} block. Used as a safety net so that already declared bindings are never
   * reported as undefined.
   */
  public boolean isMentionedInScriptSetup(@NotNull String name) {
    XmlTag scriptSetup = findScriptSetupTag();
    if (scriptSetup == null) {
      return false;
    }
    XmlTagValue value = scriptSetup.getValue();
    if (value == null) {
      return false;
    }
    return Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(value.getText()).find();
  }

  // -------------------------------------------------------------------------------------------

  private void insertIntoScriptSetup(@NotNull Document document,
                                     @NotNull XmlTag scriptSetup,
                                     @NotNull String name,
                                     boolean method) {
    XmlTagValue value = scriptSetup.getValue();
    if (value == null) {
      return;
    }
    // The beginning of the block never moves, the end of the block is looked up in the current
    // document text because the PSI can still be outdated after a previous modification.
    int contentStart = value.getTextRange().getStartOffset();
    String text = document.getText();
    int contentEnd = text.indexOf("</script", contentStart);
    if (contentEnd < 0) {
      contentEnd = text.length();
    }
    String content = text.substring(contentStart, contentEnd);
    int insertPosition = computeInsertPosition(content);
    insertPosition = skipCreatedDeclarations(content, insertPosition);
    String indent = detectIndent(content, insertPosition);
    document.insertString(contentStart + insertPosition, indent + buildDeclaration(name, method) + "\n");
  }

  private void createScriptSetupBlock(@NotNull Document document, @NotNull String name, boolean method) {
    String declaration = buildDeclaration(name, method);
    XmlTag template = findTemplateTag();
    if (template != null) {
      TextRange templateRange = template.getTextRange();
      String indent = lineIndentAt(document.getText(), templateRange.getStartOffset());
      document.insertString(templateRange.getEndOffset(),
                            "\n" + indent + "<script setup>\n"
                            + indent + declaration + "\n"
                            + indent + "</script>\n");
    }
    else {
      String text = document.getText();
      String prefix = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
      document.insertString(text.length(), prefix + "<script setup>\n" + declaration + "\n</script>\n");
    }
  }

  /**
   * The declaration is inserted after the last import statement (imports have to stay on top of the
   * block) and directly after the first line break otherwise. The returned offset always points to
   * the beginning of a line.
   */
  public static int computeInsertPosition(@NotNull String content) {
    int lastImportEnd = -1;
    Matcher singleLine = SINGLE_LINE_IMPORT.matcher(content);
    while (singleLine.find()) {
      lastImportEnd = Math.max(lastImportEnd, singleLine.end());
    }
    Matcher withFrom = IMPORT_WITH_FROM.matcher(content);
    while (withFrom.find()) {
      lastImportEnd = Math.max(lastImportEnd, withFrom.end());
    }
    if (lastImportEnd < 0) {
      int lineBreak = content.indexOf('\n');
      return lineBreak >= 0 ? lineBreak + 1 : 0;
    }
    int lineEnd = content.indexOf('\n', lastImportEnd);
    return lineEnd < 0 ? content.length() : lineEnd + 1;
  }

  /**
   * Declarations that were created by this plugin are kept together, a new declaration is added
   * after the already existing ones.
   */
  private static int skipCreatedDeclarations(@NotNull String content, int position) {
    int index = position;
    while (index < content.length()) {
      int lineEnd = content.indexOf('\n', index);
      if (lineEnd < 0) {
        lineEnd = content.length();
      }
      String line = content.substring(index, lineEnd).trim();
      if (!CREATED_DECLARATION.matcher(line).matches()) {
        break;
      }
      index = lineEnd + 1;
    }
    return index;
  }

  /**
   * The indentation of the line the declaration is inserted into. When that line is empty the
   * indentation of the next non empty line is used.
   */
  public static String detectIndent(@NotNull String content, int position) {
    int index = position;
    while (index <= content.length()) {
      int lineEnd = content.indexOf('\n', index);
      if (lineEnd < 0) {
        lineEnd = content.length();
      }
      String line = content.substring(index, lineEnd);
      if (!line.isBlank()) {
        return leadingWhitespace(line);
      }
      index = lineEnd + 1;
    }
    return "";
  }

  private static @NotNull String lineIndentAt(@NotNull String text, int offset) {
    int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1));
    lineStart = lineStart < 0 ? 0 : lineStart + 1;
    return leadingWhitespace(text.substring(lineStart, Math.min(text.length(), offset)));
  }

  private static @NotNull String leadingWhitespace(@NotNull String line) {
    int i = 0;
    while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
      i++;
    }
    return line.substring(0, i);
  }

  private static @NotNull String buildDeclaration(@NotNull String name, boolean method) {
    return method ? "const " + name + " = () => {};" : "const " + name + " = undefined;";
  }
}
