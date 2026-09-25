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
   * True when a paragraph starts with an RTL letter or has more RTL than LTR letters.
   * The latter covers {@code "API رو چطوری صدا بزنم؟"}.
   */
  public static boolean mostlyRtl(CharSequence text) {
    int rtl = 0;
    int ltr = 0;
    for (int i = 0, n = text.length(); i < n; ) {
      int cp = Character.codePointAt(text, i);
      switch (Character.getDirectionality(cp)) {
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
        case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
          if (rtl == 0 && ltr == 0) return true;
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
