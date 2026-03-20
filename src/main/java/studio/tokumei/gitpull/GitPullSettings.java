package studio.tokumei.gitpull;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GitPullSettings(
        String gitCommand,
        boolean cloneIfMissing,
        long timeoutSeconds,
        List<RepositoryConfig> repositories,
        Map<String, RepositoryConfig> repositoriesByName
) {
    private static final String RELOAD_SUBCOMMAND = "reload";

    public static GitPullSettings fromConfig(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        String gitCommand = config.getString("git-command", "git").trim();
        boolean cloneIfMissing = config.getBoolean("clone-if-missing", true);
        long timeoutSeconds = Math.max(1L, config.getLong("timeout-seconds", 300L));

        List<RepositoryConfig> repositories = new ArrayList<>();
        Map<String, RepositoryConfig> repositoriesByName = new LinkedHashMap<>();
        ConfigurationSection repositoriesSection = config.getConfigurationSection("repositories");
        if (repositoriesSection != null) {
            for (String name : repositoriesSection.getKeys(false)) {
                ConfigurationSection repositorySection = repositoriesSection.getConfigurationSection(name);
                if (repositorySection == null) {
                    continue;
                }

                RepositoryConfig repository = new RepositoryConfig(
                        name,
                        repositorySection.getString("repository", "").trim(),
                        repositorySection.getString("directory", "").trim(),
                        repositorySection.getString("branch", "").trim()
                );
                repositories.add(repository);
                repositoriesByName.put(name, repository);
            }
        }

        return new GitPullSettings(
                gitCommand,
                cloneIfMissing,
                timeoutSeconds,
                List.copyOf(repositories),
                Map.copyOf(repositoriesByName)
        );
    }

    public boolean hasRepositories() {
        return !repositories.isEmpty();
    }

    public RepositoryConfig findRepository(String name) {
        return repositoriesByName.get(name);
    }

    public List<RepositoryConfig> validRepositories() {
        return repositories.stream()
                .filter(RepositoryConfig::hasRequiredValues)
                .filter(repository -> !isReservedName(repository.name()))
                .toList();
    }

    public boolean isReservedName(String name) {
        return RELOAD_SUBCOMMAND.equalsIgnoreCase(name);
    }

    public record RepositoryConfig(
            String name,
            String repository,
            String directory,
            String branch
    ) {
        public boolean hasRequiredValues() {
            return !repository.isBlank() && !directory.isBlank();
        }

        public boolean hasBranch() {
            return !branch.isBlank();
        }

        public Path resolveDirectory(JavaPlugin plugin) {
            Path configuredPath = Path.of(directory);
            if (configuredPath.isAbsolute()) {
                return configuredPath.normalize().toAbsolutePath();
            }

            return plugin.getServer().getWorldContainer().toPath().resolve(configuredPath).normalize().toAbsolutePath();
        }
    }
}
