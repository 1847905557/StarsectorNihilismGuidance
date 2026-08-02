# C6–C10 架构审计整改计划

## 审计结论（已确认的问题）
1. **C07-S01 一节两主题**：「战斗插件模型与每帧回调」（约 140 行）塞在「战斗引擎：状态读取」开头；插件模型是全站引用最多的前置概念（C07-S01 被 21 文件引用，一半是插件模型语义、一半是状态读取语义），并造成 C6-S01/S03 前置知识引用下一章。
2. **C08-S07 一节四主题**：势力/货物/情报/对话框四个 API 族塞一节（981 行、20 分钟预算）。
3. **C10-S04 舰队工厂是章内孤岛**：章名不涵盖、章内零引用、跨章仅 2 处（C13-S02）；实质属于 C8（生涯对象：星区、舰队与市场）。
4. **悬空承诺**：C07-S07 承诺「舰船级自定义 AI 的实现见本章后续」，但 GearInCruiseAI 已从全库消失。
5. **死引用**：C07-S01 建议参考项第 537 行把 chapter2/section1 错标为「战斗插件模型」。

**明确不动的**：C08-S04（市场+经济，主题连贯只是重）、C09-S01、C07-S03（同域内聚）、各节教学性复读（暂停纪律/跨战斗缓存禁令）、C08-S04↔S06 等概念重复（有声明、非结构问题）。

---

## 一、C7 拆分：新 C07-S01「战斗插件模型与每帧回调」+ 原 8 节顺延为 S02~S09

### 1.1 页面
- 新 `docs/chapter7/section1/`：取原 S01 第 31–170 行子主题（每帧是什么 / 注册：settings.json 的 plugins 段 / 完整示例：GearInPauseAwarePlugin / 暂停实验 / 插件错误对照表），H1 定「战斗插件模型与每帧回调」；补齐节级骨架（学习目标、前置知识、预期结果、总结、建议参考项），子主题原内容逐字保留。
- 原 S01 剩余内容（H1 保持「战斗引擎：状态读取、遍历与时间语义」）→ `section2/`：删除嵌入的插件模型子主题；前置知识第 25 行「本节『战斗插件模型与每帧回调』主题」改为「（C07-S01）」；建议参考项第 537 行错误引用修正。
- 原 S02~S08 → section3~section9（`git mv` 保历史）。

### 1.2 fixture（`tutorial-fixtures/units/`）
- 新 C07-S01 = `GearInPauseAwarePlugin.java`（现留在 C07-S01 不动）。
- `GearInCombatStatePlugin.java` 从 C07-S01 移入新 C07-S02。
- C07-S02~S08 → C07-S03~S09（`git mv`）。

### 1.3 build.gradle
- `javaUnitIds` 的 C07 段由 8 项改为 9 项（C07-S01..S09）。

### 1.4 引用更新（脚本 + 逐条人工复核）
- 先备份全部受影响文件到 `.cursor/tutorial-work/backups/`（哈希记录）。
- **从大到小**替换（防连锁）：C07-S08→S09、S07→S08、S06→S07、S05→S06、S04→S05、S03→S04、S02→S03。
- C07-S01 的 66 条引用（63 条外部）逐条语义分流：插件模型/每帧回调/暂停守卫/plugins 注册/addPlugin 语义 → 保持 C07-S01 不变；状态读取/getPlayerShip/isAlive/getShips/遍历/时间语义/实体判据语义 → 改 C07-S02。脚本输出全部上下文，逐条人工确认。
- 页面内 fixture 路径标注同步（C07-S02~S09 页面的 `tutorial-fixtures/units/C07-S0x`）。
- C7 章首页简介补「战斗插件模型」主题词。

---

## 二、C8 重组：C10-S04 移入为 S04 + 原 S04~S06 顺延 + C08-S07 一拆二

### 2.1 页面（最终 C8 = 9 节）
| 新节 | 内容 |
|---|---|
| S01–S03 | 不变 |
| S04 | 原 C10-S04「舰队生成与投放：成员、旗舰与指挥官」（`git mv`，页面内「本章/后续章节」措辞核查、`compileC10s04Java` 标注改 `compileC8s04Java`） |
| S05–S07 | 原 C8-S04~S06（市场 / 产业 / 条件）顺延 |
| S08 | 原 C8-S07 前半：「势力与货舱」（FactionAPI 关系语义 + CargoAPI 栈模型） |
| S09 | 原 C8-S07 后半：「情报与交互对话框」（Intel 登记链 + InteractionDialogAPI 生命周期） |

- C8-S07 拆分时公共骨架（学习目标/前置知识/术语/规则强度）按主题分流；完整最小例按 7 个类归属拆：FactionServicesScript + CargoServicesScript + OneShotScript 基类 → S08；IntelServicesScript + DialogServicesScript + ServicesIntel + ServicesDialog + OneShotScript → S09。
- C8 章首页简介补「舰队工厂」主题；C10 章首页简介删「舰队工厂」词，C10 变 5 节（S04=酒吧事件、S05=任务系统）。

### 2.2 fixture + build.gradle
- C10-S04 → C08-S04；C08-S04~S06 → C08-S05~S07（`git mv`）。
- C08-S07 拆为 C08-S08（3 类）与 C08-S09（5 类，含 OneShot 副本；不同 sourceSet 独立编译，类名重复无冲突）。
- `javaUnitIds`：C08 段 9 项、C10 段 5 项。

### 2.3 引用更新（同样先备份、从大到小）
- C08-S04→S05、S05→S06、S06→S07。
- C08-S07 分流：势力/货物/声望语义 → C08-S08（C10-S02 等）；对话框/Intel 语义 → C08-S09（C10-S05/S06、C11-S01/S02/S04 等）；C12-S04 泛称按上下文定。
- C10-S04 → C08-S04（C13-S02 两处）；C10-S05→C10-S04、C10-S06→C10-S05。
- 页面 fixture 路径标注同步。

---

## 三、悬空承诺与死引用
- C07-S07（顺延后 C07-S08）第 29 行：删「，舰船级自定义 AI 的实现见本章后续」，保留原版 AI 基线事实与「本节展开系统 AI 的完整接入链」。
- C07-S02 建议参考项错误条目修正（指向正确位置或删除）。

---

## 四、验证
1. 备份哈希比对（确认无意外改动）。
2. 全库 CRLF/UTF-8 无 BOM/无裸 LF/尾部单个 CRLF 契约校验（涉及文件）。
3. `/c/gradle-9.6.0/bin/gradle -p tutorial-fixtures compileAllFixtures validateShaders --no-daemon`。
4. docs build（rm .cache/.temp → vuepress build）。
5. 死链检查（C07/C08/C10 新编号映射）：0 死链。
6. 页面代码块 vs fixture 逐字一致（受影响 6 个 fixture 单元）。
7. dev 服务器重启（8080，杀旧进程），抽查新 S01/S04/S08/S09 页面 200。

## 五、不改动项（刻意保持）
- C08-S04 超重、C09-S01 超重、C07-S03 超重：主题连贯，不拆。
- 教学性复读（暂停纪律、跨战斗缓存禁令）：各节视角不同，保留。
- 概念重复讲解（C08-S04↔S06 条件 token 等）：有声明式分工，属教程设计。