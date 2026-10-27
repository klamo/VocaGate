package com.vocagate.desktop.mediapipe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resolves the packaged recognition engine before the source-tree development path. */
public final class DesktopSidecarLaunchSpec {
    private static final String SIDECAR_EXECUTABLE = "vocagate-mediapipe";
    private static final String SIDECAR_SCRIPT = "vocagate_mediapipe_sidecar.py";
    private static final String MODEL_FILE = "face_landmarker.task";

    private final List<String> command;
    private final boolean packaged;
    private final String description;

    private DesktopSidecarLaunchSpec(List<String> command, boolean packaged, String description) {
        this.command = List.copyOf(command);
        this.packaged = packaged;
        this.description = description;
    }

    public static DesktopSidecarLaunchSpec resolve(
            Path appHome,
            Map<String, String> environment,
            Path workingDirectory,
            String osName
    ) {
        Map<String, String> env = environment == null ? Map.of() : environment;
        Optional<Path> configuredSidecar = configuredFile(env, "VOCAGATE_MEDIAPIPE_SIDECAR");
        Optional<Path> configuredModel = configuredFile(env, "VOCAGATE_MEDIAPIPE_MODEL");
        if (configuredSidecar.isPresent()) {
            return developmentSpec(
                    configuredSidecar.get(),
                    configuredModel,
                    configuredValue(env, "VOCAGATE_PYTHON"),
                    false,
                    "开发覆盖"
            );
        }

        Optional<Path> packagedExecutable = packagedExecutable(appHome, osName);
        Optional<Path> packagedModel = packagedModel(appHome);
        if (packagedExecutable.isPresent() && packagedModel.isPresent()) {
            return new DesktopSidecarLaunchSpec(
                    List.of(packagedExecutable.get().toString(), "--model", packagedModel.get().toString()),
                    true,
                    "内置 MediaPipe 引擎"
            );
        }

        Optional<Path> workspaceScript = workspaceFile(
                workingDirectory,
                "desktop/src/main/python/" + SIDECAR_SCRIPT,
                "src/main/python/" + SIDECAR_SCRIPT
        );
        if (workspaceScript.isPresent()) {
            Optional<Path> workspaceModel = configuredModel.isPresent()
                    ? configuredModel
                    : workspaceFile(
                            workingDirectory,
                            "desktop/src/main/python/models/" + MODEL_FILE,
                            "src/main/python/models/" + MODEL_FILE
                    );
            return developmentSpec(
                    workspaceScript.get(),
                    workspaceModel,
                    configuredValue(env, "VOCAGATE_PYTHON"),
                    false,
                    "源码目录 MediaPipe 引擎"
            );
        }

        return new DesktopSidecarLaunchSpec(List.of(), false, "未找到 MediaPipe 识别引擎");
    }

    public List<String> command() {
        return command;
    }

    public boolean isPackaged() {
        return packaged;
    }

    public String description() {
        return description;
    }

    public boolean isAvailable() {
        return !command.isEmpty();
    }

    private static DesktopSidecarLaunchSpec developmentSpec(
            Path sidecar,
            Optional<Path> model,
            String configuredPython,
            boolean packaged,
            String description
    ) {
        List<String> command = new ArrayList<>();
        if (sidecar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".py")) {
            command.add(configuredPython == null || configuredPython.isBlank() ? "python3" : configuredPython);
        }
        command.add(sidecar.toString());
        model.ifPresent(value -> {
            command.add("--model");
            command.add(value.toString());
        });
        return new DesktopSidecarLaunchSpec(command, packaged, description);
    }

    private static Optional<Path> packagedExecutable(Path appHome, String osName) {
        if (appHome == null) {
            return Optional.empty();
        }
        String executableName = isWindows(osName) ? SIDECAR_EXECUTABLE + ".exe" : SIDECAR_EXECUTABLE;
        return regularFile(appHome.resolve("sidecar").resolve(executableName));
    }

    private static Optional<Path> packagedModel(Path appHome) {
        return appHome == null
                ? Optional.empty()
                : regularFile(appHome.resolve("sidecar/models").resolve(MODEL_FILE));
    }

    private static Optional<Path> configuredFile(Map<String, String> environment, String key) {
        String value = configuredValue(environment, key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return regularFile(Path.of(value.trim()));
    }

    private static String configuredValue(Map<String, String> environment, String key) {
        return environment.getOrDefault(Objects.requireNonNull(key), "").trim();
    }

    private static Optional<Path> workspaceFile(Path workingDirectory, String... relativePaths) {
        Path cwd = workingDirectory == null ? Path.of("").toAbsolutePath() : workingDirectory.toAbsolutePath();
        for (String relativePath : relativePaths) {
            for (Path base : List.of(cwd, cwd.getParent())) {
                if (base == null) {
                    continue;
                }
                Optional<Path> candidate = regularFile(base.resolve(relativePath).normalize());
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> regularFile(Path path) {
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }
}
