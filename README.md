# MCJS
在minecraft里面执行javascript代码

## 简介
这个项目添加了/js指令执行javascript代码 示例：
```
/js let a="Hello";runCommand("/say "+a)
runCommand(“指令") //执行指令

//1.0版本语法
registerCommand('hi', function(name) {
  return '你好，' + name + '！';
}); //注册指令

//1.1版本语法
registerCommand('hi', OP等级, function(name) {
  return '你好，' + name + '！';
}); //注册指令

/js set <key> <value>// 设置配置选项
/js reset// 重置所有配置到默认值
/js list//显示已注册命令
```
## 权限等级说明

| 等级 | 适用对象 |
|------|----------|
| 0 | 所有玩家 |
| 1 | 基础管理员 |
| 2 | 普通OP |
| 3 | 高级管理员 |
| 4 | 服主 |
##API列表
```js
// 文件操作
saveFile(path, content)//保存文件
saveBinaryFile(path, content)// 保存二进制文件
loadFile(path)// 加载文件

// 配置管理
setOption(key, value)// 设置配置
getOption(key)// 获取配置
resetOptions()// 重置配置

// 命令注册
registerCommand(name, permissionlevel, fn)

//命令执行
runCommand(command)
```
使用需要level2的op权限（服务器管理员/开启命令的本地存档/命令方块）

执行日志会保存在js_execution.log文件

## 注意事项
脚本可以执行一切指令，不要在服务器上乱跑脚本


## 配置

使用/js set 配置项 值来进行设置
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
saveFile('文件名', '内容')保存文件

loadFile('文件名')加载文件 (1.1版本更新 二进制文件返回Base64)

saveBinaryFile(path, content) 保存二进制文件

## 更新日志
### 1.1
* 新增/js set
* 命令执行权限设置
* 不知名闪退已修复
* 文件管理系统优化
* 移除了Him
