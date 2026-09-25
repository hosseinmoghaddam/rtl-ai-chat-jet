package ir.persian.rtlaichat;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(name = "PersianRtlAiChat", storages = @Storage("persian-rtl-ai-chat.xml"))
public final class RtlSettings implements PersistentStateComponent<RtlSettings> {
  /** Content-based (first strong character) direction for every paragraph of AI chat messages. */
  public boolean contentDirection = true;
  /** Right-align the prompt box while it starts with Persian text. */
  public boolean alignInput = true;
  /** Mirror the whole chat message panel to an RTL layout. */
  public boolean mirrorLayout = false;

  static RtlSettings getInstance() {
    return ApplicationManager.getApplication().getService(RtlSettings.class);
  }

  @Override
  public @NotNull RtlSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull RtlSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
