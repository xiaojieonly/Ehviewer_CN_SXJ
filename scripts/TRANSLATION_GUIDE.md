# 翻译指南

本指南介绍如何使用各种翻译脚本来翻译Android项目的strings.xml文件。

## 准备工作

### 1. 安装依赖

对于在线翻译脚本，需要安装requests库：

```bash
pip install requests
```

对于本地AI翻译脚本，需要安装transformers和torch：

```bash
pip install transformers torch
```

### 2. 获取API密钥（可选）

- **百度翻译**：访问[百度翻译开放平台](https://api.fanyi.baidu.com/)注册并获取API密钥
- **DeepL翻译**：访问[DeepL API](https://www.deepl.com/pro-api)注册并获取API密钥
- **Deepseek**：访问[Deepseek平台](https://www.deepseek.com/)注册并获取API密钥

## 使用在线翻译API

### Google翻译（免费）

Google翻译无需API密钥，可以直接使用：

```bash
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google
```

### 百度翻译

需要提供API密钥（格式：appid|secret_key）：

```bash
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type baidu --api-key "your_appid|your_secret_key"
```

### DeepL翻译

需要提供API密钥：

```bash
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type deepl --api-key "your_deepl_api_key"
```

## 使用Deepseek API

Deepseek是一个强大的AI翻译服务，提供高质量的翻译结果：

```bash
python scripts/translate_strings_deepseek.py --base app/src/main/res/values/strings.xml --api-key "your_deepseek_api_key"
```

### 高级选项

```bash
# 指定模型
python scripts/translate_strings_deepseek.py --base app/src/main/res/values/strings.xml --api-key "your_deepseek_api_key" --model deepseek-chat

# 模拟运行（不实际修改文件）
python scripts/translate_strings_deepseek.py --base app/src/main/res/values/strings.xml --api-key "your_deepseek_api_key" --dry-run
```

## 使用本地AI模型

本地AI翻译适合需要离线工作或对隐私有要求的场景。

### 安装依赖

```bash
pip install transformers torch
```

### 下载模型

首次使用需要下载模型文件。以Qwen2.5-7B-Instruct为例：

```bash
# 使用Python下载
python -c "from transformers import AutoTokenizer, AutoModelForCausalLM; AutoTokenizer.from_pretrained('Qwen/Qwen2.5-7B-Instruct'); AutoModelForCausalLM.from_pretrained('Qwen/Qwen2.5-7B-Instruct')"
```

### 使用模型翻译

```bash
# 使用GPU（需要CUDA支持）
python scripts/translate_strings_local_ai.py --base app/src/main/res/values/strings.xml --model Qwen/Qwen2.5-7B-Instruct

# 使用CPU
python scripts/translate_strings_local_ai.py --base app/src/main/res/values/strings.xml --model Qwen/Qwen2.5-7B-Instruct --device cpu
```

### 可用模型

以下是一些推荐的翻译模型：

1. **Qwen/Qwen2.5-7B-Instruct**：阿里云的通义千问模型，支持多语言翻译
2. **Qwen/Qwen2.5-14B-Instruct**：更大规模的通义千问模型，翻译质量更高
3. **THUDM/chatglm3-6b**：清华的ChatGLM模型，支持中文翻译
4. **baichuan-inc/Baichuan2-7B-Chat**：百川智能的模型，支持多语言

## 翻译流程

### 1. 准备基础文件

确保`app/src/main/res/values/strings.xml`是最新版本，包含所有需要翻译的字符串。

### 2. 选择翻译方式

根据需求选择合适的翻译方式：
- **快速翻译**：使用Google翻译（免费）
- **高质量翻译**：使用DeepL或Deepseek（需要API密钥）
- **离线翻译**：使用本地AI模型

### 3. 运行翻译脚本

```bash
# 示例：使用Google翻译
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google
```

### 4. 检查翻译结果

翻译完成后，检查各个语言文件的翻译质量，必要时进行手动调整。

### 5. 格式化文件

使用`fill_and_format_translations.py`脚本格式化所有XML文件：

```bash
python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml
```

## 注意事项

### 1. API限制

- **Google翻译**：免费但有使用限制
- **百度翻译**：每月有免费额度，超出后收费
- **DeepL**：每月有免费额度，超出后收费
- **Deepseek**：按调用次数收费

### 2. 翻译质量

- 在线翻译API的翻译质量通常较好，但可能需要手动调整
- 本地AI模型的翻译质量取决于模型大小和训练数据
- 对于专业术语，建议手动检查和调整

### 3. 性能考虑

- 在线翻译API：受网络速度影响
- 本地AI模型：需要较多的内存和GPU资源
- 建议在翻译大量字符串时使用批量处理

### 4. 隐私考虑

- 在线翻译API会将文本发送到服务器
- 如果处理敏感信息，建议使用本地AI模型

## 故障排除

### 1. 翻译失败

- 检查网络连接
- 检查API密钥是否正确
- 检查API配额是否用完

### 2. 模型加载失败

- 检查是否安装了transformers和torch
- 检查设备是否支持CUDA（如果使用GPU）
- 尝试使用较小的模型

### 3. XML解析错误

- 检查XML文件格式是否正确
- 确保文件使用UTF-8编码

## 示例工作流

### 完整翻译流程

1. **准备基础文件**：
   ```bash
   # 确保基础文件是最新的
   git pull origin main
   ```

2. **运行翻译脚本**：
   ```bash
   # 使用Google翻译
   python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google
   ```

3. **检查翻译结果**：
   ```bash
   # 查看翻译后的文件
   git diff app/src/main/res/values-zh-rCN/strings.xml
   ```

4. **格式化文件**：
   ```bash
   python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml
   ```

5. **提交更改**：
   ```bash
   git add .
   git commit -m "Update translations"
   git push origin main
   ```

## 性能优化

### 1. 批量翻译

对于大量字符串，建议使用批量翻译以减少API调用次数：

```bash
# 脚本会自动批量处理字符串
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google
```

### 2. 缓存机制

脚本会自动缓存已翻译的字符串，避免重复翻译：

```python
# 在翻译器中实现缓存
self.translated_cache = {}
```

### 3. 并发处理

对于本地AI模型，可以考虑使用并发处理提高速度：

```python
# 使用多线程或异步处理
import concurrent.futures
```

## 总结

这些翻译脚本提供了多种翻译方式，可以根据需求选择合适的工具：
- **在线翻译API**：快速、方便，适合日常使用
- **Deepseek API**：高质量翻译，适合专业需求
- **本地AI模型**：离线工作，适合隐私敏感场景

通过合理使用这些工具，可以大大提高翻译效率和质量。