# EhViewer Web App Phase 1: 项目骨架 + 核心 API 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 创建 Gradle 多模块项目结构，移植 ehviewer-core，实现 Spring Boot 后端核心 API，搭建 Vue 3 前端骨架，实现登录→搜索→浏览画廊详情的完整链路。

**架构：** ehviewer-core（纯 Java 库）从 Android 代码移植，ehviewer-web（Spring Boot）提供 REST API 和 WebSocket，web-frontend（Vue 3）提供浏览器 UI。三层通过 HTTP/JSON 通信。

**技术栈：** Kotlin 2.2, Java 21, Spring Boot 3.x, SQLite (xerial/sqlite-jdbc), Vue 3, Vite, TypeScript, Pinia, OkHttp 3.14.7, jsoup 1.15.4, smbj 0.12.0

---

## 文件结构总览

```
Ehviewer_CN_SXJ/
├── ehviewer-core/                          # 纯 Java 核心库
│   ├── build.gradle.kts
│   └── src/main/java/com/hippo/ehviewer/
│       ├── client/
│       │   ├── EhEngine.java               # 从 app/ 移植，替换 TextUtils/Log
│       │   ├── EhUrl.java                  # 直接复制
│       │   ├── EhConfig.java               # 替换 SharedPreferences
│       │   ├── EhFilter.java               # 替换 Log/EhDB
│       │   ├── EhRequestBuilder.java       # 从 app/ 移植
│       │   ├── EhCacheKeyFactory.java      # 从 app/ 移植
│       │   └── parser/                     # 22 个 Parser，替换 TextUtils
│       ├── data/                           # 23 个 DataModel，移除 Parcelable
│       ├── spider/
│       │   ├── SpiderQueen.java            # 替换 Context/AsyncTask/UniFile
│       │   ├── SpiderDen.java              # 替换 UniFile/BitmapFactory
│       │   └── SpiderInfo.java             # 替换 TextUtils
│       ├── network/                        # OkHttp 封装
│       ├── smb/
│       │   ├── SmbConnection.java          # 替换 TextUtils/MimeTypeMap
│       │   ├── SmbConfig.java
│       │   └── SmbSettings.java
│       └── util/
│           └── TextUtil.java               # 替代 android.text.TextUtils
│
├── ehviewer-web/                           # Spring Boot 应用
│   ├── build.gradle.kts
│   └── src/main/
│       ├── java/com/hippo/ehviewer/web/
│       │   ├── EhWebApplication.kt
│       │   ├── config/
│       │   │   ├── WebConfig.kt
│       │   │   ├── SecurityConfig.kt
│       │   │   ├── WebSocketConfig.kt
│       │   │   └── EhCoreConfigProperties.kt
│       │   ├── api/
│       │   │   ├── AuthController.kt
│       │   │   ├── GalleryController.kt
│       │   │   └── ImageProxyController.kt
│       │   ├── service/
│       │   │   ├── EhAuthService.kt
│       │   │   ├── GalleryService.kt
│       │   │   ├── ImageCacheService.kt
│       │   │   └── EncryptionService.kt
│       │   ├── entity/                     # JPA Entity (12 个)
│       │   ├── repository/                 # Spring Data JPA (11 个)
│       │   └── dto/                        # API 请求/响应 DTO
│       └── resources/
│           ├── application.yml
│           └── static/                     # Vue 构建输出
│
├── web-frontend/                           # Vue 3 前端
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/index.ts
│       ├── stores/auth.ts
│       ├── api/client.ts, auth.ts, gallery.ts
│       ├── views/LoginView.vue, HomeView.vue, GalleryDetailView.vue
│       ├── components/layout/, gallery/, common/
│       ├── composables/useWebSocket.ts, useInfiniteScroll.ts
│       └── types/index.ts
│
├── app/                                    # 原有 Android App (保留不变)
├── settings.gradle                         # include ehviewer-core, ehviewer-web
└── build.gradle                            # root project
```

---

## 任务 1：创建 Gradle 多模块项目结构

**文件：**
- 修改：`settings.gradle`
- 修改：`build.gradle` (root)
- 创建：`ehviewer-core/build.gradle.kts`
- 创建：`ehviewer-web/build.gradle.kts`
- 创建：`web-frontend/package.json`
- 创建：`web-frontend/vite.config.ts`
- 创建：`web-frontend/tsconfig.json`
- 创建：`web-frontend/index.html`
- 创建：`web-frontend/src/main.ts`
- 创建：`web-frontend/src/App.vue`

- [ ] **步骤 1：创建 ehviewer-core 目录结构**

```bash
mkdir -p ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser
mkdir -p ehviewer-core/src/main/java/com/hippo/ehviewer/client/data
mkdir -p ehviewer-core/src/main/java/com/hippo/ehviewer/spider
mkdir -p ehviewer-core/src/main/java/com/hippo/ehviewer/network
mkdir -p ehviewer-core/src/main/java/com/hippo/ehviewer/smb
mkdir -p ehviewer-core/src/main/java/com/hippo/ehviewer/util
```

- [ ] **步骤 2：创建 ehviewer-core/build.gradle.kts**

```kotlin
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:3.14.7")
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("org.ccil.cowan.tagsoup:tagsoup:1.2.1")
    implementation("com.hierynomus:smbj:0.12.0")
    implementation("com.alibaba:fastjson:1.2.83")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.9")
}
```

- [ ] **步骤 3：创建 ehviewer-web 目录结构**

```bash
mkdir -p ehviewer-web/src/main/java/com/hippo/ehviewer/web/config
mkdir -p ehviewer-web/src/main/java/com/hippo/ehviewer/web/api
mkdir -p ehviewer-web/src/main/java/com/hippo/ehviewer/web/service
mkdir -p ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity
mkdir -p ehviewer-web/src/main/java/com/hippo/ehviewer/web/repository
mkdir -p ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto
mkdir -p ehviewer-web/src/main/resources/static
```

- [ ] **步骤 4：创建 ehviewer-web/build.gradle.kts**

```kotlin
plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
}

group = "com.hippo.ehviewer"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ehviewer-core"))
    
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.6.4.Final")
    
    implementation("org.springframework.security:spring-security-messaging")
    
    runtimeOnly("org.springframework.boot:spring-boot-starter-actuator")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

- [ ] **步骤 5：修改 settings.gradle 添加新模块**

```groovy
// 在现有 settings.gradle 的 include ':app' 后添加
include ':ehviewer-core'
include ':ehviewer-web'
```

- [ ] **步骤 6：创建 ehviewer-web 启动类**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/EhWebApplication.kt
package com.hippo.ehviewer.web

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EhWebApplication

fun main(args: Array<String>) {
    runApplication<EhWebApplication>(*args)
}
```

- [ ] **步骤 7：创建 application.yml**

```yaml
# ehviewer-web/src/main/resources/application.yml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:data/ehviewer.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    open-in-view: false
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

ehviewer:
  download:
    path: ./data/downloads
    cache-path: ./data/cache
    cache-size-mb: 10240
    worker-count: 3
    download-delay: 0
    download-timeout: 60000
    max-concurrent-galleries: 3
    max-concurrent-images: 3
  smb:
    enabled: false
  security:
    session-timeout: 86400
    encryption-key-path: ./data/security.key
```

- [ ] **步骤 8：创建 web-frontend 基础文件**

```json
// web-frontend/package.json
{
  "name": "ehviewer-web-frontend",
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.5.13",
    "vue-router": "^4.5.0",
    "pinia": "^2.3.0",
    "axios": "^1.7.9",
    "@stomp/stompjs": "^7.0.0",
    "sockjs-client": "^1.6.1"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "typescript": "~5.7.2",
    "vite": "^6.0.5",
    "vue-tsc": "^2.2.0"
  }
}
```

```typescript
// web-frontend/vite.config.ts
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    outDir: '../ehviewer-web/src/main/resources/static',
  },
})
```

```json
// web-frontend/tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue"]
}
```

```html
<!-- web-frontend/index.html -->
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/x-icon" href="/favicon.ico" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>EhViewer Web</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
```

```typescript
// web-frontend/src/main.ts
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

```vue
<!-- web-frontend/src/App.vue -->
<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
</style>
```

- [ ] **步骤 9：验证构建**

运行：`./gradlew :ehviewer-core:build :ehviewer-web:build --dry-run`
预期：无报错，所有任务可执行

- [ ] **步骤 10：Commit**

```bash
git add settings.gradle build.gradle ehviewer-core/ ehviewer-web/ web-frontend/
git commit -m "feat: create Gradle multi-module project structure"
```

---

## 任务 2：移植 TextUtil 工具类

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/util/TextUtil.java`
- 创建：`ehviewer-core/src/test/java/com/hippo/ehviewer/util/TextUtilTest.java`

- [ ] **步骤 1：编写 TextUtil 测试**

```java
// ehviewer-core/src/test/java/com/hippo/ehviewer/util/TextUtilTest.java
package com.hippo.ehviewer.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class TextUtilTest {
    @Test
    public void testIsEmpty() {
        assertTrue(TextUtil.isEmpty(null));
        assertTrue(TextUtil.isEmpty(""));
        assertFalse(TextUtil.isEmpty("hello"));
    }

    @Test
    public void testEquals() {
        assertTrue(TextUtil.equals(null, null));
        assertTrue(TextUtil.equals("", ""));
        assertFalse(TextUtil.equals("a", "b"));
        assertFalse(TextUtil.equals(null, "a"));
    }

    @Test
    public void testIsBlank() {
        assertTrue(TextUtil.isBlank(null));
        assertTrue(TextUtil.isBlank(""));
        assertTrue(TextUtil.isBlank("   "));
        assertFalse(TextUtil.isBlank("hello"));
    }

    @Test
    public void testTrim() {
        assertEquals("", TextUtil.trim(null));
        assertEquals("", TextUtil.trim(""));
        assertEquals("hello", TextUtil.trim("  hello  "));
    }

    @Test
    public void testJoin() {
        assertEquals("a,b,c", TextUtil.join(",", "a", "b", "c"));
        assertEquals("a", TextUtil.join(",", "a"));
        assertEquals("", TextUtil.join(","));
    }

    @Test
    public void testIndexOf() {
        assertEquals(2, TextUtil.indexOf("hello", "llo"));
        assertEquals(-1, TextUtil.indexOf("hello", "xyz"));
        assertEquals(-1, TextUtil.indexOf(null, "xyz"));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :ehviewer-core:test --tests "com.hippo.ehviewer.util.TextUtilTest" -q`
预期：FAIL，编译错误 "cannot find symbol class TextUtil"

- [ ] **步骤 3：实现 TextUtil**

```java
// ehviewer-core/src/main/java/com/hippo/ehviewer/util/TextUtil.java
package com.hippo.ehviewer.util;

public class TextUtil {
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().equals(b.toString());
    }

    public static boolean isBlank(CharSequence str) {
        if (str == null) return true;
        return str.toString().trim().isEmpty();
    }

    public static String trim(CharSequence str) {
        return str == null ? "" : str.toString().trim();
    }

    public static String join(CharSequence delimiter, CharSequence... tokens) {
        if (tokens == null || tokens.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            if (tokens[i] != null) sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static int indexOf(CharSequence s, CharSequence target) {
        if (s == null || target == null) return -1;
        return s.toString().indexOf(target.toString());
    }

    private TextUtil() {}
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :ehviewer-core:test --tests "com.hippo.ehviewer.util.TextUtilTest" -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/util/TextUtil.java \
        ehviewer-core/src/test/java/com/hippo/ehviewer/util/TextUtilTest.java
git commit -m "feat: add TextUtil to replace android.text.TextUtils"
```

---

## 任务 3：移植 EhUrl（直接复制，无修改）

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhUrl.java`
- 源文件：`app/src/main/java/com/hippo/ehviewer/client/EhUrl.java`

- [ ] **步骤 1：复制 EhUrl.java**

```bash
cp app/src/main/java/com/hippo/ehviewer/client/EhUrl.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhUrl.java
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：BUILD SUCCESSFUL（EhUrl 无 Android 依赖）

- [ ] **步骤 3：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhUrl.java
git commit -m "feat: copy EhUrl.java to ehviewer-core (no changes needed)"
```

---

## 任务 4：移植核心 Data Model（移除 Parcelable）

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/GalleryInfo.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/GalleryDetail.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/GalleryComment.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/GalleryCommentList.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/GalleryPreview.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/GalleryTagGroup.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/Tag.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/ArchiverData.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/TorrentInfo.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/ListUrlBuilder.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/FavListUrlBuilder.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/PreviewSet.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/NormalPreviewSet.java`
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/LargePreviewSet.java`
- 源文件：`app/src/main/java/com/hippo/ehviewer/client/data/` 目录下对应文件

- [ ] **步骤 1：复制所有 Data Model 文件**

```bash
# 复制所有 data 模型文件
for f in GalleryInfo GalleryDetail GalleryComment GalleryCommentList \
         GalleryPreview GalleryTagGroup Tag ArchiverData TorrentInfo \
         ListUrlBuilder FavListUrlBuilder PreviewSet NormalPreviewSet \
         LargePreviewSet; do
    cp "app/src/main/java/com/hippo/ehviewer/client/data/${f}.java" \
       "ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/${f}.java"
done

# 复制其他 data 目录下的文件
cp app/src/main/java/com/hippo/ehviewer/client/data/*.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/
```

- [ ] **步骤 2：批量移除 Parcelable 实现**

对每个包含 `implements Parcelable` 或 `implements android.os.Parcelable` 的文件，执行以下替换：

```bash
# 移除 Parcelable 接口实现
find ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/ \
    -name "*.java" -exec sed -i '' \
    -e 's/implements Parcelable//' \
    -e 's/implements android.os.Parcelable//' \
    -e 's/import android.os.Parcelable;//' \
    -e 's/@Override\s*public int describeContents().*//g' \
    -e '/public void writeToParcel/,/}/d' \
    -e '/public static final Creator.*Creator.*=.*new Creator/,/};/d' \
    {} \;
```

- [ ] **步骤 3：修复编译错误**

检查复制后的文件，移除所有 `android.*` 导入：
```bash
find ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/ \
    -name "*.java" -exec sed -i '' \
    -e 's/import android\.[^;]*;//' \
    {} \;
```

- [ ] **步骤 4：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：如有编译错误，逐一修复

- [ ] **步骤 5：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/client/data/
git commit -m "feat: copy data models to ehviewer-core, remove Parcelable"
```

---

## 任务 5：移植 22 个 HTML Parser

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser/` (22 个文件)
- 源文件：`app/src/main/java/com/hippo/ehviewer/client/parser/` 目录下所有文件

- [ ] **步骤 1：复制所有 Parser 文件**

```bash
cp app/src/main/java/com/hippo/ehviewer/client/parser/*.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser/
```

- [ ] **步骤 2：批量替换 TextUtils 引用**

```bash
# 替换 android.text.TextUtils 为 TextUtil
find ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser/ \
    -name "*.java" -exec sed -i '' \
    -e 's/import android.text.TextUtils;/import com.hippo.ehviewer.util.TextUtil;/' \
    -e 's/TextUtils\./TextUtil./g' \
    {} \;
```

- [ ] **步骤 3：移除 Android Log 引用**

```bash
# 移除 android.util.Log 导入，替换为 SLF4J
find ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser/ \
    -name "*.java" -exec sed -i '' \
    -e 's/import android.util.Log;/import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;/' \
    -e 's/Log\.d(TAG,/logger.debug(/g' \
    -e 's/Log\.e(TAG,/logger.error(/g' \
    -e 's/Log\.w(TAG,/logger.warn(/g' \
    -e 's/Log\.i(TAG,/logger.info(/g' \
    {} \;

# 在每个 Parser 类中添加 Logger 字段
for f in ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser/*.java; do
    classname=$(basename "$f" .java)
    sed -i '' "/^public class ${classname}/a\\
\\    private static final Logger logger = LoggerFactory.getLogger(${classname}.class);" "$f"
done
```

- [ ] **步骤 4：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：如有编译错误，逐一修复（主要检查 TextUtil 方法签名是否匹配）

- [ ] **步骤 5：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/client/parser/
git commit -m "feat: copy 22 HTML parsers to ehviewer-core, replace TextUtils/Log"
```

---

## 任务 6：移植 EhEngine（替换 TextUtils/Log）

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhEngine.java`
- 源文件：`app/src/main/java/com/hippo/ehviewer/client/EhEngine.java`

- [ ] **步骤 1：复制 EhEngine.java**

```bash
cp app/src/main/java/com/hippo/ehviewer/client/EhEngine.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhEngine.java
```

- [ ] **步骤 2：替换 Android 依赖**

```bash
# 替换 TextUtils
sed -i '' \
    -e 's/import android.text.TextUtils;/import com.hippo.ehviewer.util.TextUtil;/' \
    -e 's/TextUtils\./TextUtil./g' \
    ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhEngine.java

# 替换 Log
sed -i '' \
    -e 's/import android.util.Log;/import org.slf4j.Logger;\nimport org.slf4j.LoggerFactory;/' \
    -e '/^public class EhEngine/a\\
\\    private static final Logger logger = LoggerFactory.getLogger(EhEngine.class);' \
    -e 's/Log\.d(TAG,/logger.debug(/g' \
    -e 's/Log\.e(TAG,/logger.error(/g' \
    ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhEngine.java
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/client/EhEngine.java
git commit -m "feat: copy EhEngine.java to ehviewer-core, replace TextUtils/Log"
```

---

## 任务 7：创建 EhCoreConfig 配置类

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/EhCoreConfig.java`

- [ ] **步骤 1：创建 EhCoreConfig**

```java
// ehviewer-core/src/main/java/com/hippo/ehviewer/EhCoreConfig.java
package com.hippo.ehviewer;

public class EhCoreConfig {
    private String downloadPath = "./data/downloads";
    private String cachePath = "./data/cache";
    private long cacheSizeBytes = 10L * 1024 * 1024 * 1024; // 10GB
    private int workerCount = 3;
    private int downloadDelay = 0;
    private int downloadTimeout = 60000;
    private int maxConcurrentGalleries = 3;
    private int maxConcurrentImages = 3;
    private boolean enableLogging = true;

    public String getDownloadPath() { return downloadPath; }
    public void setDownloadPath(String downloadPath) { this.downloadPath = downloadPath; }
    public String getCachePath() { return cachePath; }
    public void setCachePath(String cachePath) { this.cachePath = cachePath; }
    public long getCacheSizeBytes() { return cacheSizeBytes; }
    public void setCacheSizeBytes(long cacheSizeBytes) { this.cacheSizeBytes = cacheSizeBytes; }
    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int workerCount) { this.workerCount = workerCount; }
    public int getDownloadDelay() { return downloadDelay; }
    public void setDownloadDelay(int downloadDelay) { this.downloadDelay = downloadDelay; }
    public int getDownloadTimeout() { return downloadTimeout; }
    public void setDownloadTimeout(int downloadTimeout) { this.downloadTimeout = downloadTimeout; }
    public int getMaxConcurrentGalleries() { return maxConcurrentGalleries; }
    public void setMaxConcurrentGalleries(int maxConcurrentGalleries) { this.maxConcurrentGalleries = maxConcurrentGalleries; }
    public int getMaxConcurrentImages() { return maxConcurrentImages; }
    public void setMaxConcurrentImages(int maxConcurrentImages) { this.maxConcurrentImages = maxConcurrentImages; }
    public boolean isEnableLogging() { return enableLogging; }
    public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
}
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/EhCoreConfig.java
git commit -m "feat: add EhCoreConfig for dependency injection"
```

---

## 任务 8：移植 EhCookieStore（替换 SharedPreferences）

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/network/EhCookieStore.java`
- 源文件：`app/src/main/java/com/hippo/ehviewer/network/EhCookieStore.java`

- [ ] **步骤 1：复制并适配 EhCookieStore**

```bash
cp app/src/main/java/com/hippo/ehviewer/network/EhCookieStore.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/network/EhCookieStore.java
```

- [ ] **步骤 2：重写为内存 Map 存储**

由于原版使用 Android SQLite CookieDatabase，Web 版改为内存存储 + 持久化到 SQLite（由 ehviewer-web 层处理）：

```java
// 替换原有实现，使用 ConcurrentHashMap 存储
package com.hippo.ehviewer.network;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EhCookieStore implements CookieJar {
    private final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        cookieStore.put(url.host(), cookies);
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        List<Cookie> cookies = cookieStore.get(url.host());
        return cookies != null ? new ArrayList<>(cookies) : Collections.emptyList();
    }

    public void addCookie(Cookie cookie) {
        String host = cookie.domain().startsWith(".") 
            ? cookie.domain().substring(1) : cookie.domain();
        List<Cookie> cookies = cookieStore.computeIfAbsent(host, k -> new ArrayList<>());
        cookies.removeIf(c -> c.name().equals(cookie.name()));
        cookies.add(cookie);
    }

    public void clear() {
        cookieStore.clear();
    }

    public Map<String, List<Cookie>> getAll() {
        return new HashMap<>(cookieStore);
    }
}
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/network/EhCookieStore.java
git commit -m "feat: adapt EhCookieStore for web (in-memory CookieJar)"
```

---

## 任务 9：移植 SmbConnection（替换 TextUtils/MimeTypeMap）

**文件：**
- 创建：`ehviewer-core/src/main/java/com/hippo/ehviewer/smb/SmbConnection.java`
- 源文件：`app/src/main/java/com/hippo/ehviewer/smb/SmbConnection.java`

- [ ] **步骤 1：复制 SmbConnection**

```bash
cp app/src/main/java/com/hippo/ehviewer/smb/SmbConnection.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/smb/SmbConnection.java
cp app/src/main/java/com/hippo/ehviewer/smb/SmbConfig.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/smb/SmbConfig.java
cp app/src/main/java/com/hippo/ehviewer/smb/SmbSettings.java \
   ehviewer-core/src/main/java/com/hippo/ehviewer/smb/SmbSettings.java
```

- [ ] **步骤 2：替换 Android 依赖**

```bash
# 替换 TextUtils
sed -i '' \
    -e 's/import android.text.TextUtils;/import com.hippo.ehviewer.util.TextUtil;/' \
    -e 's/TextUtils\./TextUtil./g' \
    ehviewer-core/src/main/java/com/hippo/ehviewer/smb/SmbConnection.java

# 替换 MimeTypeMap
sed -i '' \
    -e 's/import android.webkit.MimeTypeMap;//' \
    ehviewer-core/src/main/java/com/hippo/ehviewer/smb/SmbConnection.java
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :ehviewer-core:compileJava -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add ehviewer-core/src/main/java/com/hippo/ehviewer/smb/
git commit -m "feat: copy SmbConnection to ehviewer-core, replace Android deps"
```

---

## 任务 10：验证 ehviewer-core 完整编译

**文件：** 无新文件

- [ ] **步骤 1：完整编译 ehviewer-core**

运行：`./gradlew :ehviewer-core:compileJava`
预期：BUILD SUCCESSFUL，所有 Java 文件编译通过

- [ ] **步骤 2：运行测试**

运行：`./gradlew :ehviewer-core:test`
预期：所有测试通过

- [ ] **步骤 3：Commit（如有修复）**

```bash
git add -A
git commit -m "fix: resolve compilation issues in ehviewer-core"
```

---

## 任务 11：创建 JPA Entity（11 个）

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/GalleryInfoBase.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/DownloadInfoEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/DownloadLabelEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/HistoryInfoEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/LocalFavoriteInfoEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/BookmarkInfoEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/QuickSearchEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/FilterEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/GalleryTagsEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/BlackListEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/DownloadDirnameEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/SmbConfigEntity.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/AuthConfigEntity.kt`

- [ ] **步骤 1：创建 GalleryInfoBase**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/GalleryInfoBase.kt
package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@MappedSuperclass
abstract class GalleryInfoBase {
    @Id
    @Column(name = "GID")
    var gid: Long = 0
    var token: String = ""
    var title: String = ""
    var titleJpn: String = ""
    var thumb: String = ""
    var category: String = ""
    var posted: String = ""
    var uploader: String = ""
    var rating: Float = 0f
    var simpleLanguage: String = ""
}
```

- [ ] **步骤 2：创建 DownloadInfoEntity**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/DownloadInfoEntity.kt
package com.hippo.ehviewer.web.entity

import jakarta.persistence.*

@Entity
@Table(name = "DOWNLOADS")
class DownloadInfoEntity : GalleryInfoBase() {
    @Id
    @Column(name = "GID")
    override var gid: Long = 0
    var state: Int = 0
    var legacy: Boolean = false
    var time: Long = 0
    var label: String = ""
    @Transient var downloaded: Int = 0
    @Transient var total: Int = 0
    @Transient var speed: Long = 0
}
```

- [ ] **步骤 3：创建其余 Entity（按规格文档中的定义）**

```kotlin
// DownloadLabelEntity.kt
@Entity
@Table(name = "DOWNLOAD_LABELS")
class DownloadLabelEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var label: String = ""
    var time: Long = 0
}

// HistoryInfoEntity.kt
@Entity
@Table(name = "HISTORY")
class HistoryInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var mode: Int = 0
    var time: Long = 0
}

// LocalFavoriteInfoEntity.kt
@Entity
@Table(name = "LOCAL_FAVORITES")
class LocalFavoriteInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var time: Long = 0
}

// BookmarkInfoEntity.kt
@Entity
@Table(name = "BOOKMARKS")
class BookmarkInfoEntity : GalleryInfoBase() {
    @Id @Column(name = "GID")
    override var gid: Long = 0
    var page: Int = 0
    var time: Long = 0
}

// QuickSearchEntity.kt
@Entity
@Table(name = "QUICK_SEARCH")
class QuickSearchEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var name: String = ""
    var mode: Int = 0
    var category: String = ""
    var keyword: String = ""
    var advanceSearch: Boolean = false
    var minRating: Int = 0
    var pageFrom: Int = 0
    var pageTo: Int = 0
    var time: Long = 0
}

// FilterEntity.kt
@Entity
@Table(name = "FILTER")
class FilterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var mode: Int = 0
    var text: String = ""
    var enable: Boolean = true
}

// GalleryTagsEntity.kt
@Entity
@Table(name = "GALLERY_TAGS")
class GalleryTagsEntity {
    @Id @Column(name = "GID")
    var gid: Long = 0
    var rows: Int = 0
    var artist: String = ""
    var cosplayer: String = ""
    var character: String = ""
    var female: String = ""
    var group: String = ""
    var language: String = ""
    var male: String = ""
    var misc: String = ""
    var mixed: String = ""
    var other: String = ""
    var parody: String = ""
    var reclass: String = ""
    var createTime: Long = 0
    var updateTime: Long = 0
}

// BlackListEntity.kt
@Entity
@Table(name = "BLACK_LIST")
class BlackListEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var badgayname: String = ""
    var reason: String = ""
    var angrywith: String = ""
    var addTime: Long = 0
    var mode: Int = 0
}

// DownloadDirnameEntity.kt
@Entity
@Table(name = "DOWNLOAD_DIRNAME")
class DownloadDirnameEntity {
    @Id @Column(name = "GID")
    var gid: Long = 0
    var dirname: String = ""
}

// SmbConfigEntity.kt
@Entity
@Table(name = "SMB_CONFIG")
class SmbConfigEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var serverAddress: String = ""
    var sharedFolder: String = ""
    var username: String = ""
    var password: String = ""
    var domain: String = "WORKGROUP"
    var enabled: Boolean = false
}

// AuthConfigEntity.kt
@Entity
@Table(name = "AUTH_CONFIG")
class AuthConfigEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
    var authType: String = ""
    var cookieValue: String = ""
    var username: String = ""
    var password: String = ""
    var apiKey: String = ""
    var createdAt: Long = 0
    var updatedAt: Long = 0
}
```

- [ ] **步骤 4：验证编译**

运行：`./gradlew :ehviewer-web:compileKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/entity/
git commit -m "feat: add JPA entities for SQLite database"
```

---

## 任务 12：创建 Spring Data JPA Repository

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/repository/` (11 个文件)

- [ ] **步骤 1：创建所有 Repository 接口**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/repository/DownloadInfoRepository.kt
package com.hippo.ehviewer.web.repository

import com.hippo.ehviewer.web.entity.DownloadInfoEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DownloadInfoRepository : JpaRepository<DownloadInfoEntity, Long> {
    fun findByState(state: Int): List<DownloadInfoEntity>
    fun findByLabel(label: String): List<DownloadInfoEntity>
}

// DownloadLabelRepository.kt
interface DownloadLabelRepository : JpaRepository<DownloadLabelEntity, Long> {
    fun findByLabel(label: String): DownloadLabelEntity?
}

// HistoryInfoRepository.kt
interface HistoryInfoRepository : JpaRepository<HistoryInfoEntity, Long> {
    fun findAllByOrderByTimeDesc(): List<HistoryInfoEntity>
}

// LocalFavoriteInfoRepository.kt
interface LocalFavoriteInfoRepository : JpaRepository<LocalFavoriteInfoEntity, Long> {
    fun findAllByOrderByTimeDesc(): List<LocalFavoriteInfoEntity>
}

// BookmarkInfoRepository.kt
interface BookmarkInfoRepository : JpaRepository<BookmarkInfoEntity, Long> {
    fun findByGid(gid: Long): BookmarkInfoEntity?
}

// QuickSearchRepository.kt
interface QuickSearchRepository : JpaRepository<QuickSearchEntity, Long> {
    fun findAllByOrderByTimeDesc(): List<QuickSearchEntity>
}

// FilterRepository.kt
interface FilterRepository : JpaRepository<FilterEntity, Long> {
    fun findByEnable(enable: Boolean): List<FilterEntity>
}

// GalleryTagsRepository.kt
interface GalleryTagsRepository : JpaRepository<GalleryTagsEntity, Long> {
    fun findByGid(gid: Long): GalleryTagsEntity?
}

// BlackListRepository.kt
interface BlackListRepository : JpaRepository<BlackListEntity, Long> {
    fun findByMode(mode: Int): List<BlackListEntity>
}

// DownloadDirnameRepository.kt
interface DownloadDirnameRepository : JpaRepository<DownloadDirnameEntity, Long> {
    fun findByGid(gid: Long): DownloadDirnameEntity?
}

// SmbConfigRepository.kt
interface SmbConfigRepository : JpaRepository<SmbConfigEntity, Long> {
    fun findByEnabled(enabled: Boolean): List<SmbConfigEntity>
}

// AuthConfigRepository.kt
interface AuthConfigRepository : JpaRepository<AuthConfigEntity, Long> {
    fun findFirstByOrderByUpdatedAtDesc(): AuthConfigEntity?
}
```

- [ ] **步骤 2：验证编译**

运行：`./gradlew :ehviewer-web:compileKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/repository/
git commit -m "feat: add Spring Data JPA repositories"
```

---

## 任务 13：创建 Spring Boot 配置类

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/WebConfig.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/SecurityConfig.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/WebSocketConfig.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/EhCoreConfigProperties.kt`

- [ ] **步骤 1：创建 WebConfig**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/WebConfig.kt
package com.hippo.ehviewer.web.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE")
            .allowedHeaders("*")
    }
}
```

- [ ] **步骤 2：创建 SecurityConfig**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/SecurityConfig.kt
package com.hippo.ehviewer.web.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                it.maximumSessions(1)
            }
            .authorizeHttpRequests {
                it.requestMatchers("/api/v1/auth/**").permitAll()
                it.requestMatchers("/ws/**").permitAll()
                it.requestMatchers("/", "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico", "/*.png").permitAll()
                it.requestMatchers("/**").authenticated()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
        return http.build()
    }
}
```

- [ ] **步骤 3：创建 WebSocketConfig**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/WebSocketConfig.kt
package com.hippo.ehviewer.web.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/progress")
            .setAllowedOrigins("*")
            .withSockJS()
    }
}
```

- [ ] **步骤 4：创建 EhCoreConfigProperties**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/EhCoreConfigProperties.kt
package com.hippo.ehviewer.web.config

import com.hippo.ehviewer.EhCoreConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "ehviewer")
class EhCoreConfigProperties {
    var download = DownloadProperties()
    var cache = CacheProperties()
    var smb = SmbProperties()
    var security = SecurityProperties()

    class DownloadProperties {
        var path: String = "./data/downloads"
        var cachePath: String = "./data/cache"
        var workerCount: Int = 3
        var downloadDelay: Int = 0
        var downloadTimeout: Int = 60000
        var maxConcurrentGalleries: Int = 3
        var maxConcurrentImages: Int = 3
    }

    class CacheProperties {
        var path: String = "./data/cache"
        var sizeMb: Int = 10240
        var thumbnailSizeMb: Int = 1024
    }

    class SmbProperties {
        var enabled: Boolean = false
    }

    class SecurityProperties {
        var sessionTimeout: Int = 86400
        var encryptionKeyPath: String = "./data/security.key"
    }

    @Bean
    fun ehCoreConfig(): EhCoreConfig {
        val config = EhCoreConfig()
        config.downloadPath = download.path
        config.cachePath = cache.path
        config.cacheSizeBytes = cache.sizeMb.toLong() * 1024 * 1024
        config.workerCount = download.workerCount
        config.downloadDelay = download.downloadDelay
        config.downloadTimeout = download.downloadTimeout
        config.maxConcurrentGalleries = download.maxConcurrentGalleries
        config.maxConcurrentImages = download.maxConcurrentImages
        return config
    }
}
```

- [ ] **步骤 5：验证编译**

运行：`./gradlew :ehviewer-web:compileKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/config/
git commit -m "feat: add Spring Boot configuration classes"
```

---

## 任务 14：创建认证 Service 和 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/EhAuthService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/EncryptionService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/AuthController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/AuthDto.kt`

- [ ] **步骤 1：创建 DTO**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/AuthDto.kt
package com.hippo.ehviewer.web.dto

data class CookieLoginRequest(val cookie: String)
data class AccountLoginRequest(val username: String, val password: String)
data class ApiKeyLoginRequest(val apiKey: String)
data class AuthResponse(val success: Boolean, val message: String, val profile: UserProfile? = null)
data class UserProfile(val userId: String, val username: String, val avatarUrl: String?)
data class ApiResponse<T>(val code: Int, val message: String, val data: T?)
```

- [ ] **步骤 2：创建 EncryptionService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/EncryptionService.kt
package com.hippo.ehviewer.web.service

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService {
    private val key: SecretKey = loadOrGenerateKey()

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(cipherText: String): String {
        val bytes = Base64.getDecoder().decode(cipherText)
        val iv = bytes.sliceArray(0..11)
        val encrypted = bytes.sliceArray(12 until bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }

    private fun loadOrGenerateKey(): SecretKey {
        val keyFile = java.io.File("data/security.key")
        if (keyFile.exists()) {
            val bytes = keyFile.readBytes()
            return SecretKeySpec(bytes, "AES")
        }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(bytes)
        return SecretKeySpec(bytes, "AES")
    }
}
```

- [ ] **步骤 3：创建 EhAuthService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/EhAuthService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.network.EhCookieStore
import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.entity.AuthConfigEntity
import com.hippo.ehviewer.web.repository.AuthConfigRepository
import okhttp3.OkHttpClient
import org.springframework.stereotype.Service
import java.util.*

@Service
class EhAuthService(
    private val authConfigRepository: AuthConfigRepository,
    private val encryptionService: EncryptionService
) {
    private val cookieStore = EhCookieStore()
    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieStore)
        .build()

    fun loginByCookie(cookie: String): AuthResponse {
        return try {
            // 解析并存储 Cookie
            parseAndStoreCookies(cookie)
            // 验证登录
            val profile = EhEngine.getProfile(httpClient)
            saveAuthConfig("cookie", cookie = cookie)
            AuthResponse(true, "登录成功", profile)
        } catch (e: Exception) {
            AuthResponse(false, "登录失败: ${e.message}")
        }
    }

    fun loginByAccount(username: String, password: String): AuthResponse {
        return try {
            val result = EhEngine.signIn(httpClient, username, password)
            if (result) {
                val profile = EhEngine.getProfile(httpClient)
                saveAuthConfig("account", username = username, password = password)
                AuthResponse(true, "登录成功", profile)
            } else {
                AuthResponse(false, "登录失败")
            }
        } catch (e: Exception) {
            AuthResponse(false, "登录失败: ${e.message}")
        }
    }

    fun loginByApiKey(apiKey: String): AuthResponse {
        return try {
            // API Key 通过 URL 参数传递
            val profile = EhEngine.getProfile(httpClient)
            saveAuthConfig("apikey", apiKey = apiKey)
            AuthResponse(true, "登录成功", profile)
        } catch (e: Exception) {
            AuthResponse(false, "登录失败: ${e.message}")
        }
    }

    fun getProfile(): UserProfile? {
        return try {
            EhEngine.getProfile(httpClient)
        } catch (e: Exception) {
            null
        }
    }

    fun logout() {
        cookieStore.clear()
    }

    fun getCookieStore(): EhCookieStore = cookieStore

    private fun parseAndStoreCookies(cookieString: String) {
        cookieString.split(";").forEach { pair ->
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2) {
                val cookie = okhttp3.Cookie.Builder()
                    .domain("e-hentai.org")
                    .path("/")
                    .name(parts[0].trim())
                    .value(parts[1].trim())
                    .build()
                cookieStore.addCookie(cookie)
            }
        }
    }

    private fun saveAuthConfig(type: String, cookie: String = "", username: String = "", password: String = "", apiKey: String = "") {
        val entity = AuthConfigEntity().apply {
            authType = type
            cookieValue = if (cookie.isNotEmpty()) encryptionService.encrypt(cookie) else ""
            this.username = username
            this.password = if (password.isNotEmpty()) encryptionService.encrypt(password) else ""
            this.apiKey = if (apiKey.isNotEmpty()) encryptionService.encrypt(apiKey) else ""
            createdAt = System.currentTimeMillis()
            updatedAt = System.currentTimeMillis()
        }
        authConfigRepository.save(entity)
    }
}
```

- [ ] **步骤 4：创建 AuthController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/AuthController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.EhAuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: EhAuthService) {

    @PostMapping("/cookie")
    fun loginByCookie(@RequestBody request: CookieLoginRequest): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.loginByCookie(request.cookie)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @PostMapping("/account")
    fun loginByAccount(@RequestBody request: AccountLoginRequest): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.loginByAccount(request.username, request.password)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @PostMapping("/apikey")
    fun loginByApiKey(@RequestBody request: ApiKeyLoginRequest): ResponseEntity<ApiResponse<AuthResponse>> {
        val result = authService.loginByApiKey(request.apiKey)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @GetMapping("/profile")
    fun getProfile(): ResponseEntity<ApiResponse<UserProfile?>> {
        val profile = authService.getProfile()
        return ResponseEntity.ok(ApiResponse(0, "success", profile))
    }

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val profile = authService.getProfile()
        val isLoggedIn = profile != null
        return ResponseEntity.ok(ApiResponse(0, "success", mapOf("loggedIn" to isLoggedIn, "profile" to (profile ?: emptyMap<String, Any>()))))
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<ApiResponse<Unit>> {
        authService.logout()
        return ResponseEntity.ok(ApiResponse(0, "success", null))
    }
}
```

- [ ] **步骤 5：验证编译**

运行：`./gradlew :ehviewer-web:compileKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 6：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/AuthController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/AuthDto.kt
git commit -m "feat: add authentication service and API (cookie/account/apikey)"
```

---

## 任务 15：创建画廊 Service 和 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/GalleryService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/GalleryController.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/GalleryDto.kt`

- [ ] **步骤 1：创建 GalleryDto**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/GalleryDto.kt
package com.hippo.ehviewer.web.dto

data class GalleryListRequest(
    val keyword: String? = null,
    val category: String? = null,
    val page: Int = 1,
    val sort: String? = null
)

data class GalleryListItem(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: String,
    val rating: Float,
    val simpleLanguage: String,
    val uploader: String?,
    val posted: String?
)

data class GalleryListResponse(
    val galleries: List<GalleryListItem>,
    val totalPages: Int,
    val currentPage: Int
)

data class GalleryDetailResponse(
    val gid: Long,
    val token: String,
    val title: String,
    val titleJpn: String,
    val thumb: String,
    val category: String,
    val rating: Float,
    val uploader: String?,
    val posted: String?,
    val simpleLanguage: String?,
    val pageCount: Int,
    val previewPages: Int,
    val previewSet: PreviewSetResponse?,
    val tags: List<TagGroup>,
    val comments: List<CommentItem>,
    val torrentCount: Int,
    val archiveCount: Int
)

data class PreviewSetResponse(
    val type: String,
    val images: List<PreviewImage>
)

data class PreviewImage(
    val index: Int,
    val imageUrl: String,
    val thumbUrl: String
)

data class TagGroup(
    val namespace: String,
    val tags: List<String>
)

data class CommentItem(
    val id: Long,
    val uploader: String,
    val comment: String,
    val time: String,
    val score: Int
)

data class GalleryPageResponse(
    val gid: Long,
    val page: Int,
    val imageUrl: String,
    val imageSize: String,
    val imageWidth: Int,
    val imageHeight: Int
)
```

- [ ] **步骤 2：创建 GalleryService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/GalleryService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.network.EhCookieStore
import com.hippo.ehviewer.web.dto.*
import okhttp3.OkHttpClient
import org.springframework.stereotype.Service

@Service
class GalleryService(private val authService: EhAuthService) {

    private fun getHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(authService.getCookieStore())
            .build()
    }

    fun searchGallery(request: GalleryListRequest): GalleryListResponse {
        val client = getHttpClient()
        val result = EhEngine.getGalleryList(
            client,
            request.keyword ?: "",
            request.page,
            request.category
        )
        return GalleryListResponse(
            galleries = result.galleryInfoList.map { info ->
                GalleryListItem(
                    gid = info.gid,
                    token = info.token,
                    title = info.title,
                    titleJpn = info.titleJpn ?: "",
                    thumb = info.thumb ?: "",
                    category = info.category ?: "",
                    rating = info.rating,
                    simpleLanguage = info.simpleLanguage ?: "",
                    uploader = info.uploader,
                    posted = info.posted
                )
            },
            totalPages = result.pages,
            currentPage = request.page
        )
    }

    fun getGalleryDetail(gid: Long): GalleryDetailResponse {
        val client = getHttpClient()
        val detail = EhEngine.getGalleryDetail(client, gid)
        return GalleryDetailResponse(
            gid = detail.gid,
            token = detail.token,
            title = detail.title,
            titleJpn = detail.titleJpn ?: "",
            thumb = detail.thumb ?: "",
            category = detail.category ?: "",
            rating = detail.rating,
            uploader = detail.uploader,
            posted = detail.posted,
            simpleLanguage = detail.simpleLanguage,
            pageCount = detail.pageCount,
            previewPages = detail.previewPages,
            previewSet = detail.previewSet?.let { ps ->
                PreviewSetResponse(
                    type = if (ps is com.hippo.ehviewer.client.data.LargePreviewSet) "large" else "normal",
                    images = emptyList() // PreviewSet images 需要逐个解析
                )
            },
            tags = detail.tagGroups?.map { group ->
                TagGroup(namespace = group.name ?: "", tags = group.tags?.toList() ?: emptyList())
            } ?: emptyList(),
            comments = detail.comments?.map { comment ->
                CommentItem(
                    id = comment.id,
                    uploader = comment.uploader,
                    comment = comment.comment,
                    time = comment.time,
                    score = comment.score
                )
            } ?: emptyList(),
            torrentCount = detail.torrentCount,
            archiveCount = detail.archiveCount
        )
    }

    fun getPageInfo(gid: Long, page: Int): GalleryPageResponse {
        val client = getHttpClient()
        val result = EhEngine.getGalleryPageApi(client, gid, page)
        return GalleryPageResponse(
            gid = gid,
            page = page,
            imageUrl = result.imageUrl,
            imageSize = result.imageSize,
            imageWidth = result.imageWidth,
            imageHeight = result.imageHeight
        )
    }
}
```

- [ ] **步骤 3：创建 GalleryController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/GalleryController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.dto.*
import com.hippo.ehviewer.web.service.GalleryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gallery")
class GalleryController(private val galleryService: GalleryService) {

    @GetMapping("/list")
    fun getGalleryList(
        @RequestParam keyword: String?,
        @RequestParam category: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam sort: String?
    ): ResponseEntity<ApiResponse<GalleryListResponse>> {
        val request = GalleryListRequest(keyword, category, page, sort)
        val result = galleryService.searchGallery(request)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @GetMapping("/detail/{gid}")
    fun getGalleryDetail(@PathVariable gid: Long): ResponseEntity<ApiResponse<GalleryDetailResponse>> {
        val result = galleryService.getGalleryDetail(gid)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }

    @GetMapping("/page/{gid}/{page}")
    fun getGalleryPage(
        @PathVariable gid: Long,
        @PathVariable page: Int
    ): ResponseEntity<ApiResponse<GalleryPageResponse>> {
        val result = galleryService.getPageInfo(gid, page)
        return ResponseEntity.ok(ApiResponse(0, "success", result))
    }
}
```

- [ ] **步骤 4：验证编译**

运行：`./gradlew :ehviewer-web:compileKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/GalleryService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/GalleryController.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/GalleryDto.kt
git commit -m "feat: add gallery service and API (list/detail/page)"
```

---

## 任务 16：创建图片代理 Service 和 API

**文件：**
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ImageCacheService.kt`
- 创建：`ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ImageProxyController.kt`

- [ ] **步骤 1：创建 ImageCacheService**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ImageCacheService.kt
package com.hippo.ehviewer.web.service

import com.hippo.ehviewer.EhCoreConfig
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

@Service
class ImageCacheService(private val config: EhCoreConfig) {

    data class CacheEntry(val filePath: String, val size: Long, val lastAccess: Long)

    private val index = ConcurrentHashMap<String, CacheEntry>()
    private val cacheDir: File = File(config.cachePath, "images")
    private val thumbDir: File = File(config.cachePath, "thumbnails")

    init {
        cacheDir.mkdirs()
        thumbDir.mkdirs()
        loadIndex()
    }

    fun getCachedImage(gid: Long, page: Int): File? {
        val key = "${gid}_${page}"
        val entry = index[key] ?: return null
        val file = File(entry.filePath)
        if (!file.exists()) {
            index.remove(key)
            return null
        }
        // 更新访问时间
        index[key] = entry.copy(lastAccess = System.currentTimeMillis())
        return file
    }

    fun cacheImage(gid: Long, page: Int, data: ByteArray): File {
        val dir = File(cacheDir, gid.toString())
        dir.mkdirs()
        val file = File(dir, String.format("%08d.jpg", page))
        FileOutputStream(file).use { it.write(data) }
        
        val key = "${gid}_${page}"
        index[key] = CacheEntry(file.absolutePath, data.size.toLong(), System.currentTimeMillis())
        evictIfNeeded()
        return file
    }

    fun getCachedThumbnail(gid: Long, index: Int): File? {
        val dir = File(thumbDir, gid.toString())
        val file = File(dir, "${index}.jpg")
        return if (file.exists()) file else null
    }

    fun cacheThumbnail(gid: Long, index: Int, data: ByteArray): File {
        val dir = File(thumbDir, gid.toString())
        dir.mkdirs()
        val file = File(dir, "${index}.jpg")
        FileOutputStream(file).use { it.write(data) }
        return file
    }

    fun getCacheStats(): Map<String, Any> {
        val totalSize = index.values.sumOf { it.size }
        return mapOf(
            "totalFiles" to index.size,
            "totalSizeBytes" to totalSize,
            "totalSizeMb" to totalSize / (1024 * 1024),
            "maxSizeMb" to config.cacheSizeBytes / (1024 * 1024)
        )
    }

    private fun evictIfNeeded() {
        val maxSize = config.cacheSizeBytes
        var currentSize = index.values.sumOf { it.size }
        if (currentSize <= maxSize) return

        val sorted = index.entries.sortedBy { it.value.lastAccess }
        for (entry in sorted) {
            if (currentSize <= maxSize) break
            val file = File(entry.value.filePath)
            if (file.exists()) file.delete()
            currentSize -= entry.value.size
            index.remove(entry.key)
        }
    }

    private fun loadIndex() {
        cacheDir.listFiles()?.forEach { gidDir ->
            gidDir.listFiles()?.forEach { file ->
                val gid = gidDir.name.toLongOrNull() ?: return@forEach
                val page = file.nameWithoutExtension.toIntOrNull() ?: return@forEach
                val key = "${gid}_${page}"
                index[key] = CacheEntry(file.absolutePath, file.length(), file.lastModified())
            }
        }
    }
}
```

- [ ] **步骤 2：创建 ImageProxyController**

```kotlin
// ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ImageProxyController.kt
package com.hippo.ehviewer.web.api

import com.hippo.ehviewer.web.service.EhAuthService
import com.hippo.ehviewer.web.service.GalleryService
import com.hippo.ehviewer.web.service.ImageCacheService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.File

@RestController
@RequestMapping("/api/v1/gallery")
class ImageProxyController(
    private val galleryService: GalleryService,
    private val imageCacheService: ImageCacheService,
    private val authService: EhAuthService
) {

    @GetMapping("/image/{gid}/{page}")
    fun getImage(
        @PathVariable gid: Long,
        @PathVariable page: Int
    ): ResponseEntity<Any> {
        // 1. 检查本地缓存
        val cached = imageCacheService.getCachedImage(gid, page)
        if (cached != null) {
            return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .contentType(MediaType.IMAGE_JPEG)
                .body(FileSystemResource(cached))
        }

        // 2. 从 E-Hentai 下载
        return try {
            val pageInfo = galleryService.getPageInfo(gid, page)
            val client = OkHttpClient.Builder()
                .cookieJar(authService.getCookieStore())
                .build()
            val request = Request.Builder().url(pageInfo.imageUrl).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val data = response.body?.bytes() ?: return ResponseEntity.notFound().build()
                val file = imageCacheService.cacheImage(gid, page, data)
                ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(FileSystemResource(file))
            } else {
                ResponseEntity.status(response.code).build()
            }
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :ehviewer-web:compileKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add ehviewer-web/src/main/java/com/hippo/ehviewer/web/service/ImageCacheService.kt \
        ehviewer-web/src/main/java/com/hippo/ehviewer/web/api/ImageProxyController.kt
git commit -m "feat: add image cache service and proxy controller"
```

---

## 任务 17：创建 Vue 3 前端骨架

**文件：**
- 创建：`web-frontend/src/router/index.ts`
- 创建：`web-frontend/src/stores/auth.ts`
- 创建：`web-frontend/src/api/client.ts`
- 创建：`web-frontend/src/api/auth.ts`
- 创建：`web-frontend/src/api/gallery.ts`
- 创建：`web-frontend/src/types/index.ts`
- 创建：`web-frontend/src/views/LoginView.vue`
- 创建：`web-frontend/src/views/HomeView.vue`
- 创建：`web-frontend/src/views/GalleryDetailView.vue`
- 创建：`web-frontend/src/components/layout/AppHeader.vue`
- 创建：`web-frontend/src/components/gallery/GalleryGrid.vue`
- 创建：`web-frontend/src/components/gallery/GalleryCard.vue`
- 创建：`web-frontend/src/components/common/TagChip.vue`

- [ ] **步骤 1：创建 TypeScript 类型定义**

```typescript
// web-frontend/src/types/index.ts
export interface GalleryListItem {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: string
  rating: number
  simpleLanguage: string
  uploader: string | null
  posted: string | null
}

export interface GalleryListResponse {
  galleries: GalleryListItem[]
  totalPages: number
  currentPage: number
}

export interface GalleryDetailResponse {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: string
  rating: number
  uploader: string | null
  posted: string | null
  simpleLanguage: string | null
  pageCount: number
  previewPages: number
  tags: TagGroup[]
  comments: CommentItem[]
  torrentCount: number
  archiveCount: number
}

export interface TagGroup {
  namespace: string
  tags: string[]
}

export interface CommentItem {
  id: number
  uploader: string
  comment: string
  time: string
  score: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface UserProfile {
  userId: string
  username: string
  avatarUrl: string | null
}
```

- [ ] **步骤 2：创建 Router**

```typescript
// web-frontend/src/router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import GalleryDetailView from '../views/GalleryDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: true }
    },
    {
      path: '/gallery/:gid',
      name: 'gallery-detail',
      component: GalleryDetailView,
      meta: { requiresAuth: true }
    },
  ]
})

router.beforeEach((to, from, next) => {
  const isLoggedIn = localStorage.getItem('auth_token')
  if (to.meta.requiresAuth && !isLoggedIn) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
```

- [ ] **步骤 3：创建 Auth Store**

```typescript
// web-frontend/src/stores/auth.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserProfile } from '../types'

export const useAuthStore = defineStore('auth', () => {
  const isLoggedIn = ref(!!localStorage.getItem('auth_token'))
  const profile = ref<UserProfile | null>(null)

  function setLoggedIn(value: boolean) {
    isLoggedIn.value = value
    if (!value) {
      localStorage.removeItem('auth_token')
      profile.value = null
    }
  }

  function setProfile(p: UserProfile) {
    profile.value = p
  }

  return { isLoggedIn, profile, setLoggedIn, setProfile }
})
```

- [ ] **步骤 4：创建 API Client**

```typescript
// web-frontend/src/api/client.ts
import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

client.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client
```

- [ ] **步骤 5：创建 Auth API**

```typescript
// web-frontend/src/api/auth.ts
import client from './client'
import type { ApiResponse, UserProfile } from '../types'

export interface AuthResponse {
  success: boolean
  message: string
  profile: UserProfile | null
}

export const authApi = {
  loginByCookie(cookie: string) {
    return client.post<any, ApiResponse<AuthResponse>>('/auth/cookie', { cookie })
  },
  loginByAccount(username: string, password: string) {
    return client.post<any, ApiResponse<AuthResponse>>('/auth/account', { username, password })
  },
  loginByApiKey(apiKey: string) {
    return client.post<any, ApiResponse<AuthResponse>>('/auth/apikey', { apiKey })
  },
  getProfile() {
    return client.get<any, ApiResponse<UserProfile>>('/auth/profile')
  },
  getStatus() {
    return client.get<any, ApiResponse<{ loggedIn: boolean }>>('/auth/status')
  },
  logout() {
    return client.post<any, ApiResponse<void>>('/auth/logout')
  },
}
```

- [ ] **步骤 6：创建 Gallery API**

```typescript
// web-frontend/src/api/gallery.ts
import client from './client'
import type { ApiResponse, GalleryListResponse, GalleryDetailResponse } from '../types'

export const galleryApi = {
  getList(keyword?: string, category?: string, page: number = 1) {
    return client.get<any, ApiResponse<GalleryListResponse>>('/gallery/list', {
      params: { keyword, category, page }
    })
  },
  getPopular() {
    return client.get<any, ApiResponse<GalleryListResponse>>('/gallery/popular')
  },
  getDetail(gid: number) {
    return client.get<any, ApiResponse<GalleryDetailResponse>>(`/gallery/detail/${gid}`)
  },
  getImageUrl(gid: number, page: number) {
    return `/api/v1/gallery/image/${gid}/${page}`
  },
  getThumbUrl(gid: number, index: number) {
    return `/api/v1/gallery/thumb/${gid}/${index}`
  },
}
```

- [ ] **步骤 7：创建 LoginView**

```vue
<!-- web-frontend/src/views/LoginView.vue -->
<template>
  <div class="login-container">
    <div class="login-card">
      <h1>EhViewer Web</h1>
      <div class="login-tabs">
        <button 
          v-for="tab in tabs" 
          :key="tab.key"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>
      
      <form @submit.prevent="handleLogin" class="login-form">
        <div v-if="activeTab === 'cookie'" class="form-group">
          <label>Cookie 值</label>
          <textarea 
            v-model="cookieValue" 
            placeholder="ipb_member_id=xxx; ipb_pass_hash=xxx"
            rows="3"
          ></textarea>
        </div>
        
        <div v-if="activeTab === 'account'" class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="用户名" />
          <label>密码</label>
          <input v-model="password" type="password" placeholder="密码" />
        </div>
        
        <div v-if="activeTab === 'apikey'" class="form-group">
          <label>API Key</label>
          <input v-model="apiKey" type="text" placeholder="API Key" />
        </div>
        
        <button type="submit" :disabled="loading" class="login-btn">
          {{ loading ? '登录中...' : '登录' }}
        </button>
        
        <p v-if="error" class="error">{{ error }}</p>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref('cookie')
const cookieValue = ref('')
const username = ref('')
const password = ref('')
const apiKey = ref('')
const loading = ref(false)
const error = ref('')

const tabs = [
  { key: 'cookie', label: 'Cookie' },
  { key: 'account', label: '账号密码' },
  { key: 'apikey', label: 'API Key' },
]

async function handleLogin() {
  loading.value = true
  error.value = ''
  
  try {
    let result
    if (activeTab.value === 'cookie') {
      result = await authApi.loginByCookie(cookieValue.value)
    } else if (activeTab.value === 'account') {
      result = await authApi.loginByAccount(username.value, password.value)
    } else {
      result = await authApi.loginByApiKey(apiKey.value)
    }
    
    if (result.data.success) {
      localStorage.setItem('auth_token', 'true')
      authStore.setLoggedIn(true)
      if (result.data.profile) {
        authStore.setProfile(result.data.profile)
      }
      router.push('/')
    } else {
      error.value = result.data.message
    }
  } catch (e: any) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: #f5f5f5;
}
.login-card {
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.1);
  width: 100%;
  max-width: 400px;
}
h1 {
  text-align: center;
  margin-bottom: 1.5rem;
  color: #333;
}
.login-tabs {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}
.login-tabs button {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #ddd;
  background: #f5f5f5;
  cursor: pointer;
  border-radius: 4px;
}
.login-tabs button.active {
  background: #4a90d9;
  color: white;
  border-color: #4a90d9;
}
.form-group {
  margin-bottom: 1rem;
}
.form-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
}
.form-group input,
.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}
.login-btn {
  width: 100%;
  padding: 0.75rem;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  cursor: pointer;
}
.login-btn:disabled {
  background: #ccc;
}
.error {
  color: #e74c3c;
  text-align: center;
  margin-top: 1rem;
}
</style>
```

- [ ] **步骤 8：创建 HomeView**

```vue
<!-- web-frontend/src/views/HomeView.vue -->
<template>
  <div class="home">
    <AppHeader />
    <div class="content">
      <div class="search-bar">
        <input 
          v-model="keyword" 
          @keyup.enter="search"
          placeholder="搜索画廊..."
        />
        <button @click="search">搜索</button>
      </div>
      
      <GalleryGrid 
        :galleries="galleries" 
        :loading="loading"
        @load-more="loadMore"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { galleryApi } from '../api/gallery'
import type { GalleryListItem } from '../types'
import AppHeader from '../components/layout/AppHeader.vue'
import GalleryGrid from '../components/gallery/GalleryGrid.vue'

const keyword = ref('')
const galleries = ref<GalleryListItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const hasMore = ref(true)

async function search() {
  currentPage.value = 1
  galleries.value = []
  await loadMore()
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  
  try {
    const result = await galleryApi.getList(keyword.value, undefined, currentPage.value)
    galleries.value.push(...result.data.galleries)
    hasMore.value = currentPage.value < result.data.totalPages
    currentPage.value++
  } catch (e) {
    console.error('Failed to load galleries:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadMore()
})
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 1rem;
}
.search-bar {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.search-bar input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 16px;
}
.search-bar button {
  padding: 0.75rem 1.5rem;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
</style>
```

- [ ] **步骤 9：创建 GalleryDetailView**

```vue
<!-- web-frontend/src/views/GalleryDetailView.vue -->
<template>
  <div class="gallery-detail" v-if="detail">
    <AppHeader />
    <div class="content">
      <div class="header">
        <img :src="detail.thumb" class="cover" />
        <div class="info">
          <h1>{{ detail.title }}</h1>
          <p v-if="detail.titleJpn" class="subtitle">{{ detail.titleJpn }}</p>
          <div class="meta">
            <span>⭐ {{ detail.rating.toFixed(1) }}</span>
            <span>📄 {{ detail.pageCount }} 页</span>
            <span>{{ detail.category }}</span>
            <span v-if="detail.simpleLanguage">{{ detail.simpleLanguage }}</span>
          </div>
          <div class="tags" v-if="detail.tags.length">
            <TagChip 
              v-for="(tag, i) in flatTags" 
              :key="i" 
              :tag="tag" 
            />
          </div>
          <div class="actions">
            <button @click="openReader">阅读</button>
            <button @click="download">下载</button>
          </div>
        </div>
      </div>
      
      <div class="previews" v-if="detail.previewSet">
        <h2>预览</h2>
        <div class="preview-grid">
          <img 
            v-for="(img, i) in detail.previewSet.images" 
            :key="i"
            :src="img.thumbUrl"
            @click="openReaderAt(i + 1)"
          />
        </div>
      </div>
      
      <div class="comments" v-if="detail.comments.length">
        <h2>评论 ({{ detail.comments.length }})</h2>
        <div v-for="comment in detail.comments" :key="comment.id" class="comment">
          <strong>{{ comment.uploader }}</strong>
          <span class="score">+{{ comment.score }}</span>
          <p>{{ comment.comment }}</p>
          <span class="time">{{ comment.time }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { galleryApi } from '../api/gallery'
import type { GalleryDetailResponse } from '../types'
import AppHeader from '../components/layout/AppHeader.vue'
import TagChip from '../components/common/TagChip.vue'

const route = useRoute()
const router = useRouter()
const detail = ref<GalleryDetailResponse | null>(null)

const flatTags = computed(() => {
  if (!detail.value) return []
  return detail.value.tags.flatMap(group => 
    group.tags.map(tag => `${group.namespace}:${tag}`)
  )
})

function openReader() {
  router.push(`/reader/${route.params.gid}/1`)
}

function openReaderAt(page: number) {
  router.push(`/reader/${route.params.gid}/${page}`)
}

function download() {
  alert('下载功能将在 Phase 3 实现')
}

onMounted(async () => {
  const gid = Number(route.params.gid)
  const result = await galleryApi.getDetail(gid)
  detail.value = result.data
})
</script>

<style scoped>
.gallery-detail {
  min-height: 100vh;
  background: #f5f5f5;
}
.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1rem;
}
.header {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 2rem;
}
.cover {
  width: 300px;
  border-radius: 8px;
}
.info {
  flex: 1;
}
h1 {
  margin: 0 0 0.5rem;
}
.subtitle {
  color: #666;
  margin-bottom: 1rem;
}
.meta {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
  color: #666;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 1rem;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
.actions button {
  padding: 0.5rem 1rem;
  border: 1px solid #4a90d9;
  background: white;
  color: #4a90d9;
  border-radius: 4px;
  cursor: pointer;
}
.actions button:first-child {
  background: #4a90d9;
  color: white;
}
.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 0.5rem;
}
.preview-grid img {
  width: 100%;
  cursor: pointer;
  border-radius: 4px;
}
.comment {
  background: white;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 0.5rem;
}
.comment .score {
  color: #4a90d9;
  margin-left: 0.5rem;
}
.comment .time {
  color: #999;
  font-size: 12px;
  display: block;
  margin-top: 0.5rem;
}
</style>
```

- [ ] **步骤 10：创建 AppHeader**

```vue
<!-- web-frontend/src/components/layout/AppHeader.vue -->
<template>
  <header class="app-header">
    <div class="header-left">
      <router-link to="/" class="logo">EhViewer</router-link>
    </div>
    <nav class="header-nav">
      <router-link to="/">首页</router-link>
      <router-link to="/downloads">下载</router-link>
      <router-link to="/favorites">收藏</router-link>
      <router-link to="/history">历史</router-link>
      <router-link to="/settings">设置</router-link>
    </nav>
    <div class="header-right">
      <button @click="logout" class="logout-btn">退出</button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { authApi } from '../../api/auth'

const router = useRouter()
const authStore = useAuthStore()

async function logout() {
  await authApi.logout()
  authStore.setLoggedIn(false)
  router.push('/login')
}
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  padding: 0.75rem 1rem;
  background: #333;
  color: white;
}
.header-left .logo {
  color: white;
  text-decoration: none;
  font-weight: bold;
  font-size: 1.25rem;
}
.header-nav {
  display: flex;
  gap: 1rem;
  margin-left: 2rem;
}
.header-nav a {
  color: #ccc;
  text-decoration: none;
}
.header-nav a:hover,
.header-nav a.router-link-active {
  color: white;
}
.header-right {
  margin-left: auto;
}
.logout-btn {
  background: transparent;
  border: 1px solid #666;
  color: #ccc;
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  cursor: pointer;
}
.logout-btn:hover {
  border-color: #999;
  color: white;
}
</style>
```

- [ ] **步骤 11：创建 GalleryGrid**

```vue
<!-- web-frontend/src/components/gallery/GalleryGrid.vue -->
<template>
  <div class="gallery-grid">
    <GalleryCard 
      v-for="gallery in galleries" 
      :key="gallery.gid" 
      :gallery="gallery" 
    />
    <div v-if="loading" class="loading">加载中...</div>
    <div ref="sentinel" class="sentinel"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { GalleryListItem } from '../../types'
import GalleryCard from './GalleryCard.vue'

const props = defineProps<{
  galleries: GalleryListItem[]
  loading: boolean
}>()

const emit = defineEmits<{
  loadMore: []
}>()

const sentinel = ref<HTMLElement>()

onMounted(() => {
  if (!sentinel.value) return
  const observer = new IntersectionObserver(entries => {
    if (entries[0].isIntersecting) {
      emit('loadMore')
    }
  })
  observer.observe(sentinel.value)
  
  onUnmounted(() => observer.disconnect())
})
</script>

<style scoped>
.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

@media (min-width: 640px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }
}

@media (min-width: 1024px) {
  .gallery-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
}

.loading {
  grid-column: 1 / -1;
  text-align: center;
  padding: 2rem;
  color: #666;
}

.sentinel {
  height: 1px;
}
</style>
```

- [ ] **步骤 12：创建 GalleryCard**

```vue
<!-- web-frontend/src/components/gallery/GalleryCard.vue -->
<template>
  <router-link :to="`/gallery/${gallery.gid}`" class="gallery-card">
    <div class="thumb-wrapper">
      <img :src="gallery.thumb" :alt="gallery.title" loading="lazy" />
      <span class="category" :style="{ background: categoryColor }">{{ gallery.category }}</span>
    </div>
    <div class="info">
      <h3>{{ gallery.title }}</h3>
      <div class="meta">
        <span class="rating">⭐ {{ gallery.rating.toFixed(1) }}</span>
        <span v-if="gallery.simpleLanguage" class="lang">{{ gallery.simpleLanguage }}</span>
      </div>
    </div>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { GalleryListItem } from '../../types'

const props = defineProps<{
  gallery: GalleryListItem
}>()

const categoryColor = computed(() => {
  const colors: Record<string, string> = {
    'Doujinshi': '#e74c3c',
    'Manga': '#3498db',
    'Artist CG': '#9b59b6',
    'Western': '#f39c12',
    'Non-H': '#2ecc71',
    'Image Set': '#1abc9c',
    'Cosplay': '#e67e22',
    'Asian Porn': '#95a5a6',
    'Misc': '#7f8c8d',
  }
  return colors[props.gallery.category] || '#7f8c8d'
})
</script>

<style scoped>
.gallery-card {
  display: block;
  text-decoration: none;
  color: inherit;
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transition: box-shadow 0.2s;
}
.gallery-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
.thumb-wrapper {
  position: relative;
  aspect-ratio: 2/3;
  overflow: hidden;
}
.thumb-wrapper img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.category {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  color: white;
}
.info {
  padding: 8px;
}
h3 {
  font-size: 13px;
  line-height: 1.3;
  margin: 0 0 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #666;
}
</style>
```

- [ ] **步骤 13：创建 TagChip**

```vue
<!-- web-frontend/src/components/common/TagChip.vue -->
<template>
  <span class="tag-chip" :class="namespace">{{ tag }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  tag: string
}>()

const namespace = computed(() => {
  const parts = props.tag.split(':')
  return parts.length > 0 ? parts[0] : 'misc'
})
</script>

<style scoped>
.tag-chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 12px;
  background: #e0e0e0;
  color: #333;
}
.tag-chip.artist { background: #e74c3c; color: white; }
.tag-chip.language { background: #3498db; color: white; }
.tag-chip.female { background: #e91e63; color: white; }
.tag-chip.male { background: #2196f3; color: white; }
.tag-chip.parody { background: #9c27b0; color: white; }
.tag-chip.character { background: #ff9800; color: white; }
.tag-chip.group { background: #4caf50; color: white; }
.tag-chip.misc { background: #607d8b; color: white; }
</style>
```

- [ ] **步骤 14：安装前端依赖并验证构建**

运行：`cd web-frontend && npm install && npm run build`
预期：构建成功，输出到 `ehviewer-web/src/main/resources/static/`

- [ ] **步骤 15：Commit**

```bash
git add web-frontend/
git commit -m "feat: add Vue 3 frontend skeleton (login, home, gallery detail)"
```

---

## 任务 18：端到端验证

**文件：** 无新文件

- [ ] **步骤 1：启动后端**

运行：`./gradlew :ehviewer-web:bootRun`
预期：Spring Boot 启动成功，监听 8080 端口

- [ ] **步骤 2：启动前端开发服务器**

运行：`cd web-frontend && npm run dev`
预期：Vite 开发服务器启动，监听 3000 端口

- [ ] **步骤 3：验证 API 可访问**

运行：`curl http://localhost:8080/api/v1/auth/status`
预期：返回 JSON 响应

- [ ] **步骤 4：验证前端可访问**

打开浏览器访问 `http://localhost:3000`
预期：显示登录页面

- [ ] **步骤 5：Commit（如有修复）**

```bash
git add -A
git commit -m "fix: resolve end-to-end issues"
```

---

## 总结

Phase 1 完成后，系统将具备：
- ehviewer-core 核心库（从 Android 移植，替换 Android 依赖）
- Spring Boot 后端（认证、画廊、图片代理 API）
- Vue 3 前端（登录、首页搜索、画廊详情页）
- SQLite 数据库（12 个 JPA Entity）
- WebSocket 基础设施

下一步 Phase 2 将实现阅读器组件、收藏管理和评论功能。
