package ir.persian.rtlaichat;

import com.intellij.openapi.editor.bidi.BidiRegionsSeparator;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * The editor runs the BiDi algorithm separately for every lexer token unless the language says otherwise.
 * The AI Assistant prompt lexer ({@code ChatInput}) emits each word and each space as its own token, so every Persian
 * word became a separate RTL island inside an LTR line and {@code "سلام خوبی"} was shown as {@code "خوبی سلام"}.
 * Like plain text, treat the whole line as one BiDi paragraph.
 */
public final class ChatInputBidiRegionsSeparator extends BidiRegionsSeparator {
  @Override
  public boolean createBorderBetweenTokens(@NotNull IElementType previousTokenType, @NotNull IElementType tokenType) {
    return false;
  }
}
