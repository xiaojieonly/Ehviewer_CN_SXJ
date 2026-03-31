# 翻译文件管理脚本

这些脚本用于管理Android项目的翻译文件（strings.xml）。

## 脚本说明

### 1. fill_and_format_translations.py

这个脚本用于：
- 根据基础strings.xml文件补全其他语言的XML文件
- 格式化所有XML文档

#### 使用方法

```bash
# 补全并格式化所有翻译文件
python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml

# 只显示将要进行的更改，不实际修改文件（模拟运行）
python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml --dry-run
```

#### 功能说明

1. **补全缺失字符串**：脚本会检查所有语言文件，找出基础文件中存在但语言文件中缺失的字符串，并自动添加
2. **格式化XML**：脚本会格式化所有语言文件和配置覆盖文件，使其具有统一的格式
3. **区分语言文件和配置文件**：
   - 语言文件：values-de、values-en、values-es、values-fr、values-ja、values-ko、values-th、values-zh-rCN、values-zh-rHK、values-zh-rTW
   - 配置覆盖文件：values-land、values-large、values-night、values-sw*、values-v*

### 2. add_missing_strings.py

这个脚本专门用于将基础strings.xml中的字符串添加到其他语言文件中。

#### 使用方法

```bash
# 将缺失的字符串添加到所有语言文件
python scripts/add_missing_strings.py --base app/src/main/res/values/strings.xml

# 只显示将要进行的更改，不实际修改文件（模拟运行）
python scripts/add_missing_strings.py --base app/src/main/res/values/strings.xml --dry-run
```

### 3. translate_strings_online.py

这个脚本使用在线翻译API服务翻译strings.xml文件。

#### 支持的API

- **Google翻译**：免费，无需API密钥
- **百度翻译**：需要API密钥（appid|secret_key格式）
- **DeepL翻译**：需要API密钥

#### 使用方法

```bash
# 使用Google翻译（自动查找基础文件）
python scripts/translate_strings_online.py --api-type google

# 指定基础文件路径
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google

# 使用百度翻译（需要API密钥）
python scripts/translate_strings_online.py --api-type baidu --api-key "your_appid|your_secret_key"

# 使用DeepL翻译（需要API密钥）
python scripts/translate_strings_online.py --api-type deepl --api-key "your_deepl_api_key"

# 只显示将要进行的翻译，不实际修改文件（模拟运行）
python scripts/translate_strings_online.py --api-type google --dry-run
```

### 4. translate_strings_deepseek.py

这个脚本使用Deepseek API翻译strings.xml文件。

#### 使用方法

```bash
# 使用Deepseek翻译（自动查找基础文件）
python scripts/translate_strings_deepseek.py --api-key "your_deepseek_api_key"

# 指定基础文件路径
python scripts/translate_strings_deepseek.py --base app/src/main/res/values/strings.xml --api-key "your_deepseek_api_key"

# 指定模型
python scripts/translate_strings_deepseek.py --api-key "your_deepseek_api_key" --model deepseek-chat

# 只显示将要进行的翻译，不实际修改文件（模拟运行）
python scripts/translate_strings_deepseek.py --api-key "your_deepseek_api_key" --dry-run
```

### 5. translate_strings_local_ai.py

这个脚本使用本地AI模型翻译strings.xml文件。

#### 依赖安装

```bash
pip install transformers torch
```

#### 使用方法

```bash
# 使用Qwen模型（自动查找基础文件）
python scripts/translate_strings_local_ai.py --model Qwen/Qwen2.5-7B-Instruct

# 指定基础文件路径
python scripts/translate_strings_local_ai.py --base app/src/main/res/values/strings.xml --model Qwen/Qwen2.5-7B-Instruct

# 使用CPU运行
python scripts/translate_strings_local_ai.py --model Qwen/Qwen2.5-7B-Instruct --device cpu

# 只显示将要进行的翻译，不实际修改文件（模拟运行）
python scripts/translate_strings_local_ai.py --model Qwen/Qwen2.5-7B-Instruct --dry-run
```

## 工作流程

### 管理翻译文件

1. **添加新字符串**：
   - 在基础文件 `app/src/main/res/values/strings.xml` 中添加新的字符串
   - 运行脚本将新字符串添加到所有语言文件

2. **格式化文件**：
   - 运行脚本格式化所有XML文件，保持一致的格式

3. **检查差异**：
   - 使用 `--dry-run` 选项查看将要进行的更改
   - 确认无误后再实际运行

### 翻译文件

1. **在线翻译**：
   - 使用Google翻译（免费）或百度/DeepL翻译（需要API密钥）
   - 运行翻译脚本将基础文件中的字符串翻译成其他语言

2. **Deepseek翻译**：
   - 使用Deepseek API进行翻译
   - 需要Deepseek API密钥

3. **本地AI翻译**：
   - 使用本地AI模型进行翻译
   - 需要安装transformers和torch库
   - 首次使用需要下载模型文件

## 注意事项

1. **复数形式**：脚本会处理复数形式（plurals）的字符串
2. **配置覆盖文件**：脚本不会修改配置覆盖文件（如values-land、values-large等）中的字符串，只会格式化它们
3. **XML格式**：脚本会自动格式化XML文件，添加适当的缩进和换行
4. **API限制**：在线翻译API通常有调用频率限制，请合理使用
5. **本地AI资源**：本地AI模型需要较多的内存和GPU资源，请根据设备情况选择合适的模型

## 示例

### 管理翻译文件

假设你在基础文件中添加了一个新字符串：

```xml
<string name="new_string">New String</string>
```

运行脚本后，所有语言文件都会自动添加这个字符串：

```bash
python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml
```

### 翻译文件

使用Google翻译将基础文件翻译成其他语言：

```bash
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google
```

使用Deepseek翻译：

```bash
python scripts/translate_strings_deepseek.py --base app/src/main/res/values/strings.xml --api-key "your_deepseek_api_key"
```

使用本地AI翻译：

```bash
python scripts/translate_strings_local_ai.py --base app/src/main/res/values/strings.xml --model Qwen/Qwen2.5-7B-Instruct
```