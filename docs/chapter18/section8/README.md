# 配置 MagicLib 自动尾迹

长程导弹背后那条连续不断的发光轨迹，极少靠代码在每帧硬拼粒子来凑 —— 真按那套写法，弹体多了以后光维护生命周期就够您头疼。直接交给 `magicTrail_data.csv` 和 `MagicAutoTrails` 接管静态绑定，只要采样间距与颜色渐变定得克制，不用写一行渲染脚本就能替都林弹体拉出顺滑的双层尾迹。

---

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~25 分钟。_

## 前置知识

- [C1-S5] 的 `GearIn_durin_shot` 弹体；
- [C18-S1] 的 MagicLib manifest 依赖；
- 能够编辑 `data/config/settings.json` 与 CSV，并在 `starsector.log` 定位加载警告；
- 能够准备一张带透明通道、沿纵向安排延伸图案的 trail PNG。

## 在 settings 中登记自定义 sprite

主体层使用 GearIn 自己的纹理。在 `data/config/settings.json` 的 `graphics.fx` 中登记：

```json
{
    "graphics": {
        "fx": {
            "GearIn_trail_clean": "graphics/fx/GearIn_trail_clean.png"
        }
    }
}
```

CSV 的 `sprite` 列保存 `GearIn_trail_clean` 这个键，`MagicAutoTrails` 再通过 `Global.getSettings().getSprite("fx", sprite)` 取得图片。纹理横向跨越尾迹宽度，纵向沿路径延伸；将带透明通道的图案按这个方向保存到 `graphics/fx/GearIn_trail_clean.png`。

辉光层使用 MagicLib 已登记的 `base_trail_smooth`。主体与辉光由此分别使用自有资源和依赖库资源，CSV 都通过 sprite 键取得它们。

## 选择当前数据文件路径

当前数据文件使用：

```text
data/config/modFiles/magicTrail_data.csv
```

MagicLib 主动把这一路径加入读取列表，使用默认位置时无需修改 `modSettings.json`。同路径文件以 MagicLib 为 master，按 `trail` 列合并，外部行可以替换同键基础行。自有行键带 MOD 前缀，并为每层尾迹保留独立名称。

::: tip 兼容路径

`data/trails/trail_data.csv` 仍位于 MagicLib 的 `magicTrail_files` 列表中，用于读取采用旧目录结构的 MOD。新数据使用 `data/config/modFiles/magicTrail_data.csv`，可以直接取得当前 30 列表头和 `frameOffsetMult` 字段。

:::

## 用 projectile ID 绑定尾迹

**`projectile` 对应实际弹体，`trail` 对应一层尾迹记录。**主体的局部绑定为：

```csv
trail,projectile,sprite
GearIn_durin_trail_body,GearIn_durin_shot,GearIn_trail_clean
```

`GearIn_durin_shot` 与 `DamagingProjectileAPI.getProjectileSpecId()` 的返回值逐字匹配。武器显示名称、weapon ID 和 projectile ID 分属不同数据关系；修改显示文本不会改变尾迹，写成 weapon ID 则不会命中这个弹体分组。

`trail` 标识数据行，`projectile` 将多行分到同一个弹体。master 合并会用后处理的同键行替换已有行；同一份外部文件重复使用一个 `trail` 时，后一行也会替换前一行。叠加表现因而要使用不同的行键。

## 为同一弹体叠加两层尾迹

第二行继续使用 `GearIn_durin_shot`，但改用另一行键和 sprite：

```csv
GearIn_durin_trail_glow,GearIn_durin_shot,base_trail_smooth
```

主体层从 10 su 收窄到 3 su，颜色由亮红橙过渡到暗红；辉光层从 32 su 收窄到 14 su，透明度较低。发射一次弹体后，两层共享移动路径和采样时机，却分别按自己的尺寸、颜色与生命周期淡出。

## 谁负责读取和追加尾迹

MagicLib 已经在自己的 `data/config/settings.json` 中注册 `MagicAutoTrails`。应用加载时，`Magic_modPlugin` 调用 `MagicAutoTrails.getTrailData()`；加载器读取配置中的 `magicTrail_files`，再固定加入当前默认文件 `data/config/modFiles/magicTrail_data.csv`。

每份表通过下面的关系进入运行时：

```text
trail 行键
→ merged spreadsheet
→ 按 projectile 分组的 PROJ_TRAILS
→ DamagingProjectileAPI.getProjectileSpecId()
→ 每条匹配记录各自取得 trail ID
→ MagicTrailPlugin.addTrailMemberAdvanced(...)
```

战斗插件只处理 `projectileSpecId` 出现在数据表中的弹体。同一个 projectile 分组中有两条记录时，插件会分配两个独立 ID，并在相同弹体位置提交两层节点。GearIn 不需要再注册自己的战斗插件。

Dev Mode 下，`MagicAutoTrails.init(...)` 会在每场战斗开始时重新读取数据。修改 CSV 后退出当前战斗并重新进入，新的参数就会参与下一次初始化。

## 控制节点采样与生命周期

示例主体层使用以下值：

```csv
minLength,fadeIn,duration,fadeOut
6,0,0.04,0.35
```

正数 `minLength` 让移动距离超过阈值后增加节点，本例为 6 su；减小阈值通常会提高连续性，也会增加节点。负数使用间隔为 0.06–0.07 秒的共享计时器，计时器在弹体遍历中推进，因此单弹的实际采样还受对象数量与顺序影响。需要独立控制每枚弹体的时间时，采用运行时跟踪器。

::: warning 同一弹体分组使用首条记录的采样参数
多层尾迹共用分组首条记录的采样模式和 `minLength`。只修改第二层的这个字段，不会让它独立改变节点间隔；本例两行保持相同值。
:::

每个节点先按 `fadeIn` 淡入，保持 `duration` 秒，再按 `fadeOut` 淡出。主体层立即出现、保持 0.04 秒并在 0.35 秒内消失；辉光层的保持时间为 0.05 秒、淡出时间为 0.2 秒，因此宽光晕比主体更快收回。

`fadeOnFadeOut = TRUE` 会在弹体自身进入 fading 状态时，用当前伤害比例继续压低节点透明度。它不会替代节点自己的三段生命周期。

## 控制尺寸、颜色与透明度

主体层的外观字段为：

```csv
sizeIn,sizeOut,colorIn,colorOut,opacity
10,3,"[255,90,70]","[120,25,20]",0.8
```

`sizeIn` 与 `sizeOut` 是节点生成时与生命周期结束前的宽度。10→3 形成逐渐收窄的实体尾部；32→14 则让辉光包在主体外侧。`colorIn` 与 `colorOut` 分别控制同一节点生命周期两端的 RGB，`opacity` 设置最终透明度倍率，本例采用 0–1 范围内的值。

这五项共同决定单个节点如何变化。连续节点使用同一组变化规则，最终在弹体后方组成稳定色带。

## 控制纹理滚动

自定义主体纹理使用：

```csv
textLength,textScroll,randomTextureOffset
256,600,TRUE
```

`textLength` 表示一个纹理周期沿尾迹占用的世界长度，单位为 su。`textScroll` 则以每秒千分之一个纹理周期计：1000 表示每秒滚动一个完整周期，本例的 600 表示每秒 0.6 个周期。改变符号会反转方向。`randomTextureOffset = TRUE` 为不同尾迹选择随机起点，减少接缝重合。

辉光层使用 `textLength = -1` 与 `textScroll = 0`，让平滑光晕保持静态。纹理长度、节点宽度与 sprite 的实际图案需要一起校准；长度过短时重复接缝会变得明显。

## 控制位置、速度与旋转

`distance = 18` 让节点生成点沿弹体朝向偏移 18 su。`drift = 1` 完整继承弹体的横向速度；`dispersion = 0` 保持节点速度不随机散开。`frameOffsetMult = 1` 保留默认的帧间速度偏移补偿。

以下速度、旋转与帧间偏移字段则用于制作烟雾、火花或扭曲带状效果：

```csv
velocityIn,velocityOut,randomVelocity,angle,rotationIn,rotationOut,randomRotation,frameOffsetMult
0,0,0,0,0,0,FALSE,1
```

`velocityIn` 与 `velocityOut` 让节点自身速度随生命周期变化，`randomVelocity` 给两者加入同一随机比例。`angle` 改变尾迹相对弹体朝向的方向；`rotationIn` 与 `rotationOut` 控制节点角速度，`randomRotation` 为每个节点随机化旋转方向和倍率。

`angleAdjustment = TRUE` 在速度足够时，把弹体本身的朝向改为当前速度方向，再计算尾迹。`BALLISTIC_AS_BEAM` 会跳过这项修正。只想旋转尾迹表现时使用 `angle`；本例保留 `angleAdjustment = FALSE`，弹体朝向继续由它原来的行为控制。

## 选择混合方式与渲染层

两层示例都使用 `additive = TRUE`。加色混合会把亮色叠加到战场画面，适合能量辉光和发光弹体；设为 `FALSE` 时使用普通透明混合，更适合烟尘、碎屑和实体色带。

主体与辉光需要各自承担清晰的视觉分工：主体收窄到 3 su，辉光虽然宽到 32 su，却采用更低透明度和更短淡出时间；宽度、亮度与时长配合取舍，弹体轮廓才能继续留在画面里。

`renderBelowExplosions = FALSE` 使用默认尾迹渲染层。改为 `TRUE` 后，MagicLib 把节点提交到较低的效果层，使爆炸和其他粒子覆盖在尾迹上方。该字段只改变绘制顺序，不改变节点位置和生命周期。

## 30 列字段速查表

| 字段                    | 类型        | 示例值                    | 直接结果或主要错误                              |
| ----------------------- | ----------- | ------------------------- | ----------------------------------------------- |
| `trail`                 | 字符串      | `GearIn_durin_trail_body` | 合并行键；master 合并中后处理的同键行替换已有行 |
| `projectile`            | 字符串      | `GearIn_durin_shot`       | 与 projectile spec ID 不一致时不会生成尾迹      |
| `sprite`                | 字符串      | `GearIn_trail_clean`      | 从 `graphics.fx` 取图；键缺失时无法绘制         |
| `minLength`             | 浮点        | `6`                       | 正值按距离采样，负值启用慢速模式                |
| `fadeIn`                | 浮点秒      | `0`                       | 节点淡入时间                                    |
| `duration`              | 浮点秒      | `0.04`                    | 节点完整显示时间                                |
| `fadeOut`               | 浮点秒      | `0.35`                    | 节点淡出时间                                    |
| `sizeIn`                | 浮点 su     | `10`                      | 节点初始宽度                                    |
| `sizeOut`               | 浮点 su     | `3`                       | 节点结束宽度                                    |
| `colorIn`               | RGB 字符串  | `[255,90,70]`             | 节点初始颜色；整数解析错误会中止本次加载调用    |
| `colorOut`              | RGB 字符串  | `[120,25,20]`             | 节点结束颜色                                    |
| `opacity`               | 浮点        | `0.8`                     | 最大透明度                                      |
| `textLength`            | 浮点 su     | `256`                     | 纹理重复长度                                    |
| `textScroll`            | 浮点        | `600`                     | 每秒滚动 0.6 个纹理周期，符号决定方向           |
| `randomTextureOffset`   | 布尔        | `TRUE`                    | 随机选择纹理起点                                |
| `distance`              | 浮点 su     | `18`                      | 节点相对弹体位置的偏移                          |
| `dispersion`            | 浮点 su/s   | `0`                       | 节点速度的随机散布范围                          |
| `drift`                 | 浮点比例    | `1`                       | 继承弹体横向速度的比例                          |
| `frameOffsetMult`       | 浮点倍率    | `1`                       | 帧间偏移补偿；字段缺失时默认 1                  |
| `fadeOnFadeOut`         | 布尔        | `TRUE`                    | 弹体 fading 时同步压低节点透明度                |
| `angleAdjustment`       | 布尔        | `FALSE`                   | 按速度方向修正弹体朝向                          |
| `additive`              | 布尔        | `TRUE`                    | `TRUE` 使用加色混合                             |
| `velocityIn`            | 浮点 su/s   | `0`                       | 节点初始速度                                    |
| `velocityOut`           | 浮点 su/s   | `0`                       | 节点结束速度                                    |
| `randomVelocity`        | 浮点比例    | `0`                       | 为两端速度加入随机倍率                          |
| `angle`                 | 浮点角度    | `0`                       | 尾迹相对弹体方向的角度偏移                      |
| `rotationIn`            | 浮点角度/秒 | `0`                       | 节点初始角速度                                  |
| `rotationOut`           | 浮点角度/秒 | `0`                       | 节点结束角速度                                  |
| `randomRotation`        | 布尔        | `FALSE`                   | 随机化节点旋转方向和倍率                        |
| `renderBelowExplosions` | 布尔        | `FALSE`                   | `TRUE` 让爆炸和其他效果覆盖尾迹                 |

RGB 字符串采用示例中的紧凑写法，逗号后直接接整数。颜色解析逐项读取整数字符串，额外空格或无法解析的分量也会产生异常；其他字段无法按所需 JSON 类型读取时，加载器跳过该行，并在详细日志开启时记录 `Invalid line, skipping`。

## 完整最小例

最终文件关系为：

```text
data/config/settings.json
data/config/modFiles/magicTrail_data.csv
graphics/fx/GearIn_trail_clean.png
```

`data/config/settings.json` 在现有 `graphics.fx` 中加入自定义 sprite：

```json
{
    "graphics": {
        "fx": {
            "GearIn_trail_clean": "graphics/fx/GearIn_trail_clean.png"
        }
    }
}
```

`data/config/modFiles/magicTrail_data.csv` 使用以下完整内容：

```csv
trail,projectile,sprite,minLength,fadeIn,duration,fadeOut,sizeIn,sizeOut,colorIn,colorOut,opacity,textLength,textScroll,randomTextureOffset,distance,dispersion,drift,frameOffsetMult,fadeOnFadeOut,angleAdjustment,additive,velocityIn,velocityOut,randomVelocity,angle,rotationIn,rotationOut,randomRotation,renderBelowExplosions
GearIn_durin_trail_body,GearIn_durin_shot,GearIn_trail_clean,6,0,0.04,0.35,10,3,"[255,90,70]","[120,25,20]",0.8,256,600,TRUE,18,0,1,1,TRUE,FALSE,TRUE,0,0,0,0,0,0,FALSE,FALSE
GearIn_durin_trail_glow,GearIn_durin_shot,base_trail_smooth,6,0,0.05,0.2,32,14,"[255,170,120]","[255,70,40]",0.55,-1,0,TRUE,18,0,1,1,TRUE,FALSE,TRUE,0,0,0,0,0,0,FALSE,FALSE
```

### 进游戏核对

启动游戏，确保 MagicLib 已启用且测试武器能够发射 `GearIn_durin_shot`：

- 加载阶段无 `magicTrail_data.csv` 或 sprite 错误日志；发射 `GearIn_durin_shot` 后，窄主体与宽辉光两层尾迹同时延伸，修改武器显示名而不改 projectile ID 时两层仍正常出现，改成不存在 ID 则不再生成；
- 将两行暂改为同一 `trail` 行键可验证后行覆盖前行的合并行为；调正/负 `minLength`、`additive`、`fadeOnFadeOut` 与 `renderBelowExplosions` 均可实时对比距离/定时采样、混合模式与爆炸遮挡顺序 (Dev Mode 下进新战斗会自动重读 CSV)。

## 常见问题

- **弹体完全没有尾迹**：CSV 路径、`projectile` ID 或表头字段与加载器不一致。
    - **解决方案**：使用当前默认路径，并逐字核对 `GearIn_durin_shot` 与完整表头。

- **主体层不显示，辉光层正常**：`GearIn_trail_clean` 没有进入 `graphics.fx`，或 PNG 路径错误。
    - **解决方案**：核对 settings 键、CSV sprite 和 `graphics/fx/GearIn_trail_clean.png`。

- **两层记录只显示后一层**：外部表中的两行使用同一个 `trail`，master 合并时前一行被替换。
    - **解决方案**：为主体和辉光恢复两个带 MOD 前缀的独立行键。

- **高速弹体的尾迹出现明显断口或节点过密**：正 `minLength` 与弹体每帧移动距离不匹配。
    - **解决方案**：从 6 su 开始增减采样距离，观察连续性和节点数量。

- **弹体朝向随尾迹采样改变**：`angleAdjustment` 修改了非 `BALLISTIC_AS_BEAM` 弹体的朝向。
    - **解决方案**：普通都林弹体保持 `FALSE`，并用 `angle` 单独调整尾迹方向。

- **普通色带亮得像能量辉光**：实体层错误使用加色混合。
    - **解决方案**：烟尘或实体色带使用 `additive = FALSE`，发光层使用 `TRUE`。

## 建议参考项

- `org.magiclib.plugins.MagicAutoTrails`：读取、分组并消费 projectile 尾迹数据；
- `org.magiclib.plugins.MagicTrailPlugin`：为自动尾迹分配 ID 并接收节点；
- `Starsector/mods/MagicLib/data/config/modFiles/magicTrail_data.csv`：当前 30 列表头与字段说明；
- `Starsector/mods/MagicLib/data/config/modSettings.json`：`magicTrail_files` 与兼容路径。
