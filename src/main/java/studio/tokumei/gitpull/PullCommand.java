package studio.tokumei.gitpull;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PullCommand implements CommandExecutor, TabCompleter {
    private final Gitpull plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PullCommand(Gitpull plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return handleSubCommand(sender, args);
        }

        if (args.length > 1) {
            sender.sendMessage("Usage: /pull, /pull <name>, or /pull reload");
            return true;
        }

        if (!hasPermission(sender, "gitpull.pull")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        GitPullSettings settings = plugin.loadSettings();
        if (args.length == 0) {
            return startAllRepositories(sender, settings);
        }

        return startSingleRepository(sender, settings, args[0]);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        if (hasPermission(sender, "gitpull.reload")) {
            completions.add("reload");
        }

        if (hasPermission(sender, "gitpull.pull")) {
            GitPullSettings settings = plugin.loadSettings();
            for (GitPullSettings.RepositoryConfig repository : settings.validRepositories()) {
                completions.add(repository.name());
            }
        }

        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], completions, matches);
        Collections.sort(matches);
        return matches;
    }

    private boolean handleSubCommand(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("reload")) {
            if (!hasPermission(sender, "gitpull.reload")) {
                sender.sendMessage("You do not have permission to reload GitPull.");
                return true;
            }

            plugin.reloadPluginConfig();
            sender.sendMessage("GitPull configuration reloaded.");
            return true;
        }

        if (!hasPermission(sender, "gitpull.pull")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        GitPullSettings settings = plugin.loadSettings();
        return startSingleRepository(sender, settings, args[0]);
    }

    private boolean startAllRepositories(CommandSender sender, GitPullSettings settings) {
        List<GitPullSettings.RepositoryConfig> repositories = settings.validRepositories();
        if (repositories.isEmpty()) {
            sender.sendMessage("GitPull is not configured correctly. Please add valid entries under repositories in config.yml.");
            return true;
        }

        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("A git pull task is already running. Please wait for it to finish.");
            return true;
        }

        sender.sendMessage("GitPull started for " + repositories.size() + " repositories. Check the server log for detailed output.");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> executeAll(sender, settings, repositories));
        return true;
    }

    private boolean startSingleRepository(CommandSender sender, GitPullSettings settings, String repositoryName) {
        GitPullSettings.RepositoryConfig repository = settings.findRepository(repositoryName);
        if (repository == null || settings.isReservedName(repositoryName)) {
            sender.sendMessage("Repository '" + repositoryName + "' was not found in config.yml.");
            return true;
        }

        if (!repository.hasRequiredValues()) {
            sender.sendMessage("Repository '" + repositoryName + "' is missing repository or directory in config.yml.");
            return true;
        }

        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("A git pull task is already running. Please wait for it to finish.");
            return true;
        }

        sender.sendMessage("GitPull task started for repository '" + repository.name() + "'. Check the server log for detailed output.");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> executeSingle(sender, settings, repository));
        return true;
    }

    private void executeAll(
            CommandSender sender,
            GitPullSettings settings,
            List<GitPullSettings.RepositoryConfig> repositories
    ) {
        int successCount = 0;
        List<String> failedRepositories = new ArrayList<>();

        try {
            for (GitPullSettings.RepositoryConfig repository : repositories) {
                GitService.GitResult result = plugin.getGitService().synchronize(settings, repository);
                logOutput(repository.name(), result);
                if (result.success()) {
                    successCount++;
                } else {
                    failedRepositories.add(repository.name());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error while running git commands: " + e.getMessage());
            failedRepositories.add("unexpected-error");
        } finally {
            running.set(false);
        }

        int finalSuccessCount = successCount;
        int failureCount = failedRepositories.size();
        String summary = "GitPull finished: " + finalSuccessCount + " succeeded, " + failureCount + " failed.";
        if (!failedRepositories.isEmpty()) {
            summary += " Failed repositories: " + String.join(", ", failedRepositories);
        }

        String finalSummary = summary;
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(finalSummary));
    }

    private void executeSingle(
            CommandSender sender,
            GitPullSettings settings,
            GitPullSettings.RepositoryConfig repository
    ) {
        GitService.GitResult result;

        try {
            result = plugin.getGitService().synchronize(settings, repository);
            logOutput(repository.name(), result);
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error while running git command for '" + repository.name() + "': " + e.getMessage());
            result = GitService.GitResult.failure("Unexpected error while running git command.", "", e.getMessage());
        } finally {
            running.set(false);
        }

        GitService.GitResult finalResult = result;
        String message = "Repository '" + repository.name() + "': " + finalResult.message();
        plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(message));
    }

    private void logOutput(String repositoryName, GitService.GitResult result) {
        if (!result.stdout().isBlank()) {
            plugin.getLogger().info("git stdout [" + repositoryName + "]:\n" + result.stdout());
        }

        if (!result.stderr().isBlank()) {
            plugin.getLogger().warning("git stderr [" + repositoryName + "]:\n" + result.stderr());
        }

        if (!result.success()) {
            plugin.getLogger().warning("Repository '" + repositoryName + "': " + result.message());
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender instanceof ConsoleCommandSender || sender.hasPermission(permission);
    }
}
