package com.vocagate.desktop.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class DesktopUsbReverseSetup {
    private static final long COMMAND_TIMEOUT_SECONDS = 5L;

    public interface CommandRunner {
        CommandResult run(List<String> command) throws IOException, InterruptedException;
    }

    public static final class CommandResult {
        private final int exitCode;
        private final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }

        public int exitCode() {
            return exitCode;
        }

        public String output() {
            return output;
        }
    }

    public static final class Result {
        private final boolean ready;
        private final String message;

        private Result(boolean ready, String message) {
            this.ready = ready;
            this.message = message;
        }

        public static Result ready(String message) {
            return new Result(true, message);
        }

        public static Result notReady(String message) {
            return new Result(false, message);
        }

        public boolean ready() {
            return ready;
        }

        public String message() {
            return message;
        }
    }

    private final CommandRunner runner;
    private final List<String> adbCandidates;

    public DesktopUsbReverseSetup() {
        this(new ProcessCommandRunner(), System.getenv(), System.getProperty("os.name", ""));
    }

    public DesktopUsbReverseSetup(CommandRunner runner, Map<String, String> env, String osName) {
        this.runner = runner;
        this.adbCandidates = adbCandidates(env, osName);
    }

    public Result prepare(int port) {
        if (port <= 0 || port > 65535) {
            return Result.notReady("USB reverse 失败: 监听端口无效");
        }

        List<String> missingAdbErrors = new ArrayList<>();
        for (String adb : adbCandidates) {
            try {
                return prepareWithAdb(adb, port);
            } catch (IOException e) {
                missingAdbErrors.add(adb + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.notReady("USB reverse 已中断");
            }
        }

        String detail = missingAdbErrors.isEmpty() ? "" : ": " + summarize(String.join(" / ", missingAdbErrors));
        return Result.notReady("USB reverse 未配置: 找不到 adb" + detail);
    }

    public static List<String> adbCandidates(Map<String, String> env, String osName) {
        Set<String> candidates = new LinkedHashSet<>();
        String executableName = isWindows(osName) ? "adb.exe" : "adb";
        addIfPresent(candidates, env.get("ADB"));
        addSdkCandidate(candidates, env.get("ANDROID_HOME"), executableName);
        addSdkCandidate(candidates, env.get("ANDROID_SDK_ROOT"), executableName);
        candidates.add("adb");
        return new ArrayList<>(candidates);
    }

    private Result prepareWithAdb(String adb, int port) throws IOException, InterruptedException {
        CommandResult devices = runner.run(command(adb, "devices"));
        if (devices.exitCode() != 0) {
            return Result.notReady("USB reverse 检查失败: " + summarize(devices.output()));
        }
        if (!hasAuthorizedDevice(devices.output())) {
            return Result.notReady("未检测到已授权 USB 设备，请确认手机已连接并允许 USB 调试");
        }

        CommandResult reverseList = runner.run(command(adb, "reverse", "--list"));
        if (reverseList.exitCode() != 0) {
            return Result.notReady("USB reverse 查询失败: " + summarize(reverseList.output()));
        }
        if (hasReverse(reverseList.output(), port)) {
            return Result.ready("USB reverse 已存在，等待手机连接");
        }

        runner.run(command(adb, "reverse", "--remove", "tcp:" + port));
        CommandResult create = runner.run(command(adb, "reverse", "tcp:" + port, "tcp:" + port));
        if (create.exitCode() == 0) {
            return Result.ready("USB reverse 已就绪，等待手机连接");
        }
        return Result.notReady("USB reverse 创建失败: " + summarize(create.output()));
    }

    private static List<String> command(String adb, String... args) {
        List<String> command = new ArrayList<>();
        command.add(adb);
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    private static boolean hasAuthorizedDevice(String output) {
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("List of devices")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 2 && "device".equals(parts[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReverse(String output, int port) {
        String token = "tcp:" + port + " tcp:" + port;
        return output.contains(token);
    }

    private static void addSdkCandidate(Set<String> candidates, String sdkRoot, String executableName) {
        if (sdkRoot == null || sdkRoot.trim().isEmpty()) {
            return;
        }
        candidates.add(trimTrailingSlash(sdkRoot.trim()) + "/platform-tools/" + executableName);
    }

    private static void addIfPresent(Set<String> candidates, String value) {
        if (value != null && !value.trim().isEmpty()) {
            candidates.add(value.trim());
        }
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/") || value.endsWith("\\")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.US).contains("win");
    }

    private static String summarize(String output) {
        String normalized = output == null ? "" : output.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.isEmpty()) {
            return "无详细输出";
        }
        if (normalized.length() > 120) {
            return normalized.substring(0, 117) + "...";
        }
        return normalized;
    }

    private static final class ProcessCommandRunner implements CommandRunner {
        @Override
        public CommandResult run(List<String> command) throws IOException, InterruptedException {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(124, "command timed out");
            }
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                output = builder.toString();
            }
            return new CommandResult(process.exitValue(), output);
        }
    }
}
