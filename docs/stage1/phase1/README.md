# 熟悉 MOD 文件夹

*本节预期阅读时间：~5 分钟。*

*本节预期操作时间：~3 分钟。*

## 快速获取 MOD 文件夹

您可以通过[这里](https://share.weiyun.com/xO3SkGzF)下载一个已经组织好的 MOD 文件夹，省去了几乎所有麻烦。

## 在其它操作之前...

您要做的第一件事是为您的 MOD 想好**中文名称与英文名称**，然后将英文名称调整为适合 **MOD ID** 的形式，并得出它的**前缀名**。

::: tip

假设您希望做一个叫`齿轮工业`的 MOD，那您可以翻译得出它的英文名是`Gear Industry`。

**MOD ID 不允许出现空格**，而建议以下划线连接，但仅去除空格也行。因此您的 MOD ID 可以是`Gear_Industry`或`GearIndustry`。

**前缀名至少应该包括四个字母，并且以下划线结尾**。因此您的前缀名是`Gear_`或`GearIn_`，也可以在 MOD ID 的基础上进一步自由发挥。

:::

本教程的 MOD 范例名即为`齿轮工业`，MOD ID 为`GearIndustry`，前缀名为`GearIn_`。

## 基础 MOD 文件夹构成总览

首先将`SampleMod`文件夹名改为您的 MOD ID，**MOD 文件夹名应当保持永久不变**，请注意不要在后方加入版本号。

然后是有些令人眼花缭乱的 MOD 文件夹内部：

```
.(MOD 文件夹本身)
├── data (一切数据相关)
│   ├──campaign
│   │   ├── terrain (进阶内容)
│   │   └── sim_opponents.csv
│   ├── config
│   │   ├── hull_styles.json (进阶内容)
│   │   ├── settings.json (进阶内容)
│   │   └── sounds.json (进阶内容)
│   ├── hullmods (舰船插件)
│   │   └── hullmods.csv
│   ├── hulls (舰船)
│   │   ├── ship_data.csv
│   │   └── wing_data.csv
│   ├── missions (战役)
│   │   └── mission_list.csv
│   ├── shipsystems (战术系统)
│   ├── strings (文本)
│   │   ├── description.csv (文本描述)
│   │   └── strings.json (进阶内容)
│   ├── trails (进阶内容)
│   ├── variants (装配文件)
│   ├── weapons (武器与弹体)
│   │   ├── proj
│   │   └── weapon_data.csv
│   └── world
│       └── faction (势力配置)
│           ├── default_ship_roles.json
│           └── factions.csv
├── graphics (一切贴图相关)
│   ├──backgrounds (星系背景)
│   ├── factions (势力旗帜)
│   ├── fx (进阶内容)
│   ├── hullmods (舰船插件)
│   ├── icons (进阶内容)
│   │   └── 各类子类
│   ├── illustrations (进阶内容)
│   ├── missiles (弹体/不仅仅是导弹)
│   ├── portraits (进阶内容)
│   ├── ships (舰船)
│   └── weapons (武器)
├── jars (进阶内容)
├── sounds (进阶内容)
├── src (进阶内容)
├── change_log.txt (更新日志)
└── mod_info.json (MOD INFO)
```

## 正式开始

**如果您觉得上述目录过于复杂，也不必担心，您现在需要关注的只有`mod_info.json`**。其它位置只有在介绍到对应模块时才会进行说明。

使用`VS Code`(此后默认所有文件都用它打开)打开`mod_info.json`，根据说明填入您的 MOD 信息，且不要随意改动没有说明的部分。然后将您的 MOD 当做一个普通的 MOD 一样放入您的游戏文件夹内的`mods`文件夹。

现在打开游戏，如果不出意外，那么您的 MOD 就已经能被识别了。
