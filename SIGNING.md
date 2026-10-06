# 发布签名配置指南

本文档说明如何为 SQLite Viewer 配置正式发布签名密钥。

## 为什么需要签名密钥？

- **唯一标识**：签名密钥唯一标识应用的发布者
- **安全更新**：只有使用相同密钥签名的 APK 才能覆盖安装
- **完整性保证**：防止 APK 被篡改

⚠️ **重要**：签名密钥一旦丢失，将无法发布应用更新。务必妥善备份！

## 配置步骤

### 1. 生成密钥库文件

在项目根目录**外**的安全位置执行：

```bash
# Linux/macOS
keytool -genkey -v -keystore ~/keystore/sqliteviewer-release.jks \
  -alias sqliteviewer \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storetype JKS

# Windows
keytool -genkey -v -keystore %USERPROFILE%\keystore\sqliteviewer-release.jks ^
  -alias sqliteviewer ^
  -keyalg RSA ^
  -keysize 2048 ^
  -validity 10000 ^
  -storetype JKS
```

**参数说明：**
- `-keystore`: 密钥库文件保存路径
- `-alias`: 密钥别名（建议使用 `sqliteviewer`）
- `-keyalg RSA -keysize 2048`: 使用 RSA 2048 位加密
- `-validity 10000`: 有效期 10000 天（约 27 年）
- `-storetype JKS`: 密钥库类型

**需要输入的信息：**
1. 密钥库密码（6 个字符以上，务必记住！）
2. 密钥密码（建议与密钥库密码相同）
3. 姓名或组织名称
4. 组织单位（可选）
5. 组织名称（可选）
6. 城市或区域
7. 省份或州
8. 国家代码（如 CN）

### 2. 创建 keystore.properties

在项目根目录创建 `keystore.properties` 文件（使用 `keystore.properties.example` 作为模板）：

```properties
storeFile=C:/Users/YourName/keystore/sqliteviewer-release.jks
storePassword=your_keystore_password
keyAlias=sqliteviewer
keyPassword=your_key_password
```

**注意：**
- Windows 路径使用正斜杠 `/` 或双反斜杠 `\\`
- 绝对路径或相对项目根目录的路径均可
- **此文件已加入 .gitignore，不会被提交到 Git**

### 3. 验证配置

```bash
# 构建签名的 Release APK
./gradlew assembleRelease

# 检查 APK 签名信息
keytool -printcert -jarfile app/build/outputs/apk/release/SQLiteViewer.apk
```

如果看到你的证书信息（而不是 Android Debug），说明配置成功。

### 4. 备份密钥库

**强烈建议：**
1. 将 `.jks` 文件备份到多个安全位置（加密 U 盘、云盘加密目录等）
2. 将密钥信息（密码、别名）保存到密码管理器
3. 不要将密钥库提交到 Git 仓库或公开位置

## 使用方式

### 本地构建

如果配置了 `keystore.properties`，执行以下命令会使用正式签名：

```bash
./gradlew assembleRelease
```

### CI/CD 构建（GitHub Actions）

在 GitHub Actions 中使用签名密钥：

1. 将密钥库文件转为 Base64：
   ```bash
   base64 -i ~/keystore/sqliteviewer-release.jks | pbcopy  # macOS
   base64 ~/keystore/sqliteviewer-release.jks              # Linux
   certutil -encode sqliteviewer-release.jks out.txt       # Windows
   ```

2. 在 GitHub 仓库设置 Secrets：
   - `KEYSTORE_FILE`: Base64 编码的密钥库内容
   - `KEYSTORE_PASSWORD`: 密钥库密码
   - `KEY_ALIAS`: 密钥别名
   - `KEY_PASSWORD`: 密钥密码

3. 在 workflow 中解码并使用：
   ```yaml
   - name: Decode Keystore
     run: |
       echo "${{ secrets.KEYSTORE_FILE }}" | base64 -d > release.jks

   - name: Build Release APK
     env:
       KEYSTORE_FILE: release.jks
       KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
       KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
       KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
     run: ./gradlew assembleRelease
   ```

## 无密钥库时的行为

如果项目根目录没有 `keystore.properties` 文件：
- Release 构建会自动使用 **debug 签名**（用于测试）
- APK 可以正常构建，但使用的是不安全的调试密钥
- 提示信息会出现在 README 中

这种机制允许：
- 新贡献者无需配置密钥即可构建测试版本
- 维护者使用正式密钥构建发布版本

## 故障排除

### 错误：Keystore was tampered with, or password was incorrect

**原因**：密钥库密码错误

**解决**：检查 `keystore.properties` 中的 `storePassword` 是否正确

### 错误：Key alias 'xxx' does not exist

**原因**：密钥别名不存在

**解决**：使用以下命令查看密钥库中的别名：
```bash
keytool -list -v -keystore ~/keystore/sqliteviewer-release.jks
```

### 错误：Failed to read key xxx from store

**原因**：密钥密码错误

**解决**：检查 `keystore.properties` 中的 `keyPassword` 是否正确

## 安全最佳实践

✅ **应该做：**
- 将密钥库保存在项目目录外的安全位置
- 使用强密码（混合大小写、数字、特殊字符）
- 定期备份密钥库到多个位置
- 使用密码管理器保存密钥信息

❌ **不应该做：**
- 将 `.jks` 文件提交到 Git
- 将密码写在代码或公开文档中
- 与他人共享密钥库文件
- 使用弱密码或默认密码

## 密钥库信息查看

```bash
# 查看密钥库内容
keytool -list -v -keystore ~/keystore/sqliteviewer-release.jks

# 查看 APK 签名信息
keytool -printcert -jarfile app/build/outputs/apk/release/SQLiteViewer.apk

# 验证两个 APK 使用相同签名
jarsigner -verify -verbose -certs app/build/outputs/apk/release/SQLiteViewer.apk
```

## 参考资源

- [Android 官方文档：为应用签名](https://developer.android.com/studio/publish/app-signing)
- [keytool 命令参考](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
