package ir.persian.rtlaichat;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.sun.tools.attach.VirtualMachine;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Obtains an {@link Instrumentation} for the running IDE by self-attaching the bundled agent jar.
 * IntelliJ-based IDEs start with {@code -Djdk.attach.allowAttachSelf=true}, so this works out of the box.
 */
final class AgentLoader {
  static final String PLUGIN_ID = "ir.persian.rtlaichat";
  private static final String AGENT_CLASS = "ir.persian.rtlaichat.agent.RtlAgent";
  private static final String AGENT_JAR = "rtl-ai-chat-agent.jar";

  private AgentLoader() {
  }

  static synchronized Instrumentation get() throws Exception {
    Instrumentation inst = fromAgentClass();
    if (inst != null) return inst;

    Path jar = agentJar();
    VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
    try {
      vm.loadAgent(jar.toString());
    }
    finally {
      vm.detach();
    }

    inst = fromAgentClass();
    if (inst == null) throw new IllegalStateException("Agent was loaded but did not publish Instrumentation");
    return inst;
  }

  private static Instrumentation fromAgentClass() {
    try {
      Class<?> agent = Class.forName(AGENT_CLASS, true, ClassLoader.getSystemClassLoader());
      return (Instrumentation)agent.getField("instrumentation").get(null);
    }
    catch (ClassNotFoundException e) {
      return null;
    }
    catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path agentJar() {
    IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
    if (plugin == null) throw new IllegalStateException("Plugin descriptor not found: " + PLUGIN_ID);
    Path jar = plugin.getPluginPath().resolve("agent").resolve(AGENT_JAR);
    if (!Files.isRegularFile(jar)) throw new IllegalStateException("Agent jar not found: " + jar);
    return jar;
  }
}
