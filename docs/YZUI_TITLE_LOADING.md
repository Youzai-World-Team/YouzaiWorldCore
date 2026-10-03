# 标题页与资源重载界面

标题页保留原版旋转全景，使用薄荷绿 MD3 配色、圆角卡片和统一按钮。左侧为加入服务器、选项、退出及开发者入口；右侧为公告或更新信息。正文独立滚动，下载与忽略按钮固定在卡片底部，支持滚轮以及 Page Up / Page Down / Home / End。紧凑窗口缩减留白和标志尺寸。

标题页右上角提供「切换深色 / 切换浅色」。它与设置页共用 `ClientExternalSettings.setYzuiTheme`，即时生效并保存到 `yzwc/client/global_settings.json` 的 `core_module.yzui_theme`。资源重载界面、标题文字、按钮、卡片及滚动条都读取该主题。

资源重载使用居中的品牌卡片、圆角进度条和百分比。首次启动只使用原版提前注册的标志纹理与几何绘制，不依赖尚未就绪的字体和普通模组纹理。完成后显示 100%，卡片随原版透明度淡出。仅替换绘制调用，原版进度采样、完成及异常回调、资源应用和 Overlay 关闭逻辑保持原样。

标题页和加载页统一复用 `src/client/resources/assets/youzaiworldcore/textures/gui/startup/logo.png`，与官网静态资源的 `public/images/uzw-tm.png` 内容完全一致。保留原有彩色图案，只裁去透明留白并等比缩放。加载页的提前注册纹理直接从 classpath 读取该图，不再读取旧 `mojangstudios.png`；标题页也不再使用旧 `yzw-logo.png`。

## 客户端测试路径

| 路径 | 预期结果 |
| --- | --- |
| 启动客户端 → 标题页 | 两处均显示官网彩色标志，无旧标志、白块或拉伸；标题页保留旋转全景，所有按钮可用 |
| 标题页 → 切换深色 / 切换浅色 | 界面配色即时更新，标志保留原色；按钮名称表示下一次切换的主题 |
| 标题页 → 选项 → YouzaiWorldCore 外观设置 → 修改主题 → 返回 | 标题页与设置页主题一致；重启客户端后加载页继续使用保存的主题 |
| 游戏中 F3+T；选项 → 资源包 → 更换资源包 → 完成 | 重载卡片跟随当前主题，进度推进并完成淡出，回到原有页面或游戏 |
| 标题页 → 调整窗口 / GUI 缩放 | 从 320×240 GUI 尺寸到宽屏，标志、副标题、卡片与按钮不重叠、不出屏 |
| 长公告 / 长更新日志 → 在正文上滚轮 | 能滚到最后一行；滚动条位置正确；下载和忽略按钮不随正文移动 |
| 长公告 / 长更新日志 → Page Up / Page Down / Home / End | 能翻页、返回顶部与到达末尾；Tab 与 Enter / 空格继续控制按钮 |
| 可选更新 → 前往下载 / 忽略 | 下载先打开链接确认；忽略后恢复公告，已忽略版本重启后不再提示 |
| 强制更新 → 加入服务器 | 显示强制更新弹窗；标题页不显示忽略入口 |
| 开发版 Player数字账号 → 加入服务器 | 保留原有加入限制弹窗 |
| 开发者模式 → 测试入口 | dedicated 模式连接配置地址；其余模式进入单人世界选择 |
| 标题页 → 退出 → 取消；标题页 → 选项 → 返回 | 返回同一标题布局；弹窗及普通页面沿用现有背景规则 |
| 设置中禁用界面动画 → 返回 / 重载资源 | 标题操作正常显示；资源重载仍按原版生命周期完成，不残留遮罩 |

## 自动检查与验证边界

```powershell
python -B tools/verify_yzui_title_loading.py --java-home 'C:/Program Files/Java/25'
```

该检查执行实际 Java 布局、滚动状态、圆角绘制和加载渲染器，使用记录绘制调用的 Minecraft 替身。覆盖 126 组标题布局、320 组加载绘制状态，验证按钮几何、正文与固定操作区、滚动边界、标志 UV、进度边界、淡出透明度和两种主题的对比度。标志测试从真实 classpath 读取 PNG，核对尺寸、透明留白、等比缩放及原色保留。同时检查十种语言的新增文案、JSON 重复键和编码。

首次重设计时，完整 `compileClientJava --offline` 在既有 `MapLoadedCapture.java:55` 的 `ChunkPos.x/z` 私有访问错误处中断，未修改该地图文件。界面相关 7 个 Java 文件另以项目现有依赖和编译输出独立编译；该临时任务关闭注解处理，Mixin 注入点另外与本地 Minecraft 26.2 字节码核对。

替换官网标志后，6 个相关客户端类及共用滚动依赖再次通过独立编译。更新后的回归检查通过 46,634 个断言，并核对了深浅主题的绘制记录预览、图片与官网源文件的一致性，以及启动纹理读取的注入点。

未运行 build、游戏或开发服务器；绘制记录预览和上述自动检查不能替代实际客户端的输入、旁白、GPU、第三方 Mixin 组合及完整资源重载验收。

## 修改文件

- `src/client/java/top/csituka/youzaiworldcore/client/screen/title/TitleMenuLayout.java`：标题页统一几何布局。
- `src/client/java/top/csituka/youzaiworldcore/client/screen/title/YzuiTitleMenu.java`：主题控件、公告滚动和原有入口逻辑。
- `src/client/java/top/csituka/youzaiworldcore/client/render/YzuiLoadingRenderer.java`：无字体依赖的加载卡片。
- `src/client/java/top/csituka/youzaiworldcore/client/render/YzuiBrandLogo.java`：共用彩色标志及启动阶段资源读取。
- `src/client/java/top/csituka/youzaiworldcore/mixin/client/LogoTextureMixin.java`：将原版提前注册纹理的数据来源切换为官网标志。
- `src/client/java/top/csituka/youzaiworldcore/mixin/client/TitleScreenMixin.java`：标题页生命周期与绘制接入。
- `src/client/java/top/csituka/youzaiworldcore/mixin/client/LoadingOverlayMixin.java`：替换资源重载视觉。
- `src/client/java/top/csituka/youzaiworldcore/mixin/client/MouseHandlerScrollMixin.java`：标题正文滚动命中与 Overlay 输入保护。
- `src/main/java/top/csituka/youzaiworldcore/update/TitleScreenScrollState.java`：按实际视口计算并约束滚动范围。
- `src/main/resources/assets/youzaiworldcore/lang/{zh_cn,zh_tw,zh_hk,lzh,en_us,en_gb,de_de,es_es,fr_fr,ru_ru}.json`：标题副标题、操作区名称与主题按钮文案。
- `tools/verify_yzui_title_loading.py`：专项回归检查。
- `docs/YZUI_TITLE_LOADING.md`：实现说明、验证边界和手动测试路径。
