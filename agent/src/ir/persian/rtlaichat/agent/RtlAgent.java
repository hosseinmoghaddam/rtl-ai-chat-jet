package ir.persian.rtlaichat.agent;

import java.lang.instrument.Instrumentation;

/**
 * Tiny java agent that is attached to the running IDE to obtain an {@link Instrumentation} instance.
 * It lives on the system class path, so patched Compose code can call {@link #mostlyRtl(CharSequence)}.
 */
public final class RtlAgent {
  public static volatile Instrumentation instrumentation;

  private RtlAgent() {
  }

  public static void agentmain(String args, Instrumentation inst) {
    instrumentation = inst;
  }

  public static void premain(String args, Instrumentation inst) {
    instrumentation = inst;
  }

  /**
   * True when a paragraph has more RTL letters than LTR letters, e.g. {@code "API رو چطوری صدا بزنم؟"},
   * which the plain first-strong-character rule would lay out as LTR.
   */
  public static boolean mostlyRtl(CharSequence text) {
    int rtl = 0;
    int ltr = 0;
    for (int i = 0, n = text.length(); i < n; ) {
      int cp = Character.codePointAt(text, i);
      switch (Character.getDirectionality(cp)) {
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
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
