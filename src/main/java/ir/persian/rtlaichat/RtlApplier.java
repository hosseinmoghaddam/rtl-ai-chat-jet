package ir.persian.rtlaichat;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Applies {@link RtlSettings} at IDE start-up and whenever the settings change. */
public final class RtlApplier implements AppLifecycleListener {
  private static final Logger LOG = Logger.getInstance(RtlApplier.class);
  private static volatile String lastError;

  @Override
  public void appFrameCreated(@NotNull List<String> commandLineArgs) {
    applySettings();
  }

  static void applySettings() {
    RtlSettings settings = RtlSettings.getInstance();
    boolean contentDirection = settings.contentDirection;
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        ComposeTextDirectionPatch.setEnabled(contentDirection);
        lastError = null;
      }
      catch (Throwable t) {
        lastError = t.toString();
        LOG.warn("Cannot apply the Compose RTL text direction patch", t);
        NotificationGroupManager.getInstance().getNotificationGroup("Persian RTL AI Chat")
          .createNotification("Persian RTL for AI Chat",
                              "Could not enable RTL for chat messages: " + t.getMessage(),
                              NotificationType.WARNING)
          .notify(null);
      }
    });
    ApplicationManager.getApplication().invokeLater(() -> {
      ChatLayoutMirror.setEnabled(settings.mirrorLayout);
      ChatInputAligner.updateAll();
    });
  }

  static String status() {
    if (lastError != null) return "Error: " + lastError;
    if (!ComposeTextDirectionPatch.isEnabled()) return "Message direction patch is off";
    if (ComposeTextDirectionPatch.isPatched()) return "Message direction patch is active";
    return "Message direction patch is waiting for the chat to open";
  }
}
