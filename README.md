# 豆包语音模块

自动保存和豆包打电话时，豆包生成的每一条语音。

## 功能

在豆包（`com.larus.nova`）语音通话过程中，把豆包说出的每一段语音自动抓取并逐段保存到本地，
方便事后回听、留存或转发。纯 Hook 方案，只录豆包的声音，不包含麦克风输入，不干扰正常通话。

## 特点

- 逐段保存：豆包每说完一段，就落一个文件，不做长时间连续录制
- 只录对方：只捕获豆包生成的 TTS 语音，不含麦克风/自己说话的声音
- 无感运行：通话中后台自动完成，不需要手动点录制

## 安装

1. 设备需要已 Root，并安装 LSPosed / EdXposed 等 Xposed 框架
2. 安装 `DoubaoTtsSaver_v11.apk`
3. 在 LSPosed 中启用本模块，作用域勾选 **豆包（com.larus.nova）**
4. 重启豆包，正常发起一次语音通话即可

> 未 Root 或未启用 Xposed 时模块不会生效。

## 文件说明

| 文件 | 说明 |
| --- | --- |
| `DoubaoTtsSaver_v11.apk` | 已签名安装包（versionCode 11 / versionName 11.0） |
| `source/` | 模块源码 |

源码结构：

```
source/
├── app/src/main/AndroidManifest.xml      # 模块声明（Xposed 元数据、作用域）
├── app/src/main/java/com/operit/ttssaver/
│   ├── DoubaoHook.java                   # 入口，Hook 豆包语音 SDK，捕获 TTS 数据
│   ├── WavWriter.java                    # PCM 数据逐段写成 WAV
│   ├── MainActivity.java                 # 模块说明界面
│   └── L.java                            # 日志工具
├── app/src/main/res/values/xposed_scope.xml  # 作用域：com.larus.nova
├── libs/jump3r.jar                       # MP3 编解码
├── libs/xposed-api.jar                   # Xposed API
├── test/                                 # 音频格式相关的测试代码
└── xposed_init                           # Xposed 入口类声明
```

- Xposed 入口类：`com.operit.ttssaver.DoubaoHook`
- 作用域包名：`com.larus.nova`
- 输出：逐段 WAV 音频文件

## 校验

```
SHA-256  DoubaoTtsSaver_v11.apk
161e345dd489c596375dd015b53c64b4ebcaa53971968e87583b259d300725a1
```

## 免责声明

仅供个人学习与自用（保存自己与豆包的通话语音）。请勿用于传播他人语音或任何侵权用途。
