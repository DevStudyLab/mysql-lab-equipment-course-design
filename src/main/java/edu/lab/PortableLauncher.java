package edu.lab;

import javax.swing.JOptionPane;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.io.File;
import java.util.*;

/** Reuses the local-demo scripts with the runtimes shipped beside the native launcher. */
public final class PortableLauncher {
    private PortableLauncher() {}
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(System.getProperty("jpackage.app-path")).toAbsolutePath().getParent();
        Path runtime = root.resolve("runtime");
        Path log = runtime.resolve("startup.log");
        try {
            Files.createDirectories(runtime);
            try (FileChannel channel = FileChannel.open(runtime.resolve("launcher.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    JOptionPane.showMessageDialog(null, "系统已经启动，请查看已打开的窗口。", "实验室设备管理系统", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                int result;
                try {
                    List<String> command = powershell(root.resolve("scripts/start-local-demo.ps1"));
                    Collections.addAll(command, "-MySqlBin", root.resolve("mysql/bin").toString(),
                            "-JavaExe", runtime.resolve("bin/java.exe").toString(),
                            "-JarPath", root.resolve("app/lab-equipment-demo.jar").toString());
                    if (Arrays.asList(args).contains("--check")) command.add("-CheckOnly");
                    result = run(command, root, log, false);
                } finally {
                    if (run(powershell(root.resolve("scripts/stop-local-demo.ps1")), root, log, true) != 0)
                        throw new IllegalStateException("关闭演示数据库失败，请查看启动日志；不要移动正在使用的数据目录。");
                }
                if (result != 0) throw new IllegalStateException("启动失败，详细原因见：" + log);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "启动提示", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    private static List<String> powershell(Path script) {
        return new ArrayList<>(Arrays.asList(new File(System.getenv("SystemRoot"), "System32/WindowsPowerShell/v1.0/powershell.exe").getPath(),
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString()));
    }
    private static int run(List<String> command, Path root, Path log, boolean append) throws Exception {
        return new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true)
                .redirectOutput(append ? ProcessBuilder.Redirect.appendTo(log.toFile()) : ProcessBuilder.Redirect.to(log.toFile()))
                .start().waitFor();
    }
}
