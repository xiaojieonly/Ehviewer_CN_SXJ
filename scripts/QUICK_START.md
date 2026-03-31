# 快速开始指南

## 安装依赖

```bash
# 安装在线翻译脚本依赖
pip install requests

# 安装本地AI翻译脚本依赖（可选）
pip install transformers torch
```

## 快速翻译

### 使用Google翻译（推荐新手）

```bash
# 1. 补全缺失的字符串
python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml

# 2. 使用Google翻译（免费，自动查找基础文件）
python scripts/translate_strings_online.py --api-type google
```

### 使用Deepseek翻译（需要API密钥）

```bash
# 1. 获取Deepseek API密钥
# 访问 https://www.deepseek.com/ 注册并获取API密钥

# 2. 翻译文件（自动查找基础文件）
python scripts/translate_strings_deepseek.py --api-key YOUR_API_KEY
```

### 使用本地AI翻译（离线）

```bash
# 1. 安装依赖
pip install transformers torch

# 2. 翻译文件（自动查找基础文件，首次会下载模型）
python scripts/translate_strings_local_ai.py --model Qwen/Qwen2.5-7B-Instruct
```

## 预览更改

使用 `--dry-run` 选项预览更改而不实际修改文件：

```bash
python scripts/translate_strings_online.py --base app/src/main/res/values/strings.xml --api-type google --dry-run
```

## 检查翻译结果

翻译完成后，检查翻译质量：

```bash
# 查看中文文件的更改
git diff app/src/main/res/values-zh-rCN/strings.xml

# 查看英文文件的更改
git diff app/src/main/res/values-en/strings.xml
```

## 格式化文件

翻译完成后，格式化所有XML文件：

```bash
python scripts/fill_and_format_translations.py --base app/src/main/res/values/strings.xml
```

## 提交更改

```bash
git add .
git commit -m "Update translations"
git push origin main
```

## 常见问题

### Q: 翻译脚本运行很慢怎么办？

A: 在线翻译受网络速度影响，本地AI翻译受设备性能影响。可以：
1. 使用 `--dry-run` 预览，确认后再实际运行
2. 分批翻译，先翻译部分文件

### Q: 翻译质量不理想怎么办？

A: 
1. 尝试不同的翻译API（Google、DeepL、Deepseek）
2. 手动检查和调整翻译结果
3. 对于专业术语，建议手动翻译

### Q: 如何获取API密钥？

A:
- **百度翻译**：访问 [百度翻译开放平台](https://api.fanyi.baidu.com/)
- **DeepL**：访问 [DeepL API](https://www.deepl.com/pro-api)
- **Deepseek**：访问 [Deepseek平台](https://www.deepseek.com/)

### Q: 本地AI翻译需要什么配置？

A:
- **GPU推荐**：至少8GB显存（如RTX 3070）
- **CPU模式**：可以运行但速度较慢
- **内存**：至少16GB RAM

## 下一步

1. 阅读 [翻译指南](TRANSLATION_GUIDE.md) 了解详细使用方法
2. 查看 [脚本说明](README.md) 了解所有脚本功能
3. 运行 [示例脚本](example_usage.py) 学习使用方法

## 获取帮助

如果遇到问题：
1. 查看 [故障排除](TRANSLATION_GUIDE.md#故障排除) 部分
2. 提交Issue报告问题
3. 查阅相关文档