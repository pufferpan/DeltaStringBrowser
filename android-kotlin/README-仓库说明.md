# 安卓端（Kotlin / Jetpack Compose）

这是「三角洲行动 StringTables 归类浏览器」的安卓端原生实现（Kotlin + Compose Material 3），
Windows 端在仓库根目录。

## 本仓库里没有包含什么（需要你自己提供）

- `app/src/main/assets/strings.db`：**游戏本地化数据不随仓库分发**（版权归原厂商）。
  请用 Windows 端导出你自己的 StringTables，然后在安卓端用「导入」功能导入（支持文件夹 / ZIP 包），
  导入后会重新生成本地数据库。
- `keystore.properties` + `keystore/release.jks`：签名密钥属于个人凭据，不入库。
  想签 release 包就自己生成一个 keystore，并在 `keystore.properties` 里写：

  ```
  storeFile=keystore/release.jks
  storePassword=你的密码
  keyAlias=你的别名
  keyPassword=你的密码
  ```

  `app/build.gradle.kts` 里是**可选**读取（`hasSigning`）：没有这个文件也能正常构建 debug 包。

## 构建

```bash
./gradlew :app:assembleDebug      # 产物在 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease    # 需要上面的 keystore.properties
```