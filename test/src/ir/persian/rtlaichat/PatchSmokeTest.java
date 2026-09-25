package ir.persian.rtlaichat;

import com.sun.tools.attach.VirtualMachine;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * Standalone check (outside the IDE) that the Compose patch changes real Skia text layout.
 * Usage: java -Djdk.attach.allowAttachSelf=true -cp <ide lib/*>:build/classes:test-classes ir.persian.rtlaichat.PatchSmokeTest agent.jar outDir
 */
public final class PatchSmokeTest {
  private static final String[] TEXTS = {
    "سلام! من از React و TypeScript استفاده می‌کنم. چطور می‌توانم یک API ساده بسازم؟",
    "API رو چطوری صدا بزنم؟",
    "This English paragraph must stay left-to-right.",
    "fun main() { println(\"hi\") }",
  };
  private static final int WIDTH = 560;

  public static void main(String[] args) throws Throwable {
    String agentJar = args[0];
    File outDir = new File(args[1]);

    report("before");
    render(new File(outDir, "before.png"));

    VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
    vm.loadAgent(agentJar);
    vm.detach();

    ComposeTextDirectionPatch.setEnabled(true);
    System.out.println("patched=" + ComposeTextDirectionPatch.isPatched());
    report("after");
    render(new File(outDir, "after.png"));

    ComposeTextDirectionPatch.setEnabled(false);
    report("reverted");
  }

  private static void report(String label) throws Throwable {
    Object ltr = layoutDirectionLtr();
    int unspecified = textDirection("getUnspecified");
    Object resolved = method(Class.forName("androidx.compose.ui.text.TextStyleKt"), "resolveTextDirection").invoke(null, ltr, unspecified);
    StringBuilder sb = new StringBuilder(label + ": resolveTextDirection(Ltr, Unspecified)=" + resolved
                                         + " (Ltr=" + textDirection("getLtr") + ", ContentOrLtr=" + textDirection("getContentOrLtr") + ")");
    for (String text : TEXTS) {
      Object p = paragraph(text);
      Class<?> pc = Class.forName("androidx.compose.ui.text.Paragraph");
      sb.append("\n   dir=").append(pc.getMethod("getParagraphDirection", int.class).invoke(p, 0))
        .append(" lineLeft=").append(Math.round((Float)pc.getMethod("getLineLeft", int.class).invoke(p, 0)))
        .append(" lineRight=").append(Math.round((Float)pc.getMethod("getLineRight", int.class).invoke(p, 0)))
        .append("  «").append(text, 0, Math.min(24, text.length())).append("…»");
    }
    System.out.println(sb);
  }

  private static void render(File file) throws Throwable {
    Class<?> pc = Class.forName("androidx.compose.ui.text.Paragraph");
    Class<?> canvasType = Class.forName("androidx.compose.ui.graphics.Canvas");
    int rowHeight = 90;
    int height = rowHeight * TEXTS.length;

    Object bitmap = method(Class.forName("androidx.compose.ui.graphics.ImageBitmapKt"), "ImageBitmap-")
      .invoke(null, WIDTH, height, 0, true, colorSpaceSrgb());
    Object canvas = Class.forName("androidx.compose.ui.graphics.CanvasKt")
      .getMethod("Canvas", Class.forName("androidx.compose.ui.graphics.ImageBitmap")).invoke(null, bitmap);
    long black = (Long)Class.forName("androidx.compose.ui.graphics.ColorKt").getMethod("Color", int.class).invoke(null, 0xFF000000);
    Method translate = canvasType.getMethod("translate", float.class, float.class);
    Method paint = method(pc, "paint-RPmYEkk");
    for (String text : TEXTS) {
      Object p = paragraph(text);
      paint.invoke(p, canvas, black, null, null);
      translate.invoke(canvas, 0f, (float)rowHeight);
    }

    BufferedImage text = (BufferedImage)Class.forName("androidx.compose.ui.graphics.DesktopImageConverters_desktopKt")
      .getMethod("asAwtImage", Class.forName("androidx.compose.ui.graphics.ImageBitmap")).invoke(null, bitmap);
    BufferedImage out = new BufferedImage(WIDTH + 20, height + 20, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, out.getWidth(), out.getHeight());
    g.setColor(new Color(0xCC, 0xCC, 0xFF));
    for (int i = 0; i < TEXTS.length; i++) g.drawRect(10, 10 + i * rowHeight, WIDTH, rowHeight - 6);
    g.drawImage(text, 10, 10, null);
    g.dispose();
    ImageIO.write(out, "png", file);
  }

  private static Object paragraph(String text) throws Throwable {
    Class<?> styleType = Class.forName("androidx.compose.ui.text.TextStyle");
    Object companion = styleType.getField("Companion").get(null);
    Object defaultStyle = companion.getClass().getMethod("getDefault").invoke(companion);
    Object style = Class.forName("androidx.compose.ui.text.TextStyleKt")
      .getMethod("resolveDefaults", styleType, Class.forName("androidx.compose.ui.unit.LayoutDirection"))
      .invoke(null, defaultStyle, layoutDirectionLtr());

    Class<?> constraintsType = Class.forName("androidx.compose.ui.unit.Constraints");
    Object cc = constraintsType.getField("Companion").get(null);
    long constraints = (Long)method(cc.getClass(), "restrictConstraints-").invoke(cc, 0, WIDTH, 0, 10000, true);
    Object density = Class.forName("androidx.compose.ui.unit.DensityKt").getMethod("Density", float.class, float.class).invoke(null, 2f, 1f);
    Object resolver = Class.forName("androidx.compose.ui.text.font.FontFamilyResolver_sikioKt").getMethod("createFontFamilyResolver").invoke(null);

    return method(Class.forName("androidx.compose.ui.text.ParagraphKt"), "Paragraph-Ul8oQg4")
      .invoke(null, text, style, constraints, density, resolver, Collections.emptyList(), Collections.emptyList(), Integer.MAX_VALUE, 1);
  }

  private static Object layoutDirectionLtr() throws Throwable {
    return Class.forName("androidx.compose.ui.unit.LayoutDirection").getField("Ltr").get(null);
  }

  private static int textDirection(String getterPrefix) throws Throwable {
    Class<?> td = Class.forName("androidx.compose.ui.text.style.TextDirection");
    Object companion = td.getField("Companion").get(null);
    for (Method m : companion.getClass().getMethods()) {
      if (m.getName().equals(getterPrefix) || m.getName().startsWith(getterPrefix + "-")) return (Integer)m.invoke(companion);
    }
    throw new NoSuchMethodException(getterPrefix);
  }

  private static Object colorSpaceSrgb() throws Throwable {
    Class<?> spaces = Class.forName("androidx.compose.ui.graphics.colorspace.ColorSpaces");
    Object instance = spaces.getField("INSTANCE").get(null);
    return spaces.getMethod("getSrgb").invoke(instance);
  }

  private static Method method(Class<?> owner, String prefix) throws NoSuchMethodException {
    for (Method m : owner.getMethods()) {
      if (m.getName().startsWith(prefix) && !m.getName().endsWith("$default")) return m;
    }
    throw new NoSuchMethodException(owner.getName() + "." + prefix);
  }
}
