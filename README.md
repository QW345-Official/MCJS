# MCJS

Execute JavaScript code in Minecraft

## Introduction

This project has added the `/js` directive to execute JavaScript code. Example:

```
/js let a="Hello";runCommand("/say "+a)
runCommand("command") // execute command

// Syntax of version 1.0
registerCommand('hi', function(name) {
 return 'Hello, ' + name + '!'! ';
}); // Register directive

// Syntax of version 1.1
registerCommand('hi', OP等级, function(name) {
 return 'Hello, ' + name + '!'! ';
}); // Register directives

// Syntax of version 1.2 (with new parameter support)
registerCommand('hi', OP_Level, function(name, arg1, customname) {
 return 'Hello, ' + name + '! The input parameters are: ' + arg1 + customname;
}); // Register command, supports receiving command parameters

/js set <key> <value> // Set configuration option
/js reset// Reset all configurations to their default values
/js list// Display registered commands
```

## Permission Level Description

| Level | Applicable to |
|------|----------|
| 0 | All players |
| 1 | Basic administrator |
| 2 | Ordinary OP |
| 3 | Senior administrator |
| 4 | Server Owner |

## API List

```js
// File operations
saveFile(path, content) // Save the file
saveBinaryFile(path, content) // Save binary file
loadFile(path) // Load the file

// Configuration management
setOption(key, value) // Set configuration
getOption(key) // Get configuration
resetOptions() // Reset configuration

// Command registration
registerCommand(name, permissionlevel, fn)

// Command execution
runCommand(command)
```

Requires OP permission at level 2 (server administrator / local save for enabling commands / command block) to use.

The execution log will be saved in the `js_execution.log` file.

## Precautions

Scripts can execute any command, so please refrain from running scripts on the server indiscriminately.

## Configuration

Use `/js set config_item value` to make settings.

| Configuration Item | Default Value | Description |
|--------|--------|------|
| `infoDisplay` | true | Whether to display the execution success prompt |
| `errDisplay` | true | Whether to display error messages |
| `customCommandInfo` | true | Whether to display the execution result of the custom command |
| `codeLenLimit` | 10000 | Maximum length limit of script (characters) |
| `codeTimeLimit` | 5000 | Script execution timeout (milliseconds) |
| `currentCodeLimit` | 1 | Limit on the number of scripts executed simultaneously |
| `logExecution` | true | Whether to log execution details |
| `allowBinaryFiles` | true | Whether to allow binary file operations |
| `maxFileSize` | 10MB | Maximum file loading size |

## Experimental Features

`saveFile('filename', 'content')` saves a file

`loadFile('filename')` loads a file (updated in version 1.1: binary files return Base64)

`saveBinaryFile(path, content)` saves a binary file

## Update Log

### 1.0

* Initial version
* Add `/js <script>` to execute JavaScript
* Add `registerCommand(name, fn)` to register a command
* Add `runCommand(command)` to execute commands

### 1.1

* Added `/js set`
* Command execution permission setting (`registerCommand(name, level, fn)`)
* The issue of unknown crashes has been fixed
* Optimization of document management system
* Removed "Him"

### 1.2

* **Custom command supports receiving parameters**: The callback function signature in `registerCommand(name, level, fn)` is extended to `fn(name, arg1, customname)`
 * `name`: The name of the player who executed the command
 * `arg1`: The first argument of the command (an empty string if there is no argument)
 * `customname`: The remaining command parameters (with original spaces retained; empty string if no parameters)
* **Persistent dynamic commands**: All commands registered through `registerCommand` will be automatically saved to `config/mcjs/registercommands.js`
 * The server will automatically recover after rebooting, without the need to re-execute the script
 * The file is automatically maintained by the mod; please exercise caution when editing manually
* **New `/js list` enhancement**: Displays a list of registered commands, including those restored from persistence

#### 1.2 Command Examples

```
/js registerCommand('hi', 2, function(name, arg1, customname) {
 return 'Hello, ' + name + '! The input parameters are: ' + arg1 + customname;
});
```

| Input | Output |
|------|------|
| `/hi` | `Hello, Steve! The input parameters are: ` |
| `/hi test` | `Hello, Steve! The input parameter is: test` |
| `/hi test` | ditto (extra spaces are ignored as separators) |
| `/hi test test2` | `Hello, Steve! The input parameter is: testtest2` |

Example content of the persistent file `config/mcjs/registercommands.js`:

```js
// MCJS persistent command registration file. Automatically maintained by the mod, please exercise caution when editing manually.
registerCommand('hi', 2, function(name, arg1, customname) { return '你好，' + name + '！，输入的参数为：' + arg1 + customname;  });
```

#### 1.2 Precautions


* The function body cannot reference external closure variables, otherwise they will be lost after persistent restoration - `registercommands.js` only saves the function definition itself.
* **Do not repeatedly execute the same `registerCommand('same_name', ...)`.** Brigadier will throw an exception for duplicate literals; to overwrite, please restart the server or manually clean up.
* Chinese messy code issue: Before compiling via the Windows command line, you can first run `chcp 65001`, which will not affect the actual compilation results.
* The permission level is determined by the second parameter of `registerCommand`, and defaults to `2` if not specified.

#### 1.2 API Changes

```js
// 1.1 and earlier versions
registerCommand(name, permissionlevel, fn)
// fn parameter: function(name) { ... }

// Starting from 1.2
registerCommand(name, permissionlevel, fn)
// fn parameter: function(name, arg1, customname) { ... }
// Compatible with old function(name) { ... }
```



# MCJS

在 Minecraft 里执行 JavaScript 代码

## 简介

这个项目添加了 `/js` 指令来执行 JavaScript 代码。示例：

```
/js let a="Hello";runCommand("/say "+a)
runCommand("指令") // 执行指令

// 1.0 版本语法
registerCommand('hi', function(name) {
  return '你好，' + name + '！';
}); // 注册指令

// 1.1 版本语法
registerCommand('hi', OP等级, function(name) {
  return '你好，' + name + '！';
}); // 注册指令

// 1.2 版本语法（新增参数支持）
registerCommand('hi', OP等级, function(name, arg1, customname) {
  return '你好，' + name + '！，输入的参数为：' + arg1 + customname;
}); // 注册指令，支持接收命令参数

/js set <key> <value>// 设置配置选项
/js reset// 重置所有配置到默认值
/js list// 显示已注册命令
```

## 权限等级说明

| 等级 | 适用对象 |
|------|----------|
| 0 | 所有玩家 |
| 1 | 基础管理员 |
| 2 | 普通 OP |
| 3 | 高级管理员 |
| 4 | 服主 |

## API 列表

```js
// 文件操作
saveFile(path, content)        // 保存文件
saveBinaryFile(path, content)  // 保存二进制文件
loadFile(path)                 // 加载文件

// 配置管理
setOption(key, value)          // 设置配置
getOption(key)                 // 获取配置
resetOptions()                 // 重置配置

// 命令注册
registerCommand(name, permissionlevel, fn)

// 命令执行
runCommand(command)
```

使用需要 level 2 的 OP 权限（服务器管理员 / 开启命令的本地存档 / 命令方块）。

执行日志会保存在 `js_execution.log` 文件。

## 注意事项

脚本可以执行一切指令，不要在服务器上乱跑脚本。

## 配置

使用 `/js set 配置项 值` 来进行设置。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `infoDisplay` | true | 是否显示执行成功提示 |
| `errDisplay` | true | 是否显示错误信息 |
| `customCommandInfo` | true | 自定义命令是否显示执行结果 |
| `codeLenLimit` | 10000 | 脚本最大长度限制（字符） |
| `codeTimeLimit` | 5000 | 脚本执行超时时间（毫秒） |
| `currentCodeLimit` | 1 | 同时执行的脚本数量限制 |
| `logExecution` | true | 是否记录执行日志 |
| `allowBinaryFiles` | true | 是否允许二进制文件操作 |
| `maxFileSize` | 10MB | 最大文件加载大小 |

## 实验性功能

`saveFile('文件名', '内容')` 保存文件

`loadFile('文件名')` 加载文件（1.1 版本更新：二进制文件返回 Base64）

`saveBinaryFile(path, content)` 保存二进制文件

## 更新日志

### 1.0

* 初始版本
* 添加 `/js <脚本>` 执行 JavaScript
* 添加 `registerCommand(name, fn)` 注册指令
* 添加 `runCommand(command)` 执行指令

### 1.1

* 新增 `/js set`
* 命令执行权限设置（`registerCommand(name, level, fn)`）
* 不知名闪退已修复
* 文件管理系统优化
* 移除了 Him

### 1.2

* **自定义命令支持接收参数**：`registerCommand(name, level, fn)` 中的回调函数签名扩展为 `fn(name, arg1, customname)`
  * `name`：执行命令的玩家名
  * `arg1`：命令的第一个参数（无参数时为空字符串）
  * `customname`：命令剩余参数（保留原始空格，无参数时为空字符串）
* **动态命令持久化**：所有通过 `registerCommand` 注册的命令会自动保存到 `config/mcjs/registercommands.js`
  * 服务器重启后自动恢复，无需重新执行脚本
  * 文件由 mod 自动维护，手动编辑请谨慎
* **新增 `/js list` 增强**：显示已注册命令列表，含持久化恢复的命令

#### 1.2 命令示例

```
/js registerCommand('hi', 2, function(name, arg1, customname) {
  return '你好，' + name + '！，输入的参数为：' + arg1 + customname;
});
```

| 输入 | 输出 |
|------|------|
| `/hi` | `你好，Steve！，输入的参数为：` |
| `/hi test` | `你好，Steve！，输入的参数为：test` |
| `/hi  test` | 同上（多余空格被忽略作为分隔） |
| `/hi test test2` | `你好，Steve！，输入的参数为：testtest2` |

持久化文件 `config/mcjs/registercommands.js` 内容示例：

```js
// MCJS 持久化命令注册文件。由 mod 自动维护，手动编辑请谨慎。
registerCommand('hi', 2, function(name, arg1, customname) { return '你好，' + name + '！，输入的参数为：' + arg1 + customname; });
```

#### 1.2 注意事项


* 函数体不能引用外部闭包变量，否则持久化恢复后会丢失——`registercommands.js` 只保存函数定义本身。
* **不要重复执行同一个 `registerCommand('同名', ...)`**，Brigadier 会对重复 literal 抛异常；如需覆盖请重启服务器或手动清理。
* 中文乱码问题：Windows 命令行编译前可先 `chcp 65001`，不影响实际编译结果。
* 权限等级以 `registerCommand` 第二个参数为准，未填时默认 `2`。

#### 1.2 API 变更

```js
// 1.1 及以前
registerCommand(name, permissionlevel, fn)
// fn 参数：function(name) { ... }

// 1.2 起
registerCommand(name, permissionlevel, fn)
// fn 参数：function(name, arg1, customname) { ... }
// 兼容旧的 function(name) { ... }
```
