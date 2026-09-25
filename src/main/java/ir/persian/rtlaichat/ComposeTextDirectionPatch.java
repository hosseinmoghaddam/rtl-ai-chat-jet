package ir.persian.rtlaichat;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.MethodVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;

/**
 * The AI Assistant chat is rendered with Compose (Jewel). Compose resolves an unspecified
 * {@code TextStyle.textDirection} strictly from the layout direction, which is always LTR in the IDE,
 * so Persian paragraphs are laid out left-to-right and left-aligned.
 * <p>
 * Two small patches fix that:
 * <ol>
 *   <li>{@code TextStyleKt.resolveTextDirection}: treat {@code TextDirection.Unspecified} as {@code TextDirection.Content},
 *   so every paragraph takes its direction from its text (like HTML {@code dir="auto"}).</li>
 *   <li>{@code SkiaParagraphIntrinsics_skikoKt.contentBasedTextDirection}: additionally treat a paragraph as RTL when most of
 *   its letters are RTL, so {@code "API رو چطوری صدا بزنم؟"} is RTL although it starts with a Latin word.</li>
 * </ol>
 * English text and code stay LTR. Both patches are reverted when the feature is switched off.
 */
final class ComposeTextDirectionPatch {
  private static final Logger LOG = Logger.getInstance(ComposeTextDirectionPatch.class);

  private static final String TEXT_STYLE_KT = "androidx/compose/ui/text/TextStyleKt";
  private static final String PARAGRAPH_INTRINSICS_KT = "androidx/compose/ui/text/platform/SkiaParagraphIntrinsics_skikoKt";
  private static final Set<String> TARGETS = Set.of(TEXT_STYLE_KT, PARAGRAPH_INTRINSICS_KT);

  private static final String TEXT_DIRECTION = "androidx/compose/ui/text/style/TextDirection";
  private static final String TEXT_DIRECTION_COMPANION = TEXT_DIRECTION + "$Companion";
  private static final String RESOLVED_TEXT_DIRECTION = "androidx/compose/ui/text/style/ResolvedTextDirection";
  private static final String AGENT_CLASS = "ir/persian/rtlaichat/agent/RtlAgent";
  private static final int ASM_API = Opcodes.ASM9;

  private static ClassFileTransformer transformer;
  private static volatile boolean patched;

  private ComposeTextDirectionPatch() {
  }

  static synchronized boolean isEnabled() {
    return transformer != null;
  }

  /** True once the Compose class has actually been rewritten (it may be loaded later than the plugin). */
  static boolean isPatched() {
    return patched;
  }

  static synchronized void setEnabled(boolean enabled) throws Exception {
    if (enabled == (transformer != null)) return;
    Instrumentation inst = AgentLoader.get();
    if (enabled) {
      transformer = new Transformer();
      inst.addTransformer(transformer, true);
    }
    else {
      inst.removeTransformer(transformer);
      transformer = null;
      patched = false;
    }
    for (Class<?> c : inst.getAllLoadedClasses()) {
      if (TARGETS.contains(c.getName().replace('.', '/')) && inst.isModifiableClass(c)) {
        inst.retransformClasses(c);
      }
    }
  }

  private static final class Transformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
      if (className == null || !TARGETS.contains(className)) return null;
      try {
        byte[] result;
        if (TEXT_STYLE_KT.equals(className)) {
          result = patchResolveTextDirection(classfileBuffer);
          if (result != null) patched = true;
        }
        else {
          result = canSeeAgent(loader) ? patchContentBasedTextDirection(classfileBuffer) : null;
        }
        LOG.info((result != null ? "Patched " : "Left unchanged (unexpected shape) ") + className);
        return result;
      }
      catch (Throwable t) {
        LOG.warn("Failed to patch " + className, t);
        return null;
      }
    }
  }

  private static boolean canSeeAgent(ClassLoader loader) {
    if (loader == null) return false;
    try {
      Class<?> agent = Class.forName(AGENT_CLASS.replace('/', '.'), false, loader);
      return agent.getClassLoader() == ClassLoader.getSystemClassLoader();
    }
    catch (Throwable e) {
      return false;
    }
  }

  /** Patch 1: {@code if (textDirection == Unspecified) textDirection = Content;} */
  static byte[] patchResolveTextDirection(byte[] classBytes) {
    String[] getters = new String[2];
    ClassReader reader = new ClassReader(classBytes);
    MethodMatcher matcher = (access, name, desc) -> (access & Opcodes.ACC_STATIC) != 0
                                                     && name.startsWith("resolveTextDirection")
                                                     && desc.equals("(Landroidx/compose/ui/unit/LayoutDirection;I)I");
    // Discover the name-mangled getters used for TextDirection.Unspecified / TextDirection.Content.
    boolean found = scan(reader, matcher, new MethodVisitor(ASM_API) {
      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (!TEXT_DIRECTION_COMPANION.equals(owner) || !"()I".equals(descriptor)) return;
        if (name.equals("getUnspecified") || name.startsWith("getUnspecified-")) getters[0] = name;
        else if (name.equals("getContent") || name.startsWith("getContent-")) getters[1] = name;
      }
    });
    if (!found || getters[0] == null || getters[1] == null) return null;

    return prepend(reader, matcher, mv -> {
      Label keep = new Label();
      mv.visitVarInsn(Opcodes.ILOAD, 1);
      mv.visitFieldInsn(Opcodes.GETSTATIC, TEXT_DIRECTION, "Companion", "L" + TEXT_DIRECTION_COMPANION + ";");
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TEXT_DIRECTION_COMPANION, getters[0], "()I", false);
      mv.visitJumpInsn(Opcodes.IF_ICMPNE, keep);
      mv.visitFieldInsn(Opcodes.GETSTATIC, TEXT_DIRECTION, "Companion", "L" + TEXT_DIRECTION_COMPANION + ";");
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TEXT_DIRECTION_COMPANION, getters[1], "()I", false);
      mv.visitVarInsn(Opcodes.ISTORE, 1);
      mv.visitLabel(keep);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    });
  }

  /** Patch 2: {@code if (RtlAgent.mostlyRtl(text)) return ResolvedTextDirection.Rtl;} */
  static byte[] patchContentBasedTextDirection(byte[] classBytes) {
    ClassReader reader = new ClassReader(classBytes);
    MethodMatcher matcher = (access, name, desc) -> (access & Opcodes.ACC_STATIC) != 0
                                                     && name.equals("contentBasedTextDirection")
                                                     && desc.startsWith("(Ljava/lang/String;")
                                                     && desc.endsWith(")L" + RESOLVED_TEXT_DIRECTION + ";");
    if (!scan(reader, matcher, null)) return null;

    return prepend(reader, matcher, mv -> {
      Label keep = new Label();
      mv.visitVarInsn(Opcodes.ALOAD, 0);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, AGENT_CLASS, "mostlyRtl", "(Ljava/lang/CharSequence;)Z", false);
      mv.visitJumpInsn(Opcodes.IFEQ, keep);
      mv.visitFieldInsn(Opcodes.GETSTATIC, RESOLVED_TEXT_DIRECTION, "Rtl", "L" + RESOLVED_TEXT_DIRECTION + ";");
      mv.visitInsn(Opcodes.ARETURN);
      mv.visitLabel(keep);
      mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    });
  }

  private interface MethodMatcher {
    boolean matches(int access, String name, String descriptor);
  }

  private interface Prologue {
    void emit(MethodVisitor mv);
  }

  /** Returns whether a method matching {@code matcher} exists; feeds its code to {@code inspector} if given. */
  private static boolean scan(ClassReader reader, MethodMatcher matcher, MethodVisitor inspector) {
    boolean[] found = new boolean[1];
    reader.accept(new ClassVisitor(ASM_API) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (!matcher.matches(access, name, descriptor)) return null;
        found[0] = true;
        return inspector;
      }
    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return found[0];
  }

  /**
   * Inserts {@code prologue} at the start of the matching method. The prologue may only branch forward to a label
   * placed at its end, with an {@code F_SAME} frame, and needs at most 2 stack slots.
   */
  private static byte[] prepend(ClassReader reader, MethodMatcher matcher, Prologue prologue) {
    ClassWriter writer = new ClassWriter(reader, 0);
    reader.accept(new ClassVisitor(ASM_API, writer) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!matcher.matches(access, name, descriptor)) return mv;
        return new MethodVisitor(ASM_API, mv) {
          @Override
          public void visitCode() {
            super.visitCode();
            prologue.emit(getDelegate());
          }

          @Override
          public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 2), maxLocals);
          }
        };
      }
    }, 0);
    return writer.toByteArray();
  }
}
