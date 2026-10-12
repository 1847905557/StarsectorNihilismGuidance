# 注册音频资源

本节把 [C5-S1] 准备好的 OGG 文件登记到 `data/config/sounds.json`，使游戏能够按声音 ID 找到文件。登记完成后，武器、系统和代码可以引用这些 ID。

*本节预期阅读时间：约 8 分钟。*

*本节预期操作时间：约 10 分钟。*

## 学习目标

完成后，您将能够：

- 创建普通音效和音乐的 JSON 条目；
- 为条目填写文件路径、音量和音高；
- 使用追加或 `replace` 文件覆盖策略，并根据日志定位加载错误。

## 前置知识

- [C5-S1] 的三个 OGG 文件、声音 ID、声道和目录；
- JSON 对象、数组和字符串写法；
- [C1-S1] 的 MOD 目录与数据合并基础。

## 创建声音定义文件

在 MOD 根目录创建 `data/config/sounds.json`。普通音效直接使用声音 ID 作为根对象键，值中填写相对 MOD 根目录的 `file`。音乐必须放在根对象的 `music` 分类中，并使用 `source` 和 `file` 指向资源。

```json
{
  "GearIn_weapon_fire": [
    {
      "file": "sounds/GearIn_weapon_fire.ogg",
      "volume": 1.0,
      "pitch": 1.0
    }
  ],
  "GearIn_system_loop": [
    {
      "file": "sounds/GearIn_system_loop.ogg",
      "volume": 0.8,
      "pitch": 1.0
    }
  ],
  "music": {
    "GearIn_market_music": [
      {
        "source": "sounds",
        "file": "GearIn_market_theme.ogg",
        "volume": 0.8,
        "pitch": 1.0
      }
    ]
  }
}
```

`volume` 和 `pitch` 使用 1.0 作为原始值。需要统一调整时可以修改条目数值，文件本身保持 [C5-S1] 的格式和声道。

## 选择文件合并方式

游戏会读取核心数据和 MOD 数据中的 `sounds.json`。默认文件会与其他定义合并；需要整体替换某个数据文件时，在 `mod_info.json` 的 `replace` 数组里声明该路径，整份文件改为替换读取。只在确实需要替换整个文件时使用 `replace`，新增 GearIn ID 直接写入普通文件。

## 让使用方引用 ID

登记后，`.wpn`、`.system` 和 Java 代码只引用 `GearIn_weapon_fire`、`GearIn_system_loop` 等 ID。具体字段和调用位置见 [C5-S3]；Java 播放方法见 [C7-S4]。

## 验证方案

前置数据：包含本节 `sounds.json` 登记行的 GearIn MOD 已启用。

1. 启动游戏并检查 `sounds.json` 的 JSON 解析结果。
2. 在日志中确认三个声音 ID 已加载。
3. 进入 [C5-S3] 的武器或系统场景观察声音。
4. 修改一个条目的 `volume` 并重启，确认游戏读取了新值。

预期结果：

- 三个 ID 都能被加载；
- 音乐 ID 位于 `music` 分类；
- 修改的条目按新值生效，其余条目不受影响。

## 故障分析

- **日志出现 JSON 解析错误**：逗号、括号或字符串不完整。
    - **解决方案**：用 JSON 编辑器定位语法错误并重新加载。

- **日志出现文件不存在**：`file` 路径不是 MOD 根目录相对路径，或文件名大小写不同。
    - **解决方案**：把 `file` 改为相对 MOD 根目录的路径，逐字符核对文件名大小写后重启。

- **音乐不播放但普通音效正常**：音乐条目没有放入 `music`，或缺少 `source`。
    - **解决方案**：移动条目并补齐字段。

- **覆盖没有生效**：`replace` 数组中的路径与目标文件不一致。
    - **解决方案**：核对 `mod_info.json` 中的路径逐字符一致后重启。

## 总结

`data/config/sounds.json` 负责把声音 ID 映射到 OGG 文件。普通音效写在根对象，音乐写在 `music` 分类；追加用于新增定义，`replace` 用于整文件替换。完成登记后，在 [C5-S3] 配置具体使用位置。

## 建议参考项

- `data/config/sounds.json`：原版声音注册表，合并行为的原版蓝本。
