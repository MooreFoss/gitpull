# GitPull

一个用于 Spigot/Paper 服务端的插件，用来从 Git 仓库执行拉取操作。

支持功能：

- `/pull` 拉取全部已配置仓库
- `/pull <name>` 拉取指定仓库
- `/pull reload` 重载配置文件
- 支持多个仓库分别拉取到不同目录
- 支持 OP 与控制台执行
- 支持 Tab 补全仓库名和 `reload`

## 安装

1. 将插件 jar 放入服务端的 `plugins` 目录。
2. 启动一次服务器，生成默认配置文件。
3. 编辑 `plugins/gitpull/config.yml`。
4. 执行 `/pull reload` 重载配置，或直接重启服务器。

## 命令

### `/pull`

拉取配置文件中的全部仓库。

### `/pull <name>`

拉取指定名字的仓库。

### `/pull reload`

重载插件配置文件。

## 权限

- `gitpull.pull`：允许执行 `/pull` 和 `/pull <name>`
- `gitpull.reload`：允许执行 `/pull reload`

默认这两个权限都是 `op`。

## 配置文件

配置文件路径：

```text
plugins/gitpull/config.yml
```

示例配置：

```yml
git-command: git
clone-if-missing: true
timeout-seconds: 300

repositories:
  autoop:
    repository: "https://github.com/example/repo.git"
    directory: "world/datapacks/repo"
    branch: "master"

  devpack:
    repository: "https://github.com/example/repo.git"
    directory: "world/datapacks/dev"
    branch: "dev"
```

### 全局配置项

#### `git-command`

要调用的 Git 命令，默认是：

```yml
git-command: git
```

如果服务端环境变量中找不到 `git`，可以写绝对路径，例如：

```yml
git-command: "/bin/git"
```

#### `clone-if-missing`

当目标目录不是 Git 仓库时，是否自动执行 `git clone`。

```yml
clone-if-missing: true
```

#### `timeout-seconds`

单次仓库操作的超时时间，单位为秒。

```yml
timeout-seconds: 300
```

### 仓库配置项

每个仓库都写在 `repositories` 下，并使用唯一名字区分。

例如：

```yml
repositories:
  example:
    repository: "https://github.com/example/repo.git"
    directory: "world/datapacks/repo"
    branch: "master"
```

#### `repository`

远程仓库地址。

支持 HTTPS：

```yml
repository: "https://github.com/username/repo.git"
```

也支持 SSH：

```yml
repository: "git@github.com:username/repo.git"
```

#### `directory`

本地拉取目录。

- 绝对路径：直接使用该路径
- 相对路径：相对于服务端根目录解析

例如：

```yml
directory: "world/datapacks/AutoOP"
```

这会拉取到服务端根目录下的 `world/datapacks/AutoOP`。

#### `branch`

要拉取或克隆的分支，可为空。

```yml
branch: "master"
```

如果留空，则使用仓库默认分支：

```yml
branch: ""
```

## 使用示例

### 拉取全部仓库

```text
/pull
```

### 拉取单个仓库

```text
/pull autoop
```

### 重载配置

```text
/pull reload
```

## 私密仓库

本插件调用的是服务端系统中的 `git`，所以私密仓库认证依赖服务器本机的 Git 配置。

推荐方式：

### 1. SSH 密钥

将仓库地址写成：

```yml
repository: "git@github.com:username/private-repo.git"
```

然后在服务端机器上配置 SSH 密钥，并把公钥加到 GitHub 账号或仓库的 Deploy Key 中。

### 2. Git 凭据管理器

如果使用 HTTPS，可以先在服务端机器上手动执行一次 Git 登录，让系统记住凭据，之后插件即可复用。

不推荐把 token 直接明文写进配置文件：

```yml
repository: "https://username:yourtoken@github.com/username/repo.git"
```


## 注意事项

- 仓库名字不能使用 `reload`
- `/pull` 执行全部仓库时，会按配置顺序依次执行
- 某个仓库失败时，不会影响后续仓库继续执行
- 如果已有拉取任务正在运行，新的 `/pull` 请求会被拒绝
- 如果目标目录已存在且不是 Git 仓库，并且目录非空，插件不会强行覆盖该目录

## 日志说明

- 成功时会在聊天栏或控制台看到成功提示
- 详细的 `git stdout` 和 `git stderr` 会输出到服务端日志
- 批量拉取结束后会显示成功数和失败数
