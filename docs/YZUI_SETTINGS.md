# YZUI 设置中心

启用 YZUI 后，标题页和暂停菜单中的「选项」进入独立的 MD3 设置中心。点击侧栏分类切换右侧内容，普通浏览只显示当前分类，滚动与键盘焦点不会越过分类边界；顶部搜索跨分类筛选已展开的实际选项。标题页的辅助功能快捷入口打开同一个设置中心，并选中辅助功能分类。

设计沿用现有 YZUI 主题与官网配色，以圆角容器、分层背景、主题色按钮和留白组织内容。设置中心的侧栏在窄窗口中继续提供分类切换；ModMenu 独立 Core 页在窄窗口中使用分类下拉框。页面支持现有明暗主题、滚动、键盘焦点和旁白。原版在多个分类中重复提供的同一选项只显示一次，搜索仍保留它所属的所有分类名称。

侧栏首个按钮为「通用」，其上方显示「Minecraft」文本标题；「界面外观」上方显示「YouzaiWorldCore」文本标题。Core 的分类顺序统一为「界面外观 → HUD → 地图 → 导出/导入配置 → 关于 YouzaiWorldCore → 开发者」。设置中心、ModMenu 独立页和窄窗口分类下拉框共用 `CoreSettingsSection` 的名称与顺序。两个分组文本标题不响应点击，侧栏没有「全部」按钮。切换分类会清空搜索并回到该分类顶部；搜索时高亮跟随结果所属分类，无匹配结果时清除高亮。

## 2026-11-13：按用途拆分 Core 设置

| 分类 | 内容 |
| --- | --- |
| 界面外观 | 主题与效果入口、YZUI 总开关、界面动画、MCsans 字体。 |
| HUD | YZHUD 显示开关、组件位置/缩放/透明度编辑入口。 |
| 地图 | 地图设置入口，打开现有地图预览及日常设置面板。 |
| 导出/导入配置、关于、开发者 | 继续使用各自已有的内容与操作。 |

视频分类仅提供原版视频设置或 Sodium 入口，Core 的外观、HUD 与地图不再附在视频分类后。三个分类分别有自己的导航标识和搜索归属，进入专用编辑页后返回原设置分类。已有外观/配置/关于/开发者标识仍为 0/1/2/3，新增 HUD/地图标识为 4/5；导航显示顺序独立于标识。

本轮分类调整保留原有保存机制：Core 普通开关即时保存，HUD 编辑器仍需保存或取消，地图仍使用原有设置面板。开关与下拉框的显示行布局见下面的下一阶段；地图循环选择和深入专用编辑器的全局搜索属于后续调整。Shift+F 设置页未纳入本轮。

本轮离线回归通过 252 项断言：路由/控件 64 项、统一设置模型 86 项、列表与侧栏 26 项、开关与下拉框 29 项、实际 Core 分类构建与导航 44 项、配方书初始化 3 项；同时核验 Minecraft 26.3 的 22 个反射字段、11 类设置子页与十种语言的分类名称。新增检查覆盖分类内容隔离、搜索归属、窄窗口键盘选择、稳定标识与导航顺序、原有开关的一次保存、编辑页返回目标，以及配置操作期间禁止分类切换。测试使用配置存储、编辑器和绘制替身；未执行 Gradle 模组编译、游戏启动或服务端联调。

23:12 的启动崩溃由本轮将 `buildVisualSection` 改名引起：`ItemGroupsSettingsEntryMixin` 仍注入旧名称，导致设置屏幕类变换失败。已恢复原方法名并保留界面外观分类内容，物品分组入口继续在该方法尾部追加。离线脚本新增该入口的注册、注入方法签名及影子字段源码检查；修复前已确认检查能检出目标缺失，修复后通过。此检查不执行 Mixin 运行时变换，修复后的游戏启动仍需重新构建后验证。

为 `tools/verify_yzui_settings.py` 增加 `.gitignore` 例外，保留既有回归及本轮新增的分类检查。

从模组目录运行：

```sh
python3 -B tools/verify_yzui_settings.py --java-home /path/to/jdk-25
```

游戏内重点验收：分别从标题页、暂停菜单和 ModMenu 进入，检查三类入口位置；从主题、HUD 和地图页返回；调整窗口宽度后检查选中分类；安装/未安装 Sodium 时检查视频与 Core 分类互不混入。

## 2026-11-13：精简语言页与整理设置行

语言选择页只保留顶部标题、搜索、语言名称、选中样式及原版完成/返回操作。移除字体入口、语言准确性提示、重复标题及独立显示的语言代码；字体选项仍在统一设置的「语言」分类。语言选择页标题与统一设置分类都只显示「语言」。语言代码只作为搜索词，名称、代码和全角代码都可搜索。选择时只更新选中状态，点击完成才交给原版应用；尚未应用的选择在窗口尺寸改变后仍保留。

统一设置里的开关和下拉框将名称与控件分开：宽内容区左侧显示名称及说明，右侧显示紧凑开关或当前值；内容行不足 340 像素时自动上下排列。名称和说明按可用宽度换行并计入行高，控件不再重复显示名称，原消息仍用于搜索和旁白。独立 Core 页及其他页面的控件默认绘制方式保持原样。

本阶段离线回归通过 276 项断言，在已有 252 项上新增语言页 11 项、实际设置行布局 13 项。布局检查记录绘制命令，覆盖文字只绘制一次、宽窄布局、长译名完整性、回调和旁白；语言检查执行真实方法并使用原版应用替身。未执行模组构建、游戏绘制或启动验证。游戏内应重点检查语言页搜索、选择后完成/返回、选择后缩放窗口，以及长名称设置在宽窄窗口下的操作。

## 2026-11-13：视频分组标题使用主题绘制

原版视频页的「显示、画质、偏好设置」由 `OptionsList.HeaderEntry` 内的 `FocusableTextWidget` 绘制，其 `ON_FOCUS` 背景会在点击获得焦点后显示黑框。统一设置现在根据标题行的结构提取文字，去掉原版加粗和下划线，使用 `SettingsList.Row.subheading` 的主题色静态标题。标题没有子控件，不接受点击或进入控件焦点；文字顺序和分组搜索保留，标题之外的富文本继续使用原有交互。

本轮离线回归通过 284 项断言，新增 8 项覆盖标题识别、静态行、样式清理、原模型保持、主题绘制、分组搜索及原标题控件不参与绘制；修复前已确认测试能检出问题。已核验 Minecraft 26.3 的标题行及控件接口，未执行模组构建或游戏内验证。游戏内应在视频分类依次点击、悬停小标题并使用 Tab 导航，确认无原版黑框且实际选项仍可操作。

## 2026-11-13：其他设置页的说明文字统一处理

共用设置列表进一步处理直接放在页面布局中的 `FocusableTextWidget`：世界设置等页面的分类标题转为静态主题色小标题，纯说明转为可换行的静态内容；游戏规则等已有静态分类也去掉原版加粗、下划线。文字不再带有原版控件的点击焦点背景或黑色边框。

文字中包含链接时，检查全部子组件的点击事件，保留原控件的鼠标、键盘与旁白行为；绘制改为原文字布局的 `visitLines` 与共用文字收集器，绕过原版背景和边框。链接的命中区域、悬停提示和鼠标形状继续由原版文字布局处理。

本轮离线回归通过 297 项断言，新增静态及链接文字 12 项和语言分类标题 1 项，并核验当前 Minecraft 文字绘制接口。检查覆盖直接布局标题、纯说明、长文字、子组件链接、鼠标/键盘回调及旁白。未执行模组构建或游戏内验证；需在世界设置、限制说明及含链接的说明页确认焦点和悬停效果。

## 2026-11-14：移除冗余入口与整理外部链接确认框

设置中心完整移除「在线选项」分类，不再创建其原版模型；直接请求在线页时回到设置中心。辅助功能移除高对比度、黑白徽标、辅助功能指南按钮、复述功能及其快捷键选项。过滤依据原版选项身份，已隐藏控件不加入搜索、显示或状态同步；字幕、高对比度方块轮廓等其他选项继续保留。此次只调整界面入口，不重置已有设置值。

「模组设置」根据模组 ID 移除 Minecraft 和 Moog’s Structure Lib 的独立配置按钮，保留已安装模组浏览器、Sodium 组合入口和其他模组配置按钮。「资源与信息」移除「遥测数据」详情按钮；原有可选遥测开关继续按环境许可显示。

原版外部链接确认框改为与退出确认页相同的居中圆角卡片，标准宽度 400、内边距 24、按钮高度 28。正文使用主题色静态文字，保留完整网址与原版警告；长内容可滚动，窄窗口缩小卡片，按钮固定在底部。标题最多三行，避免长译名挤占操作区。移除设置页的大外壳、搜索、侧栏和多余返回箭头，默认聚焦取消。打开、复制和取消继续使用原版按钮实例，Esc 仍交给原版确认页处理。主菜单、聊天、更新页和设置页中的原版链接确认均使用此布局；显式原版兼容流程与第三方自定义确认页沿用既有路由。

本轮离线回归通过 340 项断言：原有检查继续通过，新增过滤、配置值保持、实际模组入口筛选，以及链接确认路由、文本提取、卡片尺寸、窄屏长网址和按钮回调检查。测试运行实际设置模型、模组兼容类与卡片布局，原版客户端及按钮回调使用替身。未执行 Gradle 构建或游戏启动；游戏内应确认浏览器打开、剪贴板复制、Esc 返回以及明暗主题的实际绘制。

## 共用控件与专用流程

开/关选项复用 Core「启用 YZUI」的 `CheckboxButton` 开关，其他有限选项复用「界面动画」的 `DropdownButton` 下拉框；「按住/切换」「左/右」等模式选择也使用下拉框。选择目标值只调用一次原版回调，保留保存、权限校验和设置联动，避免经过中间选项。控件同步原版的当前值、禁用状态、可见性、提示与旁白；预设改变后同步显示，自定义当前值即使不在可选列表里也仍可见。动态可选范围改变时关闭旧菜单，提交前再次校验目标值。

`YzuiSettingsScreenSwitchMixin` 在 `Gui.setScreen` 的切换入口选择新屏幕，并在已有切换动画之前确定目标。新布局由独立的 `YzuiOptionsScreen`、`SettingsFrame` 和 `SettingsList` 绘制；没有向原版设置屏幕注入新布局。`UnifiedSettingsPage` 汇总原版实际控件、配置回调和权限状态。普通子页的跳转会定位到统一页面中的对应分类。

资源包、按键绑定、语言选择、游戏规则、遥测详细说明和鸣谢保留专用布局，因为这些流程包含排序、捕获输入、应用/放弃、服务端校验或大量只读内容。语言入口旁直接展开字体选项，可选遥测开关也直接显示在统一页面中。外观/HUD 编辑器、配置备份选择和第三方模组配置保留各自的专用页面。游戏规则页继承 `InWorldGameRulesScreen`，保留原版网络响应要求的屏幕类型。

统一页只提交实际操作过且仍待应用的滑条。切换焦点、滚动、退出或重排前会提交这些值；预设改变时同步关联控件。返回统一页时重新创建原版模型，防止旧控件覆盖刚在 Sodium、语言选择或配置导入页面保存的设置。

原版设置继续使用 `options.txt`，Core 设置继续使用现有 `yzwc/client/global_settings.json`。此次没有增加配置格式或迁移步骤。

| 页面 | 实现与保留的行为 |
| --- | --- |
| 统一设置分类 | 视场角以及皮肤、声音、视频、控制、鼠标、聊天、精简后的辅助功能、字体等具体设置按选中分类展开。资源与信息、Core、模组配置分别提供自己的分类。 |
| 分类切换与搜索 | 普通浏览只显示选中分类；搜索跨分类查找已展开的选项、描述和分类名称，重复选项保留分类搜索别名。 |
| 开关与下拉选择 | 开/关使用 Core 同款开关，其他选择使用同款下拉框；鼠标或键盘直接选择目标值，支持 Esc 取消、长列表滚动和动态选项同步。 |
| 视频 | 未安装 Sodium 时直接展开原版预设、全屏、GUI 比例等设置，保留 Ctrl + 滚轮、显卡警告和保存。安装 Sodium 后只显示钠与附属设置入口，不创建或展示原版视频控件。 |
| 按键绑定 | 按分类显示操作，支持键盘与鼠标捕获、取消绑定、冲突提示、单项及全部重置。 |
| 语言 | 只显示语言名称，代码用于搜索；保留选择、完成应用与返回，字体设置位于统一设置页。 |
| 资源包 | 使用原版选择模型，支持启停、排序、固定包限制、不兼容确认、拖入文件和目录变化。 |
| 遥测 | 可选遥测开关直接显示在统一页；账号或环境不允许时不补出开关。遥测数据详情按钮已移除。 |
| 世界与游戏规则 | 世界选项直接展开，保留难度与权限状态；游戏规则使用专用编辑页，保留请求、校验、只提交改动项和放弃确认。 |
| 鸣谢与确认页面 | 提供可滚动的鸣谢内容，保留链接、确认与取消回调、操作延时和返回路径。 |
| YouzaiWorldCore | 六个分类的控件和说明按选中分类展开，复用现有配置回调。导出期间统一页锁定导航与配置修改，底部显示进度。下拉菜单在列表裁剪之后绘制并优先处理点击。 |

关闭 YZUI 后恢复原有选项页及原有隐藏项目的行为。在统一列表关闭 YZUI 时会返回原有选项页；从 ModMenu 再次启用后，返回设置会重新创建完整列表。`YouzaiWorldCoreSettingsScreen` 的原构造方式和独立页面保留，ModMenu 仍可直接打开它。

模组兼容入口按两条路径提供：注册了 ModMenu 配置屏幕的模组会出现在「模组设置」列表中；向原版选项界面添加入口的其他模组可通过「原版选项（兼容入口）」访问。兼容入口及其子页面保留原版流程，退出该流程后恢复 YZUI 路由。第三方自定义屏幕保留其自身界面。

检测到 Fabric 已加载 Sodium 时，视频分组只保留「钠与附属设置」入口，统一页不创建原版视频模型，不参与其预设更新、显卡警告与全屏应用流程。由 YZUI 接管的原版视频页面跳转也直接转交 Sodium；第三方自己的页面及显式原版兼容流程保持各自路由。

「钠与附属设置」调用 Sodium 的公开 `VideoSettingsScreen.createScreen(Screen)` 工厂，保留 Reese’s Sodium Options、Sodium Extra 和 Iris 参与页面创建与初始化的流程。创建与切换分开，路由直接返回工厂生成的页面，避免嵌套切换；父屏幕用于返回统一设置页。入口异常时回到 ModMenu 列表。已对用户提供目录中的以下 jar 做版本及相关接口核验；这些核验不等同于游戏内组合测试。

| 模组 | 本地核验版本 |
| --- | --- |
| ModMenu | 20.0.1 |
| Sodium | 0.9.1+mc26.2 |
| Sodium Extra | 0.9.3+mc26.2 |
| Reese’s Sodium Options | 2.2.3+mc26.2 |
| Iris | 1.11.2+mc26.2 |
| Sodium Fullbright | 1.2.0 |

工作台闪退已根据 `run/logs/latest.log` 和 `run/crash-reports/crash-2026-09-12_22.59.31-client.txt` 定位：配方书 `ImageButton` 绘制时直接使用了为空的悬停动画字段。`ImageButtonYzuiMixin` 改为首次使用时创建动画状态并在之后的帧中复用，避免依赖字段初始化覆盖所有构造路径。

以下为此前连续列表版本的验收记录，其中「视觉」现已拆为「界面外观、HUD、地图」，「滚动定位」现为分类内容切换。原版与专用流程的验收项目继续适用；当前分类行为以上面的说明与回归为准。游戏内手动验收尚未实际执行，涉及服务端权限、规则响应及 Core 服务端配置的项目应在匹配版本的测试服务端中验证。

| 编号 | 测试路径与操作 | 预期结果 |
| --- | --- | --- |
| 01 | ModMenu → YouzaiWorldCore → 视觉 → 启用 YZUI；分别从标题页和暂停菜单点击选项。 | 都进入完整的设置列表；具体控件直接可操作，普通分类不再显示进入子页的按钮。底部完成返回正确的上级。 |
| 02 | 标题页 → 辅助功能快捷按钮；再点声音、视频、Core 等分类导航。 | 打开统一页并定位辅助功能；之后的导航只滚动列表，搜索可跨分类命中，Esc 返回标题页。 |
| 03 | 统一页 → 定位 Core 视觉 → 关闭 YZUI；重新进入选项，再通过 ModMenu 开启 YZUI 并返回。 | 关闭时恢复原有页面及隐藏项目行为；重新开启后完整列表恢复，无循环切换。 |
| 04 | 同一列表调整视场角、总音量、各分类音量和皮肤模型部件。 | 无需进入子页；数值和效果遵循原版；拖动后立即滚动、换焦点或返回也能保存。 |
| 05 | 未安装 Sodium 的测试实例：视频分组 → 切换预设，再修改单项；切换全屏、按 F11、调整 GUI 比例、使用 Ctrl + 滚轮。 | 预设与「自定义」标签及关联选项同步；全屏按钮同步真实状态；缩放后位置正确；退出时应用全屏分辨率。 |
| 06 | 未安装 Sodium 且能触发原版显卡警告的环境：更改相应视频选项，分别接受、取消、按 Esc。 | 警告使用新布局，仅打开一次；每种选择执行正确回调，返回视频页后状态正确。 |
| 07 | 统一列表中的控制与鼠标分组；点击按键绑定，绑定键盘、鼠标和重复按键，再单项重置、全部重置。 | 鼠标选项直接可改；绑定页保留捕获、Esc 取消绑定、冲突提示和重置；完成回到原列表位置。 |
| 08 | 「语言」分类 → 打开语言选择，搜索语言名称、代码 `en_us` 和全角 `ＥＮ＿ＵＳ`；选择后改变窗口尺寸再完成，再测试未应用时返回。 | 两处标题都只显示「语言」；搜索忽略大小写并进行 Unicode 归一化；窗口变化保留待应用选择；完成应用语言；未应用选择遵循原版返回行为。语言页不显示字体入口、准确性提示、重复标题或语言代码，字体选项直接位于统一页。 |
| 09 | 同一列表中调整聊天和辅助功能，切换旁白；搜索声音、控制、辅助功能中共享的设置。 | 原版控件完整，同一选项只显示一次，所属分类名称都可搜到它；控件名称和值可被旁白读取。 |
| 10 | 在线选项 → 查看账号限制、在线相关设置、好友与隐私链接；在出现的确认页使用确认、取消、Esc。 | 账号与环境允许的操作可访问；限制信息可读，链接仍能操作；确认延时和取消回调正确，返回链正确。 |
| 11 | 资源与信息分组 → 分别在允许与不允许可选遥测的环境下查看；切换统一页的开关，再打开遥测详情。 | 支持时直接显示开关，不支持时不补出开关；详情中的必需、可选及未启用事件状态与实际设置一致。 |
| 12 | 资源包 → 启停普通包，调整顺序，尝试移动或停用固定包；添加不兼容包并分别确认、取消。 | 图标、标题、说明、操作互不遮挡；固定包遵守原版限制；不兼容包只在确认后选中；返回和完成遵循原版流程。 |
| 13 | 保持资源包页面打开，拖入资源包，修改目录内容，并用新内容替换同名包。 | 保留原版文件复制确认；列表、顺序、说明和图标随原版目录检测更新；搜索和滚动状态合理保留。 |
| 14 | 暂停菜单 → 选项 → 定位世界分组；分别在有权限、无权限及权限发生变化时操作难度和相关按钮。 | 世界选项直接展开，按钮状态与原版权限一致；难度变化反映到页面；权限变化只刷新对应模型，无重复切换或递归。 |
| 15 | 世界分组 → 游戏规则 → 等待响应，修改布尔值和数值；输入非法值再修正并完成。 | 规则使用专用编辑页；非法输入阻止提交；只提交改动项；完成回到统一列表，服务端规则与保存结果一致。 |
| 16 | 修改规则后返回，分别取消放弃、确认放弃；编辑期间撤销游戏管理权限。 | 确认页使用新布局；取消保留编辑内容，确认放弃返回上级；权限撤销执行原版退出流程，无重复进入。 |
| 17 | 鸣谢与归属 → 鸣谢、归属及链接；滚动到末尾并返回。 | 内容完整可读，链接保持可用；所有返回路径回到打开它的设置页。 |
| 18 | 同一列表中滚动到 Core 四个分组，修改配置、导入/导出；打开下拉菜单并点击覆盖在其他行上的条目，测试键盘和 Esc。 | 四个分组全部直接展开；下拉菜单绘制和命中正确。导出期间锁定导航、输入和退出并显示进度；导入保留备份选择流程，完成返回统一列表。 |
| 19 | ModMenu → YouzaiWorldCore 配置；分别在 YZUI 开启和关闭时操作并返回。 | 始终可打开保留的独立 Core 设置页，并返回 ModMenu；与嵌入页共用实际配置。 |
| 20 | 视频分组或模组分组 → 钠与附属设置；访问 Reese 界面、Extra 选项、Iris 光影入口、Fullbright 对应设置后返回。 | 原有界面和扩展可用，设置保存正常；回到统一页原滚动位置，新数值不被旧控件覆盖。需逐项确认附属模组实际注册的入口。 |
| 21 | 模组设置 → ModMenu 及任一注册配置页；原版兼容入口 → 第三方入口及其子页 → 返回。 | 配置页可打开；兼容流程内不被反复替换；退出兼容流程后再次点击选项仍进入 YZUI。 |
| 22 | 调整窗口到窄屏、宽屏及不同 GUI 比例；切换明暗主题；在长列表跨分类搜索、清除搜索。 | 容器、长文本复选框、滑条和底部按钮可用；宽屏显示定位导航；无匹配时有提示；Esc 先关闭下拉菜单或清空搜索，再返回。 |
| 23 | 使用 Tab、Shift + Tab、方向键、回车和旁白操作统一列表、确认页与 Core 控件；切换开发者模式后继续操作新增字段。 | 焦点可见，能到达操作控件；动态控件及时更新；滚动后命中正确；仍存在的焦点可保留，已移除控件不继续接收输入。 |
| 24 | 重新启动客户端，开启 YZUI，进入测试世界并首次右键工作台；反复开关配方书、翻页、悬停按钮、合成并关闭重开。 | 首帧和后续交互均不再因悬停状态为空而崩溃；配方书开关、翻页与合成保持原版行为。 |
| 25 | 未安装 Sodium 的实例：调整视频滑条后在 600 ms 内点击其他分类、搜索、完成或选择新预设；安装 Sodium 的实例：进入钠修改选项并返回。 | 待应用值正确提交，新预设具有后操作的优先级；隐藏的重复控件与已退出页面的旧模型不会覆盖最新值。 |
| 26 | 宽屏设置页：检查侧栏首项、两个文本标题与 Core 分类名称，依次点击已经可见的分组、中部与末尾分组；使用右侧滚轮和滚动条上下滚动；搜索后点击分类，清空搜索并调整窗口宽度。 | 首个按钮为「通用」，上方显示「Minecraft」；第一个 Core 设置按钮上方显示「YouzaiWorldCore」。Core 按钮及右侧对应标题直接显示「视觉」等分类名称，没有重复前缀。两个文本标题不可点击，没有「全部」按钮。右侧分组标题按实际布局定位到顶部，末尾空间不足时滚到尽头并高亮点击项；手动滚动时高亮同步当前分组，滚到底部选中最后分组。搜索结果仍有正确归属，重排后再次定位正确。 |
| 27 | 安装 Sodium 及附属的实例：选项 → 视频 → 钠与附属设置，访问 Reese、Extra、Iris 等入口，修改并返回；与未安装 Sodium 的测试实例对照，再关闭 YZUI。 | 安装时视频分组只提供钠入口，没有原版视频控件；原有附属页面可用，返回统一页且设置不被覆盖。未安装时原版视频设置完整展开；关闭 YZUI 后沿用原有流程。 |
| 28 | 设置页 → 皮肤、声音、聊天或辅助功能中的开/关项；与 Core「启用 YZUI」对照，分别鼠标点击与聚焦后按空格；查看禁用项并切换关联预设。 | 开关样式一致，名称完整、状态可见；每次操作只应用一次，禁用项不可修改，预设变化后状态同步，旁白包含名称和当前状态。 |
| 29 | 设置页 → 聊天显示、主手、旁白、按住/切换等选择项；打开下拉框直接选末项，使用方向键与回车选择，按 Esc 取消；在列表上下边缘和长选项列表中操作。 | 采用 Core「界面动画」同款下拉框；打开和移动候选项不写配置，选定后直接应用目标值；弹层不被行裁剪，命中与显示一致，Esc 只关闭菜单。 |
| 30 | 未安装 Sodium 的实例：更改视频预设及单项；在声音设备等动态选项中检查可选列表变化；搜索当前选项值，退出设置后重新进入。 | 当前值和可选范围同步，「自定义」等当前值不显示为空；范围变化后旧菜单关闭，不应用失效条目；搜索与保存正常。 |

本次验证：专用脚本通过 183 项断言（路由/控件 64 项、统一设置模型 61 项、实际列表与侧栏 26 项、实际开关与下拉框 29 项、配方书动画初始化 3 项），同时核验 Minecraft 26.2 的 24 个反射字段、11 类设置子页与好友确认页、Mixin 注册以及 10 种语言各 22 个设置中心文案。

此前的常规 `compileClientJava --offline --console=plain` 在通用源码的地图模块中被阻断：`MapServerSettings`、`MapTerrainStore` 引用了不存在的 `GlobalSettings.MAP_MODULE`。当时随后通过离线 Gradle 解析现有编译依赖，并用 JDK 25 对控件调整涉及的 5 个 UI Java 文件执行定向 `javac` 编译，使用实际 Minecraft 依赖及现有项目编译输出，编译通过。本轮侧栏标题调整重新执行并通过上述离线回归脚本，没有重复 Gradle 验证。上述历史编译结果不代表当前整个工程编译通过。

脚本以客户端替身执行实际的路由、控件辅助、统一设置模型、`SettingsList`、`SettingsFrame`、`SettingsChoiceControl`、`CheckboxButton` 和 `DropdownButton`。覆盖分类展开、专用流程返回、去重和搜索别名、值同步、延迟提交、权限及操作锁。控件回归通过实际鼠标、键盘与弹层命中逻辑验证直接选择、一次回调、外部状态同步、动态范围、失效选择、提示、旁白及焦点恢复。侧栏回归覆盖已可见标题、变高行、滚动同步、末尾截断、筛选、页面恢复及窄屏；Sodium 回归覆盖有无安装、停止创建原版视频模型、正常跳转及返回后保留值。原版客户端、基础列表坐标、回调、字体与绘制使用替身，因此这些检查不等同于游戏内组合测试。

配方书回归提取实际的延迟初始化字段和方法执行，覆盖未初始化状态、跨帧复用和不同按钮隔离；此检查不执行绘制或 Mixin 运行时变换，不能替代游戏内首次打开工作台的验证。脚本不启动 Minecraft、不运行 Gradle、不联网，也不执行模组 jar。可从 `YouzaiWorldCore` 目录复现：

```powershell
$expected = 'C:\Users\zxabinbina\Desktop\YouzaiWorld\API_go\YouzaiWorldCore'
if ((Get-Location).Path -ne $expected) { throw '工作目录不符，停止执行。' }
python -X utf8 -B tools/verify_yzui_settings.py --java-home 'C:/Program Files/Java/25' --mods-dir 'E:/PCL/.minecraft/versions/Youzai World Clinet 26.2/mods'
```

客户端编译验证使用本机已有缓存，不下载依赖：

```powershell
$expected = 'C:\Users\zxabinbina\Desktop\YouzaiWorld\API_go\YouzaiWorldCore'
if ((Get-Location).Path -ne $expected) { throw '工作目录不符，停止执行。' }
if (-not (Test-Path -LiteralPath 'gradle/wrapper/gradle-wrapper.jar' -PathType Leaf)) {
    throw '缺少 Gradle Wrapper jar：不得下载，停止后续 Gradle 验证。'
}
$env:GRADLE_USER_HOME = 'C:\Users\zxabinbina\.gradle'
.\gradlew.bat compileClientJava --offline --console=plain
```

没有执行完整构建、开发服务器或游戏启动。GPU 绘制、真实输入、服务端响应和多个模组共同加载后的行为仍需按上表验收。

本次 Core 分类名称调整修改 2 个文件：`UnifiedSettingsPage.java` 移除分类名称的重复前缀，侧栏按钮和右侧分组标题统一复用原有翻译；`docs/YZUI_SETTINGS.md` 同步说明与验收路径。

设置中心实现及后续修订累计涉及 32 个文件，以下路径均相对模组目录。共享文件仅列出本任务负责的改动，既有及其他任务的修改不在本次检查范围内。

| 文件 | 本次作用 |
| --- | --- |
| [YzuiOptionsScreen.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/YzuiOptionsScreen.java) | 统一设置列表、专用页面、保存生命周期和交互。 |
| [UnifiedSettingsPage.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/UnifiedSettingsPage.java) | 展开原版及 Core 控件，维护去重、值同步、权限、延迟提交和源页面生命周期；安装 Sodium 时用入口替代原版视频模型。 |
| [YzuiSettingsRouter.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/YzuiSettingsRouter.java) | 普通子页定位到统一页，保留专用流程、父页恢复及兼容范围；按安装状态把视频页面转交 Sodium。 |
| [SettingsFrame.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/SettingsFrame.java) | MD3 外壳、搜索、导航、侧栏文本分组与底部操作，侧栏高亮跟随内容分组。 |
| [SettingsList.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/SettingsList.java) | 可变高度列表、筛选、控件布局与旁白；准确定位标题并保留筛选结果的分组归属。 |
| [SettingsChoiceControl.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/SettingsChoiceControl.java) | 将原版循环选择适配为开关或下拉框，保留目标值回调、动态状态、提示与旁白。 |
| [CheckboxButton.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/widget/CheckboxButton.java) | 提供不触发回调的开关状态同步方法。 |
| [DropdownButton.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/widget/DropdownButton.java) | 提供动态选项与当前值的显示同步，支持显示列表之外的自定义值。 |
| [SettingsWidgets.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/SettingsWidgets.java) | 原版控件/选项关系收集、字段读取与待应用滑条提交。 |
| [SettingsCompatibility.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/SettingsCompatibility.java) | ModMenu、Sodium 及其他模组入口，使用工厂创建带返回路径的 Sodium 页面。 |
| [EmbeddedCoreSettings.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/EmbeddedCoreSettings.java) | Core 嵌入内容的输入、绘制与旁白。 |
| [YzuiGameRulesScreen.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/options/YzuiGameRulesScreen.java) | 保持原版协议类型的 MD3 游戏规则页。 |
| [YzuiSettingsScreenSwitchMixin.java](../src/client/java/top/csituka/youzaiworldcore/mixin/client/YzuiSettingsScreenSwitchMixin.java) | 屏幕切换入口路由。 |
| [YouzaiWorldCoreSettingsScreen.java](../src/client/java/top/csituka/youzaiworldcore/client/screen/YouzaiWorldCoreSettingsScreen.java) | 提供展开的控件、说明和操作进度，保留原独立页面与嵌入接口。 |
| [ImageButtonYzuiMixin.java](../src/client/java/top/csituka/youzaiworldcore/mixin/client/ImageButtonYzuiMixin.java) | 配方书按钮首次使用时初始化悬停动画，修复工作台闪退。 |
| [OptionsScreenMixin.java](../src/client/java/top/csituka/youzaiworldcore/mixin/client/OptionsScreenMixin.java) | 原有首页删减仅在 YZUI 关闭时生效。 |
| [ChatOptionsMixin.java](../src/client/java/top/csituka/youzaiworldcore/mixin/client/ChatOptionsMixin.java) | 原有聊天选项删减仅在 YZUI 关闭时生效。 |
| [AccessibilityOptionsScreenMixin.java](../src/client/java/top/csituka/youzaiworldcore/mixin/client/AccessibilityOptionsScreenMixin.java) | 原有辅助功能选项删减仅在 YZUI 关闭时生效。 |
| [CycleButtonYzuiMixin.java](../src/client/java/top/csituka/youzaiworldcore/mixin/client/CycleButtonYzuiMixin.java) | 新设置页的布尔按钮保留完整选项文字。 |
| [youzaiworldcore.client.mixins.json](../src/client/resources/youzaiworldcore.client.mixins.json) | 注册屏幕切换 Mixin。 |
| [zh_cn.json](../src/main/resources/assets/youzaiworldcore/lang/zh_cn.json)、[zh_hk.json](../src/main/resources/assets/youzaiworldcore/lang/zh_hk.json)、[zh_tw.json](../src/main/resources/assets/youzaiworldcore/lang/zh_tw.json)、[lzh.json](../src/main/resources/assets/youzaiworldcore/lang/lzh.json) | 四种中文语言各提供 22 个设置中心文案，已移除「全部」。 |
| [en_us.json](../src/main/resources/assets/youzaiworldcore/lang/en_us.json)、[en_gb.json](../src/main/resources/assets/youzaiworldcore/lang/en_gb.json)、[de_de.json](../src/main/resources/assets/youzaiworldcore/lang/de_de.json)、[es_es.json](../src/main/resources/assets/youzaiworldcore/lang/es_es.json)、[fr_fr.json](../src/main/resources/assets/youzaiworldcore/lang/fr_fr.json)、[ru_ru.json](../src/main/resources/assets/youzaiworldcore/lang/ru_ru.json) | 六种其他语言各提供 22 个设置中心文案，已移除「全部」。 |
| [verify_yzui_settings.py](../tools/verify_yzui_settings.py) | 离线路由、开关与下拉框、单屏模型、侧栏定位与高亮、Sodium 替换、滑条提交、配方书初始化、接口与资源验证。 |
| [YZUI_SETTINGS.md](YZUI_SETTINGS.md) | 设计说明、验证范围、手动测试路径和修改清单。 |
