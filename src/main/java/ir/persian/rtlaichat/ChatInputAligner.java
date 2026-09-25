package ir.persian.rtlaichat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.BidiTextDirection;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.awt.event.HierarchyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.Bidi;

/**
 * The AI Assistant prompt box is a regular IntelliJ editor: left-aligned, with the global (usually content-based per
 * line) BiDi base direction. While the prompt is Persian this listener right-aligns it and switches that editor's
 * base direction to RTL, so a line like {@code "API رو چطوری صدا بزنم؟"} reads correctly too.
 */
public final class ChatInputAligner implements EditorFactoryListener {
  private static final Logger LOG = Logger.getInstance(ChatInputAligner.class);
  private static final Key<Boolean> IN_AI_CHAT = Key.create("persian.rtl.aichat.inAiChat");
  private static volatile boolean baseDirectionUnsupported;

  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    if (!(editor instanceof EditorImpl) || editor.getEditorKind() == EditorKind.MAIN_EDITOR) return;
    EditorImpl impl = (EditorImpl)editor;

    editor.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        // Let the editor's own caches process the change before touching its layout.
        ApplicationManager.getApplication().invokeLater(() -> update(impl), ModalityState.any());
      }
    }, impl.getDisposable());

    impl.getContentComponent().addHierarchyListener(e -> {
      if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && e.getComponent().isShowing()) update(impl);
    });
  }

  static void update(EditorImpl editor) {
    if (editor.isDisposed()) return;

    Boolean inChat = editor.getUserData(IN_AI_CHAT);
    if (inChat == null) {
      inChat = AiChatUi.isInsideAiChat(editor.getComponent());
      if (inChat == null) return; // not attached yet, retry on the next change
      editor.putUserData(IN_AI_CHAT, inChat);
    }
    if (!inChat) return;

    boolean rtl = RtlSettings.getInstance().alignInput && AiChatUi.isRtlText(editor.getDocument().getImmutableCharSequence());
    setBaseDirection(editor, rtl);
    try {
      if (editor.isRightAligned() != rtl) {
        editor.setHorizontalTextAlignment(rtl ? EditorImpl.TEXT_ALIGNMENT_RIGHT : EditorImpl.TEXT_ALIGNMENT_LEFT);
      }
    }
    catch (LinkageError e) {
      LOG.warn("Editor right alignment is not available in this IDE version", e);
    }
  }

  /** Re-evaluates all open AI chat prompt editors, e.g. after the settings changed. */
  static void updateAll() {
    for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
      if (editor instanceof EditorImpl) update((EditorImpl)editor);
    }
  }

  /**
   * The platform only has a global BiDi base direction setting, so the per-editor value in {@code EditorView} is set
   * directly and its layout caches are reset the same way {@code EditorView.reinitSettings()} does.
   * If the internals differ in some IDE version, only right alignment is applied.
   */
  private static void setBaseDirection(EditorImpl editor, boolean rtl) {
    if (baseDirectionUnsupported) return;
    try {
      int wanted = rtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : globalBidiFlags();
      boolean changed = setBidiFlags(editor.getView(), wanted);
      // Newer IDEs may paint through a second view (backed by the "AD" editor model).
      Field adView = findField(EditorImpl.class, "myAdView");
      Object secondView = adView == null ? null : adView.get(editor);
      if (secondView != null) changed |= setBidiFlags(secondView, wanted);
      if (changed) {
        editor.getContentComponent().revalidate();
        editor.getContentComponent().repaint();
      }
    }
    catch (Throwable t) {
      baseDirectionUnsupported = true;
      LOG.warn("Per-editor BiDi direction is not supported in this IDE version; only right alignment will be used", t);
    }
  }

  private static boolean setBidiFlags(Object view, int wanted) throws ReflectiveOperationException {
    Class<?> viewClass = view.getClass();
    Field flags = field(viewClass, "myBidiFlags");
    if (flags.getInt(view) == wanted) return false;
    flags.setInt(view, wanted);

    Object logicalPositionCache = field(viewClass, "myLogicalPositionCache").get(view);
    method(logicalPositionCache, "reset", boolean.class).invoke(logicalPositionCache, false);
    Object textLayoutCache = field(viewClass, "myTextLayoutCache").get(view);
    method(textLayoutCache, "resetToDocumentSize", boolean.class).invoke(textLayoutCache, false);
    Method invalidateFolds = viewClass.getDeclaredMethod("invalidateFoldRegionLayouts");
    invalidateFolds.setAccessible(true);
    invalidateFolds.invoke(view);
    Object sizeManager = field(viewClass, "mySizeManager").get(view);
    method(sizeManager, "reset").invoke(sizeManager);
    return true;
  }

  private static Field findField(Class<?> owner, String name) {
    try {
      return field(owner, name);
    }
    catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static int globalBidiFlags() {
    BidiTextDirection direction = EditorSettingsExternalizable.getInstance().getBidiTextDirection();
    if (direction == BidiTextDirection.LTR) return Bidi.DIRECTION_LEFT_TO_RIGHT;
    if (direction == BidiTextDirection.RTL) return Bidi.DIRECTION_RIGHT_TO_LEFT;
    return Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
  }

  private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
    Field f = owner.getDeclaredField(name);
    f.setAccessible(true);
    return f;
  }

  private static Method method(Object target, String name, Class<?>... parameters) throws NoSuchMethodException {
    Method m = target.getClass().getDeclaredMethod(name, parameters);
    m.setAccessible(true);
    return m;
  }
}
