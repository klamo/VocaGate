import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

public final class ReleasePackagingSmokeTest {
    private static final Pattern SEMANTIC_VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{[^}]+}}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern EXACT_REQUIREMENT = Pattern.compile("[A-Za-z0-9_.-]+==[A-Za-z0-9_.!+-]+");

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        verifyReleaseProperties(root);
        verifyGitIgnore(root);
        verifyMediaPipeSidecarInputs(root);
        verifyReleaseNoteSources(root);
        verifyAndroidPackageScript(root);
        verifyAndroidReleaseSigningGuard(root);
        System.out.println("Release packaging smoke test passed.");
    }

    private static void verifyMediaPipeSidecarInputs(Path root) throws IOException {
        Path pythonDir = root.resolve("desktop/src/main/python");
        Path lockPath = pythonDir.resolve("models/face_landmarker.lock.json");
        require(Files.isRegularFile(lockPath), "Missing MediaPipe model lock: " + lockPath);
        Map<String, String> lock = parseFlatJsonObject(Files.readString(lockPath, StandardCharsets.UTF_8));
        for (String field : List.of("model_id", "version", "url", "sha256", "license_source")) {
            String value = lock.get(field);
            require(value != null && !value.isBlank(), "MediaPipe model lock field must be nonempty: " + field);
        }
        require(!lock.get("url").contains("/latest/"),
                "MediaPipe model lock URL must be immutable and must not contain /latest/");
        require(SHA256.matcher(lock.get("sha256")).matches(),
                "MediaPipe model lock sha256 must be 64 lower-case hexadecimal characters");

        Path requirementsPath = pythonDir.resolve("requirements-release.txt");
        require(Files.isRegularFile(requirementsPath), "Missing release sidecar requirements");
        List<String> requirements = Files.readAllLines(requirementsPath, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        for (String requirement : requirements) {
            require(EXACT_REQUIREMENT.matcher(requirement).matches(),
                    "Release sidecar requirement must use an exact pin: " + requirement);
        }
        for (String packageName : List.of("mediapipe", "numpy", "pyinstaller")) {
            require(requirements.stream().anyMatch(requirement -> requirement.startsWith(packageName + "==")),
                    "Release sidecar requirements must pin " + packageName);
        }

        String shell = Files.readString(root.resolve("scripts/prepare_mediapipe_sidecar.sh"), StandardCharsets.UTF_8);
        require(shell.contains("macos-arm64") && shell.contains("windows-x64")
                        && shell.contains("Unsupported target"),
                "Shell sidecar staging helper must restrict targets");
        require(shell.contains("sha256") && shell.contains("$MODEL_URL") && shell.contains("--onedir"),
                "Shell sidecar staging helper must verify the locked model and use PyInstaller onedir");

        String batch = Files.readString(root.resolve("scripts/prepare_mediapipe_sidecar.bat"), StandardCharsets.UTF_8);
        require(batch.contains("macos-arm64") && batch.contains("windows-x64")
                        && batch.contains("certutil") && batch.contains("--onedir"),
                "Windows sidecar staging helper must mirror target, hash, and PyInstaller behavior");
    }

    private static Map<String, String> parseFlatJsonObject(String json) {
        var result = new java.util.LinkedHashMap<String, String>();
        int index = skipWhitespace(json, 0);
        require(index < json.length() && json.charAt(index++) == '{', "Model lock must be a JSON object");
        index = skipWhitespace(json, index);
        while (index < json.length() && json.charAt(index) != '}') {
            ParsedJsonString key = parseJsonString(json, index);
            index = skipWhitespace(json, key.nextIndex());
            require(index < json.length() && json.charAt(index++) == ':', "Model lock JSON is missing a colon");
            index = skipWhitespace(json, index);
            ParsedJsonString value = parseJsonString(json, index);
            require(result.putIfAbsent(key.value(), value.value()) == null,
                    "Model lock JSON contains a duplicate key: " + key.value());
            index = skipWhitespace(json, value.nextIndex());
            require(index < json.length(), "Model lock JSON is missing a closing brace");
            if (json.charAt(index) == ',') {
                index = skipWhitespace(json, index + 1);
            } else {
                require(json.charAt(index) == '}', "Model lock JSON must contain only string fields");
            }
        }
        require(index < json.length() && json.charAt(index++) == '}', "Model lock JSON is missing a closing brace");
        require(skipWhitespace(json, index) == json.length(), "Model lock JSON has trailing content");
        return result;
    }

    private static ParsedJsonString parseJsonString(String json, int index) {
        require(index < json.length() && json.charAt(index) == '"', "Model lock JSON keys and values must be strings");
        StringBuilder value = new StringBuilder();
        for (int cursor = index + 1; cursor < json.length(); cursor++) {
            char character = json.charAt(cursor);
            require(character != '\\', "Model lock JSON must not use escaped strings");
            if (character == '"') {
                return new ParsedJsonString(value.toString(), cursor + 1);
            }
            require(character >= 0x20, "Model lock JSON strings must not contain control characters");
            value.append(character);
        }
        throw new IllegalStateException("Model lock JSON contains an unterminated string");
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private record ParsedJsonString(String value, int nextIndex) {
    }

    private static void verifyReleaseProperties(Path root) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(root.resolve("gradle.properties"))) {
            properties.load(input);
        }

        String version = properties.getProperty("vocagate.version");
        require(version != null && SEMANTIC_VERSION.matcher(version).matches(),
                "vocagate.version must use semantic x.y.z format");

        String versionCode = properties.getProperty("vocagate.versionCode");
        try {
            require(versionCode != null && Integer.parseInt(versionCode) > 0,
                    "vocagate.versionCode must be a positive integer");
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("vocagate.versionCode must be a positive integer", exception);
        }
    }

    private static void verifyGitIgnore(Path root) throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(".gitignore"), StandardCharsets.UTF_8);
        for (String requiredRule : List.of(
                "release/",
                "release-signing.properties",
                "*.jks",
                "*.keystore",
                "desktop/build/release-staging/",
                "desktop/build/sidecar/")) {
            require(lines.contains(requiredRule), "Missing .gitignore rule: " + requiredRule);
        }
    }

    private static void verifyReleaseNoteSources(Path root) throws Exception {
        Path templatePath = root.resolve("docs/release-notes-template.md");
        List<Path> helpers = List.of(
                root.resolve("scripts/prepare_release_notes.sh"),
                root.resolve("scripts/prepare_release_notes.bat"));

        require(Files.isRegularFile(templatePath), "Missing release note template");
        for (Path helper : helpers) {
            require(Files.isRegularFile(helper), "Missing release note helper: " + helper.getFileName());
            String helperText = Files.readString(helper, StandardCharsets.UTF_8);
            require(helperText.contains("{{version}}"),
                    "Release note helper does not materialize the version token: " + helper.getFileName());
        }

        String version = "0.2.0";
        String materialized = materializeReleaseNotes(Files.readString(templatePath, StandardCharsets.UTF_8), version);
        require(releaseNotesPath(root, version).equals(root.resolve("release/0.2.0/RELEASE_NOTES.md")),
                "Release notes must be written to release/<version>/RELEASE_NOTES.md");
        expectFailure(() -> materializeReleaseNotes("# {{unknown}}", "0.2.0"),
                "Release note materialization must reject unresolved placeholders");
        expectFailure(() -> materializeReleaseNotes("# VocaGate {{version}}", "not-a-version"),
                "Release note materialization must reject invalid versions");
        verifyShellReleaseNotesBehavior(root, templatePath, version, materialized);
        for (String requiredTopic : List.of(
                "Platform artifacts",
                "Debug-to-release Android migration",
                "Desktop pre-release signing",
                "Wi-Fi/USB limits",
                "Model/sidecar changes",
                "Telemetry")) {
            require(materialized.contains(requiredTopic), "Release note template is missing: " + requiredTopic);
        }

        String shellHelper = Files.readString(helpers.get(0), StandardCharsets.UTF_8);
        require(shellHelper.contains("[[ \"$VERSION\" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+$ ]]"),
                "Shell release note helper must validate its version argument");
        require(shellHelper.contains("OUTPUT_DIR=\"$REPO_ROOT/release/$VERSION\"")
                        && shellHelper.contains("OUTPUT=\"$OUTPUT_DIR/RELEASE_NOTES.md\""),
                "Shell release note helper must use a host-local release output path");
        require(shellHelper.contains("grep -Eq '\\{\\{[^}]+}}'"),
                "Shell release note helper must reject unresolved placeholders");

        String batchHelper = Files.readString(helpers.get(1), StandardCharsets.UTF_8);
        require(batchHelper.contains("findstr /r \"^[0-9][0-9]*\\.[0-9][0-9]*\\.[0-9][0-9]*$\""),
                "Windows release note helper must validate its version argument");
        require(batchHelper.contains("OUTPUT_DIR=%REPO_ROOT%\\release\\%VERSION%")
                        && batchHelper.contains("OUTPUT=%OUTPUT_DIR%\\RELEASE_NOTES.md"),
                "Windows release note helper must use a host-local release output path");
        require(batchHelper.contains("powershell -NoProfile")
                        && batchHelper.contains("[System.IO.File]::ReadAllText")
                        && batchHelper.contains("GetRandomFileName")
                        && batchHelper.contains("Move-Item -LiteralPath $temp -Destination $output -Force"),
                "Windows release note helper must preserve text through a PowerShell temp-file atomic publish");
        require(batchHelper.contains("-match '\\{\\{[^}]+\\}\\}'"),
                "Windows release note helper must reject unresolved placeholders");
    }

    private static void verifyShellReleaseNotesBehavior(
            Path root, Path templatePath, String version, String expectedNotes) throws Exception {
        Path fixture = Files.createTempDirectory("vocagate-release-notes-");
        Path fixtureScript = fixture.resolve("scripts/prepare_release_notes.sh");
        Path fixtureTemplate = fixture.resolve("docs/release-notes-template.md");
        Files.createDirectories(fixtureScript.getParent());
        Files.createDirectories(fixtureTemplate.getParent());
        Files.copy(root.resolve("scripts/prepare_release_notes.sh"), fixtureScript);
        Files.copy(templatePath, fixtureTemplate);

        int successExit = runShellHelper(fixtureScript, fixture, version);
        require(successExit == 0, "Shell release note helper must materialize a valid template");
        Path output = releaseNotesPath(fixture, version);
        require(Files.isRegularFile(output), "Shell release note helper must write release/<version>/RELEASE_NOTES.md");
        String actualNotes = Files.readString(output, StandardCharsets.UTF_8);
        require(actualNotes.equals(expectedNotes), "Shell release note helper must preserve template text and blank lines");
        require(!UNRESOLVED_PLACEHOLDER.matcher(actualNotes).find(),
                "Shell release note helper must not leave unresolved placeholders");

        Files.writeString(fixtureTemplate, "# {{unknown}}\n", StandardCharsets.UTF_8);
        Files.writeString(output, "existing notes\n", StandardCharsets.UTF_8);
        int failureExit = runShellHelper(fixtureScript, fixture, version);
        require(failureExit != 0, "Shell release note helper must fail on unresolved placeholders");
        require(Files.readString(output, StandardCharsets.UTF_8).equals("existing notes\n"),
                "Shell release note helper must preserve an existing final file on failed materialization");
    }

    private static int runShellHelper(Path script, Path fixture, String version) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", script.toString(), version)
                .directory(fixture.toFile())
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0 && !new String(output, StandardCharsets.UTF_8).contains("unresolved placeholders")) {
            throw new IllegalStateException("Shell release note helper failed unexpectedly: "
                    + new String(output, StandardCharsets.UTF_8));
        }
        return exitCode;
    }

    private static String materializeReleaseNotes(String template, String version) {
        require(SEMANTIC_VERSION.matcher(version).matches(), "Release note version must use semantic x.y.z format");
        String materialized = template.replace("{{version}}", version);
        require(!UNRESOLVED_PLACEHOLDER.matcher(materialized).find(),
                "Release note template contains unresolved placeholders after version materialization");
        return materialized;
    }

    private static Path releaseNotesPath(Path root, String version) {
        return root.resolve("release").resolve(version).resolve("RELEASE_NOTES.md");
    }

    private static void verifyAndroidPackageScript(Path root) throws IOException {
        String script = Files.readString(root.resolve("scripts/package_android_release.sh"), StandardCharsets.UTF_8);
        int finalArtifact = script.indexOf("FINAL_APK=\"$RELEASE_DIR/VocaGate-$VERSION-android.apk\"");
        int staleRemoval = script.indexOf("rm -f \"$FINAL_APK\"");
        int gradleInvocation = script.indexOf("gradle :app:assembleRelease");
        int certificateVerification = script.indexOf("$APKSIGNER verify --verbose --print-certs");
        int publishRename = script.indexOf("mv -f \"$TEMP_APK\" \"$FINAL_APK\"");
        require(finalArtifact >= 0 && staleRemoval > finalArtifact && staleRemoval < gradleInvocation,
                "Package script must remove a stale final APK before invoking Gradle");
        require(certificateVerification >= 0 && publishRename > certificateVerification,
                "Package script must publish the final APK only after certificate verification");
        require(script.contains("storeFile storePassword keyAlias keyPassword expectedCertificateSha256"),
                "Package script must require the expected release certificate SHA-256");
        require(!script.contains("if [ -n \"$EXPECTED_DIGEST\" ]"),
                "Package script must not treat expectedCertificateSha256 as optional");
        require(script.indexOf("Release certificate SHA-256 digest does not match expectedCertificateSha256")
                        < publishRename,
                "Package script must compare the expected certificate digest before publishing");
    }

    private static void verifyAndroidReleaseSigningGuard(Path root) throws IOException {
        String buildScript = Files.readString(root.resolve("app/build.gradle.kts"), StandardCharsets.UTF_8);
        Path guardScript = root.resolve("tools/test_release_signing_guard.sh");
        require(buildScript.contains("signingConfigs.create(\"release\")"),
                "Android release build type must always use a dedicated release signing config");
        require(buildScript.contains("project.gradle.taskGraph.addTaskExecutionGraphListener"),
                "Android release signing must be guarded from the resolved task graph");
        require(buildScript.contains("isReleaseArtifactTask"),
                "Android release signing guard must target artifact-producing release tasks");
        require(!buildScript.contains("task.name.contains(\"Release\", ignoreCase = true)"),
                "Android release signing guard must not block non-artifact release tasks");
        require(!buildScript.contains("gradle.startParameter.taskNames"),
                "Android release signing must not depend only on requested task names");
        require(Files.isRegularFile(guardScript), "Missing executable release signing guard test");
        String guardText = Files.readString(guardScript, StandardCharsets.UTF_8);
        require(guardText.contains("assert_credentialless_release_guard :app:assemble")
                        && guardText.contains("assert_credentialless_release_guard :app:build")
                        && guardText.contains("gradle \"$task\"")
                        && guardText.contains("gradle :app:assembleDebug")
                        && guardText.contains("app/build/outputs/apk/release/app-release.apk"),
                "Release signing guard test must verify aggregate failure, no release APK, and debug success");
    }

    private static void expectFailure(ThrowingRunnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        } catch (Exception exception) {
            throw new IllegalStateException(message, exception);
        }
        throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
