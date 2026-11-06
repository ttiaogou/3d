# Mobile3DStudio 0.3.0 改动清单

基准仓库：`https://github.com/ttiaogou/3d`
基准分支：`main`
读取到的最新提交：`a767f27482e50032d9a4eabef9b4050cfde2ab9a`
版本：`0.3.0` / `versionCode 3`

## 本次改动

- 将 Dice Fragment 3D v1.4 的离线 WebGL 引擎放入 `app/src/main/assets/dice/`。
- 新增 `DiceSceneWebView.kt`，通过本地 WebView 容器接入原始模型渲染。
- 重构 `EditorScreen.kt`：平面图片工作区与模型工作台分开；模型工作台支持模型类型、骰子点数输入、生成模型、场景对象选择、XYZ 移动、XYZ 旋转、默认、撤销 `↶`、返回上一级 `↪`。
- 重构 `Mobile3DStudioApp.kt`：连接 WebView JS Bridge、同步场景对象和选中变换、保留平面图片状态、处理模型参数和导入图片。
- 更新 Android Manifest 的硬件加速配置。
- 同步最新仓库的 `.github/workflows/build-android.yml` 路径与 Android SDK 安装配置。

## 保留的模型算法

- 骰子 1：沿用原引擎不规则轮廓、倒角、侧壁和随机点位。
- 骰子 2：沿用原引擎双切面三角网格裁切、切面封口、第二切面裁切第一封口、闭合网格体积计算；目标切除率独立随机 35.5%–89.5%。
- 扑克：沿用原引擎圆角厚度网格、正反纹理和侧边渲染。

## 当前限制

- 骰子 1 的点位由原引擎 WebGL 着色器表现凹点明暗，不是顶点几何凹槽；骰子 2 的凹点是实际顶点位移几何。
- 原引擎扑克牌状态为单个红桃 Q 实例；骰子可创建多个实例。
- PNG 原生保存流程尚未接入；GLB 网格导出器尚未实现。
- 模型场景尚未保存为可恢复工程文件；撤销历史保存在当前 WebView 会话中。

## 验证

- `node --check` 检查嵌入 JavaScript，通过。
- Android XML 资源解析，通过。
- 关键模型 API、随机切除范围、体积测量字段、XYZ 控件及撤销/返回符号静态检查，通过。
- 当前环境没有 Android SDK 与 Gradle，未完成 `assembleDebug`，也未完成 Android 实机 WebGL 视觉验收。
