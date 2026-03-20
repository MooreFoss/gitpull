package studio.tokumei.gitpull;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GitService {
    private final JavaPlugin plugin;

    public GitService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public GitResult synchronize(GitPullSettings settings, GitPullSettings.RepositoryConfig repository) {
        try {
            Path directory = repository.resolveDirectory(plugin);
            if (Files.exists(directory) && !Files.isDirectory(directory)) {
                return GitResult.failure("Configured path is a file, not a directory.", "", "");
            }

            if (isGitRepository(directory)) {
                return pullRepository(settings, repository, directory);
            }

            if (!settings.cloneIfMissing()) {
                return GitResult.failure("Configured directory is not a git repository.", "", "");
            }

            if (Files.exists(directory) && directoryHasFiles(directory)) {
                return GitResult.failure("Configured directory exists and is not empty, so clone was skipped.", "", "");
            }

            return cloneRepository(settings, repository, directory);
        } catch (IOException e) {
            return GitResult.failure("Failed to access the configured directory: " + e.getMessage(), "", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GitResult.failure("Git operation was interrupted.", "", "");
        } catch (InvalidPathException e) {
            return GitResult.failure("Configured directory path is invalid: " + e.getInput(), "", "");
        }
    }

    private GitResult pullRepository(
            GitPullSettings settings,
            GitPullSettings.RepositoryConfig repository,
            Path directory
    ) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(settings.gitCommand());
        command.add("-C");
        command.add(directory.toString());
        command.add("pull");
        if (repository.hasBranch()) {
            command.add("origin");
            command.add(repository.branch());
        }

        return runCommand(command, directory.getParent(), settings.timeoutSeconds(), "pull");
    }

    private GitResult cloneRepository(
            GitPullSettings settings,
            GitPullSettings.RepositoryConfig repository,
            Path directory
    ) throws IOException, InterruptedException {
        Path targetDirectory = directory.toAbsolutePath().normalize();
        Path parent = directory.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> command = new ArrayList<>();
        command.add(settings.gitCommand());
        command.add("clone");
        if (repository.hasBranch()) {
            command.add("--branch");
            command.add(repository.branch());
        }
        command.add(repository.repository());
        command.add(targetDirectory.toString());

        Path workingDirectory = parent != null ? parent : plugin.getDataFolder().toPath();
        return runCommand(command, workingDirectory, settings.timeoutSeconds(), "clone");
    }

    private GitResult runCommand(List<String> command, Path workingDirectory, long timeoutSeconds, String operation)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            Files.createDirectories(workingDirectory);
            builder.directory(workingDirectory.toFile());
        }

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return GitResult.failure(
                    "Unable to start git. Check 'git-command' in config.yml and confirm git is installed: " + e.getMessage(),
                    "",
                    ""
            );
        }

        StreamOutput output = collectOutput(process, Duration.ofSeconds(timeoutSeconds));

        if (!output.finishedInTime()) {
            process.destroyForcibly();
            return GitResult.failure(
                    "Git " + operation + " timed out after " + timeoutSeconds + " seconds.",
                    output.stdout(),
                    output.stderr()
            );
        }

        if (output.exitCode() != 0) {
            return GitResult.failure(
                    "Git " + operation + " failed with exit code " + output.exitCode() + ".",
                    output.stdout(),
                    output.stderr()
            );
        }

        return GitResult.success(
                "Git " + operation + " completed successfully.",
                output.stdout(),
                output.stderr()
        );
    }

    private StreamOutput collectOutput(Process process, Duration timeout) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> stdoutFuture = executor.submit(new StreamReader(process.getInputStream()));
            Future<String> stderrFuture = executor.submit(new StreamReader(process.getErrorStream()));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                return new StreamOutput(-1, false, "", "");
            }

            return new StreamOutput(
                    process.exitValue(),
                    true,
                    getFutureOutput(stdoutFuture),
                    getFutureOutput(stderrFuture)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private String getFutureOutput(Future<String> future) throws InterruptedException {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            return cause == null ? e.getMessage() : cause.getMessage();
        } catch (TimeoutException e) {
            return "Timed out while reading process output.";
        }
    }

    private boolean isGitRepository(Path directory) {
        return Files.isDirectory(directory.resolve(".git"));
    }

    private boolean directoryHasFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.findAny().isPresent();
        }
    }

    private record StreamOutput(int exitCode, boolean finishedInTime, String stdout, String stderr) {
    }

    public record GitResult(boolean success, String message, String stdout, String stderr) {
        public static GitResult success(String message, String stdout, String stderr) {
            return new GitResult(true, message, stdout, stderr);
        }

        public static GitResult failure(String message, String stdout, String stderr) {
            return new GitResult(false, message, stdout, stderr);
        }
    }

    private static final class StreamReader implements Callable<String> {
        private final InputStream stream;

        private StreamReader(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public String call() throws IOException {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append(System.lineSeparator());
                    }
                    builder.append(line);
                }
            }
            return builder.toString();
        }
    }
}
