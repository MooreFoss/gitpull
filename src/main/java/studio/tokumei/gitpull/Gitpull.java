package studio.tokumei.gitpull;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Gitpull extends JavaPlugin {
    private GitService gitService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.gitService = new GitService(this);
        validateConfiguration();
        registerCommands();
    }

    @Override
    public void onDisable() {
    }

    public GitPullSettings loadSettings() {
        return GitPullSettings.fromConfig(this);
    }

    public GitService getGitService() {
        return gitService;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        validateConfiguration();
    }

    private void registerCommands() {
        PluginCommand pullCommand = getCommand("pull");
        if (pullCommand == null) {
            getLogger().severe("Command /pull is missing from plugin.yml.");
            return;
        }

        PullCommand pullCommandHandler = new PullCommand(this);
        pullCommand.setExecutor(pullCommandHandler);
        pullCommand.setTabCompleter(pullCommandHandler);
    }

    private void validateConfiguration() {
        GitPullSettings settings = loadSettings();

        if (settings.gitCommand().isBlank()) {
            getLogger().warning("Config value 'git-command' is empty. /pull will not run until it is configured.");
        }

        if (!settings.hasRepositories()) {
            getLogger().warning("Config section 'repositories' is empty. /pull will not run until at least one repository is configured.");
            return;
        }

        for (GitPullSettings.RepositoryConfig repository : settings.repositories()) {
            if (settings.isReservedName(repository.name())) {
                getLogger().warning("Repository name '" + repository.name() + "' is reserved and will be ignored.");
            }

            if (repository.repository().isBlank()) {
                getLogger().warning("Repository '" + repository.name() + "' is missing 'repository' and will be ignored.");
            }

            if (repository.directory().isBlank()) {
                getLogger().warning("Repository '" + repository.name() + "' is missing 'directory' and will be ignored.");
            }
        }
    }
}
