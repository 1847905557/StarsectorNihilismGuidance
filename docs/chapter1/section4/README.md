# 制作一艘相位舰

相位舰在战场上神出鬼没，做起来却和普通舰船几乎一样，区别只在右键系统和两张光效贴图。原版中看似复杂的舰种，在数据层面不过是换了一个系统，相位舰是其中最直观的例子。

---

_本节预期阅读时间：\~8 分钟。_

_本节预期操作时间：\~10 分钟。_

## 准备贴图

我们再次花费宝贵的 30 秒，糊出一张贴图：

![锻件级相位护卫舰贴图](./GearIn_forging.png 'GearIn_forging.png')

舰船命名为 `锻件-级 (Forging)`，ID 为 `GearIn_forging`。贴图放入 `graphics/ships/phase/`，理由与 [C1-S3] 的战机文件夹相同。

![锻件级贴图在相位舰目录中的位置](./storage.png 'storage.png')

## 制作舰船文件

相位舰的舰船文件与普通舰船没有任何区别，按 [C1-S2] 的流程制作即可。

**即使是相位舰，也必须设置合理的护盾范围**。不要因为它没有护盾就跳过 `S` 这一步。

![舰船编辑器中的锻件级相位护卫舰](./phase_make.png 'phase_make.png')

## 填写相位舰数据

天下数据一大抄。复制原版 `小精灵-级 (Gremlin)` 相位护卫舰的一行，修改后写入 `ship_data.csv`：

```csv
锻件,GearIn_forging,相位护卫舰,未知,ammofeed,6,2000,350,1200,2000,100,35,,90,120,90,45,90,200,PHASE,phasecloak,90,,,0.075,0.075,4,10,10,20,1,20,9,26000,,10,20,180,0.25,6,6,,,,,,,,,PHASE,GearIn_bp,,,,0.5,2,2,,228
```

锻件全船只有一个中型实弹槽，因此它专注于实弹火力。战术系统换成了 `加速填弹器` (`ammofeed`)，在需要时提供爆发输出，弥补失去的小型导弹。

为了让这个过程更紧张，它的幅能耗散从小精灵的 160 直接砍到 100，迫使驾驶者斟酌何时开火。再把相位的激活与维持幅能从 0.06 略微抬到 0.075，一艘还算有驾驶手感的相位舰就做好了。

相位舰的数据与普通舰船的区别集中在以下几列：

| 列             | 填写                                       |
| -------------- | ------------------------------------------ |
| `shield type`  | `PHASE`                                    |
| `defense id`   | 右键系统 ID，原版相位为 `phasecloak`       |
| `phase cost`   | 激活相位的幅能，按幅能容量的比例计算       |
| `phase upkeep` | 相位中每秒的维持幅能，按幅能容量的比例计算 |
| `hints`        | `PHASE`，在势力学说与生产中归入相位舰      |

`shield upkeep` 与 `shield efficiency` 对相位舰无意义，留空即可。

## 制作相位光效贴图

**但上述准备还不够，此时进入游戏必然报错。**

相位舰还需要两张光效贴图，即进入相位后船体上的亮紫色辉光。所幸粗糙贴图的处理一直都不是什么难事：

![锻件级相位护卫舰的第一张相位光效贴图](./GearIn_forging_glow1.png 'GearIn_forging_glow1.png')![锻件级相位护卫舰的第二张相位光效贴图](./GearIn_forging_glow2.png 'GearIn_forging_glow2.png')

白色贴图在明亮模式下无法辨认，下图衬以黑色背景：

![黑色背景下的锻件级相位光效贴图](./phase_glow.png 'phase_glow.png')

::: caution 光效贴图的位置与命名

两张贴图必须与舰船贴图位于同一文件夹，文件名为舰船贴图名加后缀 `_glow1` 与 `_glow2`，即 `GearIn_forging_glow1.png` 与 `GearIn_forging_glow2.png`。

这两个后缀由原版相位系统 `data/shipsystems/phasecloak.system` 中的 `phaseHighlight` 与 `phaseDiffuse` 指定。使用原版相位时，后缀就是这两个。

:::

光效贴图应当是纯白色的。游戏会用相位系统的颜色为它着色，带颜色的贴图会混出难看的杂色。

## 相位测试

1. 启用齿轮工业和 Console Commands，进入任意生涯存档。
2. 在控制台执行 `Addship GearIn_forging`。
3. 进入一场战斗并部署锻件，按右键进出相位。

进入相位时船体变得半透明并浮现紫色辉光，辉光的形状应当与两张光效贴图一致；退出相位时辉光消失，幅能按 `phase cost` 与 `phase upkeep` 增长。

![锻件级相位护卫舰在战斗中进入相位](./phase_play.png 'phase_play.png')

相位舰船通常还会内置 `phasefield` 船插，内置船插的做法见 [C1-S6]。

## 常见问题

- **加载时报告找不到贴图**：`_glow1` 或 `_glow2` 的文件名、大小写或所在文件夹不对。
    - **解决方案**：将两张贴图放在 `graphics/ships/phase/`，命名为 `GearIn_forging_glow1.png` 与 `GearIn_forging_glow2.png`。

- **加载时报错 `Key [shield type] has invalid value [phasecloak]`**：`shield type` 与 `defense id` 两列写反了。
    - **解决方案**：`shield type` 写 `PHASE`，`defense id` 写 `phasecloak`。

## 建议参考项

- `data/hulls/gremlin.ship`：小精灵的船体文件，可与锻件对照。
- `data/shipsystems/phasecloak.system`：原版相位系统，`phaseHighlight` 与 `phaseDiffuse` 定义光效后缀。
- `graphics/ships/afflictor/`：原版相位舰的光效贴图样本。
