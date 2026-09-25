package ir.persian.rtlaichat;

import java.awt.Component;
import java.awt.Window;

/** Helpers to recognise the JetBrains AI Assistant chat UI and to detect RTL text. */
final class AiChatUi {
  /** Package of the JetBrains AI Assistant / Junie plugin ({@code com.intellij.ml.llm}). */
  private static final String AI_ASSISTANT_PACKAGE = "com.intellij.ml.llm.";

  private AiChatUi() {
  }

  /**
   * @return {@code TRUE} if the component lives inside AI Assistant UI, {@code FALSE} if it is attached to a window
   * and does not, {@code null} if it is not attached to a window yet (the answer is unknown).
   */
  static Boolean isInsideAiChat(Component component) {
    Component c = component;
    Component last = null;
    while (c != null) {
      if (isAiAssistantClass(c.getClass())) return Boolean.TRUE;
      last = c;
      c = c.getParent();
    }
    return last instanceof Window ? Boolean.FALSE : null;
  }

  private static boolean isAiAssistantClass(Class<?> type) {
    // Also look at superclasses: AI Assistant often subclasses platform components.
    for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
      if (t.getName().startsWith(AI_ASSISTANT_PACKAGE)) return true;
      if (t.getName().startsWith("javax.swing.") || t.getName().startsWith("java.awt.")) return false;
    }
    return false;
  }

  static boolean isComposePanel(Component component) {
    for (Class<?> t = component.getClass(); t != null && t != Object.class; t = t.getSuperclass()) {
      if (t.getName().equals("androidx.compose.ui.awt.ComposePanel")) return true;
    }
    return false;
  }

  /**
   * Same rule as the patched Compose text: RTL when the first strong character is RTL (HTML {@code dir="auto"})
   * or when most letters are RTL (so {@code "API رو چطوری صدا بزنم؟"} is RTL too).
   */
  static boolean isRtlText(CharSequence text) {
    int rtl = 0;
    int ltr = 0;
    for (int i = 0, n = text.length(); i < n; ) {
      int cp = Character.codePointAt(text, i);
      switch (Character.getDirectionality(cp)) {
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
          if (ltr == 0 && rtl == 0) return true;
          rtl++;
          break;
        case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
          ltr++;
          break;
        default:
          break;
      }
      i += Character.charCount(cp);
    }
    return rtl > ltr;
  }
}
