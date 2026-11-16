# MOD 骨架和入口文件

一个 MOD 应该是什么？

不不不，先抛开那些富有想象力的、充满希望与愿景的回答不谈，编者的意思是，在游戏眼里，它就是一个文件夹加一份说明文件。

本节建立这个文件夹，写好那份说明，让游戏承认它的存在。它的名字和标识必须在一开始就定下，往后不再改动。

---

_本节预期阅读时间：\~6 分钟。_

_本节预期操作时间：\~5 分钟。_

## 前置知识

- 文件与文件夹的基本操作：新建、复制、重命名，并能在资源管理器中显示文件扩展名；
- 一个能以 UTF-8 无 BOM 保存文本的编辑器，如 VS Code；
- 已安装并能正常启动 Starsector。

## 熟悉 MOD 文件夹

每个 MOD 都是 `Starsector/mods/` 下的一个直接子文件夹。启动器只认根目录里放着 `mod_info.json` 的文件夹：没有这个文件、文件解析失败或缺少必需字段，整个文件夹都会被跳过，不出现在 MOD 列表里。

本教程从零开始制作一个示例 MOD，名为齿轮工业 (Gear Industry)，此后各章的舰船、武器与代码都加入这个 MOD。它的 MOD ID，即游戏用来区分各个 MOD 的唯一标识，定为 `GearIndustry`，文件夹也以此命名：

```text
Starsector/mods/GearIndustry/
├── data/
├── graphics/
├── sounds/
└── mod_info.json
```

打开 `Starsector/starsector-core/`，您会发现原版的结构与此几乎一致。MOD 的 `data/`、`graphics/`、`sounds/` 与原版同名目录一一对应，游戏按相同的相对路径读取它们。

| 目录        | 用途                                      |
| ----------- | ----------------------------------------- |
| `data/`     | CSV、JSON、舰船、武器、战术系统等游戏数据 |
| `graphics/` | 舰船、武器、图标、肖像和界面图片          |
| `sounds/`   | OGG 音频                                  |
| `jars/`     | 编译后的 Java JAR，第 6 章起使用          |

现在只需要建立 `GearIndustry` 文件夹和 `mod_info.json`，其余目录用到时再建。

MOD ID 写在下文的 `mod_info.json` 中。游戏并不要求文件夹名与它相同，但编者建议保持一致，并且一经发布就不再改动。名字一变，用户覆盖更新时就会留下旧文件夹，`mods` 里于是塞满同一个 MOD 的不同版本。

## 编写 mod\_info.json

在 `Starsector/mods/GearIndustry/mod_info.json` 写入：

```json
{
    "id": "GearIndustry",
    "name": "齿轮工业",
    "author": "YourName",
    "version": {
        "major": 0,
        "minor": 1,
        "patch": 0
    },
    "description": "Starsector MOD 教程的示例 MOD。",
    "gameVersion": "0.98.5a-RC3"
}
```

::: caution 必须保存为 UTF-8 无 BOM

带 BOM 或使用其他编码的文件会解析失败，MOD 不出现在启动器列表中。

记事本恰好擅长制造这类问题。请改用 VS Code 一类的专业编辑器。

:::

| 字段          | 必需 | 作用                                                     |
| ------------- | ---- | -------------------------------------------------------- |
| `id`          | 是   | MOD 的唯一标识，其他 MOD 声明依赖时也用它                |
| `name`        | 是   | 启动器中显示的名称                                       |
| `author`      | 否   | 作者或团队，显示在启动器信息面板                         |
| `version`     | 建议 | 当前版本，缺失时启动器的版本位置留空                     |
| `description` | 是   | 启动器信息面板中的说明                                   |
| `gameVersion` | 是   | 要求的游戏版本；缺失或主版本不符时，启动器标红并禁止勾选 |

以下字段可选：`dependencies` 声明本 MOD 依赖的其他 MOD，如提供公共代码的依赖库；`jars` 列出 MOD 自带的 Java 代码包；`modPlugin` 指定游戏启动时调用的 MOD 入口类，见 [C3-S3]。它们都在用到时再加。**现在不要预先填写 `jars` 与 `modPlugin`**：指向不存在的 JAR 或类，游戏会在启动阶段直接报错。

::: info 宽松 JSON

Starsector 实际读取的是允许注释、尾逗号和 `#` 注释的宽松 JSON。原版文件大量使用这些写法，您复制原版时会见到它们；本教程自己的示例则保持标准 JSON，便于编辑器检查。

:::

## 选择 ID 前缀

所有 MOD 的舰船、武器、战机、船插、战术系统、势力和字符串键都汇入同一张表。您起名为 `test_ship`，别人多半也会这样起；两边同时启用，同名对象相互覆盖，报错却可能记在您头上。

因此，在制作任何东西之前先定一个前缀。前缀取自 MOD 名称的缩写，短到每次输入都不嫌麻烦，又不至于与别人撞上。本教程取 Gear Industry 的缩写，固定使用：

```text
MOD ID:       GearIndustry
数据/资源前缀: GearIn_
```

数据 ID 写成 `前缀 + 对象名`，如下一节制作的舰船工匠 (Artizan) 写作 `GearIn_artizan`。ID 只用英文字母、数字和下划线，大小写一经确定就不再改动：其他文件靠 ID 找到它，存档也记录它，发布后再改等于让旧存档里的东西凭空消失。

## 只写新增的内容

MOD 的 `data/` 会与原版数据合并。`ship_data.csv`、`weapon_data.csv` 这类注册表按行合并，因此 MOD 里的同名文件**只写您新增的行**。复制整份原版 CSV 再改其中一行，得到的是一份与原版重复的表，以及一堆莫名其妙的问题。

需要参考原版时，打开 `starsector-core/data` 下的同名文件对照即可。各类文件的具体合并方式在用到时说明。

## 确认 MOD 被识别

启动 `starsector.exe`，在启动器中打开 Mods 列表，找到 "齿轮工业"。它应当没有任何红色提示，信息面板显示描述、作者和要求的游戏版本。勾选它并启动游戏，能进入主菜单即可。

所谓 Modder 的门槛，只有这么低。

## 去哪里看报错

游戏的一切报错都写在 `Starsector/starsector-core/starsector.log`。游戏启动失败、读档崩溃、某艘船显示不出来，第一件事都是打开这份日志，从最早出现的 `ERROR` 或异常堆栈读起；后面的报错往往只是它的连锁反应。

论坛上有一些辅助分析日志的工具，但编者要求您直接读日志。**作为 Modder，您必须学会手撕日志。** 工具只会匹配它见过的错误，而您写出的错误它多半没见过。

也有认为 AI 能检查错误的观点。它确实可以作为辅助工具。但也只能作为辅助工具。

## 常见问题

- **启动器中没有齿轮工业**：`mod_info.json` 不在 MOD 的直接根目录，或文件实际名为 `mod_info.json.txt`。后者常见于 Windows 隐藏了文件扩展名。
    - **解决方案**：在资源管理器中开启 "文件扩展名" 显示，确认文件恰好是 `Starsector/mods/GearIndustry/mod_info.json`。

- **日志报告解析 MOD 失败**：JSON 缺少引号、逗号或花括号，文件带 BOM，或缺少 `id`、`name`、`description`。
    - **解决方案**：在日志中搜索 `GearIndustry`，按报错位置逐字检查 JSON，并以 UTF-8 无 BOM 保存。

- **启动器将 MOD 标红并禁止勾选**：缺少 `gameVersion` 时提示 "Error: this mod does not specify which version of the game it requires and can not be enabled."；主版本不符时提示 "Error: this mod requires a different version of the game."。
    - **解决方案**：将 `gameVersion` 写为实际游戏版本。

## 建议参考项

- `starsector.log`：游戏的全部报错与日志都写在这里。
- `data/hulls/ship_data.csv`：原版舰船注册表，对照 "只写新增行" 时的样本。
- `data/weapons/weapon_data.csv`：原版武器注册表，与上一项同理。
