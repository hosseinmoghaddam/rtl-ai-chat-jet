package ir.persian.rtlaichat;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.JComponent;

public final class RtlConfigurable implements Configurable {
  private JBCheckBox contentDirection;
  private JBCheckBox alignInput;
  private JBCheckBox mirrorLayout;
  private JBLabel status;

  @Override
  public @Nls String getDisplayName() {
    return "Persian RTL for AI Chat";
  }

  @Override
  public JComponent createComponent() {
    contentDirection = new JBCheckBox("Right-to-left Persian paragraphs in chat messages (auto direction per paragraph)");
    alignInput = new JBCheckBox("Right-align the prompt box when the text starts with Persian");
    mirrorLayout = new JBCheckBox("Mirror the whole chat panel to right-to-left layout (English and code paragraphs become RTL too)");
    status = new JBLabel();
    status.setForeground(UIUtil.getContextHelpForeground());
    reset();
    return FormBuilder.createFormBuilder()
      .addComponent(contentDirection)
      .addComponent(alignInput)
      .addComponent(mirrorLayout)
      .addVerticalGap(8)
      .addComponent(status)
      .addComponentFillVertically(new javax.swing.JPanel(), 0)
      .getPanel();
  }

  @Override
  public boolean isModified() {
    RtlSettings s = RtlSettings.getInstance();
    return contentDirection.isSelected() != s.contentDirection
           || alignInput.isSelected() != s.alignInput
           || mirrorLayout.isSelected() != s.mirrorLayout;
  }

  @Override
  public void apply() {
    RtlSettings s = RtlSettings.getInstance();
    s.contentDirection = contentDirection.isSelected();
    s.alignInput = alignInput.isSelected();
    s.mirrorLayout = mirrorLayout.isSelected();
    RtlApplier.applySettings();
  }

  @Override
  public void reset() {
    RtlSettings s = RtlSettings.getInstance();
    contentDirection.setSelected(s.contentDirection);
    alignInput.setSelected(s.alignInput);
    mirrorLayout.setSelected(s.mirrorLayout);
    status.setText(RtlApplier.status() + ". Already rendered messages update after reopening the chat.");
  }

  @Override
  public void disposeUIResources() {
    contentDirection = null;
    alignInput = null;
    mirrorLayout = null;
    status = null;
  }
}
