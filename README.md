# XpcMCLogin - Minecraft 服务器登录插件

![许可证](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-GPLv3-blue)
![版本](https://img.shields.io/badge/%E7%89%88%E6%9C%AC-1.0.0-green)
![Spigot及其分支](https://img.shields.io/badge/Spigot%E5%8F%8A%E5%85%B6%E5%88%86%E6%94%AF-1.20%2B-orange)

## 📖 简介

XpcMCLogin 是一个轻量级、安全可靠的 Minecraft 服务器登录插件，专为 Spigot 1.20+ 服务器设计。它提供了完整的账号注册、登录系统，并包含多项安全保护措施。

## 📚 版本规划
- **Alpha版（内测版）**：编译成功、放入服务器，启动服务器时无报错的版本
- **Beat版（公测版）**：编译成功、放入服务器，启动服务器时无报错、基本功能正常的版本
- **RC版（准稳定版）**：编译成功、放入服务器，启动服务器时无报错、基本功能正常、细节部分正常的版本
- **Release版（稳定版）**：不会出现bug、全部功能正常的版本

## ✨ 功能特性

### 🔒 账号系统
- **安全密码存储**：使用 SHA-512 加盐哈希算法存储密码
- **IP限制**：防止同一IP注册过多账号
- **密码重置**：已注册玩家再次注册可重置密码

### 🛡️ 安全保护
- **移动限制**：未登录玩家无法移动
- **操作限制**：未登录玩家无法使用物品/打开背包
- **伤害免疫**：未登录玩家不会受到伤害
- **指令限制**：未登录玩家只能使用登录相关指令

### ⚙️ 管理功能
- **位置管理**：可设置初始加入位置和重生点
- **强制登录**：管理员可强制登录其他玩家
- **超时踢出**：自动踢出长时间未登录的玩家

## 📥 安装指南

1. 在[Releases](https://github.com/wbq-8/XpcMCLogin/releases)下载最新版本的插件 JAR 文件
2. 将文件放入服务器的 `plugins/` 目录
3. 重启服务器
4. 插件会自动生成配置文件和数据文件

## 🎮 命令使用

### 玩家命令
| 命令 | 参数 | 描述 | 示例 |
|------|------|------|------|
| `/l` 或 `/login` | `<密码>` | 登录账号 | `/l mypassword123` |
| `/reg` 或 `/register` | `<密码>` | 注册账号 | `/reg mypassword123` |
| `/xpcloginother` | `<玩家名>` | 强制登录其他玩家 | `/xpcloginother Steve` |

### 管理员命令
| 命令 | 参数 | 描述 | 权限节点 |
|------|------|------|------|
| `/xpcsetspawn` | 无 | 设置默认重生点 | `xpclogin.admin` |
| `/xpcsetjoin` | 无 | 设置初始加入位置 | `xpclogin.admin` |

## ⚙️ 配置选项

配置文件位于 `plugins/XpcMCLogin/config.yml`:

```
# 登录超时时间(秒)
loginTimeout: 120

# 每个IP允许的最大账号数
maxAccountsPerIP: 2

# 初始加入位置
initialJoinLocation:
  world: world
  x: 0.0
  y: 64.0
  z: 0.0
  yaw: 0.0
  pitch: 0.0

# 默认重生点
defaultSpawnLocation:
  world: world
  x: 0.0
  y: 64.0
  z: 0.0
  yaw: 0.0
  pitch: 0.0
```

## 📂 数据存储

插件数据存储在以下位置：
- 玩家数据：`plugins/XpcMCLogin/players.yml`
- IP限制数据：`plugins/XpcMCLogin/ipdata.yml`

## ❓ 常见问题

**Q: 如何修改IP限制数量？**  
A: 编辑 config.yml 中的 `maxAccountsPerIP` 值

**Q: 玩家改名后数据会丢失吗？**  
A: 会，插件使用玩家名而非UUID存储数据，改名后需要手动更新数据文件

**Q: 如何备份插件数据？**  
A: 备份整个 XpcMCLogin 文件夹即可

## 📜 许可证

本项目采用 GNU General Public License v3.0 开源许可证  
许可证内容可以在[LICENSE](https://github.com/wbq-8/XpcMCLogin/blob/master/LICENSE)文件中找到