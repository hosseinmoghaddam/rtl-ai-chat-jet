package ir.persian.rtlaichat;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.HierarchyEvent;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Optional, off by default: switches the whole AI chat message panel to a right-to-left layout.
 * {@code ComposePanel.setComponentOrientation} is forwarded to Compose as {@code LayoutDirection.Rtl}, which mirrors
 * the chat UI (bubbles, icons, lists) and makes every paragraph RTL, including English ones.
 */
final class ChatLayoutMirror {
  private static final Set<Component> mirrored = Collections.newSetFromMap(new WeakHashMap<>());
  private static final AWTEventListener listener = event -> {
    if (!(event instanceof HierarchyEvent)) return;
    HierarchyEvent e = (HierarchyEvent)event;
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
    Component c = e.getComponent();
    if (c.isShowing() && AiChatUi.isComposePanel(c)) apply(c);
  };
  private static boolean enabled;

  private ChatLayoutMirror() {
  }

  /** Must be called on the EDT. */
  static void setEnabled(boolean value) {
    if (enabled == value) return;
    enabled = value;
    if (value) {
      Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.HIERARCHY_EVENT_MASK);
      for (Window window : Window.getWindows()) scan(window);
    }
    else {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      for (Component c : mirrored.toArray(new Component[0])) {
        c.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
      }
      mirrored.clear();
    }
  }

  private static void scan(Component c) {
    if (AiChatUi.isComposePanel(c)) {
      apply(c);
      return;
    }
    if (c instanceof Container) {
      for (Component child : ((Container)c).getComponents()) scan(child);
    }
  }

  private static void apply(Component composePanel) {
    if (mirrored.contains(composePanel) || !Boolean.TRUE.equals(AiChatUi.isInsideAiChat(composePanel))) return;
    composePanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    mirrored.add(composePanel);
  }
}
