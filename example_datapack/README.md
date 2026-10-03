# 自定义空投示例

这是一个独立的数据包示例，演示第三方命名空间如何声明新的空投类型和战利品表。完整字段、默认值与排错方法见发布源码中的 `INTEGRATION.md`（发布数据包 ZIP 也附带此文档）。

将本目录复制为世界 `datapacks/example_airdrops`，确保 `pack.mcmeta` 直接位于该目录内。执行 `/reload` 后，可发现：

- 类型：`example_airdrops:survival`
- 战利品表：`example_airdrops:airdrop/survival`
- 医疗类型：`example_airdrops:medical`，包含蜂蜜瓶、治疗药水和金苹果。

本例使用 `appearance: "food"`，每次抽取 3～6 次，并在战利品表中展示相对权重和数量范围。类型的 `weight` 控制类型选择概率，战利品表内的 `weight` 控制该表内的物资概率，两者不要混用。

生存补给另有独立奖励池：每箱仅判定一次，1% 概率获得一颗钻石。普通池不包含钻石，所以每箱最多一颗。

医疗补给展示 `conditions` 和 `settings`：主世界、主世界生物群系标签、晴天、白天自动触发；投放距离 48–128 格；箱子保留十分钟，单机重进不重置计时，不允许液面落箱。管理员 `spawn` / `crate` 绕过自动条件，便于测试。

管理员使用 `/airdrop_supply_drops crate example_airdrops:survival` 检查自定义物资，或 `/airdrop_supply_drops spawn example_airdrops:survival` 测试完整飞机事件。类型 `weight` 已参与自动类型选择；`appearance` 只接受 `mineral` 或 `food`，分别使用蓝色或橙色标识木箱，空中与落地外观一致。

类型文件的整数 `schema_version` 必须为 1，整数 `weight` 范围为 1～1,000,000；非法类型被排除，执行 `/airdrop_supply_drops validate` 可查看错误路径。已启动空投和已落地箱子不受后续 `/reload` 的抽奖和设置变化影响。
