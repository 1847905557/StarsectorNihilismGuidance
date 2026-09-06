# 操纵生涯舰队

本节围绕已经生成的 `CampaignFleetAPI`，安排舰队任务、读取运动状态，并通过 `FleetDataAPI` 修改成员集合。

::: tip 任务队列不代替位置读取
`addAssignment(...)` 只安排待执行行为。实时位置、朝向和距离仍要从舰队实体读取，不能用当前任务推断地图状态。
:::

*本节预期阅读时间：\~14 分钟。*

*本节预期操作时间：\~20 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `addAssignment()` 建立舰队任务队列；
- 读取舰队位置、朝向、速度与当前任务；
- 通过 `FleetDataAPI` 添加、移除并遍历成员；
- 判断行为队列与成员数据的职责边界。

## 前置知识

- [C11-S1] 的舰队生成、投放与 `FleetParamsV3`；
- [C10-S4] 的实体位置与地点容器。

## 安排舰队行为

`CampaignFleetAPI` 同时是地图实体和舰队行为入口。以下局部片段先清空旧任务，再让舰队前往目标并保持警戒：

```java
fleet.clearAssignments();
fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 30f, "前往训练中继站");
fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 10f, "在中继站附近警戒");
```

任务按加入顺序执行。持续时间使用生涯天数；当前任务完成或超时后，舰队进入下一项。

## 读取运动与任务状态

位置来自 `SectorEntityToken`，任务状态来自舰队行为队列：

```java
Vector2f location = fleet.getLocation();
float facing = fleet.getFacing();
CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
float distance = Misc.getDistance(fleet, playerFleet);
FleetAssignmentDataAPI assignment = fleet.getCurrentAssignment();
```

朝向表达实体面对的角度，位置和距离表达当前地图状态。任务队列只说明舰队准备执行的行为，无法代替实时位置判断。

## 修改舰队成员

`FleetDataAPI` 持有成员集合与旗舰关系。以下局部片段创建成员、加入舰队并重新同步视图：

```java
FleetMemberAPI member = Global.getFactory().createFleetMember(
        FleetMemberType.SHIP,
        "lasher_Standard"
);
FleetDataAPI data = fleet.getFleetData();
data.addFleetMember(member);
data.setFlagship(member);
data.syncIfNeeded();
```

移除成员时应先确认它仍属于目标舰队：

```java
if (data.getMembersListCopy().contains(member)) {
    data.removeFleetMember(member);
    data.syncIfNeeded();
}
```

成员的船体、装配、舰长和维修状态由 `FleetMemberAPI` 管理；任务队列由 `CampaignFleetAPI` 管理。修改成员集合不会自动建立新的舰队行为。

## 验证方案

1. 生成并投放测试舰队，依次加入移动与警戒任务。
2. 在生涯地图观察舰队抵达目标后切换任务文本。
3. 添加一名成员并记录舰队规模，再移除该成员并重新记录。

预期结果：

- 当前任务按队列从移动切换为警戒；
- 添加成员后舰队成员数精确增加 1；
- 移除成员后舰队成员数恢复基线。

## 故障分析

- **舰队停在原地且没有任务文本**：舰队没有进入有效地点容器，或任务目标已经失效。

    - **解决方案**：确认舰队已经加入地点容器，并检查任务目标仍属于可访问地点。

- **成员列表已经改变，但地图图标规模没有立即更新**：成员数据视图仍等待同步。

    - **解决方案**：完成批量修改后调用 `syncIfNeeded()`，并避免在遍历原集合时直接删除成员。

## 总结

`CampaignFleetAPI` 管理生涯行为与地图状态，`FleetDataAPI` 管理舰队内部成员。任务队列、实时位置和成员集合需要分别读取与修改。

## 建议参考项

- `com.fs.starfarer.api.campaign.CampaignFleetAPI`：生涯舰队行为与地图状态管理；
- `com.fs.starfarer.api.campaign.FleetAssignment`：舰队任务枚举；
- `com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI`：成员任务条目，携带当前任务与目标；
- `com.fs.starfarer.api.campaign.FleetDataAPI`：舰队内部成员集合管理。
