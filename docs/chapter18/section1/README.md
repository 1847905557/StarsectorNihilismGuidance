# 让启动器替 MOD 检查硬依赖

GearIn 同时使用四个渲染与工具前置；缺少其中任何一个时，最好的失败位置是启动器，而非战斗加载到一半。本节把硬依赖写进 manifest，并从启动器结果确认加载门槛。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 在 `mod_info.json` 中声明依赖 ID 与版本，让启动器在加载前完成硬依赖检查；
- 认识依赖 ID、名称和可选版本字段各自承担的职责；
- 从启动器提示定位缺失依赖和版本门槛故障。

## 前置知识

- 了解 MOD 的 `mod_info.json` 文件位置；
- 能够使用启动器勾选或取消勾选 MOD。

## 明确硬依赖检查需求

假设您把整套 MOD 复制给朋友，对方只装了 LazyLib，那么启动器应直接列出 GraphicsLib、MagicLib 与 BoxUtil。

## 文件与 API 链路

`mod_info.json.dependencies` 负责加载前判定。启动器按依赖 ID 查找前置，`version` 按 major/minor/patch 三段精确匹配比对，逐段相等才兼容。

### 把四项依赖交给启动器

在 `mod_info.json` 的 `dependencies` 数组中声明依赖 ID 与名称，显式版本门槛按需保留：

```json
"dependencies": [
  {"id":"lw_lazylib","name":"LazyLib","version":"3.0.0"},
  {"id":"shaderLib","name":"GraphicsLib","version":"1.12.1"},
  {"id":"MagicLib","name":"MagicLib","version":"1.5.6"},
  {"id":"BoxUtil","name":"BoxUtil","version":"1.5.5"}
]
```

```json
"dependencies": [
  {"id":"lw_lazylib","name":"LazyLib"},
  {"id":"shaderLib","name":"GraphicsLib"},
  {"id":"MagicLib","name":"MagicLib"},
  {"id":"BoxUtil","name":"BoxUtil"}
]
```

在实际情况中，MOD 通常只写依赖 ID 和名称；`version` 用于精确匹配检查，按需填写。

卸载任一前置后再启动，启动器会立即指出对应依赖并阻止 GearIn 启动。

## 验证方案

前置数据：GearIn 的 `mod_info.json` 已声明四项依赖。

1. 保持四项依赖启用，启动游戏并确认启动器允许勾选 GearIn。
2. 退出游戏，卸载 BoxUtil(从 mods 目录移走)，再尝试勾选 GearIn。
3. 恢复 BoxUtil，将其中一个 `id` 临时改成不存在的值，再次尝试勾选 GearIn。
4. 恢复精确 ID，重新启动并确认启动器恢复允许加载。

预期结果：

- 四项依赖齐全时，启动器允许 GearIn 加载；
- BoxUtil 未安装时，启动器在进入游戏前阻止 GearIn 加载并指出缺失依赖；
- 依赖 ID 不存在时，启动器指出对应依赖无法满足；
- 恢复精确 ID 后，启动器重新允许 GearIn 加载。

## 故障分析

- **启动器提示依赖版本不满足**：dependencies 中的版本与已安装前置版本逐段不等 (major/minor/patch 精确匹配)。

    - **解决方案**：核对两份 manifest 的版本值，保持一致后重新启动。

- **启动器找不到依赖**：`id` 与前置 manifest 的 ID 不一致。

    - **解决方案**：恢复精确 ID 后重新启动器。

## 总结

硬依赖的价值在于让错误提前发生，而且提示对象明确。manifest 决定能否加载；版本字段参与精确匹配检查，按需声明。

## 建议参考项

- `mod_info.json`：硬依赖声明与版本；
