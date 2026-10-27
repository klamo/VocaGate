package com.vocagate.desktop.mediapipe;

import com.vocagate.core.GateConfig;
import com.vocagate.core.GateState;
import com.vocagate.core.GateStateMachine;
import com.vocagate.core.update.AlgorithmProfile;
import com.vocagate.desktop.vision.DesktopRecognitionResult;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MediaPipeFaceLandmarkerFrameAnalyzer implements AutoCloseable {
    private static final int SIDECAR_FRAME_MAX_WIDTH = 256;
    private static final String SIDECAR_SCRIPT_NAME = "vocagate_mediapipe_sidecar.py";
    private static final String MODEL_FILE_NAME = "face_landmarker.task";
    private static final GateConfig DEFAULT_DESKTOP_MEDIAPIPE_CONFIG = new GateConfig(
            0.18f,
            0.08f,
            3,
            4,
            250L,
            300L,
            150L
    );

    private final List<String> command;
    private GateConfig gateConfig;
    private GateStateMachine gateStateMachine;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private volatile String lastErrorLine = "";
    private boolean sidecarReady = false;

    public MediaPipeFaceLandmarkerFrameAnalyzer(List<String> command) {
        this(command, DEFAULT_DESKTOP_MEDIAPIPE_CONFIG);
    }

    public MediaPipeFaceLandmarkerFrameAnalyzer(List<String> command, GateConfig gateConfig) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("sidecar command is required");
        }
        this.command = new ArrayList<>(command);
        this.gateConfig = gateConfig == null ? DEFAULT_DESKTOP_MEDIAPIPE_CONFIG : gateConfig;
        this.gateStateMachine = new GateStateMachine(this.gateConfig);
    }

    public static Optional<MediaPipeFaceLandmarkerFrameAnalyzer> createDefault() {
        return createDefault(null);
    }

    public static Optional<MediaPipeFaceLandmarkerFrameAnalyzer> createDefault(AlgorithmProfile profile) {
        DesktopSidecarLaunchSpec launchSpec = DesktopSidecarLaunchSpec.resolve(
                appHome().orElse(null),
                System.getenv(),
                Paths.get("").toAbsolutePath(),
                System.getProperty("os.name", "")
        );
        if (!launchSpec.isAvailable()) {
            return Optional.empty();
        }
        return Optional.of(new MediaPipeFaceLandmarkerFrameAnalyzer(
                launchSpec.command(),
                gateConfigForProfile(profile)
        ));
    }

    public static GateConfig gateConfigForProfile(AlgorithmProfile profile) {
        return profile == null ? DEFAULT_DESKTOP_MEDIAPIPE_CONFIG : profile.toGateConfig();
    }

    public synchronized DesktopRecognitionResult analyze(BufferedImage image, long nowMs) {
        if (image == null || image.getWidth() < 16 || image.getHeight() < 16) {
            return DesktopRecognitionResult.noFrame();
        }

        try {
            ensureStarted();
            writer.write(MediaPipeSidecarProtocol.frameRequestLine(image, nowMs, SIDECAR_FRAME_MAX_WIDTH));
            writer.newLine();
            writer.flush();
            MediaPipeSidecarFrameResult sidecarResult = readRecognitionResult();
            return toRecognitionResult(sidecarResult, nowMs);
        } catch (IOException e) {
            close();
            throw new IllegalStateException("MediaPipe sidecar failed: " + errorSummary(e), e);
        }
    }

    public synchronized void reset() {
        gateStateMachine = new GateStateMachine(gateConfig);
    }

    public synchronized void applyAlgorithmProfile(AlgorithmProfile profile) {
        gateConfig = gateConfigForProfile(profile);
        reset();
    }

    public synchronized void warmUp() {
        try {
            ensureStarted();
            waitUntilReady();
            reset();
        } catch (IOException e) {
            close();
            throw new IllegalStateException("MediaPipe sidecar warm-up failed: " + errorSummary(e), e);
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly(writer);
        closeQuietly(reader);
        writer = null;
        reader = null;
        sidecarReady = false;
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    private void ensureStarted() throws IOException {
        if (process != null && process.isAlive()) {
            return;
        }
        close();
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> environment = builder.environment();
        environment.put("PYTHONUNBUFFERED", "1");
        environment.putAll(sidecarEnvironmentFor(environment, defaultCacheRoot()));
        process = builder.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        startErrorReader(process);
    }

    static Map<String, String> sidecarEnvironmentFor(Map<String, String> source, Path cacheRoot) {
        Map<String, String> environment = new HashMap<>();
        if (source != null) {
            environment.putAll(source);
        }
        environment.putIfAbsent("MPLBACKEND", "Agg");
        if (cacheRoot == null) {
            return environment;
        }

        Path matplotlibConfig = cacheRoot.resolve("matplotlib");
        Path pythonCache = cacheRoot.resolve("pycache");
        if (createDirectoriesQuietly(matplotlibConfig)) {
            environment.putIfAbsent("MPLCONFIGDIR", matplotlibConfig.toString());
        }
        if (createDirectoriesQuietly(pythonCache)) {
            environment.putIfAbsent("PYTHONPYCACHEPREFIX", pythonCache.toString());
        }
        return environment;
    }

    static Path defaultCacheRootFor(Map<String, String> env, String osName, String userHome) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase();
        String home = userHome == null ? "" : userHome.trim();
        if (normalizedOs.contains("win")) {
            String localAppData = env == null ? "" : env.getOrDefault("LOCALAPPDATA", "").trim();
            if (!localAppData.isEmpty()) {
                return Paths.get(localAppData, "VocaGate", "mediapipe");
            }
        }
        if (normalizedOs.contains("mac") && !home.isEmpty()) {
            return Paths.get(home, "Library", "Caches", "VocaGate", "mediapipe");
        }
        String xdgCache = env == null ? "" : env.getOrDefault("XDG_CACHE_HOME", "").trim();
        if (!xdgCache.isEmpty()) {
            return Paths.get(xdgCache, "vocagate", "mediapipe");
        }
        if (!home.isEmpty()) {
            return Paths.get(home, ".cache", "vocagate", "mediapipe");
        }
        return Paths.get(".vocagate-cache", "mediapipe").toAbsolutePath().normalize();
    }

    private static String defaultPythonCommand() {
        Path workingDirectory = Paths.get("").toAbsolutePath();
        return defaultPythonCommandFor(
                System.getenv("VOCAGATE_PYTHON"),
                appHome().orElse(null),
                workingDirectory,
                System.getProperty("os.name", "")
        );
    }

    static String defaultPythonCommandFor(String configured, Path appHome, Path workingDirectory, String osName) {
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim();
        }
        Optional<Path> appVenvPython = findFirstRegularFile(appHome,
                ".venv-mediapipe/bin/python",
                ".venv-mediapipe/Scripts/python.exe"
        );
        if (appVenvPython.isPresent()) {
            return appVenvPython.get().toString();
        }
        Optional<Path> venvPython = findWorkspacePathFrom(workingDirectory,
                ".venv-mediapipe/bin/python",
                ".venv-mediapipe/Scripts/python.exe"
        );
        if (venvPython.isPresent()) {
            return venvPython.get().toString();
        }
        String normalizedOs = osName == null ? "" : osName.toLowerCase();
        return normalizedOs.contains("win") ? "python" : "python3";
    }

    private static Path defaultCacheRoot() {
        return defaultCacheRootFor(
                System.getenv(),
                System.getProperty("os.name", ""),
                System.getProperty("user.home", "")
        );
    }

    private static Optional<Path> findSidecarScript() {
        String configured = System.getenv("VOCAGATE_MEDIAPIPE_SIDECAR");
        if (configured != null && !configured.trim().isEmpty()) {
            Path path = Paths.get(configured.trim());
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        Optional<Path> workspacePath = findWorkspacePath(
                "desktop/src/main/python/" + SIDECAR_SCRIPT_NAME,
                "src/main/python/" + SIDECAR_SCRIPT_NAME
        );
        if (workspacePath.isPresent()) {
            return workspacePath;
        }
        return appHome().map(home -> home.resolve("sidecar").resolve(SIDECAR_SCRIPT_NAME))
                .filter(Files::isRegularFile);
    }

    private static Optional<Path> findModel() {
        String configured = System.getenv("VOCAGATE_MEDIAPIPE_MODEL");
        if (configured != null && !configured.trim().isEmpty()) {
            Path path = Paths.get(configured.trim());
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        Optional<Path> workspacePath = findWorkspacePath(
                "desktop/src/main/python/models/" + MODEL_FILE_NAME,
                "src/main/python/models/" + MODEL_FILE_NAME
        );
        if (workspacePath.isPresent()) {
            return workspacePath;
        }
        return appHome().map(home -> home.resolve("sidecar").resolve("models").resolve(MODEL_FILE_NAME))
                .filter(Files::isRegularFile);
    }

    private static Optional<Path> findWorkspacePath(String... relativePaths) {
        return findWorkspacePathFrom(Paths.get("").toAbsolutePath(), relativePaths);
    }

    private static Optional<Path> findWorkspacePathFrom(Path workingDirectory, String... relativePaths) {
        Path cwd = workingDirectory == null ? Paths.get("").toAbsolutePath() : workingDirectory.toAbsolutePath();
        for (String relativePath : relativePaths) {
            for (Path base : Arrays.asList(cwd, cwd.getParent())) {
                if (base == null) {
                    continue;
                }
                Path path = base.resolve(relativePath).normalize();
                if (Files.isRegularFile(path)) {
                    return Optional.of(path);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> findFirstRegularFile(Path base, String... relativePaths) {
        if (base == null) {
            return Optional.empty();
        }
        for (String relativePath : relativePaths) {
            Path path = base.resolve(relativePath).normalize();
            if (Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> appHome() {
        try {
            Path codeSource = Paths.get(MediaPipeFaceLandmarkerFrameAnalyzer.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
            Path libDir = Files.isRegularFile(codeSource) ? codeSource.getParent() : codeSource;
            if (libDir == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(libDir.getParent());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private MediaPipeSidecarFrameResult readRecognitionResult() throws IOException {
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("sidecar exited" + lastErrorSuffix());
            }
            if (isReadyLine(line)) {
                sidecarReady = true;
                continue;
            }
            Optional<MediaPipeSidecarFrameResult> result = MediaPipeSidecarProtocol.parseRecognitionLine(line);
            if (result.isPresent()) {
                return result.get();
            }
        }
    }

    private void waitUntilReady() throws IOException {
        while (!sidecarReady) {
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("sidecar exited" + lastErrorSuffix());
            }
            if (isReadyLine(line)) {
                sidecarReady = true;
                return;
            }
        }
    }

    private static boolean isReadyLine(String line) {
        return line != null
                && line.contains("\"type\"")
                && line.contains("ready");
    }

    private DesktopRecognitionResult toRecognitionResult(MediaPipeSidecarFrameResult sidecarResult, long nowMs) {
        GateState state = gateStateMachine.update(
                sidecarResult.score(),
                sidecarResult.trackingValid(),
                nowMs
        );
        boolean likelySpeaking = state == GateState.PRE_OPEN || gateStateMachine.isGateOpen();
        return new DesktopRecognitionResult(
                sidecarResult.score(),
                sidecarResult.motionScore(),
                sidecarResult.fps(),
                likelySpeaking,
                state,
                sidecarResult.trackingValid(),
                sidecarResult.mouthRegion(),
                sidecarResult.mouthRegion().label(),
                sidecarResult.statusLabel()
        );
    }

    private void startErrorReader(Process startedProcess) {
        Thread thread = new Thread(() -> {
            try (BufferedReader errors = new BufferedReader(new InputStreamReader(
                    startedProcess.getErrorStream(),
                    StandardCharsets.UTF_8
            ))) {
                String line;
                while ((line = errors.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        lastErrorLine = line.trim();
                    }
                }
            } catch (IOException ignored) {
            }
        }, "vocagate-mediapipe-sidecar-stderr");
        thread.setDaemon(true);
        thread.start();
    }

    private String errorSummary(IOException e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        return message + lastErrorSuffix();
    }

    private String lastErrorSuffix() {
        return lastErrorLine == null || lastErrorLine.isEmpty() ? "" : " (" + lastErrorLine + ")";
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static boolean createDirectoriesQuietly(Path path) {
        try {
            Files.createDirectories(path);
            return Files.isDirectory(path);
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
